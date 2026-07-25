package com.pontat.registreboucles.importer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs du format JSON canonique unique (export = backup). Les dates de boucle,
 * de mouvement et de journal sont des chaînes ISO-8601 ; la conversion en epoch
 * millis se fait dans [JsonImporter].
 *
 * Format v3 : `{ version, codeAppareil, exporteLe, boucles, journaux, suppressions }`.
 * TOLÉRANCE ASCENDANTE : les fichiers v1 (`{ boucles }`) et v2
 * (`{ version, boucles, journaux }`) restent importables sans erreur — tout
 * champ ajouté depuis a une valeur par défaut ici.
 */
@Serializable
data class RegistreRacine(
    val version: Int = 1,
    /** Appareil émetteur (v3+). Absent sur un fichier v1/v2. */
    val codeAppareil: String? = null,
    /** Date d'export, epoch millis (v3+) : métadonnée machine, non affichée. */
    val exporteLe: Long? = null,
    val boucles: List<BoucleJson> = emptyList(),
    val journaux: List<JournalJson> = emptyList(),
    /** Tombstones (v3+) : enregistrées à l'import, pas encore exploitées. */
    val suppressions: List<SuppressionJson> = emptyList()
)

/** Version courante écrite par l'export ([JsonExporter]). */
const val VERSION_FORMAT_COURANTE = 3

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
    val source: String? = null,     // user / ia / import (absent -> import à l'entrée)
    /** Dernière modification, ISO-8601 (v3+). Absent = jamais modifiée depuis `creee`. */
    val modifieeLe: String? = null,
    /** Code de l'appareil auteur de la dernière modification (v3+). */
    val modifieePar: String? = null,
    val mouvements: List<MouvementJson> = emptyList()
)

@Serializable
data class MouvementJson(
    val date: String,
    val note: String
)

/** Trace de suppression échangée entre appareils (v3+). */
@Serializable
data class SuppressionJson(
    val boucleId: String,
    val supprimeeLe: String,    // ISO-8601, comme les autres dates du document
    val supprimeePar: String
)

@Serializable
data class JournalJson(
    val boucleId: String,
    val date: String,   // ISO-8601
    val type: String,   // PREUVE / DECLARATION / DEFAUT
    val texte: String
)
