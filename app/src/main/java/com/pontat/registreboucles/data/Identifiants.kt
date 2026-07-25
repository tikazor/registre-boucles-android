package com.pontat.registreboucles.data

/**
 * Identifiants préfixés par appareil (ADR-02, tranché le 25/07/2026).
 *
 * Chaque appareil n'émet que des ids `<SON CODE>-###` et ne compte QUE les
 * siens pour calculer le suivant : deux appareils qui créent des boucles en
 * parallèle ne peuvent donc pas produire le même identifiant, sans aucune
 * coordination entre eux (invariant I9).
 *
 * Les identifiants existants ne sont JAMAIS réécrits : un appareil « B » qui
 * possède déjà B-001..B-040 continue simplement en B-041.
 */

/** Identifiant conforme : 1 à 4 majuscules, tiret, au moins 3 chiffres. */
private val REGEX_ID_CONFORME = Regex("""[A-Z]{1,4}-\d{3,}""")

/** Numéro d'un id appartenant à [code] (`B-041` avec code `B` -> 41), sinon null. */
private fun numeroPour(code: String, id: String): Int? =
    Regex("""${Regex.escape(code)}-(\d+)""")
        .matchEntire(id.trim())?.groupValues?.get(1)?.toIntOrNull()

/**
 * Génère l'identifiant suivant pour [codeAppareil] : `<CODE>-<numéro sur 3
 * chiffres>`. Fonction PURE (testable, sans Room ni Context).
 *
 * - ne compte que les ids portant CE préfixe : ceux des autres appareils et les
 *   ids historiques non conformes sont ignorés ;
 * - reprend après le plus grand numéro (les trous ne sont pas réutilisés) ;
 * - démarre à `001` si l'appareil n'a encore rien créé.
 */
fun genererProchainId(codeAppareil: String, idsExistants: List<String>): String {
    val max = idsExistants.mapNotNull { numeroPour(codeAppareil, it) }.maxOrNull() ?: 0
    return "%s-%03d".format(codeAppareil, max + 1)
}

/**
 * Vrai si l'identifiant respecte la convention `^[A-Z]{1,4}-\d{3,}$`.
 * Un id non conforme est TOLÉRÉ à l'import (jamais rejeté, jamais renuméroté) :
 * il est seulement signalé dans le rapport d'import.
 */
fun idConforme(id: String): Boolean = REGEX_ID_CONFORME.matches(id.trim())
