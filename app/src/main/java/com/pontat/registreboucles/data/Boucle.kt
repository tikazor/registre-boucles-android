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
    val statut: String,                     // ouverte / en_cours / fermee / defaut_applique / proposee / rejetee
    val milieu: String? = null,             // pro / perso / projet / autre (valeurs configurables)
    val source: String? = null,             // user / ia / import (null = user, historique)
    val modifieeLe: Long? = null,           // epoch millis ; null = jamais modifiée depuis sa création
    val modifieePar: String? = null         // code de l'appareil auteur de la dernière écriture
)

/**
 * Date de dernière modification de la boucle. `modifieeLe` absent (données
 * antérieures à la v5) se lit comme la date de création : c'est l'UNIQUE endroit
 * où ce repli est écrit, pour qu'aucun `?: creee` ne se disperse dans le code.
 */
fun Boucle.derniereModification(): Long = modifieeLe ?: creee
