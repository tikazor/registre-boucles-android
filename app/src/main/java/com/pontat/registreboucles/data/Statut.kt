package com.pontat.registreboucles.data

/**
 * Statut d'une boucle — SOURCE DE VÉRITÉ UNIQUE de « active » vs « terminal ».
 *
 * La colonne `Boucle.statut` reste un TEXT (aucune migration). Les valeurs sont
 * écrites en minuscules via [Statut.name].lowercase() pour rester compatibles
 * avec l'existant ("ouverte", "en_cours", "fermee", "defaut_applique").
 */
enum class Statut {
    OUVERTE,
    EN_COURS,
    FERMEE,
    DEFAUT_APPLIQUE;

    /** Boucle encore à traiter (visible liste + widget). */
    fun estActive(): Boolean = this == OUVERTE || this == EN_COURS

    /** Boucle close, quelle que soit la façon (clôturée ou action par défaut appliquée). */
    fun estTerminal(): Boolean = this == FERMEE || this == DEFAUT_APPLIQUE

    /** Valeur stockée en base (compat. avec l'existant, toujours en minuscules). */
    fun valeurStockee(): String = name.lowercase()

    companion object {
        /**
         * Tolérance identique à [Milieu.depuis] : accepte le nom d'enum ET
         * l'ancienne valeur libre, insensible à la casse. `null`/vide -> null.
         * Une valeur non reconnue renvoie `null` (jamais de retombée silencieuse
         * sur OUVERTE : l'appelant décide quoi faire d'un inconnu).
         */
        fun depuis(valeur: String?): Statut? {
            if (valeur.isNullOrBlank()) return null
            val v = valeur.trim()
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
        }
    }
}

/** Statut typé d'une boucle (null si valeur inconnue en base). */
fun Boucle.statutTypé(): Statut? = Statut.depuis(statut)

/** Prédicat unique d'« active » pour toute l'app (liste, widget, filtres). */
fun Boucle.estActive(): Boolean = statutTypé()?.estActive() == true

/** Prédicat unique de « terminal » (clôturée ou action par défaut appliquée). */
fun Boucle.estTerminal(): Boolean = statutTypé()?.estTerminal() == true
