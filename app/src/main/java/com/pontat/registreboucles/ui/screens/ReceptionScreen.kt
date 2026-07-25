package com.pontat.registreboucles.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pontat.registreboucles.data.Capture
import com.pontat.registreboucles.data.StatutCapture
import com.pontat.registreboucles.data.extrait
import com.pontat.registreboucles.data.statutTypé
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.components.OptionChoix
import com.pontat.registreboucles.ui.theme.Mnemosyne
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Boîte de réception : les notes brutes capturées depuis d'autres applications,
 * **en amont du registre**. Rien n'en sort vers les boucles sans une action
 * explicite ici.
 *
 * Deux absences volontaires :
 * - **aucune suppression** n'est proposée : une capture est ignorée, jamais
 *   effacée (invariant I14) ;
 * - **aucune analyse** du contenu n'est affichée ni calculée — pas de mots-clés,
 *   pas d'échéance devinée. Le texte est montré tel quel, et non modifiable :
 *   c'est la matière première (invariant I15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceptionScreen(
    vm: BoucleViewModel,
    onRetour: () -> Unit,
    onCreerBoucle: () -> Unit
) {
    val captures by vm.captures.collectAsStateWithLifecycle()
    val filtre by vm.filtreCapture.collectAsStateWithLifecycle()
    val recherche by vm.rechercheCapture.collectAsStateWithLifecycle()
    val message by vm.messageCapture.collectAsStateWithLifecycle()

    var consultee by remember { mutableStateOf<Capture?>(null) }

    val exportLot = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.exporterLotAnalyse(uri) }

    val visibles = captures
        .filter { c -> filtre == null || c.statutTypé() == filtre }
        .filter { c ->
            recherche.isBlank() ||
                c.contenuBrut.contains(recherche, ignoreCase = true) ||
                c.titre?.contains(recherche, ignoreCase = true) == true
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boîte de réception") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Notes capturées depuis d'autres applications. Elles ne sont pas " +
                        "des boucles : rien n'entre dans le registre sans ton accord.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = recherche,
                    onValueChange = vm::setRechercheCapture,
                    singleLine = true,
                    label = { Text("Rechercher dans les notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Filtres par statut. « Toutes » = aucun filtre.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OptionChoix("Toutes", filtre == null, Modifier.weight(1f)) {
                        vm.setFiltreCapture(null)
                    }
                    StatutCapture.entries.forEach { s ->
                        OptionChoix(s.libelle(), filtre == s, Modifier.weight(1f)) {
                            vm.setFiltreCapture(s)
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { exportLot.launch(vm.nomFichierLot()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Exporter un lot d'analyse (captures brutes)") }
            }

            if (visibles.isEmpty()) {
                item {
                    Text(
                        if (captures.isEmpty())
                            "Aucune capture. Partage du texte depuis une autre application " +
                                "pour alimenter cette boîte."
                        else "Aucune capture ne correspond à ce filtre.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(visibles, key = { it.id }) { capture ->
                CarteCapture(
                    capture = capture,
                    onConsulter = { consultee = capture },
                    onIgnorer = { vm.ignorerCapture(capture.id) },
                    onReactiver = { vm.reactiverCapture(capture.id) },
                    onCreerBoucle = {
                        vm.demarrerCreationDepuisCapture(capture)
                        onCreerBoucle()
                    }
                )
            }
        }
    }

    // Consultation : texte intégral, non modifiable.
    consultee?.let { c ->
        AlertDialog(
            onDismissRequest = { consultee = null },
            title = { Text(c.id, fontSize = 13.sp) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    c.titre?.let {
                        Text(
                            it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = Mnemosyne.couleurs.accent,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(c.contenuBrut, fontSize = 13.sp)
                    Text(
                        "\nCapturée le ${dateHeure(c.capturee)} · ${c.appareil}" +
                            (c.appSource?.let { " · $it" } ?: "") +
                            (c.boucleLiee?.let { "\nBoucle produite : $it" } ?: ""),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { consultee = null }) { Text("Fermer") } }
        )
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.effacerMessageCapture() },
            title = { Text("Lot d'analyse") },
            text = { Text(m) },
            confirmButton = {
                TextButton(onClick = { vm.effacerMessageCapture() }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun CarteCapture(
    capture: Capture,
    onConsulter: () -> Unit,
    onIgnorer: () -> Unit,
    onReactiver: () -> Unit,
    onCreerBoucle: () -> Unit
) {
    val statut = capture.statutTypé()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                statut?.libelle() ?: capture.statut,
                fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                color = couleurStatutCapture(statut)
            )
            Text(
                dateHeure(capture.capturee),
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        capture.titre?.let {
            Text(
                it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(capture.extrait(), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            capture.appareil + (capture.appSource?.let { " · $it" } ?: "") +
                (capture.boucleLiee?.let { " · → $it" } ?: ""),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionChoix("Consulter", false, Modifier.weight(1f), onConsulter)
            when (statut) {
                StatutCapture.IGNOREE, StatutCapture.EXPORTEE ->
                    OptionChoix("Réactiver", false, Modifier.weight(1f), onReactiver)
                StatutCapture.BRUTE ->
                    OptionChoix("Ignorer", false, Modifier.weight(1f), onIgnorer)
                // TRAITEE ou statut inconnu : aucune transition proposée. Et jamais
                // de suppression, quel que soit le statut (I14).
                else -> Box(Modifier.weight(1f))
            }
        }
        if (statut != StatutCapture.TRAITEE) {
            OptionChoix("Créer une boucle", false, Modifier.fillMaxWidth(), onCreerBoucle)
        }
    }
}

@Composable
private fun couleurStatutCapture(statut: StatutCapture?) = when (statut) {
    StatutCapture.BRUTE -> Mnemosyne.couleurs.accent
    StatutCapture.EXPORTEE -> Mnemosyne.couleurs.bientot
    StatutCapture.TRAITEE -> MaterialTheme.colorScheme.onSurfaceVariant
    StatutCapture.IGNOREE -> MaterialTheme.colorScheme.onSurfaceVariant
    null -> Mnemosyne.couleurs.retard
}

private fun dateHeure(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(millis))
