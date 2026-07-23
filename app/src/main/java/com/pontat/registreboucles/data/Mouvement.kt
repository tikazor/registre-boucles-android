package com.pontat.registreboucles.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mouvements",
    foreignKeys = [ForeignKey(
        entity = Boucle::class,
        parentColumns = ["id"],
        childColumns = ["boucleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("boucleId")]
)
data class Mouvement(
    @PrimaryKey(autoGenerate = true) val mouvementId: Long = 0,
    val boucleId: String,
    val date: Long,                         // epoch millis
    val type: String,                       // preuve / declaration / defaut
    val contenu: String
)
