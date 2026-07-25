package com.pontat.registreboucles.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Identifiant de capture : `C-<aaaaMMjj-HHmmss>-<4 hex>`, par exemple
 * `C-20260725-142233-9f2b`.
 *
 * Volontairement **pas** une séquence numérotée comme les boucles (`B-041`) :
 * - une capture n'est jamais désignée à l'oral, sa lisibilité importe peu ;
 * - une séquence exigerait de connaître les identifiants déjà émis, donc une
 *   coordination entre appareils. Un horodatage plus un tirage aléatoire s'en
 *   passe complètement, sans dépendre du socle multi-appareils.
 *
 * L'horodatage rend l'identifiant lisible et trivialement triable ; les 4 chiffres
 * hexadécimaux (65 536 valeurs) couvrent les captures faites dans la même
 * seconde. Le tirage n'étant pas une garantie, [genererIdCaptureUnique] vérifie
 * en plus l'unicité contre les identifiants déjà présents : c'est cette fonction
 * que le repository utilise, parce qu'une collision écraserait une capture et
 * qu'aucune capture ne doit jamais disparaître (invariant I14).
 */

private val FORMAT_HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** Un identifiant, sans contrôle d'unicité. Pure : l'heure et l'aléa sont injectés. */
fun genererIdCapture(
    maintenant: Long,
    aleatoire: Random = Random,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val horodatage = FORMAT_HORODATAGE.format(Instant.ofEpochMilli(maintenant).atZone(zone))
    val suffixe = "%04x".format(aleatoire.nextInt(0x10000))
    return "C-$horodatage-$suffixe"
}

/**
 * Identifiant garanti absent de [dejaPris]. Retire aussi le cas — improbable mais
 * destructeur — de deux captures qui tombent sur le même tirage dans la même
 * seconde. Après [essaisMax] tirages infructueux, ajoute un compteur plutôt que
 * de boucler indéfiniment ou de rendre un identifiant déjà utilisé.
 */
fun genererIdCaptureUnique(
    maintenant: Long,
    dejaPris: Set<String>,
    aleatoire: Random = Random,
    zone: ZoneId = ZoneId.systemDefault(),
    essaisMax: Int = 64
): String {
    repeat(essaisMax) {
        val candidat = genererIdCapture(maintenant, aleatoire, zone)
        if (candidat !in dejaPris) return candidat
    }
    val base = genererIdCapture(maintenant, aleatoire, zone)
    var n = 1
    while ("$base-$n" in dejaPris) n++
    return "$base-$n"
}

/** Un identifiant est-il conforme au format ? Sert au contrôle, jamais à corriger. */
fun idCaptureConforme(id: String): Boolean =
    Regex("""C-\d{8}-\d{6}-[0-9a-f]{4}(-\d+)?""").matches(id.trim())
