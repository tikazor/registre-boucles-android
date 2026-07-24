package com.pontat.registreboucles.data

/**
 * Calcul PUR de la fusion d'un import « Fusionner » avec l'existant (testable
 * sans Room). Règles :
 * - mouvements et journaux entrants sont TOUJOURS ajoutés, dédupliqués sur
 *   (boucleId, date, contenu/texte) ; jamais de suppression ;
 * - boucle entrante dont l'id est ABSENT -> créée telle quelle (avec son statut
 *   d'origine, typiquement PROPOSEE si elle vient d'une IA) ;
 * - boucle entrante dont l'id EXISTE : choix par boucle. « Prendre l'entrant »
 *   remplace uniquement les champs scalaires descriptifs ; id, date de création,
 *   statut et source de l'existant sont TOUJOURS préservés (la fusion n'altère
 *   ni le cycle de vie ni la provenance). « Garder l'existant » ne touche à rien.
 */
data class ResultatFusion(
    val bouclesNouvelles: List<Boucle>,      // à insérer (ids absents)
    val bouclesMisesAJour: List<Boucle>,     // existantes modifiées (choix « prendre l'entrant »)
    val mouvementsAjoutes: List<Mouvement>,  // entrants non déjà présents
    val journauxAjoutes: List<Journal>       // entrants non déjà présents
)

/**
 * @param prendreEntrant ids des boucles existantes pour lesquelles on adopte les
 *   champs scalaires entrants (les autres ids existants gardent l'existant).
 */
fun calculerFusion(
    existantes: List<Boucle>,
    mouvementsExistants: List<Mouvement>,
    journauxExistants: List<Journal>,
    entrantes: List<Boucle>,
    mouvementsEntrants: List<Mouvement>,
    journauxEntrants: List<Journal>,
    prendreEntrant: Set<String>
): ResultatFusion {
    val parId = existantes.associateBy { it.id }

    val nouvelles = entrantes.filter { it.id !in parId }

    val misesAJour = entrantes.mapNotNull { e ->
        val ex = parId[e.id] ?: return@mapNotNull null
        if (e.id !in prendreEntrant) return@mapNotNull null
        // Champs scalaires descriptifs uniquement ; on préserve id/creee/statut/source.
        ex.copy(
            type = e.type,
            titre = e.titre,
            origine = e.origine,
            echeance = e.echeance,
            tiers = e.tiers,
            preuveAttendue = e.preuveAttendue,
            blocage = e.blocage,
            impact = e.impact,
            defaut = e.defaut,
            milieu = e.milieu
        )
    }

    val clesMvt = mouvementsExistants.mapTo(HashSet()) { Triple(it.boucleId, it.date, it.contenu) }
    val mvtAjoutes = mouvementsEntrants.filter { Triple(it.boucleId, it.date, it.contenu) !in clesMvt }

    val clesJrn = journauxExistants.mapTo(HashSet()) { Triple(it.boucleId, it.date, it.texte) }
    val jrnAjoutes = journauxEntrants.filter { Triple(it.boucleId, it.date, it.texte) !in clesJrn }

    return ResultatFusion(nouvelles, misesAJour, mvtAjoutes, jrnAjoutes)
}

/** Un champ scalaire qui diverge entre existant et entrant (pour l'affichage du diff). */
data class DiffChamp(val champ: String, val existant: String?, val entrant: String?)

/** Boucle entrante dont l'id existe déjà et dont au moins un champ scalaire diffère. */
data class ConflitFusion(
    val id: String,
    val diffs: List<DiffChamp>
)

/**
 * Liste des conflits (id existant + champ scalaire divergent) à présenter pour
 * arbitrage « garder / prendre ». Les nouvelles boucles et les
 * mouvements/journaux ajoutés n'ont pas besoin d'arbitrage.
 */
fun calculerConflits(existantes: List<Boucle>, entrantes: List<Boucle>): List<ConflitFusion> {
    val parId = existantes.associateBy { it.id }
    return entrantes.mapNotNull { e ->
        val ex = parId[e.id] ?: return@mapNotNull null
        val diffs = buildList {
            fun cmp(nom: String, a: String?, b: String?) { if (a != b) add(DiffChamp(nom, a, b)) }
            cmp("type", ex.type, e.type)
            cmp("titre", ex.titre, e.titre)
            cmp("origine", ex.origine, e.origine)
            cmp("échéance", ex.echeance?.toString(), e.echeance?.toString())
            cmp("tiers", ex.tiers, e.tiers)
            cmp("preuveAttendue", ex.preuveAttendue, e.preuveAttendue)
            cmp("blocage", ex.blocage, e.blocage)
            cmp("impact", ex.impact, e.impact)
            cmp("defaut", ex.defaut, e.defaut)
            cmp("milieu", ex.milieu, e.milieu)
        }
        if (diffs.isEmpty()) null else ConflitFusion(e.id, diffs)
    }
}
