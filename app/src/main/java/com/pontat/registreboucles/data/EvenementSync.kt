package com.pontat.registreboucles.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Issue d'une synchronisation, telle qu'elle est consignée.
 *
 * SUCCES   : la fusion s'est appliquée entièrement.
 * CONFLITS : la fusion s'est appliquée, mais des boucles attendent un arbitrage
 *            manuel (écarts d'horodatage trop serrés pour trancher, cf. I13).
 * ECHEC    : rien n'a été écrit en base (backup impossible, fichier illisible,
 *            horloge distante suspecte non confirmée).
 */
enum class ResultatSync {
    SUCCES, CONFLITS, ECHEC;

    fun valeurStockee(): String = name.lowercase()

    fun libelle(): String = when (this) {
        SUCCES -> "Réussie"
        CONFLITS -> "À arbitrer"
        ECHEC -> "Échouée"
    }

    companion object {
        fun depuis(valeur: String?): ResultatSync? {
            val v = valeur?.trim() ?: return null
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
        }
    }
}

/**
 * Journal de synchronisation : une ligne par fichier d'état distant lu, y compris
 * les échecs. C'est la seule trace de ce qu'une fusion a fait entrer dans le
 * registre, donc elle n'est JAMAIS purgée ni modifiée après écriture (mêmes
 * raisons que les tombstones : une app-mémoire ne réécrit pas son passé).
 *
 * [exporteLeDistant] est l'horodatage déclaré par l'appareil émetteur ; le
 * comparer à [horodatage] permet de repérer après coup une horloge décalée
 * (garde-fou d'AND-08).
 */
@Entity(tableName = "evenements_sync")
data class EvenementSync(
    @PrimaryKey(autoGenerate = true) val evenementId: Long = 0,
    val horodatage: Long,                 // epoch millis, heure locale de la fusion
    val appareilDistant: String,          // code appareil émetteur du fichier lu
    val fichierLu: String,                // nom du fichier (etat-<CODE>.json)
    val exporteLeDistant: Long?,          // `exporteLe` déclaré dans le fichier ; null si absent
    val bouclesAjoutees: Int,
    val bouclesFusionnees: Int,
    val bouclesIgnorees: Int,
    val mouvementsAjoutes: Int,
    val journauxAjoutes: Int,
    val conflits: Int,
    val resultat: String,                 // ResultatSync.valeurStockee()
    val detail: String                    // phrase courte, lisible telle quelle
)

fun EvenementSync.resultatTypé(): ResultatSync? = ResultatSync.depuis(resultat)
