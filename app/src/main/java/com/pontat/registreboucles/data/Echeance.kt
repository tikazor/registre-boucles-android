package com.pontat.registreboucles.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Prédicats d'échéance — SOURCE DE VÉRITÉ UNIQUE des catégories « en retard » et
 * « échéance proche ».
 *
 * Ces fonctions sont pures (la date du jour est un paramètre) et servent À LA
 * FOIS aux compteurs du tableau de bord et aux filtres de la liste : un
 * indicateur et le filtre qu'il déclenche ne peuvent donc pas donner des
 * résultats différents. Même raison d'être que [Statut.estActive] (cf.
 * docs/explanation/invariants.md, invariant I3).
 */

/** Jours restants avant l'échéance (négatif = dépassée), en jours calendaires. */
fun joursRestantsDepuis(
    echeanceMillis: Long,
    aujourdHui: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val ech = Instant.ofEpochMilli(echeanceMillis).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(aujourdHui, ech)
}

/**
 * Boucle **active** dont l'échéance est dépassée. Une boucle sans échéance ou
 * déjà terminale n'est jamais « en retard ».
 */
fun Boucle.estEnRetard(aujourdHui: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    estActive() && echeance != null && joursRestantsDepuis(echeance, aujourdHui, zone) < 0

/**
 * Boucle **active** dont l'échéance tombe dans les [seuilJours] prochains jours
 * (aujourd'hui inclus, retard exclu).
 */
fun Boucle.estEcheanceProche(
    aujourdHui: LocalDate,
    seuilJours: Long = 7,
    zone: ZoneId = ZoneId.systemDefault()
): Boolean =
    estActive() && echeance != null &&
        joursRestantsDepuis(echeance, aujourdHui, zone) in 0..seuilJours
