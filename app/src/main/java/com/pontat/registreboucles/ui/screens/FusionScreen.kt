package com.pontat.registreboucles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontat.registreboucles.data.ConflitFusion
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.theme.Marine
import com.pontat.registreboucles.ui.theme.Teal

/**
 * Arbitrage de la fusion : pour chaque boucle existante dont un champ scalaire
 * diverge, choix « Garder l'existant » (défaut) / « Prendre l'entrant ». Les
 * mouvements/journaux sont ajoutés (dédupliqués) et les nouvelles boucles créées
 * sans arbitrage. Choix par boucle, pas champ par champ (écran mobile).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FusionScreen(
    etat: BoucleViewModel.FusionState,
    onValider: (prendreEntrant: Set<String>) -> Unit,
    onAnnuler: () -> Unit
) {
    // id -> true = prendre l'entrant ; absent/false = garder l'existant.
    val choix = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fusionner") },
                navigationIcon = {
                    IconButton(onClick = onAnnuler) {
                        Icon(Icons.Filled.Close, contentDescription = "Annuler")
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "${etat.res.boucles.size} boucle(s) entrante(s). Mouvements et journaux " +
                        "seront ajoutés sans doublon ; rien n'est supprimé. Les boucles " +
                        "nouvelles sont créées avec leur statut d'origine.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (etat.conflits.isEmpty()) {
                item {
                    Text(
                        "Aucun conflit de champ à arbitrer.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        "${etat.conflits.size} boucle(s) existante(s) divergente(s) — choisis la version à conserver :",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(etat.conflits, key = { it.id }) { conflit ->
                    CarteConflit(
                        conflit = conflit,
                        prendreEntrant = choix[conflit.id] == true,
                        onChoix = { choix[conflit.id] = it }
                    )
                }
            }

            item {
                Button(
                    onClick = { onValider(choix.filterValues { it }.keys.toSet()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Valider la fusion") }
            }
        }
    }
}

@Composable
private fun CarteConflit(
    conflit: ConflitFusion,
    prendreEntrant: Boolean,
    onChoix: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            conflit.id,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        conflit.diffs.forEach { d ->
            Column {
                Text(
                    d.champ.uppercase(),
                    fontSize = 9.5.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
                Text("• existant : ${d.existant ?: "—"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("• entrant : ${d.entrant ?: "—"}", fontSize = 12.sp, color = Teal)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OptionChoix("Garder l'existant", !prendreEntrant, Modifier.weight(1f)) { onChoix(false) }
            OptionChoix("Prendre l'entrant", prendreEntrant, Modifier.weight(1f)) { onChoix(true) }
        }
    }
}

@Composable
private fun OptionChoix(label: String, actif: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fond = if (actif) Marine else MaterialTheme.colorScheme.surface
    val texte = if (actif) Color.White else MaterialTheme.colorScheme.primary
    Box(
        modifier
            .background(fond, RoundedCornerShape(11.dp))
            .then(if (actif) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = texte)
    }
}
