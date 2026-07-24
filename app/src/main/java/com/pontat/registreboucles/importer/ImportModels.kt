package com.pontat.registreboucles.importer

import kotlinx.serialization.Serializable

/**
 * DTOs de l'export JSON. Les dates sont des chaînes ISO-8601 ; la conversion
 * en epoch millis se fait dans [JsonImporter]. Tout champ texte reste libre
 * (pas d'enum) pour ne pas casser l'import au premier écart de vocabulaire.
 */
@Serializable
data class ExportRacine(
    val boucles: List<BoucleJson> = emptyList()
)

/**
 * Racine acceptée à l'import : fichier d'export ({boucles}) OU backup
 * ({boucles, journaux}). `journaux` est ignoré si absent.
 */
@Serializable
data class ImportRacine(
    val boucles: List<BoucleJson> = emptyList(),
    val journaux: List<JournalJson> = emptyList()
)

@Serializable
data class BoucleJson(
    val id: String,
    val type: String,
    val titre: String,
    val origine: String,
    val creee: String,
    val echeance: String? = null,
    val tiers: Boolean = false,
    val preuveAttendue: String,
    val blocage: String? = null,
    val impact: String,
    val defaut: String? = null,
    val statut: String,
    val milieu: String? = null,
    val mouvements: List<MouvementJson> = emptyList()
)

@Serializable
data class MouvementJson(
    val date: String,
    val note: String
)
