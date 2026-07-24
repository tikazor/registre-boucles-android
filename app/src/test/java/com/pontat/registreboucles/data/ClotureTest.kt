package com.pontat.registreboucles.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la contrainte structurelle : une fermeture SANS entrée journal est impossible.
 * `executerCloture` est l'unique chemin vers statut=fermee ; il crée toujours un Journal
 * et rejette un texte vide.
 */
class ClotureTest {

    private class FakeStore : ClotureStore {
        val journaux = mutableListOf<Journal>()
        var boucle: Boucle? = Boucle(
            id = "B-001", type = "ACTION", titre = "t", origine = "o",
            creee = 0L, echeance = null, tiers = null, preuveAttendue = "p",
            blocage = null, impact = "i", defaut = null, statut = "ouverte", milieu = null
        )

        override suspend fun insererJournal(journal: Journal) { journaux.add(journal) }
        override suspend fun obtenirBoucle(id: String): Boucle? = boucle
        override suspend fun mettreAJourBoucle(boucle: Boucle) { this.boucle = boucle }
    }

    @Test
    fun cloture_cree_toujours_une_entree_journal_et_ferme() = runTest {
        val store = FakeStore()

        executerCloture(store, "B-001", JournalType.PREUVE, "mail confirmé le 24/07", 123L)

        // Une entrée journal a été créée, avec le bon type et le bon texte.
        assertEquals(1, store.journaux.size)
        assertEquals(JournalType.PREUVE.name, store.journaux[0].type)
        assertEquals("mail confirmé le 24/07", store.journaux[0].texte)
        assertEquals(123L, store.journaux[0].date)
        // Et seulement ALORS la boucle est passée à fermee.
        assertEquals("fermee", store.boucle?.statut)
    }

    @Test
    fun fermeture_sans_texte_de_journal_est_impossible() = runTest {
        val store = FakeStore()
        var rejete = false

        try {
            executerCloture(store, "B-001", JournalType.DECLARATION, "   ", 0L)
        } catch (e: IllegalArgumentException) {
            rejete = true
        }

        assertTrue("Un texte vide doit être rejeté", rejete)
        // Aucune écriture : ni journal, ni fermeture.
        assertTrue(store.journaux.isEmpty())
        assertEquals("ouverte", store.boucle?.statut)
    }

    @Test
    fun aucune_fermeture_ne_laisse_la_boucle_sans_journal() = runTest {
        // Toute boucle fermée via ce chemin possède au moins une entrée journal.
        val store = FakeStore()
        executerCloture(store, "B-001", JournalType.DEFAUT, "défaut appliqué", 1L)
        val fermee = store.boucle?.statut == "fermee"
        assertTrue(fermee)
        assertTrue("Une boucle fermée doit avoir un journal", store.journaux.isNotEmpty())
        assertNull(null) // garde-fou lisibilité
    }
}
