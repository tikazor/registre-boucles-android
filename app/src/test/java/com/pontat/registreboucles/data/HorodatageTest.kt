package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Horodatage de modification et traces de suppression (invariant I10).
 */
class HorodatageTest {

    private fun boucle(creee: Long, modifieeLe: Long? = null, modifieePar: String? = null) = Boucle(
        id = "B-001", type = "ACTION", titre = "t", origine = "o", creee = creee,
        echeance = null, tiers = null, preuveAttendue = "p", blocage = null, impact = "i",
        defaut = null, statut = "ouverte", milieu = null, source = "user",
        modifieeLe = modifieeLe, modifieePar = modifieePar
    )

    @Test
    fun jamais_modifiee_se_lit_comme_la_date_de_creation() {
        // Données antérieures à la v5 : modifieeLe absent.
        assertEquals(1_000L, boucle(creee = 1_000L).derniereModification())
    }

    @Test
    fun une_modification_prime_sur_la_creation() {
        assertEquals(5_000L, boucle(creee = 1_000L, modifieeLe = 5_000L).derniereModification())
    }

    @Test
    fun la_trace_de_suppression_porte_l_appareil_et_la_date() {
        val trace = traceSuppression("B-007", "PRO", 42_000L)
        assertEquals("B-007", trace.boucleId)
        assertEquals("PRO", trace.supprimeePar)
        assertEquals(42_000L, trace.supprimeeLe)
    }
}
