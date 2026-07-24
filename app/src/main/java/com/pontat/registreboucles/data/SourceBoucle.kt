package com.pontat.registreboucles.data

/**
 * Provenance d'une boucle. Stockée dans `Boucle.source` (minuscules), tolérante
 * à l'import comme [Milieu] et [Statut]. `null` en base = USER (historique).
 */
enum class SourceBoucle {
    USER,     // saisie dans l'app
    IA,       // proposée par une IA (supervision requise)
    IMPORT;   // entrée par import sans provenance déclarée

    fun valeurStockee(): String = name.lowercase()

    companion object {
        /** Accepte le nom d'enum ou la valeur stockée, insensible à la casse. */
        fun depuis(valeur: String?): SourceBoucle? {
            if (valeur.isNullOrBlank()) return null
            val v = valeur.trim()
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
        }
    }
}

/** Source typée d'une boucle. `null`/inconnu -> USER (données historiques). */
fun Boucle.sourceTypee(): SourceBoucle = SourceBoucle.depuis(source) ?: SourceBoucle.USER

/** Boucle proposée par une IA (marqueur discret dans la liste). */
fun Boucle.estIA(): Boolean = sourceTypee() == SourceBoucle.IA
