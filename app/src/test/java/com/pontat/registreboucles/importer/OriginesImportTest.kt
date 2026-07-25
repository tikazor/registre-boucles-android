package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Mouvement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Champ `origines` du contrat d'échange (AND-09) : les captures qui ont produit
 * une proposition. Optionnel, tolérant, et sans effet sur la validité d'un
 * fichier — un lot peut avoir été analysé après une réinstallation.
 */
class OriginesImportTest {

    @Test
    fun un_fichier_sans_origines_reste_valide() {
        // Non-régression : c'est le cas de TOUS les fichiers antérieurs à AND-09.
        val json = """
            {
              "version": 3,
              "boucles": [
                { "id": "IA-001", "type": "ACTION", "titre": "Sans origine", "origine": "o",
                  "creee": "2026-07-25", "preuveAttendue": "p", "impact": "i",
                  "statut": "proposee", "source": "ia" }
              ]
            }
        """.trimIndent()
        val res = JsonImporter.parse(json)
        assertEquals(1, res.boucles.size)
        assertTrue("aucune origine déclarée", res.origines.isEmpty())
    }

    @Test
    fun les_origines_declarees_sont_rendues_par_boucle() {
        val json = """
            {
              "version": 3,
              "boucles": [
                { "id": "IA-001", "type": "ACTION", "titre": "Issue de deux notes", "origine": "o",
                  "creee": "2026-07-25", "preuveAttendue": "p", "impact": "i",
                  "statut": "proposee", "source": "ia",
                  "origines": ["C-20260725-142233-9f2b", "C-20260726-081500-3a1c"] },
                { "id": "IA-002", "type": "ACTION", "titre": "Sans origine", "origine": "o",
                  "creee": "2026-07-25", "preuveAttendue": "p", "impact": "i",
                  "statut": "proposee", "source": "ia" }
              ]
            }
        """.trimIndent()
        val res = JsonImporter.parse(json)
        assertEquals(
            listOf("C-20260725-142233-9f2b", "C-20260726-081500-3a1c"),
            res.origines["IA-001"]
        )
        assertTrue("une boucle sans origines n'entre pas dans la table", "IA-002" !in res.origines)
    }

    @Test
    fun un_identifiant_de_capture_inconnu_n_empeche_pas_l_import() {
        // Le lot a pu être analysé après une réinstallation : la boucle entre quand
        // même, le lien est conservé tel quel, et l'absence est signalée ailleurs
        // (rapport d'import, via BoucleRepository.capturesInconnues).
        val json = """
            {
              "version": 3,
              "boucles": [
                { "id": "IA-001", "type": "ACTION", "titre": "Origine disparue", "origine": "o",
                  "creee": "2026-07-25", "preuveAttendue": "p", "impact": "i",
                  "statut": "proposee", "source": "ia",
                  "origines": ["C-20200101-000000-0000"] }
              ]
            }
        """.trimIndent()
        val res = JsonImporter.parse(json)
        assertEquals(1, res.boucles.size)
        assertEquals(listOf("C-20200101-000000-0000"), res.origines["IA-001"])
    }

    @Test
    fun les_entrees_vides_sont_nettoyees_sans_rejeter_le_fichier() {
        val json = """
            {
              "version": 3,
              "boucles": [
                { "id": "IA-001", "type": "ACTION", "titre": "Origines douteuses", "origine": "o",
                  "creee": "2026-07-25", "preuveAttendue": "p", "impact": "i",
                  "statut": "proposee", "source": "ia",
                  "origines": ["  C-20260725-142233-9f2b  ", "", "   "] }
              ]
            }
        """.trimIndent()
        val res = JsonImporter.parse(json)
        assertEquals(listOf("C-20260725-142233-9f2b"), res.origines["IA-001"])
    }

    @Test
    fun l_aller_retour_export_import_preserve_les_origines() {
        val boucle = Boucle(
            id = "IA-001", type = "ACTION", titre = "Née d'une note", origine = "capture com.miui.notes",
            creee = 1_784_982_153_000L, echeance = null, tiers = null, preuveAttendue = "p",
            blocage = null, impact = "i", defaut = null, statut = "ouverte", milieu = null,
            source = "ia", modifieeLe = 1_784_982_153_000L, modifieePar = "B"
        )
        val origines = mapOf("IA-001" to listOf("C-20260725-142233-9f2b"))
        val json = JsonExporter.serialiser(
            listOf(boucle), emptyList<Mouvement>(), emptyList(), emptyList(), "B", 1L, origines
        )
        assertTrue(json.contains("\"origines\""))
        assertEquals(origines, JsonImporter.parse(json).origines)
    }
}
