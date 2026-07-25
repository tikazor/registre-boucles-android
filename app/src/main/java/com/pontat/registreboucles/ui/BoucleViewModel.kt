package com.pontat.registreboucles.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.BoucleRepository
import com.pontat.registreboucles.data.ConflitFusion
import com.pontat.registreboucles.data.ConflitSync
import com.pontat.registreboucles.data.EvenementSync
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.JournalType
import com.pontat.registreboucles.data.ListeOptions
import com.pontat.registreboucles.data.Milieu
import com.pontat.registreboucles.data.Mouvement
import com.pontat.registreboucles.data.SourceBoucle
import com.pontat.registreboucles.data.Statut
import com.pontat.registreboucles.data.codeAppareilSuggere
import com.pontat.registreboucles.data.genererProchainId
import com.pontat.registreboucles.importer.ImportException
import com.pontat.registreboucles.importer.ImportResult
import com.pontat.registreboucles.importer.JsonImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Filtre de l'écran Liste. Chaque valeur correspond à UN indicateur cliquable du
 * tableau de bord : aucun libellé n'est affiché deux fois, et le compteur d'un
 * indicateur est toujours le nombre de résultats de son propre filtre (mêmes
 * prédicats, cf. data/Echeance.kt et Statut.estActive).
 */
enum class FiltreStatut(val libelle: String) {
    TOUTES("Toutes"),
    OUVERTES("Ouvertes"),
    EN_RETARD("En retard"),
    BIENTOT("≤ 7 jours"),
    FERMEES("Fermées")
}

/** Boucle ciblée par un tap sur le widget (déplie la carte, ouvre éventuellement le mouvement). */
data class CibleWidget(val boucleId: String, val ouvrirMouvement: Boolean)

class BoucleViewModel(private val repository: BoucleRepository) : ViewModel() {

    // Filtres de l'écran Liste — hébergés ici pour persister à travers la
    // navigation (retour depuis Détail / Création).
    private val _recherche = MutableStateFlow("")
    val recherche: StateFlow<String> = _recherche.asStateFlow()

    private val _filtreStatut = MutableStateFlow(FiltreStatut.TOUTES)
    val filtreStatut: StateFlow<FiltreStatut> = _filtreStatut.asStateFlow()

    // ── Identité de l'appareil ──
    // null = pas encore choisie : l'UI affiche alors l'écran d'identité, bloquant
    // (aucun identifiant ne peut être émis sans code d'appareil, cf. I9).
    private val _codeAppareil = MutableStateFlow(repository.lireCodeAppareil())
    val codeAppareil: StateFlow<String?> = _codeAppareil.asStateFlow()

    /** Code proposé au premier lancement : préfixe dominant des ids déjà en base. */
    private val _codeSuggere = MutableStateFlow<String?>(null)
    val codeSuggere: StateFlow<String?> = _codeSuggere.asStateFlow()

    /**
     * Enregistre le code de l'appareil. Renvoie false si la saisie est refusée
     * (il faut 1 à 4 lettres). Les boucles déjà créées gardent leur préfixe.
     */
    fun definirCodeAppareil(saisie: String): Boolean {
        val code = repository.ecrireCodeAppareil(saisie) ?: return false
        _codeAppareil.value = code
        return true
    }

    // Mode sombre — choix explicite persisté (SharedPreferences via le repository).
    private val _modeSombre = MutableStateFlow(repository.lireModeSombre())
    val modeSombre: StateFlow<Boolean> = _modeSombre.asStateFlow()

    fun basculerModeSombre() {
        val nouveau = !_modeSombre.value
        _modeSombre.value = nouveau
        repository.ecrireModeSombre(nouveau)
    }

    // Options des listes à choix unique (Type / Tiers / Milieu), éditables en config.
    private val _options = MutableStateFlow(repository.lireOptions())
    val options: StateFlow<ListeOptions> = _options.asStateFlow()

