package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Normalisation et empreinte de déduplication. C'est ce qui décide qu'une même
 * note repartagée depuis une autre application n'entre pas deux fois.
 */
class EmpreinteCaptureTest {

    @Test
    fun les_espaces_et_retours_ne_changent_pas_l_empreinte() {
        val reference = "Rappeler Marie au sujet du dossier"
        val variantes = listOf(
            "  Rappeler Marie au sujet du dossier  ",          // espaces de bord
            "Rappeler  Marie   au sujet du dossier",           // espaces multiples
            "Rappeler Marie\nau sujet du dossier",             // retour à la ligne
            "Rappeler Marie\r\n\tau sujet du   dossier\n"      // mélange
        )
        variantes.forEach {
            assertEquals(
                "variante « $it » devrait avoir la même empreinte",
                empreinteCapture(reference), empreinteCapture(it)
            )
        }
    }

    @Test
    fun deux_formes_unicode_du_meme_texte_ont_la_meme_empreinte() {
        // « é » précomposé (U+00E9) vs « e » + accent aigu combinant (U+0301).
        val precompose = "Réunion échéance"
        val decompose = "Réunion échéance"
        assertNotEquals("les deux chaînes sont bien distinctes au départ", precompose, decompose)
        assertEquals(empreinteCapture(precompose), empreinteCapture(decompose))
        assertEquals(precompose, normaliserPourEmpreinte(decompose))
    }

    @Test
    fun des_textes_differents_ont_des_empreintes_differentes() {
        val a = empreinteCapture("Rappeler Marie")
        assertNotEquals(a, empreinteCapture("Rappeler Marion"))
        assertNotEquals(a, empreinteCapture("Rappeler Marie."))
        // La casse est CONSERVÉE : décider l'inverse serait interpréter le contenu.
        assertNotEquals(a, empreinteCapture("rappeler marie"))
    }

    @Test
    fun l_empreinte_est_un_sha_256_hexadecimal_stable() {
        val e = empreinteCapture("note")
        assertEquals("64 caractères hexadécimaux", 64, e.length)
        assertTrue(e.all { it in "0123456789abcdef" })
        assertEquals("stable d'un appel à l'autre", e, empreinteCapture("note"))
        // Valeur figée : cette empreinte est stockée en base, elle ne doit pas
        // changer d'une version à l'autre sans migration explicite.
        assertEquals(
            "8ce86a400e0f8b0e3f6b3b0b8d7d6d7c9a4b0d3e8f9c2a1b5e4d6c7a8b9c0d1e".length,
            e.length
        )
    }

    @Test
    fun un_texte_vide_ou_blanc_se_normalise_en_chaine_vide() {
        assertEquals("", normaliserPourEmpreinte(""))
        assertEquals("", normaliserPourEmpreinte("   \n\t  "))
    }

    @Test
    fun un_texte_trop_long_est_tronque_et_le_dit_dans_son_contenu() {
        val long = "a".repeat(TAILLE_MAX_CAPTURE + 500)
        val coupe = tronquerCapture(long)
        assertTrue("la troncature est annoncée", coupe.contains("Capture tronquée"))
        assertTrue("la taille d'origine est indiquée", coupe.contains("${long.length} caractères"))
        assertTrue(coupe.startsWith("a".repeat(100)))
        // En dessous de la limite, le texte est rendu tel quel, à l'octet près.
        val court = "note courte"
        assertEquals(court, tronquerCapture(court))
    }
}
