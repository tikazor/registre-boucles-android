package com.pontat.registreboucles.data

/**
 * Code identifiant l'appareil : 1 à 4 lettres MAJUSCULES (ex. « B », « PRO »).
 * Il préfixe tous les identifiants créés ici (cf. [genererProchainId] et
 * l'invariant I9), ce qui rend deux appareils incapables d'émettre le même id.
 *
 * Il est délibérément court : il apparaît dans chaque identifiant.
 */
private val REGEX_CODE = Regex("""[A-Z]{1,4}""")

/** Préfixe d'un identifiant conforme (`B-041` -> `B`), ou null si non conforme. */
private val REGEX_ID_PREFIXE = Regex("""([A-Z]{1,4})-\d+""")

/**
 * Normalise une saisie utilisateur en code appareil valide, ou renvoie null.
 * Accepte les minuscules et les espaces autour ; refuse tout le reste
 * (chiffres, tirets, accents, vide, plus de 4 lettres).
 */
fun normaliserCodeAppareil(saisie: String?): String? {
    val v = saisie?.trim()?.uppercase() ?: return null
    return if (REGEX_CODE.matches(v)) v else null
}

/**
 * Code à proposer au premier lancement : le préfixe le plus fréquent parmi les
 * identifiants déjà en base. Sur un appareil qui possède déjà des `B-###`, on
 * propose donc « B », ce qui préserve la continuité de la séquence existante.
 * Renvoie null si la base est vide ou ne contient aucun id conforme.
 */
fun codeAppareilSuggere(idsExistants: List<String>): String? =
    idsExistants
        .mapNotNull { REGEX_ID_PREFIXE.matchEntire(it.trim())?.groupValues?.get(1) }
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy({ it.value }, { it.key }))
        ?.key
