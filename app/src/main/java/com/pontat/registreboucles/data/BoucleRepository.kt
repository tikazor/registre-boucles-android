package com.pontat.registreboucles.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.pontat.registreboucles.widget.BoucleWidget
import kotlinx.coroutines.flow.Flow

/**
 * Point d'accès unique aux données. Toute écriture sur la table `boucles`
 * (insert / update / delete / import) déclenche la mise à jour du widget Glance.
 */
class BoucleRepository(
    private val dao: BoucleDao,
    private val appContext: Context
) {

    fun observerToutes(): Flow<List<Boucle>> = dao.observerToutes()

    fun observerParId(id: String): Flow<Boucle?> = dao.observerParId(id)

    fun observerMouvements(boucleId: String): Flow<List<Mouvement>> =
        dao.observerMouvements(boucleId)

    suspend fun obtenir(id: String): Boucle? = dao.obtenir(id)

    suspend fun estVide(): Boolean = dao.compter() == 0

    suspend fun tousLesIds(): List<String> = dao.tousLesIds()

    suspend fun creer(boucle: Boucle) {
        dao.upsert(boucle)
        rafraichirWidget()
    }

    suspend fun mettreAJour(boucle: Boucle) {
        dao.mettreAJour(boucle)
        rafraichirWidget()
    }

    suspend fun supprimer(boucle: Boucle) {
        dao.supprimer(boucle)
        rafraichirWidget()
    }

    /** Passe la boucle au statut `fermee`. */
    suspend fun cloturer(id: String) {
        val boucle = dao.obtenir(id) ?: return
        dao.mettreAJour(boucle.copy(statut = "fermee"))
        rafraichirWidget()
    }

    /**
     * Ajoute un mouvement. N'affecte pas la table `boucles`, donc pas de
     * rafraîchissement du widget (compteurs et échéances inchangés).
     */
    suspend fun ajouterMouvement(mouvement: Mouvement) {
        dao.insererMouvement(mouvement)
    }

    /**
     * "Ajouter" : n'insère que les boucles dont l'id est absent de la base,
     * avec leurs mouvements. Les boucles déjà présentes (et leurs mouvements /
     * clôtures faits dans l'app) ne sont PAS touchées. Renvoie le nombre ajouté.
     */
    suspend fun importerAjouter(boucles: List<Boucle>, mouvements: List<Mouvement>): Int {
        val existants = dao.tousLesIds().toSet()
        val nouvelles = boucles.filter { it.id !in existants }
        val nouveauxIds = nouvelles.map { it.id }.toSet()
        val nouveauxMouvements = mouvements.filter { it.boucleId in nouveauxIds }
        dao.upsertToutes(nouvelles)
        dao.insererMouvements(nouveauxMouvements)
        rafraichirWidget()
        return nouvelles.size
    }

    /**
     * "Écraser" (aussi utilisé pour l'import initial d'une base vide) :
     * vide boucles + mouvements puis réinsère le JSON tel quel.
     */
    suspend fun importerEcraser(boucles: List<Boucle>, mouvements: List<Mouvement>) {
        dao.supprimerTousMouvements()
        dao.supprimerToutesBoucles()
        dao.upsertToutes(boucles)
        dao.insererMouvements(mouvements)
        rafraichirWidget()
    }

    /** Statistiques pour le widget. */
    suspend fun compterActives(): Int = dao.compterActives()

    suspend fun prochainesEcheances(limite: Int): List<Boucle> =
        dao.prochainesEcheances(limite)

    private suspend fun rafraichirWidget() {
        BoucleWidget().updateAll(appContext)
    }
}
