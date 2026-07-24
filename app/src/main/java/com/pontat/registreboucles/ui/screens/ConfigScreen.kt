package com.pontat.registreboucles.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontat.registreboucles.data.ListeOptions
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.theme.Marine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    vm: BoucleViewModel,
    onRetour: () -> Unit
) {
    val depart = remember { vm.options.value }
    val types = remember { mutableStateListOf(*depart.types.toTypedArray()) }
    val tiers = remember { mutableStateListOf(*depart.tiers.toTypedArray()) }
    val milieux = remember { mutableStateListOf(*depart.milieux.toTypedArray()) }

    fun persister() {
        vm.majOptions(
            ListeOptions(
                types = types.map { it.trim() }.filter { it.isNotBlank() },
                tiers = tiers.map { it.trim() }.filter { it.isNotBlank() },
                milieux = milieux.map { it.trim() }.filter { it.isNotBlank() }
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration") },
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
                "Modifie les valeurs proposées dans les listes à choix unique du formulaire.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Section("Type", types, ::persister)
            HorizontalDivider()
            Section("Tiers", tiers, ::persister)
            HorizontalDivider()
            Section("Milieu", milieux, ::persister)
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
