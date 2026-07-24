package com.pontat.registreboucles.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** parseDate : les 3 formats ISO-8601 acceptés + une date invalide au message lisible. */
class ImportDatesTest {

    @Test
    fun accepte_instant_datetime_local_et_date_seule() {
        // Instant (avec Z)
        assertEquals(
            Instant.parse("2026-04-12T10:00:00Z").toEpochMilli(),
            JsonImporter.parseDate("2026-04-12T10:00:00Z", "creee", "B-1")
        )
        // Datetime local (sans zone) -> interprété en UTC
        assertEquals(
            LocalDateTime.parse("2026-04-12T10:00:00").toInstant(ZoneOffset.UTC).toEpochMilli(),
            JsonImporter.parseDate("2026-04-12T10:00:00", "creee", "B-1")
        )
        // Date seule -> début de journée UTC
        assertEquals(
            LocalDate.parse("2026-04-12").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            JsonImporter.parseDate("2026-04-12", "echeance", "B-1")
        )
    }

    @Test
    fun date_invalide_leve_une_ImportException_lisible() {
        try {
            JsonImporter.parseDate("pas une date", "echeance", "B-9")
            fail("Une date invalide doit lever une ImportException")
        } catch (e: ImportException) {
            val msg = e.message ?: ""
            assertTrue("Le message cite la boucle", msg.contains("B-9"))
            assertTrue("Le message cite le champ", msg.contains("echeance"))
            assertTrue("Le message cite le format attendu", msg.contains("ISO-8601"))
        }
    }
}
