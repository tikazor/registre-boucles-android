package com.pontat.registreboucles.data

import kotlin.math.abs

/**
 * Moteur de fusion bidirectionnelle entre deux appareils (AND-08).
 *
 * TOUT est calculé ici, SANS effet de bord : la fonction rend un [PlanFusion]
 * que le repository applique en une transaction. Cette séparation est ce qui
 * rend les règles de résolution testables en JVM pure, sans Room ni émulateur —
 * et ce qui permet de vérifier l'idempotence, propriété la plus difficile de la
 * synchronisation par fichier.
 *
 * Règles, dans l'ordre d'application :
 *
 * 0. Garde-fou d'horloge : si le fichier entrant déclare un `exporteLe` en avance
 *    de plus de [MARGE_HORLOGE_MS] sur l'heure locale, RIEN n'est décidé — le plan
 *    porte [PlanFusion.avanceHorloge] et attend une confirmation explicite. On ne
 *    fait jamais confiance à une horloge en silence : tout l'arbitrage temporel
 *    (règle 4b) en dépend.
 * 1. Mouvements et journaux : union dédupliquée sur les mêmes clés que
 *    [calculerFusion] — (boucleId, date, contenu) et (boucleId, date, texte).
 *    Jamais de suppression, jamais d'arbitrage.
 * 2. Boucle entrante inconnue ici, sans tombstone : insérée telle quelle.
 * 3. Boucle entrante inconnue ici AVEC tombstone locale :
 *    - tombstone au moins aussi récente que la modification entrante -> reste
 *      supprimée (boucle ignorée) ;
 *    - tombstone plus ancienne -> résurrection, et la tombstone est retirée.
 * 4. Boucle présente des deux côtés :
 *    a. un seul côté terminal -> le côté terminal l'emporte sur tous les champs
 *       scalaires, statut compris, quels que soient les horodatages. Une clôture
 *       journalisée est un fait, pas une opinion datée.
 *    b. sinon, écart des `derniereModification()` :
 *       - > [TOLERANCE_MS] -> le plus récent gagne ; CHAQUE champ écrasé produit
 *         un mouvement de traçage (invariant I13) ;
 *       - <= [TOLERANCE_MS] avec champs divergents -> CONFLIT, aucune écriture.
 *    c. `id`, `creee` et `source` ne sont JAMAIS écrasés.
 * 5. Tombstone entrante visant une boucle encore vivante ici -> CONFLIT
 *    (motif [MotifConflit.SUPPRIMEE_A_DISTANCE]), aucune suppression. Propager
 *    l'effacement déclencherait `ON DELETE CASCADE` et détruirait mouvements et
 *    journaux de clôture, ce qui est interdit. La divergence est signalée, pas
 *    résolue en silence.
 *
 * Ce que le moteur ne fait PAS : coercer les boucles `source = ia` reçues par
 * sync. Un fichier d'état vient d'un appareil pair, dont la supervision vaut
 * décision (arbitrage du commanditaire, cf. I6 « portée »). L'insertion est
 * toutefois tracée par un mouvement, pour qu'elle ne soit pas muette.
 */

/** Tolérance d'horodatage : en deçà, on ne tranche pas, on demande un arbitrage. */
const val TOLERANCE_MS = 60_000L

/** Avance d'horloge distante tolérée sans confirmation. */
const val MARGE_HORLOGE_MS = 10 * 60 * 1000L

/** État complet d'un registre, tel qu'un fichier v3 le transporte. */
data class EtatRegistre(
    val boucles: List<Boucle> = emptyList(),
    val mouvements: List<Mouvement> = emptyList(),
    val journaux: List<Journal> = emptyList(),
    val suppressions: List<Suppression> = emptyList()
)

/** État lu dans le fichier `etat-<CODE>.json` d'un autre appareil. */
data class EtatDistant(
    val codeAppareil: String,
    val exporteLe: Long?,
    val etat: EtatRegistre
)

/** Pourquoi une boucle attend un arbitrage manuel. */
enum class MotifConflit {
    /** Modifiée des deux côtés à moins de [TOLERANCE_MS] d'écart : rien ne permet de trancher. */
    HORODATAGE_TROP_PROCHE,

