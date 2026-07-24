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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.formaterDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationScreen(
    vm: BoucleViewModel,
    onRetour: () -> Unit,
    onCree: () -> Unit
) {
    var titre by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var origine by remember { mutableStateOf("") }
    var preuveAttendue by remember { mutableStateOf("") }
    var impact by remember { mutableStateOf("") }
    var tiers by remember { mutableStateOf("") }
    var echeance by remember { mutableStateOf<Long?>(null) }
    var datePickerOuvert by remember { mutableStateOf(false) }

    val champsObligatoiresRemplis = titre.isNotBlank() && type.isNotBlank() &&
        origine.isNotBlank() && preuveAttendue.isNotBlank() && impact.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle boucle") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.pontat.registreboucles.ui.theme.Marine,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Champ("Titre *", titre) { titre = it }
            Champ("Type *", type) { type = it }
            Champ("Origine *", origine) { origine = it }
            Champ("Preuve attendue *", preuveAttendue) { preuveAttendue = it }
            Champ("Impact *", impact) { impact = it }
            Champ("Tiers", tiers) { tiers = it }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Échéance : ${formaterDate(echeance)}", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { datePickerOuvert = true }) {
                    Text(if (echeance == null) "Choisir" else "Modifier")
                }
                if (echeance != null) {
                    TextButton(onClick = { echeance = null }) { Text("Effacer") }
                }
            }

            Button(
                onClick = {
                    vm.creer(
                        titre = titre.trim(),
                        type = type.trim(),
                        origine = origine.trim(),
                        preuveAttendue = preuveAttendue.trim(),
                        impact = impact.trim(),
                        echeance = echeance,
                        tiers = tiers.trim().ifBlank { null },
                        onCree = { onCree() }
                    )
                },
                enabled = champsObligatoiresRemplis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Créer la boucle")
            }

            Text(
                "* champs obligatoires",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (datePickerOuvert) {
        val state = rememberDatePickerState(initialSelectedDateMillis = echeance)
        DatePickerDialog(
            onDismissRequest = { datePickerOuvert = false },
            confirmButton = {
                TextButton(onClick = {
                    echeance = state.selectedDateMillis
                    datePickerOuvert = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOuvert = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun Champ(label: String, valeur: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = label != "Impact *" && label != "Preuve attendue *",
        modifier = Modifier.fillMaxWidth()
    )
}
