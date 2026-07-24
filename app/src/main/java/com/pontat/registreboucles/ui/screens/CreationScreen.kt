package com.pontat.registreboucles.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Milieu
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.formaterDate

/**
 * Formulaire de création OU d'édition, présenté dans une bottom sheet.
 * Si [boucleAModifier] est non-null, le formulaire est pré-rempli et la
 * validation met à jour la boucle (les champs hors formulaire — statut,
 * blocage, défaut, dates — sont préservés côté ViewModel). Sinon, création
 * d'une nouvelle boucle (flux inchangé). [onFerme] referme la sheet.
 * Type / Tiers / Milieu sont des listes à choix unique configurables.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationForm(
    vm: BoucleViewModel,
    onFerme: () -> Unit,
    boucleAModifier: Boucle? = null,
    afficherEnregistrerEnTete: Boolean = false,
    onEnregistre: (() -> Unit)? = null
) {
    val options by vm.options.collectAsStateWithLifecycle()
    val enEdition = boucleAModifier != null
    val cle = boucleAModifier?.id

    var titre by remember(cle) { mutableStateOf(boucleAModifier?.titre ?: "") }
    var type by remember(cle) { mutableStateOf(boucleAModifier?.type ?: "") }
    var origine by remember(cle) { mutableStateOf(boucleAModifier?.origine ?: "") }
    var preuveAttendue by remember(cle) { mutableStateOf(boucleAModifier?.preuveAttendue ?: "") }
    var impact by remember(cle) { mutableStateOf(boucleAModifier?.impact ?: "") }
    var tiers by remember(cle) { mutableStateOf(boucleAModifier?.tiers ?: "") }
    var milieu by remember(cle) { mutableStateOf(boucleAModifier?.let { Milieu.depuis(it.milieu) }) }
    var echeance by remember(cle) { mutableStateOf(boucleAModifier?.echeance) }
    var datePickerOuvert by remember { mutableStateOf(false) }

    val complet = titre.isNotBlank() && type.isNotBlank() &&
        origine.isNotBlank() && preuveAttendue.isNotBlank() && impact.isNotBlank()

    // Action de soumission partagée entre le bouton du bas et l'icône d'en-tête.
    val soumettre: () -> Unit = {
        if (enEdition) {
            vm.modifier(
                id = boucleAModifier!!.id,
                titre = titre.trim(),
                type = type.trim(),
                origine = origine.trim(),
                preuveAttendue = preuveAttendue.trim(),
                impact = impact.trim(),
                echeance = echeance,
                tiers = tiers.ifBlank { null },
                milieu = milieu?.name,
                // onEnregistre ne se déclenche QUE sur une sauvegarde réussie
                // (pas sur une fermeture/annulation) — utilisé par « Amender ».
                onFait = { onEnregistre?.invoke(); onFerme() }
            )
        } else {
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
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp, 0.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (enEdition) "Modifier la boucle" else "Nouvelle boucle",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            // En ouverture partielle, le bouton du bas est hors écran : on
            // propose un raccourci « enregistrer » dans l'en-tête (retiré une
            // fois la sheet pleinement ouverte).
            if (afficherEnregistrerEnTete) {
                IconButton(onClick = soumettre, enabled = complet) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = if (enEdition) "Enregistrer" else "Créer la boucle",
                        tint = if (complet) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
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
            onClick = soumettre,
            enabled = complet,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (enEdition) "Enregistrer" else "Créer la boucle")
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
