package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Prédicats d'échéance : ce sont EUX qui alimentent à la fois les compteurs du
 * tableau de bord et les filtres de la liste. S'ils sont justes, un indicateur
 * et son filtre ne peuvent pas se contredire.
 */
class EcheanceTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val aujourdHui: LocalDate = LocalDate.of(2026, 7, 25)

    /** Échéance à J+[jours] par rapport à [aujourdHui], en millis UTC. */
    private fun echeanceDans(jours: Long): Long =
        aujourdHui.plusDays(jours).atStartOfDay(utc).toInstant().toEpochMilli()

    private fun boucle(
        statut: String = "ouverte",
        echeance: Long? = null
    ) = Boucle(
        id = "B-001", type = "ACTION", titre = "t", origine = "o", creee = 0L,
        echeance = echeance, tiers = null, preuveAttendue = "p", blocage = null,
        impact = "i", defaut = null, statut = statut, milieu = null, source = "user"
    )

    @Test
    fun jours_restants_est_compte_en_jours_calendaires() {
        assertEquals(0L, joursRestantsDepuis(echeanceDans(0), aujourdHui, utc))
        assertEquals(3L, joursRestantsDepuis(echeanceDans(3), aujourdHui, utc))
        assertEquals(-2L, joursRestantsDepuis(echeanceDans(-2), aujourdHui, utc))
    }

    @Test
    fun en_retard_uniquement_si_active_et_echeance_depassee() {
        assertTrue(boucle(echeance = echeanceDans(-1)).estEnRetard(aujourdHui, utc))
        // Aujourd'hui n'est pas du retard.
        assertFalse(boucle(echeance = echeanceDans(0)).estEnRetard(aujourdHui, utc))
        assertFalse(boucle(echeance = echeanceDans(5)).estEnRetard(aujourdHui, utc))
        // Sans échéance : jamais en retard.
        assertFalse(boucle(echeance = null).estEnRetard(aujourdHui, utc))
        // Terminale ou en supervision : hors du tableau de bord des actives.
        assertFalse(boucle("fermee", echeanceDans(-10)).estEnRetard(aujourdHui, utc))
        assertFalse(boucle("rejetee", echeanceDans(-10)).estEnRetard(aujourdHui, utc))
        assertFalse(boucle("proposee", echeanceDans(-10)).estEnRetard(aujourdHui, utc))
        // Statut inconnu (legacy) : n'est pas active, donc pas comptée en retard.
        assertFalse(boucle("archivee", echeanceDans(-10)).estEnRetard(aujourdHui, utc))
    }

    @Test
    fun echeance_proche_couvre_aujourdhui_a_J7_inclus() {
        assertTrue(boucle(echeance = echeanceDans(0)).estEcheanceProche(aujourdHui, zone = utc))
        assertTrue(boucle(echeance = echeanceDans(7)).estEcheanceProche(aujourdHui, zone = utc))
        assertFalse(boucle(echeance = echeanceDans(8)).estEcheanceProche(aujourdHui, zone = utc))
        // Le retard n'est pas « proche » : les deux catégories sont disjointes.
        assertFalse(boucle(echeance = echeanceDans(-1)).estEcheanceProche(aujourdHui, zone = utc))
        assertFalse(boucle(echeance = null).estEcheanceProche(aujourdHui, zone = utc))
        assertFalse(boucle("fermee", echeanceDans(2)).estEcheanceProche(aujourdHui, zone = utc))
    }

    @Test
    fun en_retard_et_proche_sont_mutuellement_exclusifs() {
        for (j in -3L..10L) {
            val b = boucle(echeance = echeanceDans(j))
            assertFalse(
                "J$j ne peut pas être à la fois en retard et proche",
                b.estEnRetard(aujourdHui, utc) && b.estEcheanceProche(aujourdHui, zone = utc)
            )
        }
    }

    @Test
    fun en_retard_et_proche_sont_des_sous_ensembles_des_actives() {
        // Toute boucle en retard ou proche est nécessairement active : les
        // compteurs du tableau de bord ne peuvent donc pas dépasser « Ouvertes ».
        for (j in -5L..12L) {
            val b = boucle(echeance = echeanceDans(j))
            if (b.estEnRetard(aujourdHui, utc) || b.estEcheanceProche(aujourdHui, zone = utc)) {
                assertTrue("J$j devrait être active", b.estActive())
            }
        }
    }
}
