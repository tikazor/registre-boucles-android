package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anti-rafale : deux backups à moins de 5 min -> réutilisation ; forcer -> nouveau. */
class BackupRafaleTest {

    private val seuil = 5 * 60 * 1000L

    @Test
    fun backup_recent_est_reutilise_si_non_force() {
        val maintenant = 1_000_000L
        val dernier = nomBackup(maintenant - 60_000L)   // il y a 1 min
        assertTrue(doitReutiliserBackup(dernier, maintenant, forcer = false, seuilMs = seuil))
    }

    @Test
    fun forcer_ecrit_toujours_un_nouveau_fichier() {
        val maintenant = 1_000_000L
        val dernier = nomBackup(maintenant - 60_000L)   // récent, mais forcé
        assertFalse(doitReutiliserBackup(dernier, maintenant, forcer = true, seuilMs = seuil))
    }

    @Test
    fun backup_ancien_n_est_pas_reutilise() {
        val maintenant = 1_000_000L
        val dernier = nomBackup(maintenant - 6 * 60 * 1000L)   // il y a 6 min
        assertFalse(doitReutiliserBackup(dernier, maintenant, forcer = false, seuilMs = seuil))
    }

    @Test
    fun aucun_backup_existant_ecrit_un_nouveau() {
        assertFalse(doitReutiliserBackup(null, 1_000_000L, forcer = false, seuilMs = seuil))
    }

    @Test
    fun nom_illisible_ne_reutilise_pas() {
        assertFalse(doitReutiliserBackup("autre-fichier.json", 1_000_000L, forcer = false, seuilMs = seuil))
    }

    @Test
    fun age_depuis_nom_est_correct() {
        assertEquals(60_000L, ageBackupDepuisNom(nomBackup(940_000L), 1_000_000L))
    }
}
