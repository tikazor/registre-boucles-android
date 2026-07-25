package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entrée d'une note dans la boîte de réception. Le test central est celui du
 * doublon : capturer deux fois la même note ne doit produire qu'une ligne, sans
 * erreur ni perte.
 */
class PreparationCaptureTest {

    private val T = 1_784_989_353_000L

    /**
     * Boîte de réception en mémoire, qui rejoue ce que fait le repository : lire
     * l'éventuelle capture de même empreinte, appliquer la décision. C'est le seul
     * moyen de vérifier « aucune nouvelle ligne » sans Room.
     */
    private class BoiteMemoire {
        val lignes = mutableListOf<Capture>()

        fun capturer(
            contenu: String,
            titre: String? = null,
            appSource: String? = "com.miui.notes",
            maintenant: Long
        ): PreparationCapture {
            val empreinte = empreinteCapture(tronquerCapture(contenu))
            val decision = preparerCapture(
                contenu = contenu,
                titre = titre,
                appSource = appSource,
                appareil = "B",
                maintenant = maintenant,
                existanteMemeEmpreinte = lignes.firstOrNull { it.empreinte == empreinte },
                idsPris = lignes.mapTo(HashSet()) { it.id }
            )
            if (decision is PreparationCapture.ACreer) lignes += decision.capture
            return decision
        }
    }

    @Test
    fun capturer_deux_fois_la_meme_note_ne_cree_qu_une_ligne() {
        val boite = BoiteMemoire()
        val premiere = boite.capturer("Rappeler Marie au sujet du dossier", maintenant = T)
        assertTrue(premiere is PreparationCapture.ACreer)
        assertEquals(1, boite.lignes.size)

        // Même note, repartagée plus tard depuis une autre app, reformatée.
        val seconde = boite.capturer(
            "  Rappeler Marie\nau  sujet du dossier ",
            appSource = "com.google.android.keep",
            maintenant = T + 3_600_000L
        )
        assertTrue("doublon détecté", seconde is PreparationCapture.Doublon)
        assertEquals("aucune nouvelle ligne", 1, boite.lignes.size)

        // Et l'information rendue permet de dire QUAND et D'OÙ venait l'originale.
        val existante = (seconde as PreparationCapture.Doublon).existante
        assertEquals(T, existante.capturee)
        assertEquals("com.miui.notes", existante.appSource)
    }

    @Test
    fun deux_notes_differentes_entrent_toutes_les_deux() {
        val boite = BoiteMemoire()
        boite.capturer("Rappeler Marie", maintenant = T)
        boite.capturer("Rappeler Marion", maintenant = T + 1_000)
        assertEquals(2, boite.lignes.size)
        assertEquals("identifiants distincts", 2, boite.lignes.map { it.id }.toSet().size)
    }

    @Test
    fun une_note_vide_est_refusee_sans_rien_ecrire() {
        val boite = BoiteMemoire()
        val decision = boite.capturer("   \n\t ", maintenant = T)
        assertTrue(decision is PreparationCapture.Refusee)
        assertEquals(0, boite.lignes.size)
    }

    @Test
    fun une_capture_creee_arrive_en_brute_sans_boucle_liee() {
        val boite = BoiteMemoire()
        val decision = boite.capturer("note", titre = "Sujet", maintenant = T)
        val c = (decision as PreparationCapture.ACreer).capture
        assertEquals(StatutCapture.BRUTE, c.statutTypé())
        assertNull("aucune boucle n'est créée automatiquement", c.boucleLiee)
        assertEquals("Sujet", c.titre)
        assertEquals("B", c.appareil)
        assertTrue(idCaptureConforme(c.id))
    }

    @Test
    fun le_contenu_est_stocke_tel_quel_sauf_troncature() {
        val boite = BoiteMemoire()
        // Espaces et retours sont neutralisés pour l'EMPREINTE seulement : le
        // contenu, lui, est la matière première et n'est pas réécrit.
        val brut = "  Ligne 1\n\n   Ligne 2  "
        val c = (boite.capturer(brut, maintenant = T) as PreparationCapture.ACreer).capture
        assertEquals(brut, c.contenuBrut)

        val long = "z".repeat(TAILLE_MAX_CAPTURE + 10)
        val tronquee = (boite.capturer(long, maintenant = T + 1) as PreparationCapture.ACreer).capture
        assertTrue(tronquee.contenuBrut.contains("Capture tronquée"))
    }
}
