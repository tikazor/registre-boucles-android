package com.pontat.registreboucles.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
