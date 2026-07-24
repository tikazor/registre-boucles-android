package com.pontat.registreboucles.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transitions de statut liées à la supervision :
 * - une PROPOSEE ne peut pas être clôturée ;
 * - REJETEE impossible sans motif, et seulement depuis une PROPOSEE ;
 * - accepter passe à OUVERTE en conservant source=IA.
 */
class TransitionStatutTest {

    private class FakeStore(statutInitial: String, source: String? = "ia") : ClotureStore {
        val journaux = mutableListOf<Journal>()
        var boucle: Boucle? = Boucle(
            id = "IA-001", type = "ACTION", titre = "t", origine = "o",
            creee = 0L, echeance = null, tiers = null, preuveAttendue = "p",
            blocage = null, impact = "i", defaut = null, statut = statutInitial,
            milieu = null, source = source
        )
        override suspend fun insererJournal(journal: Journal) { journaux.add(journal) }
        override suspend fun obtenirBoucle(id: String): Boucle? = boucle
        override suspend fun mettreAJourBoucle(boucle: Boucle) { this.boucle = boucle }
    }

    @Test
    fun une_proposee_ne_peut_pas_etre_cloturee() = runTest {
        val store = FakeStore(statutInitial = "proposee")
        var rejete = false
        try {
            executerCloture(store, "IA-001", JournalType.PREUVE, "preuve", 1L)
        } catch (e: IllegalArgumentException) {
            rejete = true
        }
        assertTrue("Clôturer une proposition doit être refusé", rejete)
        assertTrue(store.journaux.isEmpty())
        assertEquals("proposee", store.boucle?.statut)   // rien n'a changé
    }

    @Test
    fun rejeter_sans_motif_est_impossible() = runTest {
        val store = FakeStore(statutInitial = "proposee")
        var rejete = false
        try {
            executerTransitionTerminale(store, "IA-001", Statut.REJETEE, JournalType.DECLARATION, "   ", 1L)
        } catch (e: IllegalArgumentException) {
            rejete = true
        }
        assertTrue("Un rejet sans motif doit être refusé", rejete)
        assertTrue(store.journaux.isEmpty())
        assertEquals("proposee", store.boucle?.statut)
    }

    @Test
    fun rejeter_une_proposee_avec_motif_ecrit_journal_et_passe_a_rejetee() = runTest {
        val store = FakeStore(statutInitial = "proposee")
        executerTransitionTerminale(store, "IA-001", Statut.REJETEE, JournalType.DECLARATION, "hors périmètre", 5L)
        assertEquals(1, store.journaux.size)
        assertEquals("hors périmètre", store.journaux.single().texte)
        assertEquals("rejetee", store.boucle?.statut)
    }

    @Test
    fun on_ne_peut_pas_rejeter_une_boucle_non_proposee() = runTest {
        val store = FakeStore(statutInitial = "ouverte", source = "user")
        var rejete = false
        try {
            executerTransitionTerminale(store, "IA-001", Statut.REJETEE, JournalType.DECLARATION, "motif", 1L)
        } catch (e: IllegalArgumentException) {
            rejete = true
        }
        assertTrue("Seule une proposition peut être rejetée", rejete)
        assertEquals("ouverte", store.boucle?.statut)
    }

    @Test
    fun accepter_passe_a_ouverte_en_conservant_source_ia() {
        val proposition = Boucle(
            id = "IA-001", type = "ACTION", titre = "t", origine = "o", creee = 0L, echeance = null,
            tiers = null, preuveAttendue = "p", blocage = null, impact = "i", defaut = null,
            statut = "proposee", milieu = null, source = "ia"
        )
        val acceptee = accepterProposition(proposition)
        assertEquals("ouverte", acceptee.statut)
        assertEquals("ia", acceptee.source)          // provenance conservée
    }

    @Test
    fun accepter_une_boucle_non_proposee_est_refuse() {
        val ouverte = Boucle(
            id = "B-001", type = "ACTION", titre = "t", origine = "o", creee = 0L, echeance = null,
            tiers = null, preuveAttendue = "p", blocage = null, impact = "i", defaut = null,
            statut = "ouverte", milieu = null, source = "user"
        )
        var rejete = false
        try {
            accepterProposition(ouverte)
        } catch (e: IllegalArgumentException) {
            rejete = true
        }
        assertTrue(rejete)
    }
}
