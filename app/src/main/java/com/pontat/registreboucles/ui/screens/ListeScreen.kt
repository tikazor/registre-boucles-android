package com.pontat.registreboucles.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.couleurStatut
import com.pontat.registreboucles.ui.formaterDate
import com.pontat.registreboucles.ui.libelleStatut
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Filtre local (aucun paramètre Room). "Ouverte" = statut != "fermee".
private enum class FiltreStatut(val libelle: String) {
    TOUTES("Toutes"),
    OUVERTES("Ouvertes"),
    FERMEES("Fermées")
}

private fun estOuverte(b: Boucle): Boolean = b.statut != "fermee"

/** Jours (calendaires, fuseau local) entre aujourd'hui et l'échéance. Négatif = en retard. */
private fun joursRestants(echeanceMillis: Long): Long {
    val ech = Instant.ofEpochMilli(echeanceMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(LocalDate.now(ZoneId.systemDefault()), ech)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeScreen(
    vm: BoucleViewModel,
    onOuvrirDetail: (String) -> Unit,
    onCreer: () -> Unit,
    onOuvrirDebug: () -> Unit
) {
    val boucles by vm.boucles.collectAsStateWithLifecycle()
    var filtre by remember { mutableStateOf(FiltreStatut.TOUTES) }

    val context = LocalContext.current
    val importEnAttente by vm.importEnAttente.collectAsStateWithLifecycle()
    val erreurImport by vm.erreurImport.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val contenu = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            vm.preparerImport(contenu ?: "")
        }
    }

    // Résumé calculé en mémoire depuis les données déjà chargées (pas de requête Room).
    val resume = remember(boucles) {
        val ouvertes = boucles.filter { estOuverte(it) }
        Resume(
            ouvertes = ouvertes.size,
            enRetard = ouvertes.count { it.echeance != null && joursRestants(it.echeance) < 0 },
            bientot = ouvertes.count { it.echeance != null && joursRestants(it.echeance) in 0..7 }
        )
    }

    // Liste filtrée localement (déjà triée par échéance côté DAO).
    val liste = remember(boucles, filtre) {
        when (filtre) {
            FiltreStatut.TOUTES -> boucles
            FiltreStatut.OUVERTES -> boucles.filter { estOuverte(it) }
            FiltreStatut.FERMEES -> boucles.filter { !estOuverte(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Registre des Boucles",
                        // Appui long 2s sur le titre → écran Debug caché.
                        modifier = Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val relache = withTimeoutOrNull(2000L) { waitForUpOrCancellation() }
                                if (relache == null) onOuvrirDebug()
                            }
                        }
                    )
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Importer un JSON")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreer,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Créer une boucle")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BandeauResume(resume)
            ChipsFiltre(filtre = filtre, onChange = { filtre = it })

            if (liste.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Aucune boucle dans ce filtre.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(liste, key = { it.id }) { boucle ->
                        BoucleCard(boucle = boucle, onClick = { onOuvrirDetail(boucle.id) })
                    }
                }
            }
        }
    }

    // Ré-import sur base déjà peuplée : choix Ajouter / Écraser avant écriture Room.
    val enAttente = importEnAttente
    val err = erreurImport
    if (enAttente != null) {
        AlertDialog(
            onDismissRequest = { vm.annulerImport() },
            title = { Text("Importer ${enAttente.boucles.size} boucle(s)") },
            text = {
                Text(
                    "La base contient déjà des données.\n\n" +
                        "• Ajouter : n'insère que les boucles absentes. Tes boucles " +
                        "actuelles, clôtures et mouvements ajoutés dans l'app sont conservés.\n\n" +
                        "• Écraser : vide tout (boucles + mouvements) et réimporte le fichier tel quel."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmerAjout() }) { Text("Ajouter") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.confirmerEcrasement() }) {
                        Text("Écraser", color = Color(0xFFB85042))
                    }
                    TextButton(onClick = { vm.annulerImport() }) { Text("Annuler") }
                }
            }
        )
    } else if (err != null) {
        AlertDialog(
            onDismissRequest = { vm.effacerErreurImport() },
            title = { Text("Import impossible") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { vm.effacerErreurImport() }) { Text("OK") }
            }
        )
    }
}

private data class Resume(val ouvertes: Int, val enRetard: Int, val bientot: Int)

@Composable
private fun BandeauResume(r: Resume) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCell("Ouvertes", r.ouvertes, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        StatCell("En retard", r.enRetard, Color(0xFFB85042), Modifier.weight(1f))
        StatCell("≤ 7 jours", r.bientot, Color(0xFFC98A3D), Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, valeur: Int, couleur: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = valeur.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = couleur
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipsFiltre(filtre: FiltreStatut, onChange: (FiltreStatut) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FiltreStatut.entries.forEach { f ->
            FilterChip(
                selected = filtre == f,
                onClick = { onChange(f) },
                label = { Text(f.libelle) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoucleCard(boucle: Boucle, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = boucle.titre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                BadgeStatut(boucle.statut)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${boucle.id} · ${boucle.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Échéance : ${formaterDate(boucle.echeance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BadgeStatut(statut: String) {
    Surface(
        color = couleurStatut(statut),
        shape = RoundedCornerShape(50),
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Text(
            text = libelleStatut(statut),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
