package com.pontat.registreboucles.data

/**
 * Effet d'une décision de supervision sur les captures qui ont produit la
 * proposition. Fonction PURE : le repository lit les captures liées, applique ce
 * qu'elle rend, et rien de plus.
 *
 * Le principe du lot AND-09 : **une proposition n'est pas une décision.** À
 * l'import, les captures ne bougent donc pas. Elles ne bougent qu'au moment où
 * l'utilisateur tranche.
 *
 * - **Acceptation** : les captures liées passent `TRAITEE` et portent la boucle
 *   produite. Elles ont abouti.
 * - **Rejet** : les captures liées repassent `BRUTE`. La proposition était
 *   mauvaise, la matière première reste bonne — on ne perd pas une note parce
 *   qu'une analyse s'est trompée.
 *
 * ## Une capture, deux propositions de sorts différents
 *
 * `TRAITEE` est **absorbant**. Si une note a nourri deux propositions et que
 * l'une est acceptée, l'autre rejetée, la capture reste `TRAITEE` quel que soit
 * l'ordre des décisions.
 *
 * Ce n'est pas un arbitrage inventé pour ce lot : `Capture.versBrute()` (AND-06)
 * refuse déjà le retour depuis `TRAITEE`, parce que défaire une capture traitée
 * serait une décision sur le **registre** (la boucle existe) et non sur la boîte
 * de réception. La règle en découle, et elle a la propriété qui compte : le
 * résultat ne dépend pas de l'ordre dans lequel on supervise.
 */
enum class DecisionSupervision { ACCEPTATION, REJET }

/**
 * Captures à réécrire, et elles seules : une capture déjà dans l'état visé n'est
 * pas retournée, pour ne pas produire d'écriture inutile.
 *
 * @param boucleId boucle produite — obligatoire pour une acceptation, puisqu'une
 *   capture `TRAITEE` porte toujours la boucle qu'elle a fait naître.
 */
fun transitionsCapturesApresSupervision(
    capturesLiees: List<Capture>,
    decision: DecisionSupervision,
    boucleId: String
): List<Capture> = when (decision) {
    DecisionSupervision.ACCEPTATION -> capturesLiees.mapNotNull { c ->
        // Déjà traitée par une autre proposition : on ne réécrit pas son lien,
        // la première boucle produite reste celle qu'elle désigne.
        if (c.statutTypé() == StatutCapture.TRAITEE) null else c.versTraitee(boucleId)
    }
    DecisionSupervision.REJET -> capturesLiees.mapNotNull { c ->
        when (c.statutTypé()) {
            // TRAITEE est absorbant : un rejet ailleurs ne défait pas un aboutissement.
            StatutCapture.TRAITEE -> null
            StatutCapture.BRUTE -> null          // déjà disponible, rien à écrire
            else -> c.versBrute()                // EXPORTEE, IGNOREE ou statut inconnu
        }
    }
}

/**
 * Mention des captures d'origine ajoutée au mouvement de trace de l'acceptation.
 * Vide quand la proposition ne déclare aucune origine, pour que le libellé
 * historique d'AND-04 reste inchangé mot pour mot.
 */
fun mentionOrigines(captureIds: List<String>): String =
    if (captureIds.isEmpty()) "" else " (origine : ${captureIds.joinToString(", ")})"
