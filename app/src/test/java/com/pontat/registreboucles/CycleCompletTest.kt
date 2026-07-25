package com.pontat.registreboucles

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Capture
import com.pontat.registreboucles.data.CaptureBoucle
import com.pontat.registreboucles.data.ClotureStore
import com.pontat.registreboucles.data.DecisionSupervision
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.JournalType
import com.pontat.registreboucles.data.Mouvement
import com.pontat.registreboucles.data.PreparationCapture
import com.pontat.registreboucles.data.Statut
import com.pontat.registreboucles.data.StatutCapture
import com.pontat.registreboucles.data.accepterProposition
import com.pontat.registreboucles.data.coercerPropositionsIA
import com.pontat.registreboucles.data.empreinteCapture
import com.pontat.registreboucles.data.executerCloture
import com.pontat.registreboucles.data.executerTransitionTerminale
import com.pontat.registreboucles.data.liensDepuisOrigines
import com.pontat.registreboucles.data.mouvementAcceptation
import com.pontat.registreboucles.data.preparerCapture
import com.pontat.registreboucles.data.statutTypé
import com.pontat.registreboucles.data.transitionsCapturesApresSupervision
import com.pontat.registreboucles.data.tronquerCapture
import com.pontat.registreboucles.data.versExportee
import com.pontat.registreboucles.importer.JsonImporter
import com.pontat.registreboucles.importer.LotAnalyseExporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulation du CYCLE COMPLET, de la note griffonnée à la boucle clôturée :
 *
 * ```
 * capture -> lot d'analyse -> [IA, dehors] -> propositions -> supervision -> boucle -> clôture
 * ```
 *
 * Ce test enchaîne les fonctions RÉELLES du dépôt — `preparerCapture`,
 * `LotAnalyseExporter`, `JsonImporter`, `coercerPropositionsIA`,
 * `transitionsCapturesApresSupervision`, `executerTransitionTerminale`,
 * `executerCloture` — sur un stockage en mémoire qui rejoue ce que fait le
 * repository. Chaque test unitaire du dépôt vérifie un maillon ; celui-ci vérifie
 * qu'ils s'emboîtent.
 *
 * **Ce qu'il ne prouve pas** : rien de ce qui dépend d'Android. Ni Room ni ses
 * migrations, ni SAF, ni l'ordre réel des écritures du `BoucleRepository`, ni
 * l'interface. Le stockage ci-dessous est une reconstitution fidèle des règles,
 * pas la base de données.
 */
class CycleCompletTest {

    // 25/07/2026 14:22:33 heure de Paris.
    private val T = 1_784_982_153_000L
    private fun min(n: Long) = n * 60_000L

    private val journal = StringBuilder()
    private fun trace(ligne: String) {
        journal.appendLine(ligne)
    }

    /** Stockage en mémoire : mêmes règles que le repository, sans Room. */
    private class Registre : ClotureStore {
        val captures = mutableListOf<Capture>()
        val boucles = mutableListOf<Boucle>()
        val mouvements = mutableListOf<Mouvement>()
        val journaux = mutableListOf<Journal>()
        val liens = mutableListOf<CaptureBoucle>()

        override suspend fun insererJournal(journal: Journal) { journaux += journal }
        override suspend fun obtenirBoucle(id: String): Boucle? = boucles.firstOrNull { it.id == id }
        override suspend fun mettreAJourBoucle(boucle: Boucle) {
            val i = boucles.indexOfFirst { it.id == boucle.id }
            if (i >= 0) boucles[i] = boucle else boucles += boucle
        }

        fun majCapture(c: Capture) {
            val i = captures.indexOfFirst { it.id == c.id }
            if (i >= 0) captures[i] = c else captures += c
        }

        fun capture(id: String) = captures.first { it.id == id }
        fun capturesDeBoucle(boucleId: String): List<Capture> =
            liens.filter { it.boucleId == boucleId }.mapNotNull { l -> captures.firstOrNull { it.id == l.captureId } }
        fun bouclesDeCapture(captureId: String): List<String> =
            liens.filter { it.captureId == captureId }.map { it.boucleId }
    }

