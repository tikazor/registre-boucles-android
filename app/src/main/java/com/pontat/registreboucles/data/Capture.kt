package com.pontat.registreboucles.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Note brute capturée depuis une autre application (partage ou sélection de
 * texte). **Une capture n'est pas une boucle** : elle vit en amont du registre,
 * dans sa propre table, et rien n'entre dans le registre sans une action
 * explicite de l'utilisateur. Sans cette séparation, on remplacerait un
 * éparpillement par un dépotoir.
 *
 * [contenuBrut] est **immuable** : c'est la matière première. Aucun traitement de
 * l'application ne le réécrit, ne l'analyse ni ne l'interprète — l'analyse est
 * faite hors de l'app, par une IA, puis supervisée (invariant I15).
 *
 * [boucleLiee] est une colonne SIMPLE, volontairement **sans clé étrangère** vers
 * `boucles`. Une clé étrangère impliquerait un comportement en cascade : la
 * suppression d'une boucle emporterait la capture (CASCADE) ou interdirait la
 * suppression (RESTRICT). Or une capture ne doit JAMAIS disparaître, et une
 * boucle doit rester supprimable. Le lien est donc informatif : si la boucle est
 * supprimée, la capture conserve la trace de l'identifiant qu'elle a produit.
 */
@Entity(
    tableName = "captures",
    indices = [Index("empreinte"), Index("statut")]
)
data class Capture(
    @PrimaryKey val id: String,          // C-<aaaaMMjj-HHmmss>-<4 hex>
    val contenuBrut: String,             // texte tel quel, JAMAIS réécrit
    val titre: String? = null,           // EXTRA_SUBJECT si l'app source le fournit
    val appareil: String,                // code appareil (AND-07) ou nom libre, sinon "LOCAL"
    val appSource: String? = null,       // paquet de l'app émettrice, ex. "com.miui.notes"
    val capturee: Long,                  // epoch millis
    val empreinte: String,               // SHA-256 du contenu normalisé (déduplication)
    val statut: String,                  // StatutCapture.valeurStockee()
    val boucleLiee: String? = null       // id de la boucle produite ; pas de FK (cf. ci-dessus)
)

/**
 * Extrait affichable dans la liste : première ligne non vide, tronquée. Ne
 * modifie évidemment pas le contenu stocké — c'est une vue, pas un traitement.
 */
fun Capture.extrait(longueurMax: Int = 120): String {
    val premiere = contenuBrut.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val base = premiere.ifBlank { contenuBrut.trim() }
    return if (base.length <= longueurMax) base else base.take(longueurMax).trimEnd() + "…"
}

/**
 * Titre à proposer lors de la création d'une boucle depuis cette capture : le
 * sujet fourni par l'app source s'il existe, sinon la première ligne. C'est un
 * pré-remplissage de formulaire, pas une extraction d'information : rien n'est
 * déduit du contenu, et l'utilisateur valide.
 */
fun Capture.titrePropose(longueurMax: Int = 80): String =
    titre?.trim()?.takeIf { it.isNotBlank() } ?: extrait(longueurMax)

/** Origine à proposer pour la boucle produite : d'où vient la note. */
fun Capture.originePropose(): String =
    "capture ${appSource ?: "inconnue"}"
