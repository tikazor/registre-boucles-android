package com.pontat.registreboucles.importer

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Un JSON invalide (statut inconnu, date malformée) doit échouer proprement. */
class ImportInvalideTest {

    private fun boucleJson(statut: String, creee: String): String = """
        {
          "version": 2,
          "boucles": [
            {
              "id": "IA-001",
              "type": "ACTION",
              "titre": "Proposition",
              "origine": "test",
              "creee": "$creee",
              "preuveAttendue": "p",
              "impact": "i",
              "statut": "$statut",
              "source": "ia"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun statut_inconnu_est_rejete_avec_message_lisible() {
        try {
            JsonImporter.parse(boucleJson(statut = "archivee", creee = "2026-07-20"))
            fail("Un statut inconnu doit lever une ImportException")
        } catch (e: ImportException) {
            val msg = e.message ?: ""
            assertTrue("Cite la boucle", msg.contains("IA-001"))
            assertTrue("Cite la valeur fautive", msg.contains("archivee"))
            assertTrue("Liste les valeurs acceptées", msg.contains("proposee"))
        }
    }

    @Test
    fun date_malformee_est_rejetee_avec_message_lisible() {
        try {
            JsonImporter.parse(boucleJson(statut = "proposee", creee = "20 juillet 2026"))
            fail("Une date malformée doit lever une ImportException")
        } catch (e: ImportException) {
            val msg = e.message ?: ""
            assertTrue("Cite la boucle", msg.contains("IA-001"))
            assertTrue("Cite le format attendu", msg.contains("ISO-8601"))
        }
    }

    @Test
    fun fichier_vide_est_rejete() {
        try {
            JsonImporter.parse("   ")
            fail("Un fichier vide doit lever une ImportException")
        } catch (e: ImportException) {
            assertTrue(e.message?.contains("vide") == true)
        }
    }
}
