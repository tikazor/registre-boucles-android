package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entrée du journal de synchronisation dérivée d'un plan. Construite par une
 * fonction pure, insérée par le repository dans la même transaction que le plan :
 * une fusion ne peut pas s'appliquer sans laisser sa trace.
 */
class JournalSyncTest {

    private val T = 1_800_000_000_000L

    private fun boucle(id: String, modifieeLe: Long = T) = Boucle(
        id = id, type = "ACTION", titre = "t", origine = "o", creee = T - 10_000,
        echeance = null, tiers = null, preuveAttendue = "p", blocage = null, impact = "i",
        defaut = null, statut = "ouverte", milieu = null, source = "user",
        modifieeLe = modifieeLe, modifieePar = "PRO"
    )

    private fun distant(etat: EtatRegistre, exporteLe: Long? = T) =
        EtatDistant(codeAppareil = "PRO", exporteLe = exporteLe, etat = etat)

    @Test
    fun une_fusion_sans_conflit_est_consignee_en_succes() {
        val plan = calculerFusionSync(
            EtatRegistre(),
            distant(EtatRegistre(boucles = listOf(boucle("PRO-001"), boucle("PRO-002")))),
            maintenant = T
        )
        val e = evenementDepuisPlan(plan, distant(EtatRegistre()), "etat-PRO.json", T)
        assertEquals(ResultatSync.SUCCES, e.resultatTypé())
        assertEquals(2, e.bouclesAjoutees)
        assertEquals(0, e.conflits)
        assertEquals("PRO", e.appareilDistant)
        assertEquals("etat-PRO.json", e.fichierLu)
        assertEquals("2 ajoutée(s)", e.detail)
    }

    @Test
    fun une_fusion_avec_arbitrage_en_attente_est_consignee_en_conflits() {
        val locale = boucle("B-001", modifieeLe = T)
        val plan = calculerFusionSync(
            EtatRegistre(boucles = listOf(locale)),
            distant(EtatRegistre(boucles = listOf(locale.copy(titre = "autre", modifieeLe = T + 10_000)))),
            maintenant = T
        )
        val e = evenementDepuisPlan(plan, distant(EtatRegistre()), "etat-PRO.json", T)
        assertEquals(ResultatSync.CONFLITS, e.resultatTypé())
        assertEquals(1, e.conflits)
        assertTrue(e.detail.contains("1 à arbitrer"))
    }

    @Test
    fun une_interruption_d_horloge_est_consignee_en_echec_avec_son_ecart() {
        val plan = calculerFusionSync(
            EtatRegistre(),
            distant(EtatRegistre(boucles = listOf(boucle("PRO-001"))), exporteLe = T + 45 * 60_000L),
            maintenant = T
        )
        val e = evenementDepuisPlan(
            plan,
            distant(EtatRegistre(), exporteLe = T + 45 * 60_000L),
            "etat-PRO.json",
            T
        )
        assertEquals(ResultatSync.ECHEC, e.resultatTypé())
        assertEquals("Interrompue : horloge de PRO en avance de 45 min.", e.detail)
        assertEquals(0, e.bouclesAjoutees)
    }

    @Test
    fun une_seconde_passe_est_consignee_comme_deja_a_jour() {
        val plan = calculerFusionSync(EtatRegistre(), distant(EtatRegistre()), maintenant = T)
        val e = evenementDepuisPlan(plan, distant(EtatRegistre()), "etat-PRO.json", T)
        assertEquals(ResultatSync.SUCCES, e.resultatTypé())
        assertEquals("Déjà à jour.", e.detail)
    }
}
