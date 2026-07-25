package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.random.Random

/**
 * Identifiants, statuts et transitions des captures. Vérifie aussi, par
 * réflexion, qu'aucune méthode de suppression n'est exposée : l'invariant I14
 * (« une capture n'est jamais supprimée ») serait sinon une intention, pas une
 * garantie.
 */
class CaptureTest {

    private val zone = ZoneId.of("Europe/Paris")
    // 25/07/2026 14:22:33 heure de Paris (UTC+2).
    private val T = 1_784_982_153_000L

    private fun capture(
        id: String = "C-20260725-142233-9f2b",
        statut: StatutCapture = StatutCapture.BRUTE,
        boucleLiee: String? = null,
        contenu: String = "Rappeler Marie",
        titre: String? = null,
        appSource: String? = "com.miui.notes"
    ) = Capture(
        id = id, contenuBrut = contenu, titre = titre, appareil = "B",
        appSource = appSource, capturee = T, empreinte = empreinteCapture(contenu),
        statut = statut.valeurStockee(), boucleLiee = boucleLiee
    )

    // ── Identifiant ──

    @Test
    fun l_identifiant_respecte_le_format_annonce() {
        val id = genererIdCapture(T, Random(42), zone)
        assertTrue("format inattendu : $id", idCaptureConforme(id))
        assertTrue("horodatage lisible", id.startsWith("C-20260725-142233-"))
        assertEquals("C- + 8 + - + 6 + - + 4", "C-20260725-142233-xxxx".length, id.length)
    }

    @Test
    fun mille_generations_ne_produisent_aucune_collision() {
        // Toutes dans la MÊME seconde, cas le plus défavorable : 4 chiffres hex ne
        // suffisent pas statistiquement à cette échelle (paradoxe des anniversaires),
        // d'où le contrôle d'unicité de genererIdCaptureUnique — sans lui, une
        // capture en écraserait une autre, ce que I14 interdit.
        val vus = mutableSetOf<String>()
        repeat(1000) {
            val id = genererIdCaptureUnique(T, vus, Random, zone)
            assertTrue("collision au tirage $it : $id", vus.add(id))
        }
        assertEquals(1000, vus.size)
    }

    @Test
    fun les_generations_a_des_instants_distincts_sont_naturellement_distinctes() {
        val ids = (0 until 100).map { genererIdCapture(T + it * 1_000L, Random(1), zone) }
        assertEquals(100, ids.toSet().size)
    }

    @Test
    fun un_identifiant_hors_format_est_signale() {
        assertTrue(idCaptureConforme("C-20260725-142233-9f2b"))
        assertTrue("suffixe de désambiguïsation toléré", idCaptureConforme("C-20260725-142233-9f2b-1"))
        assertTrue(!idCaptureConforme("B-041"))
        assertTrue(!idCaptureConforme("C-2026-142233-9f2b"))
        assertTrue(!idCaptureConforme("C-20260725-142233-9F2B"))   // hex majuscule
    }

    // ── StatutCapture ──

    @Test
    fun depuis_est_tolerant_et_refuse_l_inconnu() {
        assertEquals(StatutCapture.BRUTE, StatutCapture.depuis("brute"))
        assertEquals(StatutCapture.BRUTE, StatutCapture.depuis("  BRUTE "))
        assertEquals(StatutCapture.EXPORTEE, StatutCapture.depuis("Exportee"))
        assertEquals(StatutCapture.TRAITEE, StatutCapture.depuis("TRAITEE"))
        assertEquals(StatutCapture.IGNOREE, StatutCapture.depuis("ignoree"))
        assertNull(StatutCapture.depuis(null))
        assertNull(StatutCapture.depuis(""))
        assertNull("pas de retombée silencieuse sur BRUTE", StatutCapture.depuis("bidule"))
    }

    @Test
    fun un_statut_inconnu_en_base_ne_fait_pas_passer_la_capture_pour_brute() {
        val c = capture().copy(statut = "n_importe_quoi")
        assertNull(c.statutTypé())
        assertTrue(!c.estBrute())
    }

