package com.pontat.registreboucles.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.pontat.registreboucles.importer.BackupExporter
import com.pontat.registreboucles.importer.JsonExporter
import com.pontat.registreboucles.widget.BoucleWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.io.File

/** Échec d'une sauvegarde de sécurité (écriture fichier impossible). */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

private const val TAG = "BoucleRepository"

/**
 * Point d'accès unique aux données. Toute écriture sur la table `boucles`
 * (insert / update / delete / import) déclenche la mise à jour du widget Glance.
 */
class BoucleRepository(
    private val dao: BoucleDao,
    private val appContext: Context
) {

    fun observerToutes(): Flow<List<Boucle>> = dao.observerToutes()

    fun observerParId(id: String): Flow<Boucle?> = dao.observerParId(id)

    fun observerMouvements(boucleId: String): Flow<List<Mouvement>> =
        dao.observerMouvements(boucleId)

    fun observerDernieresModifs(): Flow<List<DerniereModifRow>> =
        dao.observerDernieresModifs()

    suspend fun obtenir(id: String): Boucle? = dao.obtenir(id)

    suspend fun estVide(): Boolean = dao.compter() == 0

    suspend fun tousLesIds(): List<String> = dao.tousLesIds()

    suspend fun creer(boucle: Boucle) {
        dao.upsert(boucle)
        rafraichirWidget()
    }

    suspend fun mettreAJour(boucle: Boucle) {
        dao.mettreAJour(boucle)
        rafraichirWidget()
    }

    suspend fun supprimer(boucle: Boucle) {
        dao.supprimer(boucle)
        rafraichirWidget()
    }

    // Adaptateur DAO pour la logique de clôture (testée à part dans executerCloture).
    private val clotureStore = object : ClotureStore {
        override suspend fun insererJournal(journal: Journal) { dao.insererJournal(journal) }
        override suspend fun obtenirBoucle(id: String): Boucle? = dao.obtenir(id)
        override suspend fun mettreAJourBoucle(boucle: Boucle) { dao.mettreAJour(boucle) }
    }

    /**
     * UNIQUE clôture d'une boucle. Exige une entrée Journal (type + texte) : la
     * transition vers `fermee` est indissociable de la création du journal
     * (cf. [executerCloture]). Déclenche ensuite un backup versionné.
     */
    suspend fun cloturer(id: String, type: JournalType, texte: String) {
        executerCloture(clotureStore, id, type, texte, System.currentTimeMillis())
        creerBackup()          // backup complet à chaque clôture
        rafraichirWidget()
    }

    fun observerJournaux(boucleId: String): Flow<List<Journal>> = dao.observerJournaux(boucleId)

    /**
     * Ajoute un mouvement. N'affecte pas la table `boucles`, donc pas de
     * rafraîchissement du widget (compteurs et échéances inchangés).
     */
    suspend fun ajouterMouvement(mouvement: Mouvement) {
        dao.insererMouvement(mouvement)
    }

    /**
     * "Ajouter" : n'insère que les boucles dont l'id est absent de la base,
     * avec leurs mouvements. Les boucles déjà présentes (et leurs mouvements /
     * clôtures faits dans l'app) ne sont PAS touchées. Renvoie le nombre ajouté.
     */
    suspend fun importerAjouter(
        boucles: List<Boucle>,
        mouvements: List<Mouvement>,
        journaux: List<Journal>
    ): Int {
        creerBackupStrict()   // filet AVANT toute écriture : échec = import annulé
        val existants = dao.tousLesIds().toSet()
        val nouvelles = boucles.filter { it.id !in existants }
        val nouveauxIds = nouvelles.map { it.id }.toSet()
        val nouveauxMouvements = mouvements.filter { it.boucleId in nouveauxIds }
        val nouveauxJournaux = journaux.filter { it.boucleId in nouveauxIds }
        dao.upsertToutes(nouvelles)
        dao.insererMouvements(nouveauxMouvements)
        dao.insererJournaux(completerJournaux(nouvelles, nouveauxJournaux))
        rafraichirWidget()
        return nouvelles.size
    }

    /**
     * "Écraser" (aussi utilisé pour l'import initial d'une base vide) :
     * vide boucles + mouvements + journaux puis réinsère (restauration backup incluse).
     */
    suspend fun importerEcraser(
        boucles: List<Boucle>,
        mouvements: List<Mouvement>,
        journaux: List<Journal>
    ) {
        creerBackupStrict()   // filet AVANT le vidage : échec = import annulé
        dao.supprimerTousJournaux()
        dao.supprimerTousMouvements()
        dao.supprimerToutesBoucles()
        dao.upsertToutes(boucles)
        dao.insererMouvements(mouvements)
        dao.insererJournaux(completerJournaux(boucles, journaux))
        rafraichirWidget()
    }

    /**
     * Complète les journaux importés : toute boucle FERMÉE sans entrée journal
     * reçoit une entrée par défaut (l'invariant « aucune fermeture sans journal »
     * vaut aussi pour les données importées/restaurées).
     */
    private fun completerJournaux(boucles: List<Boucle>, journaux: List<Journal>): List<Journal> {
        val avecJournal = journaux.mapTo(HashSet()) { it.boucleId }
        val maintenant = System.currentTimeMillis()
        val defauts = boucles
            // Les DEUX états terminaux (fermee ET defaut_applique) exigent un journal.
            .filter { it.estTerminal() && it.id !in avecJournal }
            .map {
                Journal(
                    boucleId = it.id,
                    date = maintenant,
                    type = JournalType.DECLARATION.name,
                    texte = "Clôture importée (sans preuve d'origine)"
                )
            }
        return journaux + defauts
    }

    /**
     * Sérialise l'état courant (boucles + mouvements) au schéma d'import et
     * l'écrit dans l'URI fourni par ACTION_CREATE_DOCUMENT. Renvoie le succès.
     */
    suspend fun exporterVers(uri: Uri): Boolean = try {
        val contenu = JsonExporter.serialiser(
            dao.toutesLesBoucles(),
            dao.tousLesMouvements(),
            dao.tousLesJournaux()
        )
        val ok = appContext.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(contenu.toByteArray(Charsets.UTF_8))
            true
        } ?: false
        ok
    } catch (e: Exception) {
        false
    }

    /**
     * Backup local versionné complet (Boucle + Journal). Stockage app-scoped
     * externe (aucune permission runtime). Rotation : garde les 10 plus récents.
     * STRICT : lève [BackupException] (loggée) si l'écriture échoue — utilisé
     * comme filet avant tout import destructif et par la sauvegarde manuelle.
     */
    suspend fun creerBackupStrict(): File {
        val contenu = BackupExporter.serialiser(
            dao.toutesLesBoucles(),
            dao.tousLesMouvements(),
            dao.tousLesJournaux()
        )
        val dossier = File(appContext.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val fichier = File(dossier, "boucles-backup-${System.currentTimeMillis()}.json")
        try {
            fichier.writeText(contenu)
        } catch (e: Exception) {
            Log.e(TAG, "Sauvegarde impossible (${fichier.name})", e)
            throw BackupException(
                "Sauvegarde impossible : ${e.message ?: e.javaClass.simpleName}", e
            )
        }
        // Rotation : le timestamp à largeur fixe rend le tri par nom chronologique.
        // Un échec de rotation ne compromet pas la sauvegarde déjà écrite.
        try {
            dossier.listFiles { f ->
                f.name.startsWith("boucles-backup-") && f.name.endsWith(".json")
            }?.sortedByDescending { it.name }
                ?.drop(10)
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Rotation des backups impossible", e)
        }
        return fichier
    }

    /**
     * Backup best-effort (clôture automatique) : loggue et renvoie null en cas
     * d'échec sans interrompre le flux appelant.
     */
    suspend fun creerBackup(): File? = try {
        creerBackupStrict()
    } catch (e: Exception) {
        null
    }

    /** Fichier de backup le plus récent (ou null si aucun). */
    fun dernierBackupFichier(): File? {
        val dossier = File(appContext.getExternalFilesDir(null), "backups")
        return dossier.listFiles { f ->
            f.name.startsWith("boucles-backup-") && f.name.endsWith(".json")
        }?.maxByOrNull { it.name }
    }

    /**
     * Écrit le backup le plus récent (ou en crée un à la volée si aucun) vers
     * l'URI choisi (ACTION_CREATE_DOCUMENT), pour sortir la sauvegarde du
     * stockage app-scoped. Renvoie null si succès, sinon un message d'erreur.
     */
    suspend fun exporterDernierBackupVers(uri: Uri): String? {
        return try {
            val fichier = dernierBackupFichier() ?: creerBackupStrict()
            val contenu = fichier.readText()
            val flux = appContext.contentResolver.openOutputStream(uri)
                ?: return "Impossible d'ouvrir le fichier de destination."
            flux.use { it.write(contenu.toByteArray(Charsets.UTF_8)) }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Export du dernier backup impossible", e)
            e.message ?: "Export impossible."
        }
    }

    /** Statistiques pour le widget. */
    suspend fun compterActives(): Int = dao.compterActives()

    suspend fun prochainesEcheances(limite: Int): List<Boucle> =
        dao.prochainesEcheances(limite)

    private suspend fun rafraichirWidget() {
        BoucleWidget().updateAll(appContext)
    }

    // ── Préférence de thème (mode sombre) ──
    private val prefs
        get() = appContext.getSharedPreferences("registre-prefs", Context.MODE_PRIVATE)

    fun lireModeSombre(): Boolean = prefs.getBoolean("mode_sombre", false)

    fun ecrireModeSombre(actif: Boolean) {
        prefs.edit().putBoolean("mode_sombre", actif).apply()
    }

    // ── Options des listes à choix unique (Type / Tiers / Milieu) ──
    private val jsonOptions = Json { ignoreUnknownKeys = true }

    fun lireOptions(): ListeOptions {
        val brut = prefs.getString("liste_options", null) ?: return ListeOptions()
        return try {
            jsonOptions.decodeFromString(ListeOptions.serializer(), brut)
        } catch (e: Exception) {
            ListeOptions()
        }
    }

    fun ecrireOptions(options: ListeOptions) {
        prefs.edit()
            .putString("liste_options", jsonOptions.encodeToString(ListeOptions.serializer(), options))
            .apply()
    }
}
