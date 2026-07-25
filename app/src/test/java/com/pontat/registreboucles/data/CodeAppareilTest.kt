package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Normalisation du code appareil et suggestion au premier lancement. */
class CodeAppareilTest {

    @Test
    fun normalise_la_casse_et_les_espaces() {
        assertEquals("B", normaliserCodeAppareil("b"))
        assertEquals("PRO", normaliserCodeAppareil(" pro "))
        assertEquals("ABCD", normaliserCodeAppareil("AbCd"))
    }

    @Test
    fun refuse_tout_ce_qui_n_est_pas_1_a_4_lettres() {
        assertNull(normaliserCodeAppareil(""))
        assertNull(normaliserCodeAppareil("   "))
        assertNull(normaliserCodeAppareil(null))
        assertNull(normaliserCodeAppareil("ABCDE"))   // 5 lettres
        assertNull(normaliserCodeAppareil("B1"))      // chiffre
        assertNull(normaliserCodeAppareil("B-"))      // tiret
        assertNull(normaliserCodeAppareil("ÉB"))      // accent
    }

    @Test
    fun suggere_le_prefixe_dominant_des_ids_existants() {
        // Cas réel : un appareil qui possède déjà des B-### doit se voir proposer « B »,
        // pour que la séquence existante continue sans rupture.
        assertEquals("B", codeAppareilSuggere(listOf("B-001", "B-002", "B-040")))
        assertEquals("B", codeAppareilSuggere(listOf("B-001", "B-002", "PRO-001")))
        assertEquals("PRO", codeAppareilSuggere(listOf("PRO-001", "PRO-002", "B-001")))
    }

    @Test
    fun aucune_suggestion_si_rien_d_exploitable() {
        assertNull(codeAppareilSuggere(emptyList()))
        assertNull(codeAppareilSuggere(listOf("bidule", "42", "b-001")))
    }
}