    private fun Registre.capturer(contenu: String, titre: String?, app: String, quand: Long): PreparationCapture {
        val empreinte = empreinteCapture(tronquerCapture(contenu))
        val decision = preparerCapture(
            contenu = contenu, titre = titre, appSource = app, appareil = "B",
            maintenant = quand,
            existanteMemeEmpreinte = captures.firstOrNull { it.empreinte == empreinte },
            idsPris = captures.mapTo(HashSet()) { it.id }
        )
        if (decision is PreparationCapture.ACreer) captures += decision.capture
        return decision
    }

    @Test
    fun de_la_note_griffonnee_a_la_boucle_cloturee() = runTest {
        val r = Registre()

        // ─────────────────────────────────────────────────────────────────────
        // TEMPS 1 — Capture. Trois notes prises dans la journée, depuis deux apps.
        // ─────────────────────────────────────────────────────────────────────
        val n1 = r.capturer(
            "Marie attend un retour sur la convention avant le 5 août.",
            titre = "Convention", app = "com.miui.notes", quand = T
        )
        val n2 = r.capturer(
            "Revoir avec Marie le point 3 de la convention, il manque la clause de sortie.",
            titre = null, app = "com.google.android.keep", quand = T + min(90)
        )
        val n3 = r.capturer(
            "Commander les fournitures pour l'atelier de septembre.",
            titre = null, app = "com.miui.notes", quand = T + min(200)
        )
        // La même note repartagée depuis une autre app, reformatée.
        val doublon = r.capturer(
            "  Marie attend un retour sur la convention\n  avant le 5 août.  ",
            titre = null, app = "com.google.android.keep", quand = T + min(240)
        )

        assertTrue(n1 is PreparationCapture.ACreer)
        assertTrue(n2 is PreparationCapture.ACreer)
        assertTrue(n3 is PreparationCapture.ACreer)
        assertTrue("le doublon ne crée pas de 4ᵉ ligne", doublon is PreparationCapture.Doublon)
        assertEquals(3, r.captures.size)
        assertTrue("toutes brutes", r.captures.all { it.statutTypé() == StatutCapture.BRUTE })

        val idC1 = (n1 as PreparationCapture.ACreer).capture.id
        val idC2 = (n2 as PreparationCapture.ACreer).capture.id
        val idC3 = (n3 as PreparationCapture.ACreer).capture.id
        trace("1. CAPTURE   3 notes retenues, 1 doublon écarté")
        trace("             $idC1  « Convention » (com.miui.notes)")
        trace("             $idC2  clause de sortie (com.google.android.keep)")
        trace("             $idC3  fournitures atelier (com.miui.notes)")

        // ─────────────────────────────────────────────────────────────────────
        // TEMPS 2 — Lot d'analyse. Les captures brutes sortent, et passent EXPORTEE.
        // ─────────────────────────────────────────────────────────────────────
        val lot = LotAnalyseExporter.serialiser(r.captures.toList(), T + min(300))
        r.captures.toList().forEach { r.majCapture(it.versExportee()) }

        assertTrue(lot.contains("\"type\": \"lot-analyse\""))
        assertTrue("le texte brut part tel quel", lot.contains("clause de sortie"))
        assertTrue("aucun statut ne fuit dans le lot", !lot.contains("\"statut\""))
        assertTrue(r.captures.all { it.statutTypé() == StatutCapture.EXPORTEE })
        trace("2. LOT       ${LotAnalyseExporter.nomFichier(T + min(300))} — 3 captures, statut EXPORTEE")

        // ─────────────────────────────────────────────────────────────────────
        // TEMPS 3 — Analyse EXTERNE. L'IA lit le lot et rend des propositions.
        // Écrit à la main ici : l'application ne fait aucune analyse (I15).
        // L'IA fond C1 + C2 en une seule boucle, en propose une seconde depuis C3,
        // déclare par erreur `statut: "ouverte"` sur la première, et cite une
        // capture qui n'existe pas sur cet appareil.
        // ─────────────────────────────────────────────────────────────────────
        val propositions = """
            {
              "version": 3,
              "boucles": [
                {
                  "id": "IA-001", "type": "ACTION",
                  "titre": "Rendre un retour à Marie sur la convention",
                  "origine": "analyse du lot du 25/07/2026",
                  "creee": "2026-07-25", "echeance": "2026-08-05",
                  "tiers": "Marie",
                  "preuveAttendue": "Retour écrit envoyé, avec la clause de sortie tranchée",
                  "impact": "Convention bloquée tant que le point 3 n'est pas arbitré",
                  "statut": "ouverte", "source": "ia",
                  "origines": ["$idC1", "$idC2", "C-20200101-000000-0000"]
                },
                {
                  "id": "IA-002", "type": "ACTION",
                  "titre": "Commander les fournitures de l'atelier de septembre",
                  "origine": "analyse du lot du 25/07/2026",
                  "creee": "2026-07-25",
                  "preuveAttendue": "Bon de commande envoyé",
                  "impact": "Atelier sans matériel",
                  "statut": "proposee", "source": "ia",
                  "origines": ["$idC3"]
                }
              ]
            }
        """.trimIndent()

        // ─────────────────────────────────────────────────────────────────────
        // TEMPS 4a — Import. Les liens entrent ; AUCUNE capture ne bouge (I16).
        // ─────────────────────────────────────────────────────────────────────
        val res = JsonImporter.parse(propositions)
        assertEquals(2, res.boucles.size)
        assertEquals(listOf(idC1, idC2, "C-20200101-000000-0000"), res.origines["IA-001"])

        // Origine déclarée mais absente ici : tolérée, signalée, jamais bloquante.
        val connues = r.captures.mapTo(HashSet()) { it.id }
        val inconnues = res.origines.values.flatten().distinct().filterNot { it in connues }
        assertEquals(listOf("C-20200101-000000-0000"), inconnues)

        // Coercition d'AND-04 : la proposition déclarée « ouverte » est ramenée.
        val coerce = coercerPropositionsIA(res.boucles, emptySet(), T + min(400))
        r.boucles += coerce.boucles
        r.mouvements += coerce.mouvementsTrace
        r.liens += liensDepuisOrigines(res.origines, T + min(400))

        assertTrue("aucune n'entre active", r.boucles.all { it.statutTypé() == Statut.PROPOSEE })
        assertEquals(1, coerce.mouvementsTrace.size)
        assertTrue(coerce.mouvementsTrace[0].contenu.contains("ramené à \"proposee\""))
        assertTrue(
            "l'import ne touche à aucune capture",
            r.captures.all { it.statutTypé() == StatutCapture.EXPORTEE }
        )
        trace("3. ANALYSE   (hors app) 2 propositions, dont 1 fondant $idC1 + $idC2")
        trace("4a. IMPORT   statut « ouverte » ramené à « proposee » · 4 liens · captures INCHANGÉES")
        trace("             1 origine déclarée absente de l'appareil, signalée : ${inconnues.single()}")

        // ─────────────────────────────────────────────────────────────────────
        // TEMPS 4b — Supervision. C'est ICI que les captures bougent.
        // Acceptation de IA-001.
        // ─────────────────────────────────────────────────────────────────────
        val accepte = accepterProposition(r.boucles.first { it.id == "IA-001" })
        r.mettreAJourBoucle(accepte)
        val lieesA = r.capturesDeBoucle("IA-001")
        transitionsCapturesApresSupervision(lieesA, DecisionSupervision.ACCEPTATION, "IA-001")
            .forEach { r.majCapture(it) }
        r.mouvements += mouvementAcceptation(
            "IA-001", T + min(500), apresAmendement = false, origines = lieesA.map { it.id }
        )

        assertEquals(Statut.OUVERTE, r.boucles.first { it.id == "IA-001" }.statutTypé())
        assertEquals(StatutCapture.TRAITEE, r.capture(idC1).statutTypé())
        assertEquals(StatutCapture.TRAITEE, r.capture(idC2).statutTypé())
        assertEquals("IA-001", r.capture(idC1).boucleLiee)
        assertTrue(
            "la trace nomme les captures d'origine",
            r.mouvements.last().contenu.startsWith("Proposition IA acceptée (origine : ")
        )
        trace("4b. ACCEPTE  IA-001 -> ouverte · $idC1 et $idC2 -> TRAITEE")
        trace("             trace : « ${r.mouvements.last().contenu} »")

        // Rejet de IA-002 : la note redevient de la matière première.
        executerTransitionTerminale(
            r, "IA-002", Statut.REJETEE, JournalType.DECLARATION,
            "Déjà commandé la semaine dernière", T + min(560)
        )
        transitionsCapturesApresSupervision(
            r.capturesDeBoucle("IA-002"), DecisionSupervision.REJET, "IA-002"
        ).forEach { r.majCapture(it) }

        assertEquals(Statut.REJETEE, r.boucles.first { it.id == "IA-002" }.statutTypé())
        assertEquals("le rejet exige un motif journalisé", 1, r.journaux.size)
        assertEquals(StatutCapture.BRUTE, r.capture(idC3).statutTypé())
        trace("4c. REJETE   IA-002 -> rejetee (motif au journal) · $idC3 -> BRUTE, réanalysable")

        // ─────────────────────────────────────────────────────────────────────
        // Cas limite : une note liée à deux propositions de sorts différents.
        // C2 nourrit aussi une proposition IA-003, rejetée après coup.
        // ─────────────────────────────────────────────────────────────────────
        r.boucles += Boucle(
            id = "IA-003", type = "ACTION", titre = "Doublon d'analyse", origine = "analyse",
            creee = T, echeance = null, tiers = null, preuveAttendue = "p", blocage = null,
            impact = "i", defaut = null, statut = Statut.PROPOSEE.valeurStockee(),
            milieu = null, source = "ia"
        )
        r.liens += liensDepuisOrigines(mapOf("IA-003" to listOf(idC2)), T + min(600))
        executerTransitionTerminale(
            r, "IA-003", Statut.REJETEE, JournalType.DECLARATION, "Redondant avec IA-001", T + min(610)
        )
        transitionsCapturesApresSupervision(
            r.capturesDeBoucle("IA-003"), DecisionSupervision.REJET, "IA-003"
        ).forEach { r.majCapture(it) }

        assertEquals(
            "TRAITEE est absorbant : un rejet ailleurs ne défait pas un aboutissement",
            StatutCapture.TRAITEE, r.capture(idC2).statutTypé()
        )
        assertEquals("IA-001", r.capture(idC2).boucleLiee)
        trace("4d. CAS LIMITE  $idC2 nourrit aussi IA-003, rejetée -> reste TRAITEE (absorbant)")

        // ─────────────────────────────────────────────────────────────────────
        // TEMPS 5 — Clôture contre preuve. L'engagement se referme.
        // ─────────────────────────────────────────────────────────────────────
        executerCloture(
            r, "IA-001", JournalType.PREUVE,
            "Retour envoyé à Marie le 03/08, clause de sortie ajoutée au point 3", T + min(9_000)
        )
        assertEquals(Statut.FERMEE, r.boucles.first { it.id == "IA-001" }.statutTypé())
        assertEquals("clôture et rejets journalisés", 3, r.journaux.size)
        trace("5. CLOTURE   IA-001 -> fermee, contre preuve journalisée")

        // ─────────────────────────────────────────────────────────────────────
        // TRAÇABILITÉ — remonter dans les deux sens, des mois plus tard.
        // ─────────────────────────────────────────────────────────────────────
        val origineDeLaBoucle = r.capturesDeBoucle("IA-001")
        assertEquals(setOf(idC1, idC2), origineDeLaBoucle.map { it.id }.toSet())
        assertTrue(
            "le texte brut est intact, mot pour mot",
            origineDeLaBoucle.any { it.contenuBrut.contains("il manque la clause de sortie") }
        )
        assertEquals(listOf("IA-001", "IA-003"), r.bouclesDeCapture(idC2))

        // Le lien vers la capture absente a bien été conservé, sans capture en face.
        assertTrue("C-20200101-000000-0000" in r.liens.map { it.captureId })
        assertNull(r.captures.firstOrNull { it.id == "C-20200101-000000-0000" })

        trace("6. TRAÇABILITÉ  IA-001 <- $idC1 + $idC2 (+1 origine absente, lien conservé)")
        trace("                $idC2 -> IA-001, IA-003")
        trace("")
        trace("ÉTAT FINAL")
        r.boucles.sortedBy { it.id }.forEach { trace("   ${it.id}  ${it.statut}") }
        r.captures.sortedBy { it.id }.forEach {
            trace("   ${it.id}  ${it.statut}${it.boucleLiee?.let { b -> " -> $b" } ?: ""}")
        }
        trace("   ${r.mouvements.size} mouvement(s), ${r.journaux.size} journal(aux), ${r.liens.size} lien(s)")
        println(journal)
    }
}
