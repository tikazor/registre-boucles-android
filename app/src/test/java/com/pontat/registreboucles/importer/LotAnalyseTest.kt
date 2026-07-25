package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Capture
import com.pontat.registreboucles.data.StatutCapture
import com.pontat.registreboucles.data.empreinteCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Format du lot d'analyse : ce qui SORT de l'app pour être analysé ailleurs.
 * Le lot ne transporte aucune boucle et aucun statut de registre — l'analyse est
 * externe, son résultat revient par l'import de propositions supervisées.
 */
class LotAnalyseTest {

    private val T = 1_784_982_153_000L   // 25/07/2026 14:22:33 (Paris, UTC+2)
    private val zone = ZoneId.of("Europe/Paris")

    private fun capture(id: String, contenu: String, titre: String? = null) = Capture(
        id = id, contenuBrut = contenu, titre = titre, appareil = "B",
        appSource = "com.miui.notes", capturee = T, empreinte = empreinteCapture(contenu),
        statut = StatutCapture.BRUTE.valeurStockee()
    )

    @Test
    fun le_lot_declare_sa_version_son_type_et_sa_date() {
        val json = LotAnalyseExporter.serialiser(
            listOf(capture("C-20260725-142233-9f2b", "Rappeler Marie")), T
        )
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"type\": \"lot-analyse\""))
        assertTrue(json.contains("\"exporteLe\": $T"))
    }

    @Test
    fun chaque_capture_exporte_exactement_les_six_champs_prevus() {
        val json = LotAnalyseExporter.serialiser(
            listOf(capture("C-20260725-142233-9f2b", "Rappeler Marie", titre = "Dossier")), T
        )
        listOf("id", "contenuBrut", "titre", "appareil", "appSource", "capturee").forEach {
            assertTrue("champ $it absent", json.contains("\"$it\""))
        }
        // Et RIEN du registre ne fuit dans le lot : ni statut de capture, ni
        // empreinte, ni boucle liée — le lot n'est pas un état, c'est de la matière.
        listOf("statut", "empreinte", "boucleLiee").forEach {
            assertTrue("champ $it ne doit pas être exporté", !json.contains("\"$it\""))
        }
    }

    @Test
    fun le_contenu_brut_part_sans_reecriture() {
        val brut = "Ligne 1\n\n  Ligne 2 avec « accents » et \"guillemets\""
        val json = LotAnalyseExporter.serialiser(listOf(capture("C-1", brut)), T)
        // JSON échappe les retours et les guillemets, mais ne réécrit pas le texte.
        assertTrue(json.contains("Ligne 1\\n\\n  Ligne 2 avec « accents »"))
    }

    @Test
    fun un_lot_vide_reste_un_document_valide() {
        val json = LotAnalyseExporter.serialiser(emptyList(), T)
        assertTrue(json.contains("\"captures\": []"))
    }

    @Test
    fun le_nom_de_fichier_suit_la_convention_annoncee() {
        assertEquals("lot-analyse-20260725-1422.json", LotAnalyseExporter.nomFichier(T, zone))
    }
}
