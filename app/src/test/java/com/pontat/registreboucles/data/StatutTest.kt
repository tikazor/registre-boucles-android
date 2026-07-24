package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Statut : tolérance de depuis(), cohérence estActive/estTerminal, rejet d'inconnu. */
class StatutTest {

    @Test
    fun depuis_accepte_nom_enum_et_valeur_stockee_insensible_casse() {
        assertEquals(Statut.OUVERTE, Statut.depuis("ouverte"))
        assertEquals(Statut.OUVERTE, Statut.depuis("OUVERTE"))
        assertEquals(Statut.EN_COURS, Statut.depuis("en_cours"))
        assertEquals(Statut.FERMEE, Statut.depuis("Fermee"))
        assertEquals(Statut.DEFAUT_APPLIQUE, Statut.depuis("Defaut_Applique"))
        assertEquals(Statut.DEFAUT_APPLIQUE, Statut.depuis("  defaut_applique  "))
    }

    @Test
    fun depuis_null_vide_ou_inconnu_renvoie_null() {
        assertNull(Statut.depuis(null))
        assertNull(Statut.depuis(""))
        assertNull(Statut.depuis("   "))
        assertNull(Statut.depuis("archivee"))     // valeur inconnue = null, jamais OUVERTE
        assertNull(Statut.depuis("fermé"))         // accent non toléré (pas une valeur stockée)
    }

    @Test
    fun estActive_et_estTerminal_sont_exclusifs_et_exhaustifs() {
        for (s in Statut.entries) {
            assertTrue(
                "Exactement un de estActive/estTerminal doit être vrai pour $s",
                s.estActive() != s.estTerminal()
            )
        }
        assertTrue(Statut.OUVERTE.estActive())
        assertTrue(Statut.EN_COURS.estActive())
        assertFalse(Statut.FERMEE.estActive())
        assertFalse(Statut.DEFAUT_APPLIQUE.estActive())

        assertTrue(Statut.FERMEE.estTerminal())
        assertTrue(Statut.DEFAUT_APPLIQUE.estTerminal())
        assertFalse(Statut.OUVERTE.estTerminal())
        assertFalse(Statut.EN_COURS.estTerminal())
    }

    @Test
    fun valeur_stockee_est_reversible() {
        for (s in Statut.entries) {
            assertEquals(s.name.lowercase(), s.valeurStockee())
            assertEquals(s, Statut.depuis(s.valeurStockee()))
        }
    }
}
