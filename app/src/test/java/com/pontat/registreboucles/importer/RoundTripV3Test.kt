package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.Mouvement
import com.pontat.registreboucles.data.Suppression
import com.pontat.registreboucles.data.derniereModification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Format d'échange v3 : horodatage de modification, provenance de l'export et
 * tombstones. Les cas v1/v2 sont vérifiés ici en NON-RÉGRESSION (les tests
 * d'origine, dans JsonRoundTripTest, restent inchangés).
 */
class RoundTripV3Test {

    private val boucle = Boucle(
        id = "PRO-001", type = "ACTION", titre = "Boucle créée sur l'autre appareil",
        origine = "terrain", creee = 1_700_000_000_000L, echeance = 1_800_000_000_000L,
        tiers = "Équipe", preuveAttendue = "compte rendu", blocage = null,
        impact = "traçabilité", defaut = null, statut = "ouverte", milieu = "PRO",
        source = "user",
        modifieeLe = 1_700_000_500_000L, modifieePar = "PRO"
    )

    private val suppressions = listOf(
        Suppression(boucleId = "B-013", supprimeeLe = 1_700_000_900_000L, supprimeePar = "B"),
        Suppression(boucleId = "PRO-009", supprimeeLe = 1_700_001_000_000L, supprimeePar = "PRO")
    )

    @Test
    fun aller_retour_v3_complet_sans_perte() {
        val mouvements = listOf(
            Mouvement(boucleId = "PRO-001", date = 1_700_000_100_000L, type = "declaration", contenu = "note")
        )
        val journaux = listOf(
            Journal(boucleId = "PRO-001", date = 1_700_000_200_000L, type = "PREUVE", texte = "preuve")
        )

        val json = JsonExporter.serialiser(
            listOf(boucle), mouvements, journaux, suppressions,
            codeAppareil = "PRO", exporteLe = 1_700_002_000_000L
        )
        val res = JsonImporter.parse(json)

        assertEquals("horodatage de modification préservé", listOf(boucle), res.boucles)
        assertEquals(mouvements, res.mouvements)
        assertEquals(journaux, res.journaux)
        assertEquals("tombstones préservées", suppressions, res.suppressions)
        assertEquals("PRO", res.codeAppareilSource)
    }

    @Test
    fun l_export_declare_bien_la_version_3_et_sa_provenance() {
        val json = JsonExporter.serialiser(
            listOf(boucle), emptyList(), emptyList(), emptyList(),
            codeAppareil = "B", exporteLe = 42L
        )
        assertTrue("version 3 déclarée", json.contains("\"version\": 3"))
        assertTrue("appareil émetteur déclaré", json.contains("\"codeAppareil\": \"B\""))
        assertTrue("date d'export déclarée", json.contains("\"exporteLe\": 42"))
    }

    @Test
    fun un_fichier_v2_reste_importable_les_nouveaux_champs_etant_absents() {
        // Format v2 : ni codeAppareil, ni exporteLe, ni suppressions, ni modifieeLe.
        val v2 = """
            {
              "version": 2,
              "boucles": [
                {
                  "id": "B-001", "type": "ACTION", "titre": "Ancienne",
                  "origine": "o", "creee": "2026-01-02T03:04:05Z",
                  "preuveAttendue": "p", "impact": "i", "statut": "ouverte"
                }
              ],
              "journaux": []
            }
        """.trimIndent()
        val res = JsonImporter.parse(v2)
        val b = res.boucles.single()
        assertNull("jamais modifiée", b.modifieeLe)
        assertNull(b.modifieePar)
        assertTrue("aucune tombstone", res.suppressions.isEmpty())
        assertNull("aucune provenance déclarée", res.codeAppareilSource)
        // Et la lecture retombe sur la date de création.
        assertEquals(b.creee, b.derniereModification())
    }

    @Test
    fun un_fichier_v1_sans_version_reste_importable() {
        val v1 = """
            {
              "boucles": [
                {
                  "id": "B-002", "type": "ACTION", "titre": "Très ancienne",
                  "origine": "o", "creee": "2026-01-02", "tiers": true,
                  "preuveAttendue": "p", "impact": "i", "statut": "ouverte"
                }
              ]
            }
        """.trimIndent()
        val res = JsonImporter.parse(v1)
        assertEquals(1, res.boucles.size)
        assertEquals("Oui", res.boucles.single().tiers)   // booléen legacy toléré
        assertTrue(res.suppressions.isEmpty())
        assertNull(res.boucles.single().modifieeLe)
    }

    @Test
    fun les_identifiants_hors_convention_sont_toleres_et_signales() {
        val json = """
            {
              "version": 3,
              "boucles": [
                { "id": "B-001", "type": "A", "titre": "conforme", "origine": "o",
                  "creee": "2026-01-01", "preuveAttendue": "p", "impact": "i", "statut": "ouverte" },
                { "id": "legacy-7", "type": "A", "titre": "historique", "origine": "o",
                  "creee": "2026-01-01", "preuveAttendue": "p", "impact": "i", "statut": "ouverte" }
              ]
            }
        """.trimIndent()
        val res = JsonImporter.parse(json)
        // Tolérés : les deux boucles entrent, sans renumérotation.
        assertEquals(2, res.boucles.size)
        assertEquals("legacy-7", res.boucles[1].id)
        // Mais signalés dans le rapport d'import.
        assertEquals(listOf("legacy-7"), res.idsNonConformes)
    }
}
