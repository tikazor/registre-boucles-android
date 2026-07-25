package com.pontat.registreboucles.data

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Déduplication des captures. Deux fonctions PURES, sans Android : la même note
 * partagée deux fois — depuis deux applications, avec des retours à la ligne
 * différents, ou recopiée — doit produire la même empreinte et n'entrer qu'une
 * fois.
 *
 * Ce qui est neutralisé, et pourquoi :
 * - les espaces de bord (`trim`) : un partage ajoute souvent un saut de ligne ;
 * - la forme Unicode (**NFC**) : « é » composé et « e + accent combinant » sont
 *   le même texte à l'œil et doivent l'être ici ; les claviers et les
 *   presse-papiers ne produisent pas tous la même forme ;
 * - les séquences d'espaces, tabulations et retours à la ligne, ramenées à une
 *   espace simple : une note repartagée depuis une autre app est fréquemment
 *   reformatée sans être modifiée.
 *
 * Ce qui est CONSERVÉ : la casse et la ponctuation. « Appeler Marie » et
 * « appeler marie » sont deux notes différentes — décider le contraire serait
 * interpréter le contenu, ce que ce lot s'interdit.
 */

/** Texte normalisé servant de base à l'empreinte. Pure. */
fun normaliserPourEmpreinte(texte: String): String =
    Normalizer.normalize(texte, Normalizer.Form.NFC)
        .trim()
        .replace(Regex("\\s+"), " ")

/**
 * Empreinte de déduplication : SHA-256 du texte normalisé, en hexadécimal
 * minuscule. Pure et stable dans le temps — une empreinte stockée aujourd'hui
 * doit rester comparable dans deux ans, donc cette fonction ne changera pas sans
 * migration explicite.
 */
fun empreinteCapture(texte: String): String {
    val octets = MessageDigest.getInstance("SHA-256")
        .digest(normaliserPourEmpreinte(texte).toByteArray(Charsets.UTF_8))
    return octets.joinToString("") { "%02x".format(it) }
}

/** Limite de taille d'une capture. Au-delà, on tronque en le disant. */
const val TAILLE_MAX_CAPTURE = 100_000

/**
 * Tronque un texte trop long en **signalant la troncature dans le contenu
 * lui-même**, pour qu'une capture amputée ne puisse pas passer pour complète des
 * mois plus tard. Pure.
 */
fun tronquerCapture(texte: String, maximum: Int = TAILLE_MAX_CAPTURE): String {
    if (texte.length <= maximum) return texte
    val coupe = texte.take(maximum)
    return coupe + "\n\n[…] Capture tronquée : le texte d'origine faisait " +
        "${texte.length} caractères, limite ${maximum}."
}
