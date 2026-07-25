package com.pontat.registreboucles.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Trace de suppression (« tombstone »). Une boucle effacée laisse cette trace
 * derrière elle, pour toujours.
 *
 * Pourquoi : en réplique sur deux appareils, un effacement sans trace serait
 * ressuscité par le premier import venant de l'autre appareil — l'appareil B
 * n'aurait aucun moyen de distinguer « boucle jamais vue » de « boucle
 * volontairement supprimée ». La trace est cette distinction (invariant I10).
 *
 * Les tombstones ne sont JAMAIS purgées automatiquement : c'est une app-mémoire.
 * AND-07 se contente de les enregistrer ; leur exploitation à la fusion viendra
 * dans un lot ultérieur.
 */
@Entity(tableName = "suppressions")
data class Suppression(
    @PrimaryKey val boucleId: String,
    val supprimeeLe: Long,      // epoch millis
    val supprimeePar: String    // code de l'appareil qui a supprimé
)

/** Construit la trace d'une suppression. Pure, donc testable. */
fun traceSuppression(boucleId: String, codeAppareil: String, dateMillis: Long): Suppression =
    Suppression(boucleId = boucleId, supprimeeLe = dateMillis, supprimeePar = codeAppareil)
