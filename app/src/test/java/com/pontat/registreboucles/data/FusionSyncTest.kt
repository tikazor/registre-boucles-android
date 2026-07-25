package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moteur de fusion bidirectionnelle (AND-08). Tout est vérifié sur la fonction
 * pure : aucune base, aucun émulateur, aucune horloge système.
 *
 * Repère temporel : T = 1_800_000_000_000 (millis). Les écarts sont exprimés en
 * secondes autour de T pour rester lisibles.
 */
class FusionSyncTest {

    private val T = 1_800_000_000_000L
    private fun s(secondes: Long) = secondes * 1_000L

    private fun boucle(
        id: String = "B-001",
        titre: String = "Titre local",
        statut: String = "ouverte",
        creee: Long = T - s(10_000),
        modifieeLe: Long? = T,
        modifieePar: String? = "B",
        source: String? = "user",
        impact: String = "impact",
        echeance: Long? = null
    ) = Boucle(
        id = id, type = "ACTION", titre = titre, origine = "origine", creee = creee,
        echeance = echeance, tiers = null, preuveAttendue = "preuve", blocage = null,
        impact = impact, defaut = null, statut = statut, milieu = null, source = source,
        modifieeLe = modifieeLe, modifieePar = modifieePar
    )

    // Raccourcis : les entités portent un id auto-généré en 1ᵉʳ paramètre, que les
    // tests n'ont pas à connaître.
    private fun mvt(boucleId: String, date: Long, contenu: String, type: String = "declaration") =
        Mouvement(boucleId = boucleId, date = date, type = type, contenu = contenu)

    private fun jrn(boucleId: String, date: Long, texte: String, type: String = "PREUVE") =
        Journal(boucleId = boucleId, date = date, type = type, texte = texte)

    private fun distant(
        etat: EtatRegistre,
        code: String = "PRO",
        exporteLe: Long? = T
    ) = EtatDistant(codeAppareil = code, exporteLe = exporteLe, etat = etat)

    // ── Règle 1 : union des mouvements et des journaux ──

    @Test
    fun aucun_mouvement_ni_journal_perdu_et_les_doublons_sont_elimines() {
        val commun = mvt("B-001", T, "commun")
        val journalCommun = jrn("B-001", T, "commun")
        val local = EtatRegistre(
            boucles = listOf(boucle()),
            mouvements = listOf(commun, mvt("B-001", T - s(5), "local seul")),
            journaux = listOf(journalCommun)
        )
        val plan = calculerFusionSync(
            local,
            distant(
                EtatRegistre(
                    boucles = listOf(boucle()),
                    // Le mouvement commun est renvoyé par l'autre appareil : il ne doit
                    // pas entrer deux fois. Le sien, lui, doit entrer.
                    mouvements = listOf(commun, mvt("B-001", T - s(3), "distant seul")),
                    journaux = listOf(journalCommun, jrn("B-001", T - s(2), "distant seul"))
                )
            ),
            maintenant = T
        )
        assertEquals(listOf("distant seul"), plan.mouvementsAInserer.map { it.contenu })
        assertEquals(listOf("distant seul"), plan.journauxAInserer.map { it.texte })

        val apres = appliquerPlan(local, plan)
        assertEquals("aucun mouvement local perdu", 3, apres.mouvements.size)
        assertEquals("aucun journal local perdu", 2, apres.journaux.size)
    }

    // ── Règle 2 : boucle inconnue ──

    @Test
    fun une_boucle_inconnue_sans_tombstone_entre_telle_quelle() {
        val entrante = boucle(id = "PRO-007", statut = "en_cours", modifieePar = "PRO")
        val plan = calculerFusionSync(
            EtatRegistre(), distant(EtatRegistre(boucles = listOf(entrante))), maintenant = T
        )
        assertEquals(listOf(entrante), plan.bouclesAInserer)
        assertTrue(plan.conflits.isEmpty())
    }

