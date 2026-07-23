package com.pontat.registreboucles.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boucles")
data class Boucle(
    @PrimaryKey val id: String,            // ex: "B-016"
    val type: String,                       // libre (dev, partenariat, admin...)
    val titre: String,
    val origine: String,
    val creee: Long,                        // epoch millis
    val echeance: Long?,                    // epoch millis, nullable
    val tiers: String?,
    val preuveAttendue: String,
    val blocage: String?,
    val impact: String,                     // libre, texte
    val defaut: String?,                    // action par défaut si non tranché
    val statut: String                      // libre : ouverte / en_cours / fermee / defaut_applique
)
