package com.pontat.registreboucles.data

private val REGEX_ID = Regex("""B-(\d+)""")

/**
 * Génère l'identifiant suivant au format B-### à partir des ids existants.
 * Fonction PURE (testable, sans Room ni Context) : prend la liste des ids.
 * Ignore les ids non conformes, comble la suite après le plus grand numéro
 * (les trous ne sont pas réutilisés), et démarre à B-001 sur liste vide.
 */
fun genererProchainId(idsExistants: List<String>): String {
    val max = idsExistants
        .mapNotNull { REGEX_ID.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
        .maxOrNull() ?: 0
    return "B-%03d".format(max + 1)
}
