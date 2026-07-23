package com.pontat.registreboucles.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.BoucleRepository
import com.pontat.registreboucles.data.Mouvement
import com.pontat.registreboucles.importer.ImportException
import com.pontat.registreboucles.importer.ImportResult
import com.pontat.registreboucles.importer.JsonImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoucleViewModel(private val repository: BoucleRepository) : ViewModel() {

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
                    statut = "ouverte"
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

    fun cloturer(id: String) {
        viewModelScope.launch { repository.cloturer(id) }
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
                repository.importerEcraser(res.boucles, res.mouvements)
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
            repository.importerAjouter(res.boucles, res.mouvements)
            _importEnAttente.value = null
        }
    }

    /** "Écraser" : vide boucles + mouvements puis réimporte le JSON tel quel. */
    fun confirmerEcrasement() {
        val res = _importEnAttente.value ?: return
        viewModelScope.launch {
            repository.importerEcraser(res.boucles, res.mouvements)
            _importEnAttente.value = null
        }
    }

    fun annulerImport() {
        _importEnAttente.value = null
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
