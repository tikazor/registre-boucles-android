package com.pontat.registreboucles.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

private const val TAG = "DossierSync"

/** Préfixe et extension des fichiers d'état échangés dans le dossier partagé. */
private const val PREFIXE = "etat-"
private const val EXTENSION = ".json"

/** Nom du fichier d'état d'un appareil. Un appareil n'écrit que celui-ci (I11). */
fun nomFichierEtat(codeAppareil: String): String = "$PREFIXE$codeAppareil$EXTENSION"

/**
 * Code appareil porté par un nom de fichier d'état, ou null si le nom n'en est
 * pas un. Pur, donc testable : c'est ce filtre qui décide ce que la
 * synchronisation accepte de lire dans un dossier par ailleurs quelconque.
 *
 * Tolère les suffixes de duplication ajoutés par les applications de synchro
 * (`etat-PRO (1).json`, `etat-PRO.json.conflict`) en les REFUSANT : un fichier
 * dupliqué est un fichier dont on ne sait pas s'il est à jour.
 */
fun codeDepuisNomFichier(nom: String): String? {
    val n = nom.trim()
    if (!n.startsWith(PREFIXE) || !n.endsWith(EXTENSION)) return null
    val code = n.removePrefix(PREFIXE).removeSuffix(EXTENSION)
    return normaliserCodeAppareil(code)?.takeIf { it == code }
}

/** Fichier d'état repéré dans le dossier partagé. */
data class FichierEtat(val nom: String, val uri: Uri, val codeAppareil: String)

/**
 * Accès au dossier partagé via SAF. L'application ne parle jamais au réseau : le
 * dossier est un dossier local, qu'une application tierce (Nextcloud, Syncthing,
 * Drive) réplique de son côté. Nous ne faisons que lire et écrire des fichiers.
 */
class DossierSyncSaf(private val appContext: Context) {

    /**
     * Fichiers d'état présents, hors le nôtre. Un nom non conforme est ignoré en
     * silence — le dossier peut contenir tout autre chose.
     */
    fun listerEtats(dossier: Uri, codeLocal: String): List<FichierEtat> {
        val enfants = DocumentsContract.buildChildDocumentsUriUsingTree(
            dossier, DocumentsContract.getTreeDocumentId(dossier)
        )
        val trouves = mutableListOf<FichierEtat>()
        try {
            appContext.contentResolver.query(
                enfants,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0)
                    val nom = c.getString(1) ?: continue
                    val code = codeDepuisNomFichier(nom) ?: continue
                    if (code == codeLocal) continue          // on ne se relit pas
                    trouves += FichierEtat(
                        nom = nom,
                        uri = DocumentsContract.buildDocumentUriUsingTree(dossier, docId),
                        codeAppareil = code
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lecture du dossier impossible", e)
            throw SyncException("Dossier partagé illisible : ${e.message ?: e.javaClass.simpleName}", e)
        }
        return trouves.sortedBy { it.codeAppareil }
    }

    /** Contenu texte d'un fichier d'état. */
    fun lire(uri: Uri): String =
        appContext.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw SyncException("Fichier d'état illisible.")

    /**
     * Écrit NOTRE fichier d'état. Écriture par troncature (`wt`) du même document
     * quand il existe déjà, création sinon.
     *
     * Pourquoi pas « fichier temporaire puis renommage » : SAF n'offre aucun
     * remplacement atomique. `DocumentsContract.renameDocument` n'écrase pas une
     * cible existante (les fournisseurs créent `etat-B (1).json`) et reste
     * optionnel — un fournisseur peut ne pas déclarer FLAG_SUPPORTS_RENAME. La
     * séquence « supprimer puis renommer » ouvrirait une fenêtre où le fichier
     * n'existe pas, ce qui est pire pour une application de synchro qui observe le
     * dossier. On assume donc une écriture non atomique, protégée par la lecture :
     * un fichier tronqué ne parse pas, il est refusé et consigné au journal de
     * synchronisation, jamais fusionné à moitié.
     */
    fun ecrireEtat(dossier: Uri, codeLocal: String, contenu: String) {
        val nom = nomFichierEtat(codeLocal)
        try {
            val existant = uriDe(dossier, nom)
            val cible = existant ?: DocumentsContract.createDocument(
                appContext.contentResolver, dossierDocumentUri(dossier), "application/json", nom
            ) ?: throw SyncException("Création de $nom impossible dans le dossier partagé.")
            appContext.contentResolver.openOutputStream(cible, "wt")?.use {
                it.write(contenu.toByteArray(Charsets.UTF_8))
            } ?: throw SyncException("Écriture de $nom impossible.")
        } catch (e: SyncException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Écriture de l'état local impossible", e)
            throw SyncException("Écriture de $nom impossible : ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /** URI du document portant ce nom dans le dossier, ou null s'il n'existe pas. */
    private fun uriDe(dossier: Uri, nom: String): Uri? {
        val enfants = DocumentsContract.buildChildDocumentsUriUsingTree(
            dossier, DocumentsContract.getTreeDocumentId(dossier)
        )
        appContext.contentResolver.query(
            enfants,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == nom) {
                    return DocumentsContract.buildDocumentUriUsingTree(dossier, c.getString(0))
                }
            }
        }
        return null
    }

    private fun dossierDocumentUri(dossier: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(dossier, DocumentsContract.getTreeDocumentId(dossier))

    /** Nom lisible du dossier choisi, pour l'afficher dans l'écran. */
    fun nomDossier(dossier: Uri): String? = try {
        appContext.contentResolver.query(
            dossierDocumentUri(dossier),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        Log.e(TAG, "Nom du dossier illisible", e)
        null
    }
}

/** Échec d'une synchronisation, avec un message affichable tel quel. */
class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)
