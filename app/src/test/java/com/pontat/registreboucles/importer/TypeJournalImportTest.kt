package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.JournalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le type de journal est VALIDÉ à l'import (AND-10, étape 1) : un type connu est
 * conservé tel quel ; un type inconnu retombe sur la valeur neutre `DECLARATION`
 * — jamais rejeté, pour qu'un backup ancien reste réimportable — et l'écart est
 * signalé dans le rapport d'import.
 */
class TypeJournalImportTest {

    private fun jsonAvecTypeJournal(type: String): String = """
        {
          "version": 3,
          "boucles": [
            {
              "id": "B-001", "type": "ACTION", "titre": "Close",
              "origine": "o", "creee": "2026-01-02T03:04:05Z",
              "preuveAttendue": "p", "impact": "i", "statut": "fermee"
            }
          ],
          "journaux": [
            { "boucleId": "B-001", "date": "2026-01-03T00:00:00Z",
              "type": "$type", "texte": "trace" }
          ]
        }
    """.trimIndent()

    @Test
    fun type_connu_est_conserve_et_non_signale() {
        val res = JsonImporter.parse(jsonAvecTypeJournal("PREUVE"))
        assertEquals("type reconnu conservé", JournalType.PREUVE.name, res.journaux.single().type)
        assertTrue("aucun écart signalé", res.typesJournalInconnus.isEmpty())
    }

    @Test
    fun type_inconnu_est_replie_sur_declaration_et_signale() {
        val res = JsonImporter.parse(jsonAvecTypeJournal("ARCHIVE"))
        assertEquals(
            "type inconnu replié sur la valeur neutre",
            JournalType.DECLARATION.name,
            res.journaux.single().type
        )
        assertEquals(
            "l'écart est signalé avec la valeur brute d'origine",
            listOf("ARCHIVE"),
            res.typesJournalInconnus
        )
    }

    @Test
    fun un_type_inconnu_ne_rejette_pas_l_entree() {
        // Contraste avec un statut inconnu (rejeté) : le journal, lui, est toujours
        // importé — un backup ancien doit rester réimportable.
        val res = JsonImporter.parse(jsonAvecTypeJournal("XYZ"))
        assertEquals("l'entrée de journal est bien importée", 1, res.journaux.size)
    }

    @Test
    fun backup_anterieur_aux_types_connus_reste_vert() {
        // Un backup dont tous les types sont reconnus n'émet aucun signal.
        val json = """
            {
              "version": 3,
              "boucles": [
                { "id": "B-001", "type": "ACTION", "titre": "Close",
                  "origine": "o", "creee": "2026-01-02T03:04:05Z",
                  "preuveAttendue": "p", "impact": "i", "statut": "fermee" }
              ],
              "journaux": [
                { "boucleId": "B-001", "date": "2026-01-03T00:00:00Z",
                  "type": "PREUVE", "texte": "p" },
                { "boucleId": "B-001", "date": "2026-01-04T00:00:00Z",
                  "type": "DECLARATION", "texte": "d" },
                { "boucleId": "B-001", "date": "2026-01-05T00:00:00Z",
                  "type": "DEFAUT", "texte": "x" }
              ]
            }
        """.trimIndent()
        val res = JsonImporter.parse(json)
        assertEquals(3, res.journaux.size)
        assertTrue("aucun type inconnu", res.typesJournalInconnus.isEmpty())
    }
}
