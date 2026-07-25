package com.pontat.registreboucles.importer

import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.Mouvement
import com.pontat.registreboucles.data.SourceBoucle
import com.pontat.registreboucles.data.Suppression
import com.pontat.registreboucles.data.idConforme
import com.pontat.registreboucles.data.Statut
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** Erreur d'import lisible affichée à l'écran (jamais de crash silencieux). */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Résultat d'un import, prêt pour Room. [idsNonConformes] recense les
 * identifiants qui ne respectent pas la convention `<CODE>-###` : ils sont
 * TOLÉRÉS (ni rejetés, ni renumérotés), simplement signalés à l'utilisateur.
 */
data class ImportResult(
    val boucles: List<Boucle>,
    val mouvements: List<Mouvement>,
    val journaux: List<Journal>,
    val suppressions: List<Suppression> = emptyList(),
    val codeAppareilSource: String? = null,
    /**
     * Captures déclarées à l'origine de chaque boucle : boucleId -> ids de
     * captures (AND-09). Vide pour tout fichier qui ne déclare pas `origines`.
     */
    val origines: Map<String, List<String>> = emptyMap(),
    /** `exporteLe` déclaré par l'émetteur (epoch millis) ; null hors v3. Sert au
     *  garde-fou d'horloge de la synchronisation. */
    val exporteLe: Long? = null,
    val idsNonConformes: List<String> = emptyList()
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
            json.decodeFromString<RegistreRacine>(contenu)
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

            // Statut inconnu = rejet explicite (jamais de retombée silencieuse sur "ouverte").
            if (Statut.depuis(b.statut) == null) {
                throw ImportException(
                    "Statut inconnu pour la boucle \"${b.id}\" : « ${b.statut} ».\n" +
                        "Valeurs acceptées : ouverte, en_cours, fermee, defaut_applique, " +
                        "proposee, rejetee."
                )
            }

            boucles += Boucle(
                id = b.id,
                type = b.type,
                titre = b.titre,
                origine = b.origine,
                creee = creee,
                echeance = echeance,
                tiers = interpreterTiers(b.tiers),
                preuveAttendue = b.preuveAttendue,
                blocage = b.blocage,
                impact = b.impact,
                defaut = b.defaut,
                statut = b.statut,
                milieu = b.milieu,
                // L'import respecte la provenance déclarée ; IMPORT par défaut si absente.
                source = b.source ?: SourceBoucle.IMPORT.valeurStockee(),
                // Absents des fichiers v1/v2 : la boucle se lit alors comme
                // « jamais modifiée depuis sa création » (derniereModification()).
                modifieeLe = b.modifieeLe?.let { parseDate(it, "modifieeLe", b.id) },
                modifieePar = b.modifieePar
            )

            for (m in b.mouvements) {
                mouvements += Mouvement(
                    boucleId = b.id,
                    date = parseDate(m.date, "date (mouvement)", b.id),
                    // Le JSON réel ne porte qu'une note libre : on la range en
                    // mouvement de type "declaration" (l'entité garde type/contenu).
                    type = "declaration",
                    contenu = m.note
                )
            }
        }

        // Identifiants hors convention : tolérés, mais signalés (cf. ImportResult).
        val idsNonConformes = racine.boucles.map { it.id }.filterNot { idConforme(it) }

        // Journaux (présents dans les fichiers de backup ; absents des exports simples).
        val journaux = racine.journaux.map { j ->
            Journal(
                boucleId = j.boucleId,
                date = parseDate(j.date, "date (journal)", j.boucleId),
                type = j.type,
                texte = j.texte
            )
        }

        // Tombstones : absentes des fichiers v1/v2 (liste vide).
        val suppressions = racine.suppressions.map { sp ->
            Suppression(
                boucleId = sp.boucleId,
                supprimeeLe = parseDate(sp.supprimeeLe, "supprimeeLe", sp.boucleId),
                supprimeePar = sp.supprimeePar
            )
        }

        return ImportResult(
            boucles = boucles,
            mouvements = mouvements,
            journaux = journaux,
            suppressions = suppressions,
            codeAppareilSource = racine.codeAppareil,
            origines = racine.boucles
                .mapNotNull { b ->
                    val ids = b.origines.map { it.trim() }.filter { it.isNotBlank() }
                    if (ids.isEmpty()) null else b.id to ids
                }
                .toMap(),
            exporteLe = racine.exporteLe,
            idsNonConformes = idsNonConformes
        )
    }

    /**
     * Interprète le champ `tiers`, tolérant aux deux formes historiques :
     * - booléen `true`  -> "Oui" ; `false` -> null (ancien format) ;
     * - chaîne          -> valeur telle quelle (préserve toute valeur libre) ;
     * - absent / null   -> null.
     * On distingue explicitement une VRAIE chaîne "true" d'un booléen JSON via
     * [JsonPrimitive.isString], pour ne pas transformer une valeur libre.
     */
    internal fun interpreterTiers(element: JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        val prim = element as? JsonPrimitive ?: return null
        if (!prim.isString) {
            // Booléen legacy (ou autre littéral non-chaîne).
            prim.content.toBooleanStrictOrNull()?.let { return if (it) "Oui" else null }
            return prim.content.ifBlank { null }
        }
        return prim.content.ifBlank { null }
    }

    /**
     * Convertit une date ISO-8601 en epoch millis.
     * Accepte les instants (`2026-04-12T10:00:00Z`), les datetimes locaux
     * (`2026-04-12T10:00:00`) et les dates seules (`2026-04-12`).
     */
    internal fun parseDate(valeur: String, champ: String, boucleId: String): Long {
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