    @Test
    fun une_boucle_ia_deja_supervisee_ailleurs_entre_active_mais_tracee() {
        // Décision du commanditaire : pas de coercition sur le canal de sync
        // (appareil pair de confiance). L'arrivée reste tracée.
        val entrante = boucle(id = "PRO-009", statut = "ouverte", source = "ia", modifieePar = "PRO")
        val plan = calculerFusionSync(
            EtatRegistre(), distant(EtatRegistre(boucles = listOf(entrante))), maintenant = T
        )
        assertEquals("statut du pair conservé", "ouverte", plan.bouclesAInserer.single().statut)
        val trace = plan.mouvementsAInserer.single()
        assertEquals(
            "Boucle IA reçue de PRO avec le statut \"ouverte\" " +
                "(appareil pair, supervision non rejouée)",
            trace.contenu
        )
    }

    // ── Règle 3 : tombstones locales ──

    @Test
    fun une_tombstone_plus_recente_empeche_la_resurrection() {
        val entrante = boucle(id = "B-013", modifieeLe = T - s(100))
        val plan = calculerFusionSync(
            EtatRegistre(suppressions = listOf(Suppression("B-013", T - s(10), "B"))),
            distant(EtatRegistre(boucles = listOf(entrante))),
            maintenant = T
        )
        assertTrue("rien n'est réinséré", plan.bouclesAInserer.isEmpty())
        assertEquals(listOf("B-013"), plan.bouclesIgnorees)
        assertTrue("la tombstone reste", plan.suppressionsARetirer.isEmpty())
    }

    @Test
    fun une_tombstone_plus_ancienne_laisse_ressusciter_et_disparait() {
        val entrante = boucle(id = "B-013", modifieeLe = T - s(10))
        val local = EtatRegistre(suppressions = listOf(Suppression("B-013", T - s(100), "B")))
        val plan = calculerFusionSync(
            local, distant(EtatRegistre(boucles = listOf(entrante))), maintenant = T
        )
        assertEquals(listOf(entrante), plan.bouclesAInserer)
        assertEquals(listOf("B-013"), plan.suppressionsARetirer)
        assertEquals(1, plan.resurrections.size)

        val apres = appliquerPlan(local, plan)
        assertTrue("tombstone retirée", apres.suppressions.isEmpty())
        assertEquals(1, apres.boucles.size)
    }

    // ── Règle 4a : le terminal l'emporte, dans les deux sens ──

    @Test
    fun une_cloture_distante_ecrase_une_boucle_locale_active_meme_plus_recente() {
        val locale = boucle(titre = "encore ouverte", statut = "ouverte", modifieeLe = T)
        // Le distant est PLUS ANCIEN, et gagne quand même : une clôture est un fait.
        val entrante = boucle(titre = "close", statut = "fermee", modifieeLe = T - s(3_600), modifieePar = "PRO")
        val plan = calculerFusionSync(
            EtatRegistre(boucles = listOf(locale)),
            distant(EtatRegistre(boucles = listOf(entrante))),
            maintenant = T
        )
        val fusionnee = plan.bouclesAMettreAJour.single()
        assertEquals("fermee", fusionnee.statut)
        assertEquals("close", fusionnee.titre)
        assertTrue(plan.conflits.isEmpty())
    }

    @Test
    fun une_cloture_locale_resiste_a_une_version_distante_active_plus_recente() {
        val locale = boucle(titre = "close ici", statut = "fermee", modifieeLe = T - s(3_600))
        val entrante = boucle(titre = "rouverte ailleurs", statut = "en_cours", modifieeLe = T, modifieePar = "PRO")
        val plan = calculerFusionSync(
            EtatRegistre(boucles = listOf(locale)),
            distant(EtatRegistre(boucles = listOf(entrante))),
            maintenant = T
        )
        assertTrue("aucune écriture", plan.bouclesAMettreAJour.isEmpty())
        assertEquals(listOf("B-001"), plan.bouclesIgnorees)
    }

    // ── Règle 4b : LWW au-delà de la tolérance, avec traçage ──

    @Test
    fun au_dela_de_60_s_le_plus_recent_gagne_et_chaque_champ_ecrase_est_journalise() {
        val locale = boucle(titre = "ancien titre", impact = "ancien impact", modifieeLe = T - s(120))
        val entrante = boucle(
            titre = "nouveau titre", impact = "nouvel impact", modifieeLe = T, modifieePar = "PRO"
        )
        val plan = calculerFusionSync(
            EtatRegistre(boucles = listOf(locale)),
            distant(EtatRegistre(boucles = listOf(entrante))),
            maintenant = T + s(5)
        )
        val fusionnee = plan.bouclesAMettreAJour.single()
        assertEquals("nouveau titre", fusionnee.titre)
        assertEquals("nouvel impact", fusionnee.impact)

        // Libellé figé : il sera lu tel quel dans l'historique.
        assertEquals(
            listOf(
                "titre : \"ancien titre\" remplacé par \"nouveau titre\" (sync depuis PRO)",
                "impact : \"ancien impact\" remplacé par \"nouvel impact\" (sync depuis PRO)"
            ),
            plan.mouvementsAInserer.map { it.contenu }
        )
        assertTrue(plan.mouvementsAInserer.all { it.type == "declaration" })
    }

