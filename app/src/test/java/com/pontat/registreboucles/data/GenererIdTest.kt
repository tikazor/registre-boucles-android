package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Identifiants préfixés par appareil (invariant I9) : chaque appareil ne compte
 * que ses propres ids, ce qui rend deux appareils incapables d'émettre le même.
 */
class GenererIdTest {

    @Test
    fun base_vide_demarre_a_001_avec_le_prefixe_de_l_appareil() {
        assertEquals("B-001", genererProchainId("B", emptyList()))
        assertEquals("PRO-001", genererProchainId("PRO", emptyList()))
    }

    @Test
    fun increment_le_plus_grand_numero_meme_dans_le_desordre() {
        assertEquals("B-006", genererProchainId("B", listOf("B-001", "B-005", "B-003")))
    }

    @Test
    fun les_trous_ne_sont_pas_reutilises() {
        // Manque B-002..B-009 : on continue APRÈS le max, pas dans les trous.
        assertEquals("B-011", genererProchainId("B", listOf("B-001", "B-010")))
    }

    @Test
    fun les_ids_d_un_autre_appareil_sont_ignores() {
        // L'appareil PRO démarre à 001 même si B a déjà 40 boucles : les deux
        // séquences sont indépendantes, aucune collision possible.
        assertEquals("PRO-001", genererProchainId("PRO", listOf("B-001", "B-040")))
        // Et B continue la sienne sans se soucier de PRO.
        assertEquals("B-041", genererProchainId("B", listOf("B-040", "PRO-007", "PRO-100")))
    }

    @Test
    fun les_ids_historiques_non_conformes_sont_ignores_sans_crash() {
        assertEquals(
            "B-003",
            genererProchainId("B", listOf("X", "B-002", "autre", "B-abc", "b-999", "", "B-"))
        )
    }

    @Test
    fun format_sur_trois_chiffres_puis_deborde() {
        assertEquals("B-100", genererProchainId("B", listOf("B-099")))
        assertEquals("B-1000", genererProchainId("B", listOf("B-999")))
    }

    @Test
    fun un_prefixe_n_est_pas_confondu_avec_un_prefixe_plus_long() {
        // « B » ne doit pas compter les ids de « BUR ».
        assertEquals("B-001", genererProchainId("B", listOf("BUR-050")))
        assertEquals("BUR-051", genererProchainId("BUR", listOf("BUR-050", "B-900")))
    }

    @Test
    fun conformite_des_identifiants() {
        assertTrue(idConforme("B-001"))
        assertTrue(idConforme("PROJ-1234"))
        assertFalse(idConforme("B-1"))         // moins de 3 chiffres
        assertFalse(idConforme("b-001"))       // minuscules
        assertFalse(idConforme("TROPLONG-001"))
        assertFalse(idConforme("B001"))        // pas de tiret
        assertFalse(idConforme("X"))
    }
}
