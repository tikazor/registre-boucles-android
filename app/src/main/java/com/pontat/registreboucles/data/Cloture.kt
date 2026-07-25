package com.pontat.registreboucles.data

/**
 * Dépendances minimales de la clôture (permet un test unitaire pur, sans Room ni Context).
 */
interface ClotureStore {
    suspend fun insererJournal(journal: Journal)
    suspend fun obtenirBoucle(id: String): Boucle?
    suspend fun mettreAJourBoucle(boucle: Boucle)
}

/**
 * UNIQUE chemin d'écriture d'un état TERMINAL (fermee ou rejetee). Toute
 * transition terminale passe par ici : impossible d'atteindre un état terminal
 * sans entrée Journal.
 *
 * Contrainte structurelle :
 * - `statutCible` doit être terminal (require) ;
 * - le texte de journal est obligatoire (require, non vide) ;
 * - l'entrée Journal est créée AVANT le changement d'état ;
 * - garde-fous de transition :
 *     · FERMEE  n'est atteignable que depuis une boucle ACTIVE
 *       (une PROPOSEE ne peut donc PAS être clôturée : il faut l'accepter d'abord) ;
 *     · REJETEE n'est atteignable que depuis une PROPOSEE.
 */
suspend fun executerTransitionTerminale(
    store: ClotureStore,
    boucleId: String,
    statutCible: Statut,
    type: JournalType,
    texte: String,
    dateMillis: Long
) {
    require(statutCible.estTerminal()) { "Statut cible non terminal : $statutCible." }
    require(texte.isNotBlank()) { "Une transition terminale exige un texte de journal non vide." }

    val boucle = store.obtenirBoucle(boucleId) ?: return
    val actuel = Statut.depuis(boucle.statut)
    when (statutCible) {
        Statut.REJETEE -> require(actuel == Statut.PROPOSEE) {
            "Seule une proposition peut être rejetée."
        }
        else -> require(actuel?.estActive() == true) {
            "Seule une boucle active peut passer à ${statutCible.valeurStockee()}."
        }
    }

    store.insererJournal(
        Journal(boucleId = boucleId, date = dateMillis, type = type.name, texte = texte.trim())
    )
    store.mettreAJourBoucle(boucle.copy(statut = statutCible.valeurStockee()))
}

/**
 * Clôture d'une boucle active : cas particulier de [executerTransitionTerminale]
 * vers FERMEE. Aucune duplication de logique.
 */
suspend fun executerCloture(
    store: ClotureStore,
    boucleId: String,
    type: JournalType,
    texte: String,
    dateMillis: Long
) = executerTransitionTerminale(store, boucleId, Statut.FERMEE, type, texte, dateMillis)

/**
 * Accepte une proposition : PROPOSEE -> OUVERTE, source inchangée. Pure et sans
 * journal (l'acceptation n'est pas une transition terminale). Rejette toute
 * boucle qui n'est pas une proposition.
 */
fun accepterProposition(boucle: Boucle): Boucle {
    require(Statut.depuis(boucle.statut) == Statut.PROPOSEE) {
        "Seule une proposition peut être acceptée."
    }
    return boucle.copy(statut = Statut.OUVERTE.valeurStockee())
}

/**
 * Mouvement de trace créé automatiquement à l'acceptation d'une proposition
 * (l'acceptation, contrairement au rejet, n'est PAS terminale : c'est un
 * mouvement, pas un journal). Pure et testable.
 */
fun mouvementAcceptation(
    boucleId: String,
    dateMillis: Long,
    apresAmendement: Boolean,
    /**
     * Captures d'origine (AND-09). Vide -> libellé inchangé mot pour mot par
     * rapport à AND-04 : ce lot COMPLÈTE la trace, il ne la remplace pas.
     */
    origines: List<String> = emptyList()
): Mouvement =
    Mouvement(
        boucleId = boucleId,
        date = dateMillis,
        type = "declaration",
        contenu = (
            if (apresAmendement) "Proposition IA acceptée après amendement"
            else "Proposition IA acceptée"
            ) + mentionOrigines(origines)
    )
