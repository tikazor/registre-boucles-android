package com.pontat.registreboucles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.Milieu
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.formaterDate
import com.pontat.registreboucles.ui.theme.Alerte
import com.pontat.registreboucles.ui.theme.Marine
import com.pontat.registreboucles.ui.theme.Teal
import kotlinx.coroutines.launch

/**
 * File de supervision : les propositions IA (statut PROPOSEE). Rien n'entre dans
 * le registre actif sans une action explicite : Accepter / Amender / Rejeter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisionScreen(
    vm: BoucleViewModel,
    onRetour: () -> Unit
) {
    val propositions by vm.propositions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var amenderCible by remember { mutableStateOf<Boucle?>(null) }
    var rejeterCible by remember { mutableStateOf<Boucle?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supervision") },
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
        if (propositions.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Aucune proposition en attente.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(propositions, key = { it.id }) { p ->
                    CarteProposition(
                        proposition = p,
                        onAccepter = { vm.accepterProposition(p.id) },
                        onAmender = { amenderCible = p },
                        onRejeter = { rejeterCible = p }
                    )
                }
            }
        }
    }

    // Amender : formulaire d'édition ; l'acceptation suit la sauvegarde réussie.
    amenderCible?.let { cible ->
        ModalBottomSheet(
            onDismissRequest = { amenderCible = null },
            sheetState = sheetState
        ) {
            CreationForm(
                vm = vm,
                boucleAModifier = cible,
                onEnregistre = { vm.accepterProposition(cible.id) },
                onFerme = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) amenderCible = null
                    }
                }
            )
        }
    }

    // Rejeter : motif obligatoire (journal DECLARATION).
    rejeterCible?.let { cible ->
        DialogMotifRejet(
            onAnnuler = { rejeterCible = null },
            onValider = { motif ->
                vm.rejeterProposition(cible.id, motif)
                rejeterCible = null
            }
        )
    }
}

@Composable
private fun CarteProposition(
    proposition: Boucle,
    onAccepter: () -> Unit,
    onAmender: () -> Unit,
    onRejeter: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${proposition.id} · ${proposition.type}",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            EtiquetteIA()
        }
        Text(
            proposition.titre,
            fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LigneChamp("Origine", proposition.origine)
        LigneChamp("Milieu", Milieu.depuis(proposition.milieu)?.libelle ?: "—")
        LigneChamp("Preuve attendue", proposition.preuveAttendue)
        LigneChamp("Impact", proposition.impact)
        LigneChamp("Échéance", formaterDate(proposition.echeance))
        proposition.blocage?.let { LigneChamp("Blocage", it) }

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAccepter,
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White),
                modifier = Modifier.weight(1f)
            ) { Text("Accepter", fontSize = 12.5.sp) }
            OutlinedButton(
                onClick = onAmender,
                shape = RoundedCornerShape(19.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Amender", fontSize = 12.5.sp) }
            OutlinedButton(
                onClick = onRejeter,
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Alerte),
                modifier = Modifier.weight(1f)
            ) { Text("Rejeter", fontSize = 12.5.sp) }
        }
    }
}

@Composable
fun EtiquetteIA() {
    Box(
        Modifier
            .background(Marine, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text("IA", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun LigneChamp(label: String, valeur: String) {
    Column {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium
        )
        Text(valeur, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DialogMotifRejet(
    onAnnuler: () -> Unit,
    onValider: (motif: String) -> Unit
) {
    var motif by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Rejeter — motif requis") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Le rejet est tracé au journal (déclaration). Un motif est obligatoire.",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = motif,
                    onValueChange = { motif = it },
                    placeholder = { Text("Motif du rejet") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onValider(motif.trim()) }, enabled = motif.isNotBlank()) {
                Text("Rejeter", color = Alerte)
            }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}
