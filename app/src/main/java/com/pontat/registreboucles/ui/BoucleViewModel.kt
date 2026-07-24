package com.pontat.registreboucles.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.BoucleRepository
import com.pontat.registreboucles.data.Journal
import com.pontat.registreboucles.data.JournalType
import com.pontat.registreboucles.data.ListeOptions
import com.pontat.registreboucles.data.Milieu
import com.pontat.registreboucles.data.Mouvement
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

/** Filtre par statut de l'écran Liste. "Ouverte" = statut != "fermee". */
enum class FiltreStatut(val libelle: String) {
    TOUTES("Toutes"),
    OUVERTES("Ouvertes"),
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

    /** Map boucleId -> date du dernier mouvement (étiquette « Modifié le »). */
    val dernieresModifs: StateFlow<Map<String, Long>> = repository.observerDernieresModifs()
        .map { rows -> rows.associate { it.boucleId to it.derniere } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            _baseVide.value = repository.estVide()
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
            val id = genererProchainId()
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
                    statut = "ouverte",
                    milieu = milieu
                )
            )
            onCree(id)
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

    /** Backup manuel (depuis Réglages). Renvoie le nom du fichier créé (ou null). */
    fun sauvegarderManuel(onFait: (String?) -> Unit) {
        viewModelScope.launch { onFait(repository.creerBackup()?.name) }
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
                repository.importerEcraser(res.boucles, res.mouvements, res.journaux)
                _baseVide.value = false
            } else {
                _importEnAttente.value = res
            }
        }
    }

    /** "Ajouter" : n'insère que les id absents, sans toucher aux boucles existantes. */
    fun confirmerAjout() {
        val res = _importEnAttente.value ?: return
        viewModelScope.launch {
            repository.importerAjouter(res.boucles, res.mouvements, res.journaux)
            _importEnAttente.value = null
        }
    }

    /** "Écraser" : vide boucles + mouvements puis réimporte le JSON tel quel. */
    fun confirmerEcrasement() {
        val res = _importEnAttente.value ?: return
        viewModelScope.launch {
            repository.importerEcraser(res.boucles, res.mouvements, res.journaux)
            _importEnAttente.value = null
        }
    }

    fun annulerImport() {
        _importEnAttente.value = null
    }

    /** Exporte l'état courant vers l'URI choisi (ACTION_CREATE_DOCUMENT). */
    fun exporter(uri: Uri, onFini: (Boolean) -> Unit) {
        viewModelScope.launch { onFini(repository.exporterVers(uri)) }
    }

    fun effacerErreurImport() {
        _erreurImport.value = null
    }

    /** Génère l'identifiant suivant au format B-### à partir des ids existants. */
    private suspend fun genererProchainId(): String {
        val regex = Regex("""B-(\d+)""")
        val max = repository.tousLesIds()
            .mapNotNull { regex.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "B-%03d".format(max + 1)
    }

    class Factory(private val repository: BoucleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BoucleViewModel(repository) as T
        }
    }
}
