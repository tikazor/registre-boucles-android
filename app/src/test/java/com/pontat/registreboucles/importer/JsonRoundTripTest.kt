package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.Mouvement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Aller-retour parse(serialiser(x)) == x. C'est le test qui aurait attrapé la
 * perte des journaux et l'écrasement de `tiers` (étape 2).
 */
class JsonRoundTripTest {

    // Boucle "riche" : tiers chaîne libre, échéance présente, milieu présent, accents.
    private val boucleA = Boucle(
        id = "B-001",
        type = "ACTION",
        titre = "Réunion éducative — clôturée à l'été",
        origine = "ChatGPT 13/07 — audit des suivis",
        creee = 1_700_000_000_000L,
        echeance = 1_800_000_050_500L,
        tiers = "Cadre ASE référent",   // valeur libre : ne doit PAS devenir "Oui"/null
        preuveAttendue = "Message confirmant le créneau",
        blocage = "En attente d'un retour",
        impact = "Permanence impossible à organiser",
        defaut = "Relancer à J+7",
        statut = "ouverte",
        milieu = "PRO"
    )

    // Boucle "minimale" : tiers null, échéance null, milieu absent, statut terminal.
    private val boucleB = Boucle(
        id = "B-002",
        type = "DECISION",
        titre = "Clôture avec preuve à conserver",
        origine = "Terrain",
        creee = 1_699_000_000_000L,
        echeance = null,
        tiers = null,
        preuveAttendue = "Compte rendu signé",
        blocage = null,
        impact = "Traçabilité",
        defaut = null,
        statut = "fermee",
        milieu = null
    )

    // Mouvements de A, déjà triés par date (l'export trie ; type "declaration"
    // = seul type restitué par le schéma {date, note}).
    private val mouvements = listOf(
        Mouvement(boucleId = "B-001", date = 1_700_000_100_000L, type = "declaration", contenu = "Première relance é@à"),
        Mouvement(boucleId = "B-001", date = 1_700_000_200_000L, type = "declaration", contenu = "Deuxième relance")
    )

    // Journaux : la donnée la plus précieuse, doit survivre à l'aller-retour.
    private val journaux = listOf(
        Journal(boucleId = "B-001", date = 1_700_000_300_000L, type = "PREUVE", texte = "Mail reçu — clôturé"),
        Journal(boucleId = "B-002", date = 1_699_500_000_000L, type = "DECLARATION", texte = "Clôturée sur déclaration")
    )

    @Test
    fun aller_retour_sans_perte_de_champ() {
        val json = JsonExporter.serialiser(listOf(boucleA, boucleB), mouvements, journaux)
        val res = JsonImporter.parse(json)

        assertEquals("boucles préservées à l'identique", listOf(boucleA, boucleB), res.boucles)
        assertEquals("mouvements préservés", mouvements, res.mouvements)
        assertEquals("journaux préservés (preuves de clôture)", journaux, res.journaux)
    }

    @Test
    fun tiers_chaine_libre_est_preserve() {
        val json = JsonExporter.serialiser(listOf(boucleA), emptyList(), emptyList())
        val res = JsonImporter.parse(json)
        assertEquals("Cadre ASE référent", res.boucles.single().tiers)
    }

    @Test
    fun tiers_booleen_legacy_est_tolere_a_l_import() {
        // Ancien format : tiers booléen. true -> "Oui", false -> null.
        val jsonTrue = baseJson(tiersBrut = "true")
        assertEquals("Oui", JsonImporter.parse(jsonTrue).boucles.single().tiers)

        val jsonFalse = baseJson(tiersBrut = "false")
        assertNull(JsonImporter.parse(jsonFalse).boucles.single().tiers)

        // Chaîne "true" (valeur libre) NE doit PAS être confondue avec le booléen.
        val jsonChaine = baseJson(tiersBrut = "\"true\"")
        assertEquals("true", JsonImporter.parse(jsonChaine).boucles.single().tiers)
    }

    @Test
    fun ancien_format_sans_version_ni_journaux_est_accepte() {
        val res = JsonImporter.parse(baseJson(tiersBrut = "null"))
        assertEquals(1, res.boucles.size)
        assertEquals(0, res.journaux.size)
    }

    private fun baseJson(tiersBrut: String): String = """
        {
          "boucles": [
            {
              "id": "B-050",
              "type": "ACTION",
              "titre": "Legacy",
              "origine": "import",
              "creee": "2026-01-02T03:04:05Z",
              "tiers": $tiersBrut,
              "preuveAttendue": "p",
              "impact": "i",
              "statut": "ouverte"
            }
          ]
        }
    """.trimIndent()
}
