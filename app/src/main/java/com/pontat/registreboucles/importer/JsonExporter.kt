package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Mouvement
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Sérialise l'état Room dans EXACTEMENT le schéma d'import
 * (ExportRacine / BoucleJson / MouvementJson) : `tiers` booléen,
 * mouvements `{date, note}`, dates epoch millis -> ISO-8601 (UTC).
 * L'export est donc réimportable tel quel.
 */
object JsonExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun serialiser(boucles: List<Boucle>, mouvements: List<Mouvement>): String {
        val mouvParBoucle = mouvements.groupBy { it.boucleId }

        val racine = ExportRacine(
            boucles = boucles.map { b ->
                BoucleJson(
                    id = b.id,
                    type = b.type,
                    titre = b.titre,
                    origine = b.origine,
                    creee = iso(b.creee),
                    echeance = b.echeance?.let { iso(it) },
                    // Entité String? -> booléen : présent (non null) = true.
                    tiers = b.tiers != null,
                    preuveAttendue = b.preuveAttendue,
                    blocage = b.blocage,
                    impact = b.impact,
                    defaut = b.defaut,
                    statut = b.statut,
                    mouvements = (mouvParBoucle[b.id] ?: emptyList())
                        .sortedBy { it.date }
                        .map { MouvementJson(date = iso(it.date), note = it.contenu) }
                )
            }
        )

        return json.encodeToString(ExportRacine.serializer(), racine)
    }

    private fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}
