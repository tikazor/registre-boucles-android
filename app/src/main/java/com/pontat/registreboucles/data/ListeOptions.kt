package com.pontat.registreboucles.data

import kotlinx.serialization.Serializable

/**
 * Valeurs des listes à choix unique, éditables depuis l'écran Configuration.
 * Persistées en JSON dans les SharedPreferences.
 */
@Serializable
data class ListeOptions(
    val types: List<String> = listOf("ACTION", "DECISION"),
    val tiers: List<String> = listOf("Non", "Oui")
    // `milieu` n'est plus configurable : c'est désormais un enum fixe (Milieu).
)
