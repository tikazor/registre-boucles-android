package com.pontat.registreboucles.ui.screens

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pontat.registreboucles.data.ConflitSync
import com.pontat.registreboucles.data.EvenementSync
import com.pontat.registreboucles.data.MotifConflit
import com.pontat.registreboucles.data.ResultatSync
import com.pontat.registreboucles.data.formaterAvance
import com.pontat.registreboucles.data.nomFichierEtat
import com.pontat.registreboucles.data.resultatTypé
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.components.ListeDiffs
import com.pontat.registreboucles.ui.components.OptionChoix
import com.pontat.registreboucles.ui.theme.Mnemosyne
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Synchronisation manuelle entre appareils, par dossier partagé. Aucun réseau :
 * l'application lit et écrit des fichiers dans un dossier que l'application de
 * synchronisation de l'utilisateur (Nextcloud, Syncthing, Drive) réplique.
 *
 * Cet appareil n'écrit QUE son propre `etat-<CODE>.json` (invariant I11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(vm: BoucleViewModel, onRetour: () -> Unit) {
    val code by vm.codeAppareil.collectAsStateWithLifecycle()
    val nomDossier by vm.nomDossierSync.collectAsStateWithLifecycle()
    val dossier by vm.dossierSync.collectAsStateWithLifecycle()
    val enCours by vm.syncEnCours.collectAsStateWithLifecycle()
    val rapport by vm.rapportSync.collectAsStateWithLifecycle()
    val erreur by vm.erreurSync.collectAsStateWithLifecycle()
    val evenements by vm.evenementsSync.collectAsStateWithLifecycle()

    val contexte = LocalContext.current
    val choisirDossier = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Permission PERSISTANTE : sans elle, l'accès au dossier serait perdu
            // au redémarrage de l'application et il faudrait le rechoisir.
            try {
                contexte.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.e("SyncScreen", "Permission persistante refusée sur le dossier", e)
            }
            vm.definirDossierSync(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synchronisation") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Mnemosyne.couleurs.barre,
                    titleContentColor = Mnemosyne.couleurs.surBarre,
                    navigationIconContentColor = Mnemosyne.couleurs.surBarre
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Dossier partagé et identité ──
            item {
                Bloc("Dossier partagé") {
                    Text(
                        nomDossier ?: "Aucun dossier choisi.",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Un dossier répliqué par ton application de synchronisation. " +
                            "L'application n'accède jamais au réseau : elle lit et écrit " +
                            "seulement des fichiers.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            choisirDossier.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (dossier == null) "Choisir le dossier" else "Changer de dossier") }
                }
            }

            item {
                Bloc("Cet appareil") {
                    Text(
                        "Code : ${code ?: "—"}",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Fichier écrit : ${code?.let(::nomFichierEtat) ?: "—"}. Cet appareil " +
                            "n'écrit jamais le fichier d'un autre.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dernier = evenements.firstOrNull()
                    Text(
                        dernier?.let { "Dernière synchronisation : ${formaterHorodatage(it.horodatage)}" }
                            ?: "Aucune synchronisation à ce jour.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Action ──
            item {
                Button(
                    onClick = { vm.synchroniser() },
                    enabled = !enCours && dossier != null && code != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (enCours) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Synchroniser maintenant")
                    }
                }
            }

            // ── Compte rendu de la dernière exécution ──
            rapport?.let { r ->
                item {
                    Bloc("Compte rendu") {
                        if (r.evenements.isEmpty()) {
                            Text(
                                "Aucun fichier d'un autre appareil dans le dossier. " +
                                    "Notre état vient d'y être déposé : lance la synchronisation " +
                                    "sur l'autre appareil pour terminer l'appairage.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        r.evenements.forEach { LigneEvenement(it) }
                    }
                }
            }

            // ── Conflits à arbitrer ──
            val conflits = rapport?.conflits ?: emptyList()
            if (conflits.isNotEmpty()) {
                item {
                    Text(
                        "${conflits.size} boucle(s) à arbitrer",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = Mnemosyne.couleurs.bientot
                    )
                }
                items(conflits, key = { it.boucleId }) { conflit ->
                    CarteConflitSync(
                        conflit = conflit,
                        onGarderLocal = { vm.arbitrerGarderLocal(conflit) },
                        onPrendreDistant = { vm.arbitrerPrendreDistant(conflit) },
                        onSupprimerIci = { vm.arbitrerSupprimerIci(conflit) }
                    )
                }
            }

            // ── Historique, consultable et non modifiable ──
            item {
                Text(
                    "Historique des synchronisations",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (evenements.isEmpty()) {
                item {
                    Text(
                        "Vide pour l'instant.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(evenements, key = { it.evenementId }) { e ->
                    Bloc(null) {
                        Text(
                            formaterHorodatage(e.horodatage) + " · " + e.fichierLu,
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LigneEvenement(e)
                    }
                }
            }
        }
    }

    // Garde-fou d'horloge : la fusion a été écartée, on demande explicitement.
    val avance = rapport?.avanceHorloge
    if (avance != null) {
        AlertDialog(
            onDismissRequest = { vm.effacerRapportSync() },
            title = { Text("Horloge suspecte") },
            text = {
                Text(
                    "L'appareil « ${rapport?.appareilEnAvance} » déclare un export en avance de " +
                        "${formaterAvance(avance)} sur l'heure de cet appareil.\n\n" +
                        "Or l'arbitrage « le plus récent gagne » repose sur ces dates : une " +
                        "horloge fausse peut faire écraser une version pourtant plus récente. " +
                        "Vérifie l'heure des deux appareils avant de continuer."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.synchroniser(confirmeHorloge = true) }) {
                    Text("Fusionner quand même")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.effacerRapportSync() }) { Text("Annuler") }
            }
        )
    }

    erreur?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.effacerErreurSync() },
            title = { Text("Synchronisation impossible") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.effacerErreurSync() }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun Bloc(titre: String?, contenu: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (titre != null) {
            Text(
                titre,
                fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        contenu()
    }
}

@Composable
private fun LigneEvenement(e: EvenementSync) {
    val couleur = when (e.resultatTypé()) {
        ResultatSync.SUCCES -> Mnemosyne.couleurs.accent
        ResultatSync.CONFLITS -> Mnemosyne.couleurs.bientot
        ResultatSync.ECHEC -> Mnemosyne.couleurs.retard
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            e.resultatTypé()?.libelle() ?: e.resultat,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = couleur
        )
        Text(
            e.detail,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Un conflit et son arbitrage. Le diff réutilise le composant partagé écrit pour
 * le mode « Fusionner » (AND-03) : une seule représentation du diff dans l'app.
 */
@Composable
private fun CarteConflitSync(
    conflit: ConflitSync,
    onGarderLocal: () -> Unit,
    onPrendreDistant: () -> Unit,
    onSupprimerIci: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, Mnemosyne.couleurs.bientot.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            conflit.boucleId,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        when (conflit.motif) {
            MotifConflit.HORODATAGE_TROP_PROCHE -> {
                Text(
                    "Modifiée ici et sur « ${conflit.appareilDistant} » à moins d'une minute " +
                        "d'écart : rien ne permet de désigner la bonne version.",
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ListeDiffs(conflit.diffs, libelleExistant = "ici", libelleEntrant = conflit.appareilDistant)
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionChoix("Garder le local", false, Modifier.weight(1f), onGarderLocal)
                    OptionChoix("Prendre le distant", false, Modifier.weight(1f), onPrendreDistant)
                }
            }
            MotifConflit.SUPPRIMEE_A_DISTANCE -> {
                Text(
                    "Supprimée sur « ${conflit.appareilDistant} », encore présente ici. Rien " +
                        "n'a été effacé : supprimer cette boucle emporterait aussi ses " +
                        "mouvements et ses journaux de clôture.",
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionChoix("Garder ici", false, Modifier.weight(1f), onGarderLocal)
                    OptionChoix("Supprimer ici aussi", false, Modifier.weight(1f), onSupprimerIci)
                }
            }
        }
    }
}

private fun formaterHorodatage(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(millis))
