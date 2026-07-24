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
    DEFAUT_APPLIQUE,
    PROPOSEE,          // proposée par une IA : ni active ni terminale, en attente de supervision
    REJETEE;           // proposition refusée : état terminal (exige un journal, comme la clôture)

    /** Boucle encore à traiter (visible liste + widget). PROPOSEE en est exclue. */
    fun estActive(): Boolean = this == OUVERTE || this == EN_COURS

    /** Boucle close (clôturée, action par défaut appliquée, ou proposition rejetée). */
    fun estTerminal(): Boolean = this == FERMEE || this == DEFAUT_APPLIQUE || this == REJETEE

    /** En attente de supervision : n'existe que dans l'écran Supervision. */
    fun estProposition(): Boolean = this == PROPOSEE

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

/** Prédicat unique de « terminal » (clôturée, défaut appliqué, ou rejetée). */
fun Boucle.estTerminal(): Boolean = statutTypé()?.estTerminal() == true

/** Proposition IA en attente de supervision (exclue de la liste et du widget). */
fun Boucle.estProposition(): Boolean = statutTypé()?.estProposition() == true
