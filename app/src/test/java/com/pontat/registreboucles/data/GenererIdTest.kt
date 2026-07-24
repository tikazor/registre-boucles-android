package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** genererProchainId : liste vide, incrément du max, trous non réutilisés, ids non conformes ignorés. */
class GenererIdTest {

    @Test
    fun liste_vide_demarre_a_B001() {
        assertEquals("B-001", genererProchainId(emptyList()))
    }

    @Test
    fun increment_le_plus_grand_numero_meme_dans_le_desordre() {
        assertEquals("B-006", genererProchainId(listOf("B-001", "B-005", "B-003")))
    }

    @Test
    fun les_trous_ne_sont_pas_reutilises() {
        // Manque B-002..B-009 : on continue APRÈS le max, pas dans les trous.
        assertEquals("B-011", genererProchainId(listOf("B-001", "B-010")))
    }

    @Test
    fun ids_non_conformes_ignores() {
        assertEquals("B-003", genererProchainId(listOf("X", "B-002", "autre", "B-abc", "b-999")))
    }

    @Test
    fun format_sur_trois_chiffres_puis_deborde() {
        assertEquals("B-100", genererProchainId(listOf("B-099")))
        assertEquals("B-1000", genererProchainId(listOf("B-999")))
    }
}
