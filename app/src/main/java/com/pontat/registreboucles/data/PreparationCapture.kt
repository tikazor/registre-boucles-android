package com.pontat.registreboucles.data

/**
 * Décision d'entrée d'une note dans la boîte de réception, calculée SANS effet de
 * bord : tronquer, refuser, dédupliquer ou créer. Le repository se contente de
 * lire l'éventuelle capture de même empreinte et d'appliquer la décision.
 *
 * Pourquoi une fonction pure : la règle de déduplication est le seul endroit où
 * une capture peut être écartée. La vérifier en JVM, sans Room ni appareil, est
 * la seule façon d'être sûr qu'une note ne se perd pas et qu'un doublon ne
 * s'ajoute pas.
 *
 * Ce que cette fonction ne fait PAS, et ne doit jamais faire : lire le contenu
 * pour en déduire un titre, une échéance, une catégorie. Elle le mesure, elle ne
 * l'interprète pas (invariant I15).
 */
sealed interface PreparationCapture {
    /** À insérer telle quelle. */
    data class ACreer(val capture: Capture) : PreparationCapture

    /** Même empreinte déjà présente : rien à insérer, et ce n'est pas une erreur. */
    data class Doublon(val existante: Capture) : PreparationCapture

    /** Rien d'exploitable (texte vide ou blanc). */
    data class Refusee(val motif: String) : PreparationCapture
}

/**
 * @param existanteMemeEmpreinte capture déjà en base portant la même empreinte,
 *   ou null. C'est le seul état que l'appelant doit fournir.
 * @param idsPris identifiants de capture déjà utilisés, pour garantir l'unicité
 *   de l'identifiant produit (une collision écraserait une capture).
 */
fun preparerCapture(
    contenu: String,
    titre: String?,
    appSource: String?,
    appareil: String,
    maintenant: Long,
    existanteMemeEmpreinte: Capture?,
    idsPris: Set<String>
): PreparationCapture {
    val texte = tronquerCapture(contenu)
    if (normaliserPourEmpreinte(texte).isEmpty()) {
        return PreparationCapture.Refusee("Rien à capturer : le texte est vide.")
    }
    val empreinte = empreinteCapture(texte)
    existanteMemeEmpreinte?.let {
        // Contrôle en amont plutôt qu'une contrainte UNIQUE en base : on peut
        // ainsi informer proprement (« déjà capturé le … ») au lieu de planter.
        if (it.empreinte == empreinte) return PreparationCapture.Doublon(it)
    }
    return PreparationCapture.ACreer(
        Capture(
            id = genererIdCaptureUnique(maintenant, idsPris),
            contenuBrut = texte,
            titre = titre?.trim()?.takeIf { it.isNotBlank() },
            appareil = appareil,
            appSource = appSource,
            capturee = maintenant,
            empreinte = empreinte,
            statut = StatutCapture.BRUTE.valeurStockee()
        )
    )
}
