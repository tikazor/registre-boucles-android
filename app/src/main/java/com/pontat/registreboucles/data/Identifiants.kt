package com.pontat.registreboucles.data

// Convention de préfixe par producteur (cf. docs/schema.md) : l'app n'émet que
// des ids `B-###`. Les ids d'autres producteurs (ex. `IA-###`) ne sont donc PAS
// pris en compte dans le calcul du prochain numéro -> aucune collision possible.
private val REGEX_ID = Regex("""B-(\d+)""")

/**
 * Génère l'identifiant suivant au format B-### à partir des ids existants.
 * Fonction PURE (testable, sans Room ni Context) : prend la liste des ids.
 * Ignore les ids non conformes (dont les `IA-###`), comble la suite après le
 * plus grand numéro `B-###` (les trous ne sont pas réutilisés), démarre à B-001.
 */
fun genererProchainId(idsExistants: List<String>): String {
    val max = idsExistants
        .mapNotNull { REGEX_ID.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
        .maxOrNull() ?: 0
    return "B-%03d".format(max + 1)
}