    // ── Transitions ──

    @Test
    fun brute_vers_exportee_puis_retour_en_brute() {
        val c = capture()
        val exportee = c.versExportee()
        assertEquals(StatutCapture.EXPORTEE, exportee.statutTypé())
        assertEquals("contenu brut jamais réécrit", c.contenuBrut, exportee.contenuBrut)
        assertEquals(StatutCapture.BRUTE, exportee.versBrute().statutTypé())
    }

    @Test
    fun brute_vers_ignoree_puis_retour_en_brute() {
        val ignoree = capture().versIgnoree()
        assertEquals(StatutCapture.IGNOREE, ignoree.statutTypé())
        assertEquals(StatutCapture.BRUTE, ignoree.versBrute().statutTypé())
    }

    @Test
    fun brute_vers_traitee_exige_la_boucle_produite() {
        val traitee = capture().versTraitee("B-041")
        assertEquals(StatutCapture.TRAITEE, traitee.statutTypé())
        assertEquals("B-041", traitee.boucleLiee)

        // Jamais TRAITEE sans lien : le lien est un paramètre, pas un oubli possible.
        assertThrows(IllegalArgumentException::class.java) { capture().versTraitee("") }
        assertThrows(IllegalArgumentException::class.java) { capture().versTraitee("   ") }
    }

    @Test
    fun une_capture_traitee_ne_revient_pas_en_brute() {
        val traitee = capture().versTraitee("B-041")
        // Défaire une capture traitée serait une décision sur le registre (la
        // boucle existe), pas sur la boîte de réception.
        assertThrows(IllegalArgumentException::class.java) { traitee.versBrute() }
    }

    // ── Aucune suppression possible ──

    @Test
    fun aucune_methode_de_suppression_n_est_exposee_pour_les_captures() {
        // Vérification structurelle : le DAO ne doit offrir AUCUN moyen d'effacer
        // une capture. Contrôlé par réflexion pour que l'ajout d'un `@Delete`
        // « juste pour nettoyer » fasse tomber ce test.
        val suspectes = BoucleDao::class.java.methods
            .map { it.name }
            .filter { nom ->
                val n = nom.lowercase()
                ("supprim" in n || "delete" in n || "purg" in n || "vider" in n) &&
                    "capture" in n
            }
        assertEquals("méthodes de suppression de capture trouvées : $suspectes", 0, suspectes.size)

        // Et rien dans le cycle de vie ne signifie « effacée » : les quatre états
        // sont des états de traitement.
        assertEquals(
            listOf("BRUTE", "EXPORTEE", "TRAITEE", "IGNOREE"),
            StatutCapture.entries.map { it.name }
        )
    }

    // ── Pré-remplissage (aucune analyse du contenu) ──

    @Test
    fun le_titre_propose_vient_du_sujet_ou_de_la_premiere_ligne() {
        assertEquals(
            "sujet fourni par l'app source",
            "Dossier Marie", capture(titre = "Dossier Marie").titrePropose()
        )
        assertEquals(
            "première ligne à défaut",
            "Rappeler Marie",
            capture(contenu = "\n  Rappeler Marie\nsuite de la note").titrePropose()
        )
    }

    @Test
    fun l_origine_proposee_nomme_l_app_source_sans_rien_deduire() {
        assertEquals("capture com.miui.notes", capture().originePropose())
        assertEquals("capture inconnue", capture(appSource = null).originePropose())
    }

    @Test
    fun l_extrait_ne_modifie_pas_le_contenu_stocke() {
        val longue = "x".repeat(300)
        val c = capture(contenu = longue)
        val e = c.extrait(120)
        assertEquals("l'extrait est borné", 121, e.length)   // 120 + « … »
        assertTrue(e.endsWith("…"))
        assertEquals("le contenu stocké est intact", longue, c.contenuBrut)
    }
}
