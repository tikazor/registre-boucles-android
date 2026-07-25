package com.pontat.registreboucles.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Dernière date de mouvement par boucle (pour l'étiquette « Modifié le »). */
data class DerniereModifRow(val boucleId: String, val derniere: Long)

@Dao
interface BoucleDao {

    // --- Boucles : lectures (échéance croissante, nulls en dernier) ---

    @Query("SELECT * FROM boucles ORDER BY echeance IS NULL, echeance ASC")
    fun observerToutes(): Flow<List<Boucle>>

    @Query("SELECT * FROM boucles WHERE statut = :statut ORDER BY echeance IS NULL, echeance ASC")
    fun observerParStatut(statut: String): Flow<List<Boucle>>

    @Query("SELECT * FROM boucles WHERE id = :id")
    fun observerParId(id: String): Flow<Boucle?>

    @Query("SELECT * FROM boucles WHERE id = :id")
    suspend fun obtenir(id: String): Boucle?

    @Query("SELECT id FROM boucles")
    suspend fun tousLesIds(): List<String>

    @Query("SELECT * FROM boucles")
    suspend fun toutesLesBoucles(): List<Boucle>

    @Query("SELECT COUNT(*) FROM boucles")
    suspend fun compter(): Int

    // NB : ('ouverte', 'en_cours') = Statut.estActive() en valeurs stockées.
    // Toute évolution de la définition d'« active » doit rester synchronisée
    // avec Statut.estActive() (cf. data/Statut.kt), seule vérité de l'app.
    @Query("SELECT COUNT(*) FROM boucles WHERE statut IN ('ouverte', 'en_cours')")
    suspend fun compterActives(): Int

    @Query(
        "SELECT * FROM boucles WHERE statut IN ('ouverte', 'en_cours') " +
            "AND echeance IS NOT NULL ORDER BY echeance ASC LIMIT :limite"
    )
    suspend fun prochainesEcheances(limite: Int): List<Boucle>

    // --- Boucles : écritures ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(boucle: Boucle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToutes(boucles: List<Boucle>)

    @Update
    suspend fun mettreAJour(boucle: Boucle)

    @Delete
    suspend fun supprimer(boucle: Boucle)

    /**
     * Suppression tracée : la tombstone est écrite AVANT l'effacement, dans la
     * même transaction. Une suppression sans trace est un bug (invariant I10),
     * d'où la transaction : les deux réussissent, ou aucune.
     */
    @Transaction
    suspend fun supprimerAvecTrace(boucle: Boucle, trace: Suppression) {
        insererSuppression(trace)
        supprimer(boucle)
    }

    @Query("DELETE FROM boucles")
    suspend fun supprimerToutesBoucles()

    @Query("DELETE FROM mouvements")
    suspend fun supprimerTousMouvements()

    // --- Mouvements ---

    @Query("SELECT * FROM mouvements WHERE boucleId = :boucleId ORDER BY date DESC")
    fun observerMouvements(boucleId: String): Flow<List<Mouvement>>

    @Query("SELECT * FROM mouvements")
    suspend fun tousLesMouvements(): List<Mouvement>

    @Query("SELECT boucleId, MAX(date) AS derniere FROM mouvements GROUP BY boucleId")
    fun observerDernieresModifs(): Flow<List<DerniereModifRow>>

    @Query("SELECT boucleId, MAX(date) AS derniere FROM mouvements GROUP BY boucleId")
    suspend fun dernieresModifsListe(): List<DerniereModifRow>

    // --- Journal de clôture ---

    @Insert
    suspend fun insererJournal(journal: Journal): Long

    @Insert
    suspend fun insererJournaux(journaux: List<Journal>)

    @Query("DELETE FROM journaux")
    suspend fun supprimerTousJournaux()

    @Query("SELECT * FROM journaux WHERE boucleId = :boucleId ORDER BY date DESC")
    fun observerJournaux(boucleId: String): Flow<List<Journal>>

    @Query("SELECT * FROM journaux")
    suspend fun tousLesJournaux(): List<Journal>

    @Insert
    suspend fun insererMouvement(mouvement: Mouvement)

    @Insert
    suspend fun insererMouvements(mouvements: List<Mouvement>)

    // --- Suppressions (tombstones) : jamais purgées automatiquement ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insererSuppression(suppression: Suppression)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insererSuppressions(suppressions: List<Suppression>)

    @Query("SELECT * FROM suppressions")
    suspend fun toutesLesSuppressions(): List<Suppression>

    /** Utilisé uniquement par l'import « Écraser », qui remplace l'état complet. */
    @Query("DELETE FROM suppressions")
    suspend fun supprimerToutesSuppressions()

    /**
     * Retrait d'une tombstone : uniquement lors d'une résurrection décidée par le
     * moteur de fusion (la boucle revient, sa trace de suppression n'a plus lieu
     * d'être). Il n'existe aucune purge par ancienneté, ni manuelle.
     */
    @Query("DELETE FROM suppressions WHERE boucleId = :boucleId")
    suspend fun retirerSuppression(boucleId: String)

    // --- Journal de synchronisation : écrit une fois, jamais modifié ni purgé ---

    @Insert
    suspend fun insererEvenementSync(evenement: EvenementSync)

    /**
     * Applique un plan de fusion et le consigne, en UNE transaction : soit la
     * fusion et sa trace au journal existent toutes les deux, soit aucune des
     * deux. Aucune décision n'est prise ici — tout vient de [PlanFusion], calculé
     * par `calculerFusionSync` (fonction pure).
     */
    @Transaction
    suspend fun appliquerPlanSync(
        bouclesAInserer: List<Boucle>,
        bouclesAMettreAJour: List<Boucle>,
        mouvements: List<Mouvement>,
        journaux: List<Journal>,
        tombstonesAInserer: List<Suppression>,
        tombstonesARetirer: List<String>,
        evenement: EvenementSync
    ) {
        if (bouclesAInserer.isNotEmpty()) upsertToutes(bouclesAInserer)
        bouclesAMettreAJour.forEach { mettreAJour(it) }
        if (mouvements.isNotEmpty()) insererMouvements(mouvements)
        if (journaux.isNotEmpty()) insererJournaux(journaux)
        if (tombstonesAInserer.isNotEmpty()) insererSuppressions(tombstonesAInserer)
        tombstonesARetirer.forEach { retirerSuppression(it) }
        insererEvenementSync(evenement)
    }

    @Query("SELECT * FROM evenements_sync ORDER BY horodatage DESC")
    fun observerEvenementsSync(): Flow<List<EvenementSync>>

    @Query("SELECT * FROM evenements_sync ORDER BY horodatage DESC LIMIT 1")
    suspend fun dernierEvenementSync(): EvenementSync?

    // ── Captures (boîte de réception) ──
    //
    // AUCUNE méthode de suppression n'existe ici, et c'est délibéré : une capture
    // n'est jamais effacée, seulement marquée IGNOREE (invariant I14). Il n'y a
    // donc ni @Delete, ni `DELETE FROM captures`, pas même pour un « nettoyage ».

    @Insert
    suspend fun insererCapture(capture: Capture)

    @Update
    suspend fun mettreAJourCapture(capture: Capture)

    @Query("SELECT * FROM captures ORDER BY capturee DESC")
    fun observerCaptures(): Flow<List<Capture>>

    @Query("SELECT * FROM captures WHERE statut = :statut ORDER BY capturee DESC")
    suspend fun capturesParStatut(statut: String): List<Capture>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun capture(id: String): Capture?

    /** Contrôle de doublon : l'empreinte est indexée (cf. Capture.indices). */
    @Query("SELECT * FROM captures WHERE empreinte = :empreinte LIMIT 1")
    suspend fun captureParEmpreinte(empreinte: String): Capture?

    @Query("SELECT id FROM captures")
    suspend fun tousLesIdsCaptures(): List<String>

    /** Compteur du badge de la boîte de réception (masqué si zéro). */
    @Query("SELECT COUNT(*) FROM captures WHERE statut = 'brute'")
    fun observerNombreCapturesBrutes(): Flow<Int>

    // ── Liaison capture <-> boucle (AND-09) ──
    //
    // Aucune suppression ici non plus : un lien reste même quand la proposition
    // qu'il portait a été rejetée. Que cette note ait nourri une proposition
    // refusée est une information, pas un déchet.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insererLien(lien: CaptureBoucle)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insererLiens(liens: List<CaptureBoucle>)

    @Query("SELECT * FROM capture_boucle")
    fun observerLiens(): Flow<List<CaptureBoucle>>

    @Query("SELECT * FROM capture_boucle WHERE boucleId = :boucleId")
    suspend fun liensDeBoucle(boucleId: String): List<CaptureBoucle>

    @Query(
        "SELECT c.* FROM captures c INNER JOIN capture_boucle l ON l.captureId = c.id " +
            "WHERE l.boucleId = :boucleId ORDER BY c.capturee DESC"
    )
    suspend fun capturesDeBoucle(boucleId: String): List<Capture>

    /**
     * UNIQUE point d'écriture d'un lien capture -> boucle, transactionnel.
     *
     * Le commanditaire a choisi (AND-09) de conserver `captures.boucleLiee` en
     * parallèle de la table de liaison. Les deux représentations du même fait ne
     * peuvent donc diverger que si quelqu'un en écrit une sans l'autre : elles
     * sont écrites ICI, ensemble, ou pas du tout. Ne jamais contourner cette
     * méthode (cf. invariant I16).
     */
    @Transaction
    suspend fun lierCaptureEtBoucle(capture: Capture, lien: CaptureBoucle) {
        mettreAJourCapture(capture)
        insererLien(lien)
    }
}
