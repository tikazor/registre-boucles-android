package com.pontat.registreboucles.data

/**
 * Résultat de la coercition des propositions IA à l'import :
 * les boucles corrigées + les mouvements de trace à insérer.
 */
data class ResultatCoercition(
    val boucles: List<Boucle>,
    val mouvementsTrace: List<Mouvement>
)

/**
 * Coercition (PURE, testable) : la supervision est une propriété de l'APP, pas
 * du producteur. Toute boucle NOUVELLE (id absent de la base au moment de
 * l'écriture) déclarée `source = ia` est forcée en statut PROPOSEE, quel que
 * soit le statut du fichier — elle DOIT passer par l'écran Supervision.
 *
 * - Ne touche PAS aux boucles existantes (id présent dans [idsExistants]) :
 *   leur statut est déjà préservé par [calculerFusion].
 * - Ne touche PAS aux boucles non-IA.
 * - Si le statut déclaré différait de PROPOSEE, on ne perd pas l'intention du
 *   producteur : on la neutralise en la journalisant par un mouvement
 *   `declaration` daté ([maintenant]).
 *
 * @param idsExistants ids déjà en base au moment de l'écriture. Pour « Écraser »,
 *   passer un set vide (la base est vidée : toutes les boucles sont nouvelles).
 */
fun coercerPropositionsIA(
    entrantes: List<Boucle>,
    idsExistants: Set<String>,
    maintenant: Long
): ResultatCoercition {
    val proposee = Statut.PROPOSEE.valeurStockee()
    val trace = ArrayList<Mouvement>()

    val corrigees = entrantes.map { b ->
        val estNouvelle = b.id !in idsExistants
        val estIA = SourceBoucle.depuis(b.source) == SourceBoucle.IA
        if (!estNouvelle || !estIA || b.statut == proposee) {
            b
        } else {
            trace += Mouvement(
                boucleId = b.id,
                date = maintenant,
                type = "declaration",
                contenu = "Statut déclaré \"${b.statut}\" ramené à \"$proposee\" " +
                    "(source IA, supervision obligatoire)"
            )
            b.copy(statut = proposee)
        }
    }
    return ResultatCoercition(corrigees, trace)
}
