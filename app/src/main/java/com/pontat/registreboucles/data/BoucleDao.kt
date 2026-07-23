package com.pontat.registreboucles.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT COUNT(*) FROM boucles")
    suspend fun compter(): Int

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

    // --- Mouvements ---

    @Query("SELECT * FROM mouvements WHERE boucleId = :boucleId ORDER BY date DESC")
    fun observerMouvements(boucleId: String): Flow<List<Mouvement>>

    @Insert
    suspend fun insererMouvement(mouvement: Mouvement)

    @Insert
    suspend fun insererMouvements(mouvements: List<Mouvement>)
}
