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
 * UNIQUE chemin de transition vers statut=fermee.
 *
 * Contrainte structurelle : une clôture EXIGE une entrée Journal.
 * - le type (enum) et le texte sont obligatoires par signature ;
 * - le texte vide est rejeté (require) ;
 * - l'entrée Journal est créée AVANT le passage à `fermee`.
 * Aucun autre code ne met `statut = "fermee"` : fermer sans journal est impossible.
 */
suspend fun executerCloture(
    store: ClotureStore,
    boucleId: String,
    type: JournalType,
    texte: String,
    dateMillis: Long
) {
    require(texte.isNotBlank()) { "Une clôture exige un texte de journal non vide." }
    store.insererJournal(
        Journal(boucleId = boucleId, date = dateMillis, type = type.name, texte = texte.trim())
    )
    val boucle = store.obtenirBoucle(boucleId) ?: return
    store.mettreAJourBoucle(boucle.copy(statut = "fermee"))
}
