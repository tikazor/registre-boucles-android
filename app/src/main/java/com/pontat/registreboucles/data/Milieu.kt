package com.pontat.registreboucles.data

/** Milieu d'une boucle (typé, choix fermé). Stocké dans Boucle.milieu = name(). */
enum class Milieu(val libelle: String) {
    PRO("Professionnel"),
    GOUVERNANCE("Gouvernance"),
    PROJET("Projet"),
    PERSO("Personnel");

    companion object {
        /** Tolère les valeurs stockées (code enum ou ancien libellé libre). */
        fun depuis(valeur: String?): Milieu? {
            if (valeur.isNullOrBlank()) return null
            entries.firstOrNull { it.name.equals(valeur, ignoreCase = true) }?.let { return it }
            entries.firstOrNull { it.libelle.equals(valeur, ignoreCase = true) }?.let { return it }
            return null
        }
    }
}
