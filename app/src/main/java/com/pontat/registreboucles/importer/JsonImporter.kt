package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Mouvement
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** Erreur d'import lisible affichée à l'écran (jamais de crash silencieux). */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Résultat d'un import : boucles + mouvements prêts pour Room. */
data class ImportResult(
    val boucles: List<Boucle>,
    val mouvements: List<Mouvement>
)

object JsonImporter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse le contenu JSON et convertit les dates ISO-8601 en epoch millis.
     * Lève une [ImportException] au message clair si le schéma ne correspond pas.
     */
    fun parse(contenu: String): ImportResult {
        if (contenu.isBlank()) {
            throw ImportException("Le fichier est vide.")
        }

        val racine = try {
            json.decodeFromString<ExportRacine>(contenu)
        } catch (e: Exception) {
            throw ImportException(
                "Format JSON invalide ou champ obligatoire manquant.\n" +
                    "Attendu : un objet { \"boucles\": [ ... ] } avec les champs " +
                    "id, type, titre, origine, creee, preuveAttendue, impact, statut.\n\n" +
                    "Détail : ${e.message}",
                e
            )
        }

        if (racine.boucles.isEmpty()) {
            throw ImportException("Aucune boucle trouvée dans le fichier (liste \"boucles\" vide).")
        }

        val boucles = ArrayList<Boucle>(racine.boucles.size)
        val mouvements = ArrayList<Mouvement>()

        for (b in racine.boucles) {
            val creee = parseDate(b.creee, "creee", b.id)
            val echeance = b.echeance?.let { parseDate(it, "echeance", b.id) }

            boucles += Boucle(
                id = b.id,
                type = b.type,
                titre = b.titre,
                origine = b.origine,
                creee = creee,
                echeance = echeance,
                tiers = b.tiers,
                preuveAttendue = b.preuveAttendue,
                blocage = b.blocage,
                impact = b.impact,
                defaut = b.defaut,
                statut = b.statut
            )

            for (m in b.mouvements) {
                mouvements += Mouvement(
                    boucleId = b.id,
                    date = parseDate(m.date, "date (mouvement)", b.id),
                    type = m.type,
                    contenu = m.contenu
                )
            }
        }

        return ImportResult(boucles, mouvements)
    }

    /**
     * Convertit une date ISO-8601 en epoch millis.
     * Accepte les instants (`2026-04-12T10:00:00Z`), les datetimes locaux
     * (`2026-04-12T10:00:00`) et les dates seules (`2026-04-12`).
     */
    private fun parseDate(valeur: String, champ: String, boucleId: String): Long {
        val v = valeur.trim()
        return try {
            Instant.parse(v).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(v).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate.parse(v).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (e: DateTimeParseException) {
                    throw ImportException(
                        "Date invalide pour la boucle \"$boucleId\", champ \"$champ\" : « $valeur ». " +
                            "Format attendu : ISO-8601 (ex. 2026-04-12T10:00:00Z).",
                        e
                    )
                }
            }
        }
    }
}
