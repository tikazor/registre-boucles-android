package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Capture
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sérialisation d'un **lot d'analyse** : les notes brutes qui sortent de l'app
 * pour être analysées ailleurs.
 *
 * C'est un format de SORTIE, distinct du contrat d'échange des boucles
 * (`version: 3`) : il ne transporte aucune boucle, aucun statut de registre,
 * aucune interprétation du contenu. L'analyse est faite hors de l'app, par une
 * IA, et son résultat revient par le canal existant — un import de propositions
 * en `proposee`, supervisées à l'écran Supervision (invariant I15).
 *
 * Le lot n'est volontairement PAS réimportable : rien ne rentre par cette porte.
 */
@Serializable
data class LotAnalyseRacine(
    val version: Int = VERSION_LOT_ANALYSE,
    val type: String = TYPE_LOT_ANALYSE,
    val exporteLe: Long,
    val captures: List<CaptureJson>
)

@Serializable
data class CaptureJson(
    val id: String,
    val contenuBrut: String,
    val titre: String? = null,
    val appareil: String,
    val appSource: String? = null,
    /** Epoch millis — même convention que `exporteLe`, métadonnée machine. */
    @SerialName("capturee") val capturee: Long
)

const val VERSION_LOT_ANALYSE = 1
const val TYPE_LOT_ANALYSE = "lot-analyse"

private val FORMAT_NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

object LotAnalyseExporter {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    /** JSON du lot. Pure : l'heure est injectée. */
    fun serialiser(captures: List<Capture>, exporteLe: Long): String = json.encodeToString(
        LotAnalyseRacine.serializer(),
        LotAnalyseRacine(
            exporteLe = exporteLe,
            captures = captures.map {
                CaptureJson(
                    id = it.id,
                    contenuBrut = it.contenuBrut,
                    titre = it.titre,
                    appareil = it.appareil,
                    appSource = it.appSource,
                    capturee = it.capturee
                )
            }
        )
    )

    /** Nom de fichier proposé : `lot-analyse-<aaaaMMjj-HHmm>.json`. Pure. */
    fun nomFichier(exporteLe: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "lot-analyse-${FORMAT_NOM.format(Instant.ofEpochMilli(exporteLe).atZone(zone))}.json"
}
