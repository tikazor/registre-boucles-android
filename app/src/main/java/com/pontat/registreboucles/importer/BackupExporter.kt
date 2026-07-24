package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.Mouvement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Racine du backup complet : boucles (schéma d'import) + journaux de clôture. */
@Serializable
data class BackupRacine(
    val boucles: List<BoucleJson>,
    val journaux: List<JournalJson>
)

@Serializable
data class JournalJson(
    val boucleId: String,
    val date: String,   // ISO-8601
    val type: String,   // PREUVE / DECLARATION / DEFAUT
    val texte: String
)

/**
 * Backup local complet (Boucle + Journal). Réutilise le mapping des boucles de
 * [JsonExporter] (les champs déjà exportés sont inchangés) et ajoute le journal.
 */
object BackupExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun serialiser(
        boucles: List<Boucle>,
        mouvements: List<Mouvement>,
        journaux: List<Journal>
    ): String {
        val racine = BackupRacine(
            boucles = JsonExporter.mapBoucles(boucles, mouvements),
            journaux = journaux.map {
                JournalJson(
                    boucleId = it.boucleId,
                    date = JsonExporter.iso(it.date),
                    type = it.type,
                    texte = it.texte
                )
            }
        )
        return json.encodeToString(BackupRacine.serializer(), racine)
    }
}
