package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Effet d'une décision de supervision sur les captures d'origine (AND-09).
 *
 * Le point délicat est le dernier test : une même note qui a nourri deux
 * propositions dont l'une est acceptée et l'autre rejetée.
 */
class SupervisionCapturesTest {

    private val T = 1_784_982_153_000L

    private fun capture(
        id: String,
        statut: StatutCapture = StatutCapture.BRUTE,
        boucleLiee: String? = null
    ) = Capture(
        id = id, contenuBrut = "note $id", titre = null, appareil = "B",
        appSource = "com.miui.notes", capturee = T, empreinte = empreinteCapture("note $id"),
        statut = statut.valeurStockee(), boucleLiee = boucleLiee
    )

    // ── Acceptation ──

    @Test
    fun l_acceptation_fait_aboutir_les_captures_d_origine() {
        val captures = listOf(capture("C-1"), capture("C-2", StatutCapture.EXPORTEE))
        val ecrites = transitionsCapturesApresSupervision(
            captures, DecisionSupervision.ACCEPTATION, "IA-001"
        )
        assertEquals(2, ecrites.size)
        assertTrue(ecrites.all { it.statutTypé() == StatutCapture.TRAITEE })
        assertTrue("chacune porte la boucle produite", ecrites.all { it.boucleLiee == "IA-001" })
    }

    // ── Rejet ──

    @Test
    fun le_rejet_rend_la_matiere_premiere_a_nouveau_disponible() {
        // La proposition était mauvaise ; la note, elle, reste bonne.
        val captures = listOf(capture("C-1", StatutCapture.EXPORTEE), capture("C-2", StatutCapture.IGNOREE))
        val ecrites = transitionsCapturesApresSupervision(
            captures, DecisionSupervision.REJET, "IA-001"
        )
        assertEquals(2, ecrites.size)
        assertTrue(ecrites.all { it.statutTypé() == StatutCapture.BRUTE })
    }

    @Test
    fun aucune_ecriture_inutile() {
        // Déjà dans l'état visé : rien à réécrire.
        assertTrue(
            transitionsCapturesApresSupervision(
                listOf(capture("C-1", StatutCapture.BRUTE)), DecisionSupervision.REJET, "IA-001"
            ).isEmpty()
        )
        assertTrue(
            transitionsCapturesApresSupervision(
                listOf(capture("C-1", StatutCapture.TRAITEE, boucleLiee = "B-001")),
                DecisionSupervision.ACCEPTATION, "IA-001"
            ).isEmpty()
        )
    }

    // ── Une capture, deux propositions de sorts différents ──

    @Test
    fun traitee_est_absorbant_quel_que_soit_l_ordre_des_decisions() {
        // Une note a nourri deux propositions : IA-001 acceptée, IA-002 rejetée.
        // Ordre 1 : acceptation d'abord.
        var etat = capture("C-1")
        etat = transitionsCapturesApresSupervision(
            listOf(etat), DecisionSupervision.ACCEPTATION, "IA-001"
        ).single()
        assertEquals(StatutCapture.TRAITEE, etat.statutTypé())
        val apresRejet = transitionsCapturesApresSupervision(
            listOf(etat), DecisionSupervision.REJET, "IA-002"
        )
        assertTrue("un rejet ailleurs ne défait pas un aboutissement", apresRejet.isEmpty())

        // Ordre 2 : rejet d'abord, puis acceptation.
        var autre = capture("C-1", StatutCapture.EXPORTEE)
        autre = transitionsCapturesApresSupervision(
            listOf(autre), DecisionSupervision.REJET, "IA-002"
        ).single()
        assertEquals(StatutCapture.BRUTE, autre.statutTypé())
        autre = transitionsCapturesApresSupervision(
            listOf(autre), DecisionSupervision.ACCEPTATION, "IA-001"
        ).single()

        // Les deux ordres convergent : le résultat ne dépend pas de la chronologie.
        assertEquals(etat.statutTypé(), autre.statutTypé())
        assertEquals(StatutCapture.TRAITEE, autre.statutTypé())
        assertEquals("IA-001", autre.boucleLiee)
    }

    @Test
    fun la_boucle_designee_reste_la_premiere_qui_a_abouti() {
        // Deux acceptations successives : la capture garde la boucle qu'elle a
        // réellement produite en premier, on ne réécrit pas son lien.
        val traitee = capture("C-1", StatutCapture.TRAITEE, boucleLiee = "IA-001")
        assertTrue(
            transitionsCapturesApresSupervision(
                listOf(traitee), DecisionSupervision.ACCEPTATION, "IA-002"
            ).isEmpty()
        )
    }

    // ── Trace de l'acceptation (complète AND-04, ne la remplace pas) ──

    @Test
    fun le_libelle_d_acceptation_reste_inchange_sans_origine() {
        assertEquals(
            "Proposition IA acceptée",
            mouvementAcceptation("IA-001", T, apresAmendement = false).contenu
        )
        assertEquals(
            "Proposition IA acceptée après amendement",
            mouvementAcceptation("IA-001", T, apresAmendement = true).contenu
        )
    }

    @Test
    fun le_libelle_d_acceptation_mentionne_les_captures_d_origine() {
        assertEquals(
            "Proposition IA acceptée (origine : C-20260725-142233-9f2b, C-20260726-081500-3a1c)",
            mouvementAcceptation(
                "IA-001", T, apresAmendement = false,
                origines = listOf("C-20260725-142233-9f2b", "C-20260726-081500-3a1c")
            ).contenu
        )
        assertEquals("", mentionOrigines(emptyList()))
    }

    // ── Liens ──

    @Test
    fun les_liens_declares_sont_construits_sans_filtrer_les_ids_inconnus() {
        val liens = liensDepuisOrigines(
            mapOf("IA-001" to listOf("C-1", "C-2", "C-1"), "IA-002" to listOf("C-9")), T
        )
        assertEquals("doublon éliminé par boucle", 3, liens.size)
        assertEquals(setOf("C-1", "C-2"), liens.filter { it.boucleId == "IA-001" }.map { it.captureId }.toSet())
        assertTrue(liens.all { OrigineLien.depuis(it.origine) == OrigineLien.PROPOSITION })
        assertTrue(liens.all { it.lieeLe == T })
    }

    @Test
    fun un_lien_manuel_se_distingue_d_un_lien_declare() {
        val manuel = lienCapture("C-1", "B-041", OrigineLien.MANUELLE, T)
        assertEquals(OrigineLien.MANUELLE, OrigineLien.depuis(manuel.origine))
        assertEquals("manuelle", manuel.origine)
        assertNull(OrigineLien.depuis("bidule"))
        assertNull(OrigineLien.depuis(null))
    }
}
