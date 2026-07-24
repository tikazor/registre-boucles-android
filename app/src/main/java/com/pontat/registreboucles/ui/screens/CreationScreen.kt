package com.pontat.registreboucles.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.formaterDate

/**
 * Contenu du formulaire de création, présenté dans une bottom sheet.
 * [onFerme] est appelé après validation (la sheet se referme).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationForm(
    vm: BoucleViewModel,
    onFerme: () -> Unit
) {
    var titre by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var origine by remember { mutableStateOf("") }
    var preuveAttendue by remember { mutableStateOf("") }
    var impact by remember { mutableStateOf("") }
    var tiers by remember { mutableStateOf("") }
    var echeance by remember { mutableStateOf<Long?>(null) }
    var datePickerOuvert by remember { mutableStateOf(false) }

    val complet = titre.isNotBlank() && type.isNotBlank() &&
        origine.isNotBlank() && preuveAttendue.isNotBlank() && impact.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp, 0.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // En-tête : titre + croix de fermeture.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Nouvelle boucle",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onFerme) {
                Icon(Icons.Filled.Close, contentDescription = "Fermer")
            }
        }

        // Titre (pleine largeur).
        Champ("Titre *", titre, singleLine = true) { titre = it }

        // Type + Tiers sur une même ligne (champs courts).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Champ("Type *", type, Modifier.weight(1f), singleLine = true) { type = it }
            Champ("Tiers", tiers, Modifier.weight(1f), singleLine = true) { tiers = it }
        }

        // Origine (pleine largeur).
        Champ("Origine *", origine, singleLine = true) { origine = it }

        // Preuve + Impact (textarea compactes, 2 lignes).
        Champ("Preuve attendue *", preuveAttendue, singleLine = false, minLines = 2) { preuveAttendue = it }
        Champ("Impact *", impact, singleLine = false, minLines = 2) { impact = it }

        // Échéance sur une seule ligne.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
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
                    onCree = { onFerme() }
                )
            },
            enabled = complet,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Créer la boucle")
        }

        Text(
            "* champs obligatoires",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun Champ(
    label: String,
    valeur: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier
    )
}
