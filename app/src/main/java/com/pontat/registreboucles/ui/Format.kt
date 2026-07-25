package com.pontat.registreboucles.ui

import androidx.compose.ui.graphics.Color
import com.pontat.registreboucles.ui.theme.StatutDefaut
import com.pontat.registreboucles.ui.theme.StatutEnCours
import com.pontat.registreboucles.ui.theme.StatutFermee
import com.pontat.registreboucles.ui.theme.StatutOuverte
import com.pontat.registreboucles.ui.theme.StatutProposee
import com.pontat.registreboucles.ui.theme.StatutRejetee
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

private val dateHeureFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)

fun formaterDate(epochMillis: Long?): String {
    if (epochMillis == null) return "—"
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormat)
}

fun formaterDateHeure(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateHeureFormat)

/** Libellé lisible d'un statut libre. */
fun libelleStatut(statut: String): String = when (statut) {
    "ouverte" -> "Ouverte"
    "en_cours" -> "En cours"
    "fermee" -> "Fermée"
    "defaut_applique" -> "Défaut appliqué"
    "proposee" -> "Proposée"
    "rejetee" -> "Rejetée"
    else -> statut.replaceFirstChar { it.uppercase() }
}

/**
 * Couleur du badge de statut. Un statut inconnu prend la couleur « fermée »
 * (neutre) et non celle d'« ouverte » : il ne doit pas se faire passer pour une
 * boucle active (cf. invariant I8, aucune donnée trompeuse).
 */
fun couleurStatut(statut: String): Color = when (statut) {
    "ouverte" -> StatutOuverte
    "en_cours" -> StatutEnCours
    "fermee" -> StatutFermee
    "defaut_applique" -> StatutDefaut
    "proposee" -> StatutProposee
    "rejetee" -> StatutRejetee
    else -> StatutFermee
}
