package com.pontat.registreboucles.ui

import androidx.compose.ui.graphics.Color
import com.pontat.registreboucles.ui.theme.StatutDefaut
import com.pontat.registreboucles.ui.theme.StatutEnCours
import com.pontat.registreboucles.ui.theme.StatutFermee
import com.pontat.registreboucles.ui.theme.StatutOuverte
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
    else -> statut.replaceFirstChar { it.uppercase() }
}

fun couleurStatut(statut: String): Color = when (statut) {
    "ouverte" -> StatutOuverte
    "en_cours" -> StatutEnCours
    "fermee" -> StatutFermee
    "defaut_applique" -> StatutDefaut
    else -> StatutOuverte
}
