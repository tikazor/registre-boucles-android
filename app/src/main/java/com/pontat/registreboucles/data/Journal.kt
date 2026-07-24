package com.pontat.registreboucles.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Journal de clôture : preuve/déclaration/défaut conservée à chaque fermeture.
 * Entité distincte de Mouvement (aucun lien avec le suivi de mouvement).
 */
@Entity(
    tableName = "journaux",
    foreignKeys = [ForeignKey(
        entity = Boucle::class,
        parentColumns = ["id"],
        childColumns = ["boucleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("boucleId")]
)
data class Journal(
    @PrimaryKey(autoGenerate = true) val journalId: Long = 0,
    val boucleId: String,
    val date: Long,          // epoch millis
    val type: String,        // JournalType.name : PREUVE / DECLARATION / DEFAUT
    val texte: String
)