    /** Supprimée sur l'autre appareil, encore vivante ici. */
    SUPPRIMEE_A_DISTANCE
}

/**
 * Boucle qu'il faut arbitrer à la main. [diffs] réutilise le [DiffChamp]
 * d'AND-03 : l'écran de conflits réemploie l'UI de diff du mode « Fusionner ».
 * [entrante] est null pour [MotifConflit.SUPPRIMEE_A_DISTANCE] (la boucle
 * n'existe plus chez l'émetteur).
 */
data class ConflitSync(
    val boucleId: String,
    val motif: MotifConflit,
    val diffs: List<DiffChamp>,
    val entrante: Boucle?,
    val appareilDistant: String
)

/** Une boucle absente ici qui revient parce que la modification bat la tombstone. */
data class Resurrection(val boucleId: String, val supprimeeLe: Long, val modifieeLe: Long)

/**
 * Plan d'application, entièrement calculé. Le repository l'applique tel quel,
 * en une transaction, sans rejouer aucune décision.
 *
 * [bouclesAInserer] et [bouclesAMettreAJour] portent DÉJÀ les `modifieeLe` /
 * `modifieePar` de l'appareil émetteur : le repository ne doit PAS les
 * estampiller, sinon la fusion suivante verrait un écart de quelques secondes
 * et déclencherait un faux conflit — l'idempotence en dépend.
 */
data class PlanFusion(
    val bouclesAInserer: List<Boucle> = emptyList(),
    val bouclesAMettreAJour: List<Boucle> = emptyList(),
    val mouvementsAInserer: List<Mouvement> = emptyList(),
    val journauxAInserer: List<Journal> = emptyList(),
    val suppressionsAInserer: List<Suppression> = emptyList(),
    val suppressionsARetirer: List<String> = emptyList(),
    val conflits: List<ConflitSync> = emptyList(),
    val bouclesIgnorees: List<String> = emptyList(),
    val resurrections: List<Resurrection> = emptyList(),
    val avanceHorloge: Long? = null
) {
    /** Vrai quand le plan n'écrit rien du tout (fusion déjà appliquée, ou interrompue). */
    fun estVide(): Boolean =
        bouclesAInserer.isEmpty() && bouclesAMettreAJour.isEmpty() &&
            mouvementsAInserer.isEmpty() && journauxAInserer.isEmpty() &&
            suppressionsAInserer.isEmpty() && suppressionsARetirer.isEmpty()

    /** Vrai quand la fusion a été interrompue par le garde-fou d'horloge. */
    fun estInterrompu(): Boolean = avanceHorloge != null
}

/**
 * Calcule la fusion de [distant] dans [local]. Fonction PURE : aucune lecture
 * d'horloge (voir [maintenant]), aucun accès base, aucun aléa.
 *
 * @param maintenant heure locale, injectée pour rendre les règles temporelles testables.
 * @param confirmeHorloge true quand l'utilisateur a explicitement accepté un
 *   fichier dont l'horloge est en avance (règle 0).
 */
