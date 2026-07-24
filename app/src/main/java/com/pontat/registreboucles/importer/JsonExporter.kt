package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.Mouvement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * Sérialiseur du format canonique UNIQUE de l'app (export ET backup :
 * [BackupExporter] délègue ici, aucune divergence possible).
 *
 * Produit `{ version: 2, boucles, journaux }` :
 * - `tiers` en chaîne (préserve toute valeur libre ; plus de perte booléenne) ;
 * - journaux inclus (les preuves de clôture ne sont plus perdues) ;
 * - dates epoch millis -> ISO-8601 (UTC). Réimportable sans perte de champ.
 */
object JsonExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun serialiser(
        boucles: List<Boucle>,
        mouvements: List<Mouvement>,
        journaux: List<Journal>
    ): String = json.encodeToString(
        RegistreRacine.serializer(),
        RegistreRacine(
            version = VERSION_FORMAT_COURANTE,
            boucles = mapBoucles(boucles, mouvements),
            journaux = mapJournaux(journaux)
        )
    )

    /** Mapping partagé Boucle(+mouvements) -> BoucleJson. */
    fun mapBoucles(boucles: List<Boucle>, mouvements: List<Mouvement>): List<BoucleJson> {
        val mouvParBoucle = mouvements.groupBy { it.boucleId }
        return boucles.map { b ->
            BoucleJson(
                id = b.id,
                type = b.type,
                titre = b.titre,
                origine = b.origine,
                creee = iso(b.creee),
                echeance = b.echeance?.let { iso(it) },
                // Chaîne libre préservée telle quelle ; null si absent.
                tiers = b.tiers?.let { JsonPrimitive(it) },
                preuveAttendue = b.preuveAttendue,
                blocage = b.blocage,
                impact = b.impact,
                defaut = b.defaut,
                statut = b.statut,
                milieu = b.milieu,
                mouvements = (mouvParBoucle[b.id] ?: emptyList())
                    .sortedBy { it.date }
                    .map { MouvementJson(date = iso(it.date), note = it.contenu) }
            )
        }
    }

    /** Mapping partagé Journal -> JournalJson. */
    fun mapJournaux(journaux: List<Journal>): List<JournalJson> = journaux.map {
        JournalJson(
            boucleId = it.boucleId,
            date = iso(it.date),
            type = it.type,
            texte = it.texte
        )
    }

    fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}
