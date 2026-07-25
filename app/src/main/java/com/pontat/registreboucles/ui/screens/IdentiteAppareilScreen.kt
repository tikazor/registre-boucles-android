package com.pontat.registreboucles.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pontat.registreboucles.R
import com.pontat.registreboucles.ui.BoucleViewModel

/**
 * Écran d'identité, affiché au premier lancement tant qu'aucun code d'appareil
 * n'a été choisi. Bloquant par nature : sans code, aucun identifiant ne peut
 * être émis, donc aucune boucle créée (invariant I9).
 *
 * Le code proposé par défaut est le préfixe dominant des identifiants déjà en
 * base : sur un appareil qui possède déjà des « B-### », on propose « B », ce
 * qui fait continuer la séquence existante sans rupture.
 */
@Composable
fun IdentiteAppareilScreen(vm: BoucleViewModel) {
    val suggere by vm.codeSuggere.collectAsStateWithLifecycle()
    var saisie by remember { mutableStateOf("") }
    var refuse by remember { mutableStateOf(false) }

    // La suggestion arrive après lecture de la base : on préremplit dès qu'elle est là.
    LaunchedEffect(suggere) {
        if (saisie.isBlank() && suggere != null) saisie = suggere!!
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mnemosyne_grand),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp).padding(bottom = 8.dp)
        )
        Text(
            text = "Identifier cet appareil",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Chaque appareil porte un code court qui préfixe les boucles qu'il " +
                "crée (par exemple « B » donne B-041). Deux appareils ne peuvent ainsi " +
                "jamais produire le même identifiant.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = saisie,
            onValueChange = {
                saisie = it.uppercase().take(4)
                refuse = false
            },
            singleLine = true,
            isError = refuse,
            label = { Text("Code appareil") },
            supportingText = {
                Text(
                    if (refuse) "1 à 4 lettres, sans chiffre ni tiret."
                    else "1 à 4 lettres majuscules."
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        suggere?.let {
            Text(
                text = "Cet appareil possède déjà des boucles « $it-… » : garder « $it » " +
                    "poursuit la séquence existante.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Button(
            onClick = { if (!vm.definirCodeAppareil(saisie)) refuse = true },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            Text("Enregistrer")
        }

        Text(
            text = "Ce code reste modifiable dans les Réglages, mais les boucles déjà " +
                "créées gardent leur préfixe.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