fun calculerFusionSync(
    local: EtatRegistre,
    distant: EtatDistant,
    maintenant: Long,
    confirmeHorloge: Boolean = false,
    toleranceMs: Long = TOLERANCE_MS,
    margeHorlogeMs: Long = MARGE_HORLOGE_MS
): PlanFusion {
    // Règle 0 — garde-fou d'horloge, AVANT toute décision.
    val avance = avanceHorloge(distant.exporteLe, maintenant, margeHorlogeMs)
    if (avance != null && !confirmeHorloge) return PlanFusion(avanceHorloge = avance)

    val localesParId = local.boucles.associateBy { it.id }
    val tombstonesLocales = local.suppressions.associateBy { it.boucleId }
    val idsEntrants = distant.etat.boucles.mapTo(HashSet()) { it.id }

    val aInserer = mutableListOf<Boucle>()
    val aMettreAJour = mutableListOf<Boucle>()
    val tracages = mutableListOf<Mouvement>()
    val tombstonesARetirer = mutableListOf<String>()
    val conflits = mutableListOf<ConflitSync>()
    val ignorees = mutableListOf<String>()
    val resurrections = mutableListOf<Resurrection>()

    for (entrante in distant.etat.boucles) {
        val locale = localesParId[entrante.id]

        if (locale == null) {
            val tombstone = tombstonesLocales[entrante.id]
            when {
                // Règle 2 — inconnue et jamais supprimée ici : elle entre.
                tombstone == null -> {
                    aInserer += entrante
                    // Une boucle IA n'est pas coercée sur ce canal (appareil pair),
                    // mais son entrée reste tracée : jamais d'arrivée muette.
                    if (entrante.sourceTypee() == SourceBoucle.IA && !entrante.estProposition()) {
                        tracages += Mouvement(
                            boucleId = entrante.id,
                            date = maintenant,
                            type = "declaration",
                            contenu = "Boucle IA reçue de ${distant.codeAppareil} avec le statut " +
                                "\"${entrante.statut}\" (appareil pair, supervision non rejouée)"
                        )
                    }
                }
                // Règle 3 — la tombstone tient : la boucle reste supprimée.
                tombstone.supprimeeLe >= entrante.derniereModification() -> ignorees += entrante.id
                // Règle 3 — la modification est postérieure : résurrection.
                else -> {
                    aInserer += entrante
                    tombstonesARetirer += entrante.id
                    resurrections += Resurrection(
                        entrante.id, tombstone.supprimeeLe, entrante.derniereModification()
                    )
                }
            }
            continue
        }

        // Règle 4 — présente des deux côtés.
        val diffs = diffsScalaires(locale, entrante)
        if (diffs.isEmpty()) {
            // Identiques : rien à faire. C'est ce cas qui rend la 2ᵉ fusion neutre.
            continue
        }

        val localeTerminale = locale.estTerminal()
        val entranteTerminale = entrante.estTerminal()
        val ecart = entrante.derniereModification() - locale.derniereModification()

        when {
            // 4a — un seul côté terminal : il gagne, horodatages ignorés.
            entranteTerminale && !localeTerminale -> {
                aMettreAJour += adopter(locale, entrante)
                tracages += tracer(locale.id, diffs, distant.codeAppareil, maintenant)
            }
            localeTerminale && !entranteTerminale -> ignorees += locale.id

            // 4b — écart net : le plus récent gagne, chaque écrasement est journalisé.
            ecart > toleranceMs -> {
                aMettreAJour += adopter(locale, entrante)
                tracages += tracer(locale.id, diffs, distant.codeAppareil, maintenant)
            }
            -ecart > toleranceMs -> ignorees += locale.id

            // 4b — trop proche pour trancher : arbitrage manuel, aucune écriture.
            else -> conflits += ConflitSync(
                boucleId = locale.id,
                motif = MotifConflit.HORODATAGE_TROP_PROCHE,
                diffs = diffs,
                entrante = entrante,
                appareilDistant = distant.codeAppareil
            )
        }
    }

    // Règle 5 — tombstones entrantes.
    val tombstonesAInserer = mutableListOf<Suppression>()
    for (tombstone in distant.etat.suppressions) {
        // Un fichier qui déclare la boucle vivante ne peut pas déclarer sa mort.
        if (tombstone.boucleId in idsEntrants) continue
        val locale = localesParId[tombstone.boucleId]
        if (locale != null) {
            // Vivante ici : on ne supprime RIEN (la CASCADE emporterait mouvements
            // et journaux de clôture). On signale, on laisse l'utilisateur trancher.
            conflits += ConflitSync(
                boucleId = tombstone.boucleId,
                motif = MotifConflit.SUPPRIMEE_A_DISTANCE,
                diffs = emptyList(),
                entrante = null,
                appareilDistant = tombstone.supprimeePar
            )
            continue
        }
        // Absente ici : on enregistre la connaissance de la suppression, sauf si
        // on vient justement de la ressusciter.
        if (tombstone.boucleId !in tombstonesARetirer &&
            tombstonesLocales[tombstone.boucleId] == null
        ) {
            tombstonesAInserer += tombstone
        }
    }

    // Règle 1 — union dédupliquée des mouvements et journaux (jamais de perte).
    val clesMvt = local.mouvements.mapTo(HashSet()) { Triple(it.boucleId, it.date, it.contenu) }
    val mouvementsUnion = distant.etat.mouvements
        .filter { Triple(it.boucleId, it.date, it.contenu) !in clesMvt }
    val clesJrn = local.journaux.mapTo(HashSet()) { Triple(it.boucleId, it.date, it.texte) }
    val journauxUnion = distant.etat.journaux
        .filter { Triple(it.boucleId, it.date, it.texte) !in clesJrn }

    return PlanFusion(
        bouclesAInserer = aInserer,
        bouclesAMettreAJour = aMettreAJour,
        mouvementsAInserer = mouvementsUnion + tracages,
        journauxAInserer = journauxUnion,
        suppressionsAInserer = tombstonesAInserer,
        suppressionsARetirer = tombstonesARetirer,
        conflits = conflits,
        bouclesIgnorees = ignorees,
        resurrections = resurrections,
        avanceHorloge = avance
    )
}

