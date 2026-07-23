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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Mouvement
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.formaterDate
import com.pontat.registreboucles.ui.formaterDateHeure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: BoucleViewModel,
    boucleId: String,
    onRetour: () -> Unit
) {
    val boucle by vm.observerBoucle(boucleId).collectAsStateWithLifecycle(initialValue = null)
    val mouvements by vm.observerMouvements(boucleId).collectAsStateWithLifecycle(initialValue = emptyList())

    var dialogOuvert by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(boucle?.id ?: "Détail") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        val b = boucle
        if (b == null) {
            Text(
                "Boucle introuvable.",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(b.titre, style = MaterialTheme.typography.headlineSmall)
            BadgeStatut(b.statut)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Champ("Type", b.type)
                    Champ("Origine", b.origine)
                    Champ("Créée le", formaterDate(b.creee))
                    Champ("Échéance", formaterDate(b.echeance))
                    Champ("Tiers", b.tiers ?: "—")
                    Champ("Preuve attendue", b.preuveAttendue)
                    Champ("Blocage", b.blocage ?: "—")
                    Champ("Impact", b.impact)
                    Champ("Action par défaut", b.defaut ?: "—")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { dialogOuvert = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Ajouter un mouvement") }

                if (b.statut != "fermee") {
                    OutlinedButton(
                        onClick = { vm.cloturer(b.id) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Clôturer") }
                }
            }

            Text(
                "Historique",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (mouvements.isEmpty()) {
                Text("Aucun mouvement.", style = MaterialTheme.typography.bodyMedium)
            } else {
                mouvements.forEach { m -> MouvementItem(m) }
            }
        }
    }

    if (dialogOuvert) {
        DialogAjoutMouvement(
            onAnnuler = { dialogOuvert = false },
            onValider = { type, contenu ->
                vm.ajouterMouvement(boucleId, type, contenu)
                dialogOuvert = false
            }
        )
    }
}

@Composable
private fun Champ(label: String, valeur: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(valeur, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MouvementItem(m: Mouvement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    m.type.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    formaterDateHeure(m.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(m.contenu, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogAjoutMouvement(
    onAnnuler: () -> Unit,
    onValider: (type: String, contenu: String) -> Unit
) {
    val types = listOf("preuve", "declaration", "defaut")
    var type by remember { mutableStateOf(types.first()) }
    var contenu by remember { mutableStateOf("") }
    var menuOuvert by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Ajouter un mouvement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    OutlinedButton(onClick = { menuOuvert = true }) {
                        Text("Type : ${type.replaceFirstChar { it.uppercase() }}")
                    }
                    DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                        types.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.replaceFirstChar { it.uppercase() }) },
                                onClick = { type = t; menuOuvert = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = contenu,
                    onValueChange = { contenu = it },
                    label = { Text("Contenu") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onValider(type, contenu.trim()) },
                enabled = contenu.isNotBlank()
            ) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text("Annuler") }
        }
    )
}