    @Test
    fun au_dela_de_60_s_une_version_locale_plus_recente_ne_bouge_pas() {
        val locale = boucle(titre = "local récent", modifieeLe = T)
        val entrante = boucle(titre = "distant vieux", modifieeLe = T - s(120), modifieePar = "PRO")
        val plan = calculerFusionSync(
            EtatRegistre(boucles = listOf(locale)),
            distant(EtatRegistre(boucles = listOf(entrante))),
            maintenant = T
        )
        assertTrue(plan.bouclesAMettreAJour.isEmpty())
        assertTrue("aucun traçage inutile", plan.mouvementsAInserer.isEmpty())
        assertEquals(listOf("B-001"), plan.bouclesIgnorees)
    }

    @Test
    fun a_60_s_ou_moins_avec_divergence_c_est_un_conflit_et_rien_n_est_ecrit() {
        val locale = boucle(titre = "version A", modifieeLe = T)
        val entrante = boucle(titre = "version B", modifieeLe = T + s(30), modifieePar = "PRO")
        val plan = calculerFusionSync(
            EtatRegistre(boucles = listOf(locale)),
            distant(EtatRegistre(boucles = listOf(entrante))),
            maintenant = T + s(60)
        )
        assertTrue("aucune écriture", plan.estVide())
        val conflit = plan.conflits.single()
        assertEquals(MotifConflit.HORODATAGE_TROP_PROCHE, conflit.motif)
        assertEquals("titre", conflit.diffs.single().champ)
        assertEquals("version A", conflit.diffs.single().existant)
        assertEquals("version B", conflit.diffs.single().entrant)
        assertEquals("PRO", conflit.appareilDistant)
    }

    @Test
    fun a_60_s_ou_moins_sans_divergence_il_ne_se_passe_rien() {
        val locale = boucle(modifieeLe = T)
        val plan = calculerFusionSync(
            EtatRegistre(boucles = listOf(locale)),
            distant(EtatRegistre(boucles = listOf(locale.copy(modifieeLe = T + s(10))))),
            maintenant = T
        )
        assertTrue(plan.estVide())
        assertTrue("identiques : pas un conflit", plan.conflits.isEmpty())
    }

    // ── Règle 4c : champs jamais écrasés ──

    @Test
    fun id_creee_et_source_ne_sont_jamais_ecrases() {
        val locale = boucle(creee = T - s(50_000), source = "user", modifieeLe = T - s(600))
        val entrante = Boucle(
            id = "B-001", type = "DECISION", titre = "titre distant", origine = "autre",
            creee = T - s(1), echeance = T + s(99), tiers = "quelqu'un", preuveAttendue = "autre preuve",
            blocage = "un blocage", impact = "autre impact", defaut = "un défaut",
            statut = "en_cours", milieu = "PRO", source = "ia",
            modifieeLe = T, modifieePar = "PRO"
        )
        val plan = calculerFusionSync(
            EtatRegistre(boucles = listOf(locale)),
            distant(EtatRegistre(boucles = listOf(entrante))),
            maintenant = T
        )
        val fusionnee = plan.bouclesAMettreAJour.single()
        assertEquals("B-001", fusionnee.id)
        assertEquals("creee préservée", locale.creee, fusionnee.creee)
        assertEquals("source préservée", "user", fusionnee.source)
        // ... mais les champs descriptifs, eux, ont bien été adoptés.
        assertEquals("titre distant", fusionnee.titre)
        assertEquals("en_cours", fusionnee.statut)
        assertEquals("horodatage de l'émetteur conservé", T, fusionnee.modifieeLe)
        assertEquals("PRO", fusionnee.modifieePar)
    }

