package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fusion : dédup des mouvements/journaux, aucun journal perdu, choix garder/prendre. */
class FusionTest {

    private fun boucle(id: String, titre: String, statut: String = "ouverte", source: String? = "user") =
        Boucle(
            id = id, type = "ACTION", titre = titre, origine = "o", creee = 1L, echeance = null,
            tiers = null, preuveAttendue = "p", blocage = null, impact = "i", defaut = null,
            statut = statut, milieu = null, source = source
        )

    @Test
    fun nouvelle_boucle_creee_avec_son_statut_dorigine() {
        val res = calculerFusion(
            existantes = listOf(boucle("B-001", "Existante")),
            mouvementsExistants = emptyList(),
            journauxExistants = emptyList(),
            entrantes = listOf(boucle("IA-001", "Proposition", statut = "proposee", source = "ia")),
            mouvementsEntrants = emptyList(),
            journauxEntrants = emptyList(),
            prendreEntrant = emptySet()
        )
        assertEquals(1, res.bouclesNouvelles.size)
        val nouvelle = res.bouclesNouvelles.single()
        assertEquals("IA-001", nouvelle.id)
        assertEquals("proposee", nouvelle.statut)   // statut d'origine conservé
        assertEquals("ia", nouvelle.source)
        assertTrue(res.bouclesMisesAJour.isEmpty())
    }

    @Test
    fun prendre_entrant_remplace_les_scalaires_mais_preserve_statut_source_creee() {
        val existante = boucle("B-001", "Ancien titre", statut = "ouverte", source = "user")
        val entrante = existante.copy(titre = "Titre enrichi", impact = "Impact révisé",
            statut = "proposee", source = "ia", creee = 999L)

        val res = calculerFusion(
            listOf(existante), emptyList(), emptyList(),
            listOf(entrante), emptyList(), emptyList(),
            prendreEntrant = setOf("B-001")
        )
        val maj = res.bouclesMisesAJour.single()
        assertEquals("Titre enrichi", maj.titre)     // scalaire pris
        assertEquals("Impact révisé", maj.impact)
        assertEquals("ouverte", maj.statut)          // cycle de vie préservé
        assertEquals("user", maj.source)             // provenance préservée
        assertEquals(1L, maj.creee)                  // date de création préservée
    }

    @Test
    fun garder_existant_ne_touche_pas_la_boucle() {
        val existante = boucle("B-001", "Ancien titre")
        val entrante = existante.copy(titre = "Titre entrant")
        val res = calculerFusion(
            listOf(existante), emptyList(), emptyList(),
            listOf(entrante), emptyList(), emptyList(),
            prendreEntrant = emptySet()   // aucun id choisi -> garder l'existant
        )
        assertTrue("Aucune mise à jour attendue", res.bouclesMisesAJour.isEmpty())
        assertTrue("Aucune nouvelle boucle (id existant)", res.bouclesNouvelles.isEmpty())
    }

    @Test
    fun mouvements_dedupliques_sur_boucleId_date_contenu() {
        val existant = Mouvement(boucleId = "B-001", date = 100L, type = "declaration", contenu = "déjà là")
        val doublon = Mouvement(boucleId = "B-001", date = 100L, type = "autre", contenu = "déjà là")
        val nouveau = Mouvement(boucleId = "B-001", date = 200L, type = "declaration", contenu = "inédit")

        val res = calculerFusion(
            listOf(boucle("B-001", "x")), listOf(existant), emptyList(),
            listOf(boucle("B-001", "x")), listOf(doublon, nouveau), emptyList(),
            prendreEntrant = emptySet()
        )
        assertEquals(1, res.mouvementsAjoutes.size)          // le doublon est écarté
        assertEquals(200L, res.mouvementsAjoutes.single().date)
    }

    @Test
    fun aucun_journal_perdu_et_dedup() {
        val existant = Journal(boucleId = "B-001", date = 10L, type = "PREUVE", texte = "preuve A")
        val doublon = Journal(boucleId = "B-001", date = 10L, type = "PREUVE", texte = "preuve A")
        val nouveau = Journal(boucleId = "B-001", date = 20L, type = "DECLARATION", texte = "preuve B")

        val res = calculerFusion(
            listOf(boucle("B-001", "x")), emptyList(), listOf(existant),
            listOf(boucle("B-001", "x")), emptyList(), listOf(doublon, nouveau),
            prendreEntrant = emptySet()
        )
        assertEquals(1, res.journauxAjoutes.size)            // doublon écarté, aucun perdu
        assertEquals("preuve B", res.journauxAjoutes.single().texte)
    }

    @Test
    fun conflits_listent_les_champs_scalaires_divergents() {
        val existante = boucle("B-001", "Titre A")
        val entrante = existante.copy(titre = "Titre B", impact = "Impact B")
        val conflits = calculerConflits(listOf(existante), listOf(entrante))
        assertEquals(1, conflits.size)
        val champs = conflits.single().diffs.map { it.champ }
        assertTrue(champs.contains("titre"))
        assertTrue(champs.contains("impact"))
        assertFalse("origine identique -> pas de diff", champs.contains("origine"))
    }

    @Test
    fun aucun_conflit_si_boucle_entrante_est_nouvelle() {
        val conflits = calculerConflits(
            existantes = listOf(boucle("B-001", "x")),
            entrantes = listOf(boucle("IA-001", "y", statut = "proposee", source = "ia"))
        )
        assertTrue(conflits.isEmpty())
        assertNull(conflits.firstOrNull())
    }
}
