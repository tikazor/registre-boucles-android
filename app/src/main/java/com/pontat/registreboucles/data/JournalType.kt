package com.pontat.registreboucles.data

/** Type d'entrée du journal de clôture. */
enum class JournalType(val libelle: String) {
    PREUVE("Preuve"),
    DECLARATION("Déclaration"),
    DEFAUT("Défaut");

    companion object {
        fun depuis(nom: String?): JournalType =
            entries.firstOrNull { it.name == nom } ?: DECLARATION
    }
}