    // ── Règle 5 : tombstone entrante sur une boucle vivante ici ──

    @Test
    fun une_suppression_distante_ne_detruit_rien_ici_et_devient_un_conflit() {
        val locale = boucle(id = "B-013")
        val local = EtatRegistre(
            boucles = listOf(locale),
            mouvements = listOf(mvt("B-013", T, "un suivi")),
            journaux = listOf(jrn("B-013", T, "une preuve"))
        )
        val plan = calculerFusionSync(
            local,
            distant(EtatRegistre(suppressions = listOf(Suppression("B-013", T + s(600), "PRO")))),
            maintenant = T + s(700)
        )
        val conflit = plan.conflits.single()
        assertEquals(MotifConflit.SUPPRIMEE_A_DISTANCE, conflit.motif)
        assertEquals("B-013", conflit.boucleId)
        assertNull("la boucle n'existe plus chez l'émetteur", conflit.entrante)

        val apres = appliquerPlan(local, plan)
        assertEquals("boucle intacte", 1, apres.boucles.size)
        assertEquals("mouvement intact", 1, apres.mouvements.size)
        assertEquals("journal de clôture intact", 1, apres.journaux.size)
        assertTrue("aucune tombstone contradictoire écrite", apres.suppressions.isEmpty())
    }

    @Test
    fun une_suppression_distante_d_une_boucle_inconnue_est_seulement_enregistree() {
        val tombstone = Suppression("PRO-004", T, "PRO")
        val plan = calculerFusionSync(
            EtatRegistre(), distant(EtatRegistre(suppressions = listOf(tombstone))), maintenant = T
        )
        assertEquals(listOf(tombstone), plan.suppressionsAInserer)
        assertTrue(plan.conflits.isEmpty())
    }

    // ── Règle 0 : garde-fou d'horloge ──

    @Test
    fun une_horloge_distante_en_avance_interrompt_la_fusion() {
        val plan = calculerFusionSync(
            EtatRegistre(),
            distant(EtatRegistre(boucles = listOf(boucle(id = "PRO-001"))), exporteLe = T + s(1_800)),
            maintenant = T
        )
        assertTrue("fusion interrompue", plan.estInterrompu())
        assertTrue("rien n'est décidé", plan.estVide())
        assertEquals(s(1_800), plan.avanceHorloge)
        assertEquals("30 min", formaterAvance(plan.avanceHorloge!!))
    }

    @Test
    fun la_confirmation_explicite_leve_l_interruption() {
        val plan = calculerFusionSync(
            EtatRegistre(),
            distant(EtatRegistre(boucles = listOf(boucle(id = "PRO-001"))), exporteLe = T + s(1_800)),
            maintenant = T,
            confirmeHorloge = true
        )
        assertEquals(1, plan.bouclesAInserer.size)
        assertNotNull("l'avance reste consignée", plan.avanceHorloge)
    }

    @Test
    fun une_avance_sous_la_marge_ne_declenche_rien() {
        assertNull(avanceHorloge(T + s(300), T))       // 5 min : plausible
        assertNull(avanceHorloge(null, T))             // fichier v2 sans exporteLe
        assertEquals(s(1_200), avanceHorloge(T + s(1_200), T))
        assertNull("un retard n'est pas suspect", avanceHorloge(T - s(100_000), T))
    }

    // ── Idempotence : le test le plus important du lot ──

