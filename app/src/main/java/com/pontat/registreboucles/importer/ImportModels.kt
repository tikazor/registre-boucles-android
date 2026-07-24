package com.pontat.registreboucles.importer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs du format JSON canonique unique (export = backup). Les dates sont des
 * chaînes ISO-8601 ; la conversion en epoch millis se fait dans [JsonImporter].
 *
 * Un seul format pour tout : `{ version, boucles, journaux }`. L'ancien format
 * (`{ boucles }` sans version ni journaux) reste accepté à l'import : `version`
 * absent = 1, `journaux` absent = liste vide.
 */
@Serializable
data class RegistreRacine(
    val version: Int = 1,
    val boucles: List<BoucleJson> = emptyList(),
    val journaux: List<JournalJson> = emptyList()
)

/** Version courante écrite par l'export ([JsonExporter]). */
const val VERSION_FORMAT_COURANTE = 2

@Serializable
data class BoucleJson(
    val id: String,
    val type: String,
    val titre: String,
    val origine: String,
    val creee: String,
    val echeance: String? = null,
    // `tiers` est une chaîne libre en base. Sérialisé comme JsonElement pour
    // tolérer À L'IMPORT les deux formes historiques (booléen legacy OU chaîne),
    // interprétées dans JsonImporter. À l'export, toujours écrit en chaîne/null.
    val tiers: JsonElement? = null,
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

@Serializable
data class JournalJson(
    val boucleId: String,
    val date: String,   // ISO-8601
    val type: String,   // PREUVE / DECLARATION / DEFAUT
    val texte: String
)
