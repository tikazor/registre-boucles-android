package com.pontat.registreboucles.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontat.registreboucles.data.ListeOptions
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.theme.Marine
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    vm: BoucleViewModel,
    onRetour: () -> Unit
) {
    val context = LocalContext.current
    val depart = remember { vm.options.value }
    val types = remember { mutableStateListOf(*depart.types.toTypedArray()) }
    val tiers = remember { mutableStateListOf(*depart.tiers.toTypedArray()) }

    // Sortie du dernier backup vers un emplacement choisi (Drive, stockage…).
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exporterDernierBackup(uri) { erreur ->
                Toast.makeText(
                    context,
                    erreur ?: "Backup exporté",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun persister() {
        vm.majOptions(
            ListeOptions(
                types = types.map { it.trim() }.filter { it.isNotBlank() },
                tiers = tiers.map { it.trim() }.filter { it.isNotBlank() }
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marine,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Valeurs proposées dans les listes à choix unique du formulaire.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Section("Type", types, ::persister)
            HorizontalDivider()
            Section("Tiers", tiers, ::persister)
            HorizontalDivider()

            // Sauvegarde locale versionnée.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sauvegarde", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Un backup complet (boucles + journaux) est créé automatiquement à " +
                        "chaque clôture. Les 10 plus récents sont conservés, dans le stockage " +
                        "de l'app (aucune permission requise).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    vm.sauvegarderManuel { nom ->
                        Toast.makeText(
                            context,
                            if (nom != null) "Sauvegarde créée : $nom" else "Échec de la sauvegarde",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text("Sauvegarder maintenant")
                }
                Text(
                    "Les backups internes disparaissent à la désinstallation. Exporte " +
                        "le dernier vers un emplacement durable (Drive, stockage partagé…).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    val horodatage = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                    exportBackupLauncher.launch("boucles-backup-$horodatage.json")
                }) {
                    Text("Exporter le dernier backup")
                }
            }
        }
    }
}

@Composable
private fun Section(
    titre: String,
    valeurs: MutableList<String>,
    onChangement: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        valeurs.forEachIndexed { index, valeur ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = valeur,
                    onValueChange = { valeurs[index] = it; onChangement() },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { valeurs.removeAt(index); onChangement() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                }
            }
        }

        TextButton(onClick = { valeurs.add("") }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  Ajouter une valeur")
        }
    }
}
