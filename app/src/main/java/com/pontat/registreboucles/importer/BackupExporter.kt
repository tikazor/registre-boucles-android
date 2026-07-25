package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.Mouvement
import com.pontat.registreboucles.data.Suppression

/**
 * Backup = format canonique de l'app. Délègue intégralement à [JsonExporter]
 * pour qu'export et backup NE PUISSENT PAS diverger (même sérialiseur, même
 * schéma `{ version, boucles, journaux }`).
 */
object BackupExporter {

    fun serialiser(
        boucles: List<Boucle>,
        mouvements: List<Mouvement>,
        journaux: List<Journal>,
        suppressions: List<Suppression> = emptyList(),
        codeAppareil: String? = null,
        exporteLe: Long? = null
    ): String = JsonExporter.serialiser(
        boucles, mouvements, journaux, suppressions, codeAppareil, exporteLe
    )
}
