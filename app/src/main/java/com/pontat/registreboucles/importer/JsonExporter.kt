package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.Mouvement
import com.pontat.registreboucles.data.Suppression
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
        journaux: List<Journal>,
        suppressions: List<Suppression> = emptyList(),
        codeAppareil: String? = null,
        exporteLe: Long? = null,
        /** boucleId -> captures d'origine (AND-09). Vide = champ `origines` omis. */
        origines: Map<String, List<String>> = emptyMap()
    ): String = json.encodeToString(
        RegistreRacine.serializer(),
        RegistreRacine(
            version = VERSION_FORMAT_COURANTE,
            codeAppareil = codeAppareil,
            exporteLe = exporteLe,
            boucles = mapBoucles(boucles, mouvements, origines),
            journaux = mapJournaux(journaux),
            suppressions = mapSuppressions(suppressions)
        )
    )

    /** Mapping partagé Boucle(+mouvements) -> BoucleJson. */
    fun mapBoucles(
        boucles: List<Boucle>,
        mouvements: List<Mouvement>,
        origines: Map<String, List<String>> = emptyMap()
    ): List<BoucleJson> {
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
                source = b.source,
                modifieeLe = b.modifieeLe?.let { iso(it) },
                modifieePar = b.modifieePar,
                // Captures d'origine : réécrites pour qu'un aller-retour
                // export -> import ne perde pas la traçabilité.
                origines = origines[b.id] ?: emptyList(),
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

    /** Mapping partagé Suppression -> SuppressionJson. */
    fun mapSuppressions(suppressions: List<Suppression>): List<SuppressionJson> = suppressions.map {
        SuppressionJson(
            boucleId = it.boucleId,
            supprimeeLe = iso(it.supprimeeLe),
            supprimeePar = it.supprimeePar
        )
    }

    fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}