/**
 * Consigne un plan au journal de synchronisation. Pure : l'entrée est construite
 * ici, le repository ne fait que l'insérer, dans la même transaction que le plan.
 */
fun evenementDepuisPlan(
    plan: PlanFusion,
    distant: EtatDistant,
    nomFichier: String,
    maintenant: Long
): EvenementSync {
    val resultat = when {
        plan.estInterrompu() -> ResultatSync.ECHEC
        plan.conflits.isNotEmpty() -> ResultatSync.CONFLITS
        else -> ResultatSync.SUCCES
    }
    val detail = when {
        plan.estInterrompu() ->
            "Interrompue : horloge de ${distant.codeAppareil} en avance de " +
                formaterAvance(plan.avanceHorloge ?: 0L) + "."
        plan.estVide() && plan.conflits.isEmpty() -> "Déjà à jour."
        else -> buildList {
            if (plan.bouclesAInserer.isNotEmpty()) add("${plan.bouclesAInserer.size} ajoutée(s)")
            if (plan.bouclesAMettreAJour.isNotEmpty()) add("${plan.bouclesAMettreAJour.size} fusionnée(s)")
            if (plan.bouclesIgnorees.isNotEmpty()) add("${plan.bouclesIgnorees.size} inchangée(s)")
            if (plan.resurrections.isNotEmpty()) add("${plan.resurrections.size} ressuscitée(s)")
            if (plan.mouvementsAInserer.isNotEmpty()) add("${plan.mouvementsAInserer.size} mouvement(s)")
            if (plan.journauxAInserer.isNotEmpty()) add("${plan.journauxAInserer.size} journal(aux)")
            if (plan.conflits.isNotEmpty()) add("${plan.conflits.size} à arbitrer")
        }.joinToString(", ").ifEmpty { "Aucun changement." }
    }
    return EvenementSync(
        horodatage = maintenant,
        appareilDistant = distant.codeAppareil,
        fichierLu = nomFichier,
        exporteLeDistant = distant.exporteLe,
        bouclesAjoutees = plan.bouclesAInserer.size,
        bouclesFusionnees = plan.bouclesAMettreAJour.size,
        bouclesIgnorees = plan.bouclesIgnorees.size,
        mouvementsAjoutes = plan.mouvementsAInserer.size,
        journauxAjoutes = plan.journauxAInserer.size,
        conflits = plan.conflits.size,
        resultat = resultat.valeurStockee(),
        detail = detail
    )
}

/**
 * Applique un plan à un état, en mémoire. C'est le MODÈLE DE RÉFÉRENCE de ce que
 * le repository fait en base : même ordre, mêmes effets. Il existe pour deux
 * raisons que Room ne permet pas d'atteindre en JVM :
 * - vérifier l'IDEMPOTENCE (fusionner deux fois le même fichier ne change rien) ;
 * - vérifier la SYMÉTRIE (A fusionne B, puis B fusionne A -> convergence).
 *
 * Toute évolution de l'application du plan côté repository doit être répercutée
 * ici, sinon les deux tests ci-dessus ne prouvent plus rien.
 */