    fun majOptions(nouvelles: ListeOptions) {
        _options.value = nouvelles
        repository.ecrireOptions(nouvelles)
    }

    // Deep-link depuis le widget : boucle à déplier (et éventuellement ouvrir le mouvement).
    private val _cibleWidget = MutableStateFlow<CibleWidget?>(null)
    val cibleWidget: StateFlow<CibleWidget?> = _cibleWidget.asStateFlow()

    fun ciblerDepuisWidget(boucleId: String, ouvrirMouvement: Boolean) {
        _cibleWidget.value = CibleWidget(boucleId, ouvrirMouvement)
    }

    fun cibleConsommee() {
        _cibleWidget.value = null
    }

    fun setRecherche(q: String) {
        _recherche.value = q
    }

    fun setFiltreStatut(f: FiltreStatut) {
        _filtreStatut.value = f
    }

    /** null = inconnu (chargement), true = base vide (écran import), false = données présentes. */
    private val _baseVide = MutableStateFlow<Boolean?>(null)
    val baseVide: StateFlow<Boolean?> = _baseVide.asStateFlow()

    private val _erreurImport = MutableStateFlow<String?>(null)
    val erreurImport: StateFlow<String?> = _erreurImport.asStateFlow()

    /** Non-null quand un import est parsé alors que la base contient déjà des données :
     *  l'UI présente alors le choix Ajouter / Écraser. */
    private val _importEnAttente = MutableStateFlow<ImportResult?>(null)
    val importEnAttente: StateFlow<ImportResult?> = _importEnAttente.asStateFlow()

