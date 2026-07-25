package com.pontat.registreboucles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Nommage des fichiers d'état. Ce filtre décide ce que la synchronisation accepte
 * de lire dans un dossier qui peut contenir n'importe quoi d'autre — y compris
 * les copies de conflit que les applications de synchro déposent.
 */
class FichierEtatTest {

    @Test
    fun le_nom_du_fichier_derive_du_code_appareil() {
        assertEquals("etat-B.json", nomFichierEtat("B"))
        assertEquals("etat-PRO.json", nomFichierEtat("PRO"))
    }

    @Test
    fun un_nom_conforme_rend_son_code() {
        assertEquals("B", codeDepuisNomFichier("etat-B.json"))
        assertEquals("PRO", codeDepuisNomFichier("etat-PRO.json"))
        assertEquals("ABCD", codeDepuisNomFichier(" etat-ABCD.json "))
    }

    @Test
    fun un_nom_non_conforme_est_refuse() {
        assertNull(codeDepuisNomFichier("boucles-backup-20260725.json"))
        assertNull(codeDepuisNomFichier("etat-.json"))
        assertNull(codeDepuisNomFichier("etat-ABCDE.json"))     // 5 lettres
        assertNull(codeDepuisNomFichier("etat-B.txt"))
        assertNull(codeDepuisNomFichier("etat-b.json"))         // minuscule : pas notre écriture
        assertNull(codeDepuisNomFichier("registre.json"))
    }

    @Test
    fun les_copies_de_conflit_des_applis_de_synchro_sont_refusees() {
        // Un fichier dupliqué est un fichier dont on ne sait pas s'il est à jour :
        // le fusionner reviendrait à ressusciter un état arbitraire.
        assertNull(codeDepuisNomFichier("etat-PRO (1).json"))
        assertNull(codeDepuisNomFichier("etat-PRO.json.conflict"))
        assertNull(codeDepuisNomFichier("etat-PRO-conflit-2026-07-25.json"))
        assertNull(codeDepuisNomFichier("copie de etat-PRO.json"))
    }
}