fun appliquerPlan(local: EtatRegistre, plan: PlanFusion): EtatRegistre {
    if (plan.estInterrompu()) return local
    val misesAJour = plan.bouclesAMettreAJour.associateBy { it.id }
    val boucles = local.boucles.map { misesAJour[it.id] ?: it } + plan.bouclesAInserer
    val retirees = plan.suppressionsARetirer.toSet()
    val suppressions = local.suppressions.filterNot { it.boucleId in retirees } +
        plan.suppressionsAInserer.filterNot { it.boucleId in retirees }
    return EtatRegistre(
        boucles = boucles,
        mouvements = local.mouvements + plan.mouvementsAInserer,
        journaux = local.journaux + plan.journauxAInserer,
        suppressions = suppressions
    )
}

/**
 * Avance de l'horloge distante sur l'heure locale, si elle dépasse la marge.
 * null = horloge plausible. Pure, donc testable sans horloge système.
 */
fun avanceHorloge(exporteLe: Long?, maintenant: Long, margeMs: Long = MARGE_HORLOGE_MS): Long? {
    val avance = (exporteLe ?: return null) - maintenant
    return if (avance > margeMs) avance else null
}

/** Libellé lisible d'une avance d'horloge (« 3 h 12 min », « 14 min »). */
fun formaterAvance(avanceMs: Long): String {
    val minutes = avanceMs / 60_000L
    val heures = minutes / 60
    val reste = minutes % 60
    return when {
        heures >= 24 -> "${heures / 24} j ${heures % 24} h"
        heures > 0 -> "$heures h $reste min"
        else -> "$minutes min"
    }
}

/**
 * Adopte les champs de [entrante] sur [locale]. `id`, `creee` et `source` sont
 * préservés (règle 4c) ; `modifieeLe` / `modifieePar` sont ceux de l'émetteur,
 * sans quoi la fusion ne serait pas idempotente.
 */
private fun adopter(locale: Boucle, entrante: Boucle): Boucle = locale.copy(
    type = entrante.type,
    titre = entrante.titre,
    origine = entrante.origine,
    echeance = entrante.echeance,
    tiers = entrante.tiers,
    preuveAttendue = entrante.preuveAttendue,
    blocage = entrante.blocage,
    impact = entrante.impact,
    defaut = entrante.defaut,
    statut = entrante.statut,
    milieu = entrante.milieu,
    modifieeLe = entrante.derniereModification(),
    modifieePar = entrante.modifieePar
)

/**
 * Un mouvement de traçage par champ écrasé (invariant I13). Le libellé est
 * figé : il est lu tel quel dans l'historique d'une boucle, des mois plus tard.
 */
private fun tracer(
    boucleId: String,
    diffs: List<DiffChamp>,
    codeDistant: String,
    maintenant: Long
): List<Mouvement> = diffs.map { d ->
    Mouvement(
        boucleId = boucleId,
        date = maintenant,
        type = "declaration",
        contenu = "${d.champ} : \"${d.existant ?: ""}\" remplacé par " +
            "\"${d.entrant ?: ""}\" (sync depuis $codeDistant)"
    )
}

/**
 * Champs scalaires divergents entre local et entrant. Inclut `statut` — que la
 * règle 4 écrase — et exclut `id`, `creee`, `source`, jamais écrasés (4c), ainsi
 * que `modifieeLe` / `modifieePar`, qui sont des métadonnées de fusion et non
 * des données de l'utilisateur.
 */
fun diffsScalaires(locale: Boucle, entrante: Boucle): List<DiffChamp> = buildList {
    fun cmp(nom: String, a: String?, b: String?) { if (a != b) add(DiffChamp(nom, a, b)) }
    cmp("type", locale.type, entrante.type)
    cmp("titre", locale.titre, entrante.titre)
    cmp("origine", locale.origine, entrante.origine)
    cmp("échéance", locale.echeance?.toString(), entrante.echeance?.toString())
    cmp("tiers", locale.tiers, entrante.tiers)
    cmp("preuveAttendue", locale.preuveAttendue, entrante.preuveAttendue)
    cmp("blocage", locale.blocage, entrante.blocage)
    cmp("impact", locale.impact, entrante.impact)
    cmp("defaut", locale.defaut, entrante.defaut)
    cmp("statut", locale.statut, entrante.statut)
    cmp("milieu", locale.milieu, entrante.milieu)
}

/** Écart absolu des dernières modifications (diagnostic, affichage du conflit). */
fun ecartModification(locale: Boucle, entrante: Boucle): Long =
    abs(entrante.derniereModification() - locale.derniereModification())