    val boucles: StateFlow<List<Boucle>> = repository.observerToutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Propositions IA en attente de supervision (statut PROPOSEE). */
    val propositions: StateFlow<List<Boucle>> = repository.observerPropositions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Map boucleId -> date du dernier mouvement (étiquette « Modifié le »). */
    val dernieresModifs: StateFlow<Map<String, Long>> = repository.observerDernieresModifs()
        .map { rows -> rows.associate { it.boucleId to it.derniere } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            _baseVide.value = repository.estVide()
            // Sur un appareil qui possède déjà des « B-### », on proposera « B » :
            // la séquence existante continue sans rupture.
            if (_codeAppareil.value == null) {
                _codeSuggere.value = codeAppareilSuggere(repository.tousLesIds())
            }
            // Nom lisible du dossier partagé (lecture SAF, donc hors thread principal).
            _nomDossierSync.value = repository.nomDossierSync()
        }
    }

    fun observerBoucle(id: String): Flow<Boucle?> = repository.observerParId(id)

    fun observerMouvements(id: String): Flow<List<Mouvement>> = repository.observerMouvements(id)

    fun creer(
        titre: String,
        type: String,
        origine: String,
        preuveAttendue: String,
        impact: String,
        echeance: Long?,
        tiers: String?,
        milieu: String?,
        onCree: (String) -> Unit
    ) {
        viewModelScope.launch {
            // Sans identité d'appareil, aucun identifiant ne peut être émis :
            // on ne crée pas de boucle (l'UI empêche normalement d'arriver ici).
            val code = repository.lireCodeAppareil() ?: return@launch
            val id = genererProchainId(code, repository.tousLesIds())
            repository.creer(
                Boucle(
                    id = id,
                    type = type,
                    titre = titre,
                    origine = origine,
                    creee = System.currentTimeMillis(),
                    echeance = echeance,
                    tiers = tiers,
                    preuveAttendue = preuveAttendue,
                    blocage = null,
                    impact = impact,
                    defaut = null,
                    statut = Statut.OUVERTE.valeurStockee(),
                    milieu = milieu,
                    source = SourceBoucle.USER.valeurStockee()
                )
            )
            onCree(id)
        }
    }

    /**
     * Modifie une boucle existante. Ne touche qu'aux champs éditables du
     * formulaire ; id, date de création, statut, blocage et action par défaut
     * sont préservés (rechargés depuis la base). N'affecte pas le flux d'ajout.
     */
    fun modifier(
        id: String,
        titre: String,
        type: String,
        origine: String,
        preuveAttendue: String,
        impact: String,
        echeance: Long?,
        tiers: String?,
        milieu: String?,
        onFait: () -> Unit
    ) {
        viewModelScope.launch {
            val existante = repository.obtenir(id) ?: return@launch
            repository.mettreAJour(
                existante.copy(
                    titre = titre,
                    type = type,
                    origine = origine,
                    preuveAttendue = preuveAttendue,
                    impact = impact,
                    echeance = echeance,
                    tiers = tiers,
                    milieu = milieu
                )
            )
            onFait()
        }
    }

    fun ajouterMouvement(boucleId: String, type: String, contenu: String) {
        viewModelScope.launch {
            repository.ajouterMouvement(
                Mouvement(
                    boucleId = boucleId,
                    date = System.currentTimeMillis(),
                    type = type,
                    contenu = contenu
                )
            )
        }
    }

    /** Clôture EXIGEANT une entrée journal (type + texte). */
    fun cloturer(id: String, type: JournalType, texte: String) {
        viewModelScope.launch { repository.cloturer(id, type, texte) }
    }

    fun observerJournaux(boucleId: String): Flow<List<Journal>> = repository.observerJournaux(boucleId)

    // ── Supervision des propositions IA ──

    /** Message d'échec d'une action de supervision (backup strict impossible). One-shot. */
    private val _erreurSupervision = MutableStateFlow<String?>(null)
    val erreurSupervision: StateFlow<String?> = _erreurSupervision.asStateFlow()

    fun effacerErreurSupervision() {
        _erreurSupervision.value = null
    }

    /**
     * Accepter : PROPOSEE -> OUVERTE (source IA conservée) + trace automatique.
     * @param apresAmendement true quand l'acceptation suit le flux « Amender ».
     */
    fun accepterProposition(id: String, apresAmendement: Boolean = false) {
        viewModelScope.launch {
            try {
                repository.accepter(id, apresAmendement)
            } catch (e: Exception) {
                _erreurSupervision.value = e.message ?: "Action annulée : sauvegarde impossible."
            }
        }
    }

    /** Rejeter : PROPOSEE -> REJETEE avec motif obligatoire (journal DECLARATION). */
    fun rejeterProposition(id: String, motif: String) {
        viewModelScope.launch {
            try {
                repository.rejeter(id, motif)
            } catch (e: Exception) {
                _erreurSupervision.value = e.message ?: "Action annulée : sauvegarde impossible."
            }
        }
    }

    /** Backup manuel (depuis Réglages). Renvoie le nom du fichier créé (ou null). */
    fun sauvegarderManuel(onFait: (String?) -> Unit) {
        viewModelScope.launch { onFait(repository.creerBackup()?.name) }
    }

    /**
     * Exporte le dernier backup vers l'URI choisi (pour le sortir du stockage
     * app-scoped). [onFini] reçoit null si succès, sinon un message d'erreur.
     */
    fun exporterDernierBackup(uri: Uri, onFini: (erreur: String?) -> Unit) {
        viewModelScope.launch { onFini(repository.exporterDernierBackupVers(uri)) }
    }

    // Filtre par milieu de l'écran Liste (persiste en navigation, comme les autres filtres).
    private val _filtreMilieu = MutableStateFlow<Milieu?>(null)
    val filtreMilieu: StateFlow<Milieu?> = _filtreMilieu.asStateFlow()

    fun setFiltreMilieu(milieu: Milieu?) {
        _filtreMilieu.value = milieu
    }

    /**
     * Parse le JSON puis décide :
     * - base vide  -> insertion directe (aucun choix à faire) ;
     * - base pleine -> expose le résultat dans [importEnAttente] pour que l'UI
     *   propose Ajouter / Écraser avant toute écriture Room.
     */
    fun preparerImport(contenu: String) {
        viewModelScope.launch {
            val res = try {
                JsonImporter.parse(contenu)
            } catch (e: ImportException) {
                _erreurImport.value = e.message
                return@launch
            } catch (e: Exception) {
                _erreurImport.value = "Erreur inattendue à l'import : ${e.message}"
                return@launch
            }
            _erreurImport.value = null
            if (repository.estVide()) {
                // Échec de la sauvegarde de sécurité = import annulé (rien n'est écrasé).
                try {
                    repository.importerEcraser(res.boucles, res.mouvements, res.journaux, res.suppressions)
                    _baseVide.value = false
                } catch (e: Exception) {
                    _erreurImport.value = e.message ?: "Import annulé : sauvegarde impossible."
                }
            } else {
                _importEnAttente.value = res
            }
        }
    }

    /** "Ajouter" : n'insère que les id absents, sans toucher aux boucles existantes. */
    fun confirmerAjout() {
        val res = _importEnAttente.value ?: return
        viewModelScope.launch {
            try {
                repository.importerAjouter(res.boucles, res.mouvements, res.journaux, res.suppressions)
                _importEnAttente.value = null
            } catch (e: Exception) {
                // Filet manqué : rien n'a été écrit. On ferme le choix et on
                // remonte l'erreur (l'utilisateur pourra relancer l'import).
                _importEnAttente.value = null
                _erreurImport.value = e.message ?: "Import annulé : sauvegarde impossible."
            }
        }
    }

    /** "Écraser" : vide boucles + mouvements puis réimporte le JSON tel quel. */
    fun confirmerEcrasement() {
        val res = _importEnAttente.value ?: return
        viewModelScope.launch {
            try {
                repository.importerEcraser(res.boucles, res.mouvements, res.journaux, res.suppressions)
                _importEnAttente.value = null
            } catch (e: Exception) {
                _importEnAttente.value = null
                _erreurImport.value = e.message ?: "Import annulé : sauvegarde impossible."
            }
        }
    }

    fun annulerImport() {
        _importEnAttente.value = null
    }

    /** État de la résolution de fusion : le parse en cours + ses conflits scalaires. */
    data class FusionState(val res: ImportResult, val conflits: List<ConflitFusion>)

    private val _fusionEnCours = MutableStateFlow<FusionState?>(null)
    val fusionEnCours: StateFlow<FusionState?> = _fusionEnCours.asStateFlow()

    /** "Fusionner" : calcule les conflits scalaires puis ouvre l'écran d'arbitrage. */
    fun demarrerFusion() {
        val res = _importEnAttente.value ?: return
        viewModelScope.launch {
            val conflits = repository.conflitsFusion(res.boucles)
            _importEnAttente.value = null
            _fusionEnCours.value = FusionState(res, conflits)
        }
    }

    /** Applique la fusion avec, pour chaque id, le choix « prendre l'entrant ». */
    fun confirmerFusion(prendreEntrant: Set<String>) {
        val etat = _fusionEnCours.value ?: return
        viewModelScope.launch {
            try {
                repository.importerFusionner(
                    etat.res.boucles, etat.res.mouvements, etat.res.journaux, prendreEntrant,
                    etat.res.suppressions
                )
                _fusionEnCours.value = null
            } catch (e: Exception) {
                _fusionEnCours.value = null
                _erreurImport.value = e.message ?: "Fusion annulée : sauvegarde impossible."
            }
        }
    }

    fun annulerFusion() {
        _fusionEnCours.value = null
    }

    /**
     * Exporte l'état courant vers l'URI choisi (ACTION_CREATE_DOCUMENT).
     * [onFini] reçoit null si succès, sinon un message d'erreur réel.
     */
    fun exporter(uri: Uri, onFini: (erreur: String?) -> Unit) {
        viewModelScope.launch { onFini(repository.exporterVers(uri)) }
    }

    fun effacerErreurImport() {
        _erreurImport.value = null
    }

    // ── Synchronisation par dossier partagé (AND-08) ──
    //
    // Les conflits ne sont PAS persistés : ils sont recalculés à chaque
    // synchronisation par la fonction pure (mêmes données -> mêmes conflits). Une
    // app fermée au milieu d'un arbitrage ne perd donc rien : la prochaine
    // synchronisation les présente à nouveau.

    private val _dossierSync = MutableStateFlow(repository.lireDossierSync())
    val dossierSync: StateFlow<Uri?> = _dossierSync.asStateFlow()

    private val _nomDossierSync = MutableStateFlow<String?>(null)
    val nomDossierSync: StateFlow<String?> = _nomDossierSync.asStateFlow()

    private val _syncEnCours = MutableStateFlow(false)
    val syncEnCours: StateFlow<Boolean> = _syncEnCours.asStateFlow()

    private val _rapportSync = MutableStateFlow<BoucleRepository.RapportSync?>(null)
    val rapportSync: StateFlow<BoucleRepository.RapportSync?> = _rapportSync.asStateFlow()

    private val _erreurSync = MutableStateFlow<String?>(null)
    val erreurSync: StateFlow<String?> = _erreurSync.asStateFlow()

    val evenementsSync: StateFlow<List<EvenementSync>> = repository.observerEvenementsSync()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun definirDossierSync(uri: Uri) {
        repository.ecrireDossierSync(uri)
        _dossierSync.value = uri
        viewModelScope.launch { _nomDossierSync.value = repository.nomDossierSync() }
    }

    /**
     * Lance une synchronisation. [confirmeHorloge] n'est vrai que si l'utilisateur
     * a explicitement accepté un fichier dont l'horloge est en avance.
     */
    fun synchroniser(confirmeHorloge: Boolean = false) {
        if (_syncEnCours.value) return
        viewModelScope.launch {
            _syncEnCours.value = true
            _erreurSync.value = null
            try {
                _rapportSync.value = repository.synchroniser(confirmeHorloge)
            } catch (e: Exception) {
                _erreurSync.value = e.message ?: "Synchronisation impossible."
            } finally {
                _syncEnCours.value = false
            }
        }
    }

    /** Retire un conflit du rapport une fois arbitré (les autres restent affichés). */
    private fun retirerConflit(boucleId: String) {
        val rapport = _rapportSync.value ?: return
        _rapportSync.value = rapport.copy(conflits = rapport.conflits.filterNot { it.boucleId == boucleId })
    }

    fun arbitrerGarderLocal(conflit: ConflitSync) {
        viewModelScope.launch {
            try {
                repository.arbitrerGarderLocal(conflit.boucleId)
                retirerConflit(conflit.boucleId)
            } catch (e: Exception) {
                _erreurSync.value = e.message ?: "Arbitrage impossible."
            }
        }
    }

    fun arbitrerPrendreDistant(conflit: ConflitSync) {
        val entrante = conflit.entrante ?: return
        viewModelScope.launch {
            try {
                repository.arbitrerPrendreDistant(entrante, conflit.appareilDistant)
                retirerConflit(conflit.boucleId)
            } catch (e: Exception) {
                _erreurSync.value = e.message ?: "Arbitrage impossible."
            }
        }
    }

    /** Arbitrage d'une suppression distante : effacer ici aussi (avec tombstone). */
    fun arbitrerSupprimerIci(conflit: ConflitSync) {
        viewModelScope.launch {
            try {
                repository.arbitrerSupprimerIci(conflit.boucleId)
                retirerConflit(conflit.boucleId)
            } catch (e: Exception) {
                _erreurSync.value = e.message ?: "Arbitrage impossible."
            }
        }
    }

    fun effacerErreurSync() {
        _erreurSync.value = null
    }

    fun effacerRapportSync() {
        _rapportSync.value = null
    }

    class Factory(private val repository: BoucleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BoucleViewModel(repository) as T
        }
    }
}
