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

/** Anti-rafale : en deçà de ce délai, un backup non forcé réutilise le dernier fichier. */
private const val ANTI_RAFALE_MS = 5 * 60 * 1000L

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

    /**
     * Estampille une boucle avant écriture : qui l'a écrite, et quand. C'est
     * l'UNIQUE endroit où `modifieeLe` / `modifieePar` sont posés — jamais
     * depuis l'UI ni le ViewModel. Toute écriture de boucle passe par ici
     * (création, modification, transition de statut, acceptation, import).
     */
    private fun estampiller(boucle: Boucle, maintenant: Long = System.currentTimeMillis()): Boucle =
        boucle.copy(modifieeLe = maintenant, modifieePar = codeAppareilOuInconnu())

    private fun estampiller(boucles: List<Boucle>): List<Boucle> {
        val maintenant = System.currentTimeMillis()
        return boucles.map { estampiller(it, maintenant) }
    }

    suspend fun creer(boucle: Boucle) {
        dao.upsert(estampiller(boucle))
        rafraichirWidget()
    }

    suspend fun mettreAJour(boucle: Boucle) {
        dao.mettreAJour(estampiller(boucle))
        rafraichirWidget()
    }

    /**
     * Supprime une boucle en laissant une tombstone (invariant I10) : la trace
     * est écrite avant l'effacement, dans la même transaction. Sans elle, la
     * boucle serait ressuscitée par le premier import venant d'un autre appareil.
     */
    suspend fun supprimer(boucle: Boucle) {
        dao.supprimerAvecTrace(
            boucle,
            traceSuppression(boucle.id, codeAppareilOuInconnu(), System.currentTimeMillis())
        )
        rafraichirWidget()
    }

    // Adaptateur DAO pour la logique de clôture (testée à part dans executerCloture).
    // L'estampillage est appliqué ici, sans toucher à la logique de Cloture.kt.
    private val clotureStore = object : ClotureStore {
        override suspend fun insererJournal(journal: Journal) { dao.insererJournal(journal) }
        override suspend fun obtenirBoucle(id: String): Boucle? = dao.obtenir(id)
        override suspend fun mettreAJourBoucle(boucle: Boucle) {
            dao.mettreAJour(estampiller(boucle))
        }
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

    // ── Supervision des propositions (IA) ──

    /** Propositions en attente (statut PROPOSEE), pour l'écran Supervision. */
    fun observerPropositions(): Flow<List<Boucle>> =
        dao.observerParStatut(Statut.PROPOSEE.valeurStockee())

    /**
     * Accepte une proposition : PROPOSEE -> OUVERTE, source inchangée (IA).
     * Backup STRICT AVANT l'action (échec = action abandonnée, erreur remontée).
     * Trace automatique par un mouvement `declaration` (l'acceptation, contrairement
     * au rejet, n'est pas une transition terminale : mouvement, pas journal).
     *
     * @param apresAmendement true quand l'acceptation vient du flux « Amender ».
     */
    suspend fun accepter(id: String, apresAmendement: Boolean = false) {
        val boucle = dao.obtenir(id) ?: return
        creerBackupStrict()   // filet AVANT l'action ; échec -> BackupException remontée
        dao.mettreAJour(estampiller(accepterProposition(boucle)))
        dao.insererMouvement(mouvementAcceptation(id, System.currentTimeMillis(), apresAmendement))
        rafraichirWidget()
    }

    /**
     * Rejette une proposition : PROPOSEE -> REJETEE avec un motif obligatoire
     * (journal DECLARATION), via l'UNIQUE chemin d'écriture d'état terminal.
     * Backup STRICT AVANT l'action (échec = action abandonnée, erreur remontée).
     */
    suspend fun rejeter(id: String, motif: String) {
        creerBackupStrict()
        executerTransitionTerminale(
            clotureStore, id, Statut.REJETEE, JournalType.DECLARATION, motif,
            System.currentTimeMillis()
        )
        rafraichirWidget()
    }

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
        journaux: List<Journal>,
        suppressions: List<Suppression> = emptyList()
    ): Int {
        creerBackupStrict(forcer = true)   // filet AVANT toute écriture : échec = import annulé
        val existants = dao.tousLesIds().toSet()
        // Coercition : les boucles IA nouvelles sont forcées en PROPOSEE (supervision).
        val coerce = coercerPropositionsIA(boucles, existants, System.currentTimeMillis())
        val nouvelles = coerce.boucles.filter { it.id !in existants }
        val nouveauxIds = nouvelles.map { it.id }.toSet()
        val nouveauxMouvements =
            (mouvements + coerce.mouvementsTrace).filter { it.boucleId in nouveauxIds }
        val nouveauxJournaux = journaux.filter { it.boucleId in nouveauxIds }
        dao.upsertToutes(estampiller(nouvelles))
        dao.insererMouvements(nouveauxMouvements)
        dao.insererJournaux(completerJournaux(nouvelles, nouveauxJournaux))
        // Tombstones entrantes : enregistrées, pas encore exploitées (AND-07).
        dao.insererSuppressions(suppressions)
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
        journaux: List<Journal>,
        suppressions: List<Suppression> = emptyList()
    ) {
        creerBackupStrict(forcer = true)   // filet AVANT le vidage : échec = import annulé
        dao.supprimerTousJournaux()
        dao.supprimerTousMouvements()
        dao.supprimerToutesBoucles()
        // « Écraser » remplace l'état COMPLET : les tombstones locales aussi.
        dao.supprimerToutesSuppressions()
        // Après vidage, TOUTES les boucles sont nouvelles : coercition sur set vide.
        val coerce = coercerPropositionsIA(boucles, emptySet(), System.currentTimeMillis())
        dao.upsertToutes(estampiller(coerce.boucles))
        dao.insererMouvements(mouvements + coerce.mouvementsTrace)
        dao.insererJournaux(completerJournaux(coerce.boucles, journaux))
        dao.insererSuppressions(suppressions)
        rafraichirWidget()
    }

    /**
     * "Fusionner" : enrichit sans jamais détruire (cf. [calculerFusion]).
     * - mouvements/journaux entrants ajoutés avec déduplication ;
     * - boucles entrantes nouvelles créées avec leur statut d'origine ;
     * - boucles existantes : champs scalaires remplacés seulement pour les ids
     *   de [prendreEntrant] (statut/source/creee préservés).
     * Backup AVANT écriture, comme les deux autres modes.
     */
    suspend fun importerFusionner(
        boucles: List<Boucle>,
        mouvements: List<Mouvement>,
        journaux: List<Journal>,
        prendreEntrant: Set<String>,
        suppressions: List<Suppression> = emptyList()
    ) {
        creerBackupStrict(forcer = true)   // filet AVANT écriture : échec = import annulé
        val existantes = dao.toutesLesBoucles()
        val idsExistants = existantes.mapTo(HashSet()) { it.id }
        // Coercition des boucles IA NOUVELLES (les existantes gardent leur statut,
        // préservé par calculerFusion) AVANT le calcul de fusion.
        val coerce = coercerPropositionsIA(boucles, idsExistants, System.currentTimeMillis())
        val res = calculerFusion(
            existantes = existantes,
            mouvementsExistants = dao.tousLesMouvements(),
            journauxExistants = dao.tousLesJournaux(),
            entrantes = coerce.boucles,
            mouvementsEntrants = mouvements + coerce.mouvementsTrace,
            journauxEntrants = journaux,
            prendreEntrant = prendreEntrant
        )
        dao.upsertToutes(estampiller(res.bouclesNouvelles))
        estampiller(res.bouclesMisesAJour).forEach { dao.mettreAJour(it) }
        dao.insererMouvements(res.mouvementsAjoutes)
        // Les nouvelles boucles terminales sans journal reçoivent une entrée par défaut.
        dao.insererJournaux(completerJournaux(res.bouclesNouvelles, res.journauxAjoutes))
        dao.insererSuppressions(suppressions)
        rafraichirWidget()
    }

    /** Conflits de fusion (id existant + champ scalaire divergent) pour arbitrage UI. */
    suspend fun conflitsFusion(entrantes: List<Boucle>): List<ConflitFusion> =
        calculerConflits(dao.toutesLesBoucles(), entrantes)

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
     * Sérialise l'état courant (format canonique complet) et l'écrit dans l'URI
     * fourni par ACTION_CREATE_DOCUMENT. Renvoie null si succès, sinon un
     * message d'erreur réel (jamais d'échec silencieux).
     */
    suspend fun exporterVers(uri: Uri): String? {
        return try {
            val contenu = JsonExporter.serialiser(
                dao.toutesLesBoucles(),
                dao.tousLesMouvements(),
                dao.tousLesJournaux(),
                dao.toutesLesSuppressions(),
                lireCodeAppareil(),
                System.currentTimeMillis()
            )
            val flux = appContext.contentResolver.openOutputStream(uri)
                ?: return "Impossible d'ouvrir le fichier de destination."
            flux.use { it.write(contenu.toByteArray(Charsets.UTF_8)) }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Export impossible", e)
            "Export impossible : ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Backup local versionné complet (Boucle + Journal). Stockage app-scoped
     * externe (aucune permission runtime). Rotation : garde les 10 plus récents.
     * STRICT : lève [BackupException] (loggée) si l'écriture échoue — utilisé
     * comme filet avant tout import destructif et par la sauvegarde manuelle.
     *
     * Anti-rafale : si [forcer] est faux ET que le backup le plus récent date de
     * moins de [ANTI_RAFALE_MS], le fichier existant est RÉUTILISÉ (retour du
     * File, aucune écriture) — plusieurs actions de supervision d'affilée ne
     * créent qu'un seul backup au lieu d'éjecter l'historique. Les imports
     * passent [forcer] = true pour garantir un backup frais avant écriture.
     */
    suspend fun creerBackupStrict(forcer: Boolean = false): File {
        val recent = dernierBackupFichier()
        if (doitReutiliserBackup(recent?.name, System.currentTimeMillis(), forcer, ANTI_RAFALE_MS)) {
            return recent!!
        }
        val contenu = BackupExporter.serialiser(
            dao.toutesLesBoucles(),
            dao.tousLesMouvements(),
            dao.tousLesJournaux(),
            dao.toutesLesSuppressions(),
            lireCodeAppareil(),
            System.currentTimeMillis()
        )
        val dossier = File(appContext.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val fichier = File(dossier, nomBackup(System.currentTimeMillis()))
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
     * Backup best-effort (clôture automatique, supervision) : loggue et renvoie
     * null en cas d'échec sans interrompre le flux appelant. Respecte l'anti-rafale.
     */
    suspend fun creerBackup(forcer: Boolean = false): File? = try {
        creerBackupStrict(forcer)
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

    // ── Identité de l'appareil ──
    //
    // Fichier de préférences DÉDIÉ, et non `registre-prefs` : les règles de
    // sauvegarde Android ne savent exclure qu'un FICHIER entier, jamais une clé
    // (vérifié dans la doc : l'attribut `path` désigne « a file or folder »).
    // Isoler le code appareil permet de l'exclure nommément des sauvegardes —
    // sinon une restauration sur un appareil neuf clonerait l'identité et
    // provoquerait les collisions d'identifiants que l'on cherche à éviter —
    // tout en laissant `registre-prefs` (thème, listes) sauvegardable un jour.
    private val prefsAppareil
        get() = appContext.getSharedPreferences("registre-appareil", Context.MODE_PRIVATE)

    /** Code de cet appareil, ou null s'il n'a pas encore été choisi. */
    fun lireCodeAppareil(): String? = prefsAppareil.getString("code_appareil", null)

    /**
     * Pose le code de l'appareil. Refuse une saisie non conforme (1 à 4 lettres) :
     * renvoie le code normalisé si accepté, null sinon.
     */
    fun ecrireCodeAppareil(saisie: String): String? {
        val code = normaliserCodeAppareil(saisie) ?: return null
        prefsAppareil.edit().putString("code_appareil", code).apply()
        return code
    }

    /**
     * Code appareil pour l'estampillage. `?` marque une écriture faite avant que
     * l'identité ne soit posée : la valeur reste lisible et n'invente pas de
     * provenance (l'UI empêche normalement ce cas, cf. écran d'identité).
     */
    private fun codeAppareilOuInconnu(): String = lireCodeAppareil() ?: "?"

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