    @Test
    fun fusionner_deux_fois_le_meme_fichier_ne_change_rien_la_seconde_fois() {
        val local = EtatRegistre(
            boucles = listOf(
                boucle(id = "B-001", titre = "local", modifieeLe = T - s(600)),
                boucle(id = "B-002", titre = "close", statut = "fermee", modifieeLe = T - s(900))
            ),
            mouvements = listOf(mvt("B-001", T - s(700), "suivi local")),
            journaux = listOf(jrn("B-002", T - s(900), "preuve locale")),
            suppressions = listOf(Suppression("B-050", T - s(50), "B"))
        )
        val fichier = distant(
            EtatRegistre(
                boucles = listOf(
                    // 1. écrase B-001 (plus récent de 10 min)
                    boucle(id = "B-001", titre = "distant", modifieeLe = T, modifieePar = "PRO"),
                    // 2. nouvelle boucle
                    boucle(id = "PRO-001", titre = "neuve", modifieeLe = T, modifieePar = "PRO"),
                    // 3. tentative de résurrection perdue (tombstone plus récente)
                    boucle(id = "B-050", titre = "supprimée ici", modifieeLe = T - s(500), modifieePar = "PRO")
                ),
                mouvements = listOf(mvt("B-001", T - s(300), "suivi distant")),
                journaux = listOf(jrn("B-002", T - s(200), "journal distant", "DECLARATION")),
                suppressions = listOf(Suppression("PRO-800", T - s(10), "PRO"))
            )
        )

        val plan1 = calculerFusionSync(local, fichier, maintenant = T + s(1))
        val apres1 = appliquerPlan(local, plan1)
        assertTrue("la 1ʳᵉ fusion agit", !plan1.estVide())

        // 2ᵉ passe sur le MÊME fichier, à une heure différente : plan vide.
        val plan2 = calculerFusionSync(apres1, fichier, maintenant = T + s(5_000))
        assertTrue("2ᵉ fusion sans effet : $plan2", plan2.estVide())
        assertTrue("et sans conflit inventé", plan2.conflits.isEmpty())

        val apres2 = appliquerPlan(apres1, plan2)
        assertEquals(apres1.boucles.sortedBy { it.id }, apres2.boucles.sortedBy { it.id })
        assertEquals(apres1.mouvements, apres2.mouvements)
        assertEquals(apres1.journaux, apres2.journaux)
        assertEquals(apres1.suppressions, apres2.suppressions)

        // 3ᵉ passe, pour écarter toute oscillation à période 2.
        assertTrue(calculerFusionSync(apres2, fichier, maintenant = T + s(9_000)).estVide())
    }

    // ── Symétrie : les deux bases convergent ──

    @Test
    fun apres_un_aller_retour_les_deux_appareils_convergent() {
        // A : B-001 modifiée récemment, B-002 clôturée. B : B-001 ancienne, B-003 neuve.
        val etatA = EtatRegistre(
            boucles = listOf(
                boucle(id = "B-001", titre = "titre A", modifieeLe = T, modifieePar = "B"),
                boucle(id = "B-002", titre = "close A", statut = "fermee", modifieeLe = T - s(5_000))
            ),
            mouvements = listOf(mvt("B-001", T - s(4_000), "mvt A")),
            journaux = listOf(jrn("B-002", T - s(5_000), "preuve A"))
        )
        val etatB = EtatRegistre(
            boucles = listOf(
                boucle(id = "B-001", titre = "titre B", modifieeLe = T - s(3_600), modifieePar = "PRO"),
                boucle(id = "PRO-003", titre = "neuve B", modifieeLe = T - s(60), modifieePar = "PRO")
            ),
            mouvements = listOf(mvt("PRO-003", T - s(60), "mvt B")),
            journaux = emptyList()
        )

        // B fusionne le fichier de A, puis A fusionne le fichier (mis à jour) de B.
        val planB = calculerFusionSync(etatB, EtatDistant("B", T, etatA), maintenant = T + s(10))
        val etatB2 = appliquerPlan(etatB, planB)
        val planA = calculerFusionSync(etatA, EtatDistant("PRO", T + s(20), etatB2), maintenant = T + s(30))
        val etatA2 = appliquerPlan(etatA, planA)

        fun cle(e: EtatRegistre) = e.boucles
            .sortedBy { it.id }
            .map { Triple(it.id, it.titre, it.statut) }
        assertEquals("mêmes boucles, mêmes titres, mêmes statuts", cle(etatA2), cle(etatB2))

        fun mvts(e: EtatRegistre) = e.mouvements.map { it.contenu }.sorted()
        assertEquals("mêmes mouvements", mvts(etatA2), mvts(etatB2))
        fun jrns(e: EtatRegistre) = e.journaux.map { it.texte }.sorted()
        assertEquals("mêmes journaux", jrns(etatA2), jrns(etatB2))

        // Et la convergence est stable : plus rien à échanger.
        assertTrue(
            calculerFusionSync(etatA2, EtatDistant("PRO", T + s(40), etatB2), maintenant = T + s(50)).estVide()
        )
        assertTrue(
            calculerFusionSync(etatB2, EtatDistant("B", T + s(40), etatA2), maintenant = T + s(50)).estVide()
        )
    }
}
