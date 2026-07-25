package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coercition IA à l'import : une proposition IA n'entre jamais directement active. */
class CoercitionTest {

    private fun boucle(id: String, statut: String, source: String?) = Boucle(
        id = id, type = "ACTION", titre = "t", origine = "o", creee = 1L, echeance = null,
        tiers = null, preuveAttendue = "p", blocage = null, impact = "i", defaut = null,
        statut = statut, milieu = null, source = source
    )

    @Test
    fun nouvelle_ia_ouverte_est_forcee_en_proposee_avec_trace() {
        val res = coercerPropositionsIA(
            entrantes = listOf(boucle("IA-006", "ouverte", "ia")),
            idsExistants = emptySet(),
            maintenant = 42L
        )
        assertEquals("proposee", res.boucles.single().statut)
        assertEquals(1, res.mouvementsTrace.size)
        val trace = res.mouvementsTrace.single()
        assertEquals("IA-006", trace.boucleId)
        assertEquals("declaration", trace.type)
        assertEquals(42L, trace.date)
        assertTrue(trace.contenu.contains("ouverte"))
        assertTrue(trace.contenu.contains("proposee"))
    }

    @Test
    fun nouvelle_ia_deja_proposee_est_inchangee_sans_trace() {
        val res = coercerPropositionsIA(
            listOf(boucle("IA-001", "proposee", "ia")), emptySet(), 0L
        )
        assertEquals("proposee", res.boucles.single().statut)
        assertTrue("Aucune trace si le statut était déjà correct", res.mouvementsTrace.isEmpty())
    }

    @Test
    fun nouvelle_non_ia_est_inchangee() {
        val res = coercerPropositionsIA(
            listOf(boucle("B-050", "ouverte", "user")), emptySet(), 0L
        )
        assertEquals("ouverte", res.boucles.single().statut)
        assertTrue(res.mouvementsTrace.isEmpty())
    }

    @Test
    fun boucle_existante_ia_n_est_pas_coercee() {
        // Cas Fusionner : l'id existe déjà -> statut préservé, aucune coercition.
        val res = coercerPropositionsIA(
            entrantes = listOf(boucle("B-001", "ouverte", "ia")),
            idsExistants = setOf("B-001"),
            maintenant = 0L
        )
        assertEquals("ouverte", res.boucles.single().statut)
        assertTrue(res.mouvementsTrace.isEmpty())
    }

    @Test
    fun mode_ecraser_coerce_toutes_les_ia_car_set_vide() {
        val res = coercerPropositionsIA(
            entrantes = listOf(
                boucle("IA-001", "proposee", "ia"),
                boucle("IA-006", "ouverte", "ia"),
                boucle("B-001", "en_cours", "ia")   // « existant » sans objet : base vidée
            ),
            idsExistants = emptySet(),   // Écraser : toutes nouvelles
            maintenant = 0L
        )
        assertTrue("Toutes les IA finissent en proposee", res.boucles.all { it.statut == "proposee" })
        // Trace uniquement pour celles dont le statut déclaré différait (IA-006 et B-001).
        assertEquals(2, res.mouvementsTrace.size)
    }
}
