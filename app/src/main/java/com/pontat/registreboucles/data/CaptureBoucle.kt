package com.pontat.registreboucles.data

import androidx.room.Entity
import androidx.room.Index

/**
 * Liaison entre une capture et une boucle : « cette note a produit cet
 * engagement ». C'est la trace mémorielle du lot AND-09 — celle qui permet de
 * remonter, des mois plus tard, d'une boucle active à la note griffonnée un
 * mardi soir.
 *
 * **Plusieurs-à-plusieurs, et pas par excès de zèle** : une IA qui analyse un lot
 * peut fondre trois notes en une seule proposition (« ces trois messages parlent
 * du même engagement »), et une note dense peut donner naissance à plusieurs
 * propositions distinctes. Une simple colonne ne pourrait représenter ni l'un ni
 * l'autre.
 *
 * **Aucune clé étrangère**, comme pour `Capture.boucleLiee` et pour les
 * tombstones : une boucle est supprimable, une capture ne disparaît jamais, et le
 * lien doit survivre à la disparition de la boucle. Un `ON DELETE CASCADE`
 * effacerait la trace au moment précis où elle devient intéressante (« qu'est-ce
 * que j'avais noté, déjà, pour cette boucle que j'ai supprimée ? »).
 *
 * **Jamais supprimée** : un lien reste même quand la proposition qu'il portait a
 * été rejetée. Le fait que cette note ait nourri une proposition refusée est une
 * information, pas un déchet.
 */
@Entity(
    tableName = "capture_boucle",
    primaryKeys = ["captureId", "boucleId"],
    indices = [Index("captureId"), Index("boucleId")]
)
data class CaptureBoucle(
    val captureId: String,
    val boucleId: String,
    /** Quand le lien a été établi (epoch millis) : import, acceptation ou création manuelle. */
    val lieeLe: Long,
    /** Comment il a été établi. Voir [OrigineLien]. */
    val origine: String
)

/** Comment un lien capture -> boucle a été établi. */
enum class OrigineLien {
    /** Boucle créée à la main depuis la boîte de réception. */
    MANUELLE,

    /** Déclaré par le champ `origines` d'une proposition importée. */
    PROPOSITION;

    fun valeurStockee(): String = name.lowercase()

    companion object {
        fun depuis(valeur: String?): OrigineLien? {
            if (valeur.isNullOrBlank()) return null
            val v = valeur.trim()
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
        }
    }
}

/** Construit un lien. Pure, donc testable. */
fun lienCapture(
    captureId: String,
    boucleId: String,
    origine: OrigineLien,
    dateMillis: Long
): CaptureBoucle = CaptureBoucle(
    captureId = captureId,
    boucleId = boucleId,
    lieeLe = dateMillis,
    origine = origine.valeurStockee()
)

/**
 * Liens déclarés par une proposition importée. Les identifiants sont pris tels
 * quels : un identifiant inconnu localement est TOLÉRÉ (le lot a pu être analysé
 * après une réinstallation) et signalé ailleurs, pas filtré ici — un lien vers une
 * capture absente reste une information utile.
 */
fun liensDepuisOrigines(
    origines: Map<String, List<String>>,
    dateMillis: Long
): List<CaptureBoucle> = origines.flatMap { (boucleId, captureIds) ->
    captureIds.distinct().map { lienCapture(it, boucleId, OrigineLien.PROPOSITION, dateMillis) }
}
