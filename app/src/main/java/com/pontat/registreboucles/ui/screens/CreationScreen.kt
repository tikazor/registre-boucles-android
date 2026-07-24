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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pontat.registreboucles.data.Milieu
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.formaterDate

/**
 * Formulaire de création, présenté dans une bottom sheet. [onFerme] est appelé
 * après validation (la sheet se referme). Type / Tiers / Milieu sont des listes
 * à choix unique dont les valeurs viennent de la configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationForm(
    vm: BoucleViewModel,
    onFerme: () -> Unit
) {
    val options by vm.options.collectAsStateWithLifecycle()

    var titre by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var origine by remember { mutableStateOf("") }
    var preuveAttendue by remember { mutableStateOf("") }
    var impact by remember { mutableStateOf("") }
    var tiers by remember { mutableStateOf("") }
    var milieu by remember { mutableStateOf<Milieu?>(null) }
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

        Champ("Titre *", titre, singleLine = true) { titre = it }

        // Listes à choix unique (valeurs configurables).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChampListe("Type *", type, options.types, Modifier.weight(1f)) { type = it }
            ChampListe("Tiers", tiers, options.tiers, Modifier.weight(1f), avecVide = true) { tiers = it }
        }
        ChampListe(
            "Milieu",
            milieu?.libelle ?: "",
            Milieu.entries.map { it.libelle },
            Modifier.fillMaxWidth(),
            avecVide = true
        ) { choisi -> milieu = Milieu.entries.firstOrNull { it.libelle == choisi } }

        Champ("Origine *", origine, singleLine = true) { origine = it }
        Champ("Preuve attendue *", preuveAttendue, singleLine = false, minLines = 2) { preuveAttendue = it }
        Champ("Impact *", impact, singleLine = false, minLines = 2) { impact = it }

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
                    tiers = tiers.ifBlank { null },
                    milieu = milieu?.name,
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

/** Liste déroulante à choix unique. [avecVide] ajoute une entrée « (aucun) ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChampListe(
    label: String,
    valeur: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    avecVide: Boolean = false,
    onChoisir: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val choix = options.filter { it.isNotBlank() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = valeur,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (avecVide) {
                DropdownMenuItem(
                    text = { Text("(aucun)") },
                    onClick = { onChoisir(""); expanded = false }
                )
            }
            choix.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onChoisir(opt); expanded = false }
                )
            }
        }
    }
}
