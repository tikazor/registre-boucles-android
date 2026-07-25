package com.pontat.registreboucles.data

/**
 * Cycle de vie d'une capture — distinct de [Statut], qui régit les boucles.
 *
 * Une capture n'est PAS une boucle : c'est de la matière première, en amont du
 * registre. Elle n'a donc ni « actif » ni « terminal », seulement quatre états
 * de traitement, tous réversibles sauf TRAITEE.
 *
 * Aucun état ne signifie « supprimée » : une capture n'est jamais effacée, elle
 * est marquée IGNOREE (invariant I14).
 */
enum class StatutCapture {
    /** Capturée, pas encore exploitée. C'est l'état d'arrivée. */
    BRUTE,

    /** Partie dans un lot d'analyse. Réversible : retour possible en BRUTE. */
    EXPORTEE,

    /** A donné une boucle ; `boucleLiee` est alors renseigné. */
    TRAITEE,

    /** Écartée volontairement. Réversible : retour possible en BRUTE. */
    IGNOREE;

    /** Valeur stockée en base, en minuscules comme pour [Statut]. */
    fun valeurStockee(): String = name.lowercase()

    fun libelle(): String = when (this) {
        BRUTE -> "Brute"
        EXPORTEE -> "Exportée"
        TRAITEE -> "Traitée"
        IGNOREE -> "Ignorée"
    }

    companion object {
        /**
         * Même tolérance que [Statut.depuis] et [Milieu.depuis] : nom d'enum,
         * insensible à la casse, espaces tolérés. Valeur inconnue ou vide ->
         * `null`, jamais de retombée silencieuse sur BRUTE (l'appelant décide).
         */
        fun depuis(valeur: String?): StatutCapture? {
            if (valeur.isNullOrBlank()) return null
            val v = valeur.trim()
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
        }
    }
}

/** Statut typé d'une capture (null si la valeur en base n'est pas reconnue). */
fun Capture.statutTypé(): StatutCapture? = StatutCapture.depuis(statut)

/** Capture encore à exploiter : la boîte de réception au sens strict. */
fun Capture.estBrute(): Boolean = statutTypé() == StatutCapture.BRUTE

/**
 * Passage d'une capture à TRAITEE. Exige l'identifiant de la boucle produite :
 * une capture traitée sans boucle liée serait une trace perdue, donc le lien est
 * un paramètre obligatoire et non un champ que l'appelant pourrait oublier.
 */
fun Capture.versTraitee(boucleId: String): Capture {
    require(boucleId.isNotBlank()) { "Une capture TRAITEE doit porter la boucle produite." }
    return copy(statut = StatutCapture.TRAITEE.valeurStockee(), boucleLiee = boucleId)
}

/** Marque une capture comme écartée. Le contenu brut reste intact. */
fun Capture.versIgnoree(): Capture = copy(statut = StatutCapture.IGNOREE.valeurStockee())

/** Signale qu'une capture est partie dans un lot d'analyse. */
fun Capture.versExportee(): Capture = copy(statut = StatutCapture.EXPORTEE.valeurStockee())

/**
 * Retour à l'état d'arrivée, depuis EXPORTEE (lot non exploité) ou IGNOREE
 * (on s'est ravisé). Refusé depuis TRAITEE : une boucle existe déjà, la défaire
 * serait une décision sur le registre, pas sur la boîte de réception.
 */
fun Capture.versBrute(): Capture {
    require(statutTypé() != StatutCapture.TRAITEE) {
        "Une capture déjà traitée ne revient pas en brute : sa boucle existe."
    }
    return copy(statut = StatutCapture.BRUTE.valeurStockee())
}
