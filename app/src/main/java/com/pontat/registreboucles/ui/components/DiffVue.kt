package com.pontat.registreboucles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontat.registreboucles.data.DiffChamp
import com.pontat.registreboucles.ui.theme.Mnemosyne

/**
 * Affichage d'un diff champ par champ, écrit pour l'arbitrage du mode
 * « Fusionner » (AND-03) et réutilisé tel quel par l'arbitrage des conflits de
 * synchronisation (AND-08) : deux écrans, une seule représentation du diff.
 *
 * Les libellés sont paramétrables parce que les deux contextes ne nomment pas les
 * côtés de la même façon (« existant / entrant » à l'import, « ici / <CODE> » en
 * synchronisation), mais la mise en forme reste identique.
 */
@Composable
fun ListeDiffs(
    diffs: List<DiffChamp>,
    libelleExistant: String = "existant",
    libelleEntrant: String = "entrant"
) {
    diffs.forEach { d ->
        Column {
            Text(
                d.champ.uppercase(),
                fontSize = 9.5.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Text(
                "• $libelleExistant : ${d.existant ?: "—"}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "• $libelleEntrant : ${d.entrant ?: "—"}",
                fontSize = 12.sp, color = Mnemosyne.couleurs.accent
            )
        }
    }
}

/** Bouton de choix binaire d'un arbitrage (état actif = option retenue). */
@Composable
fun OptionChoix(label: String, actif: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fond = if (actif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val texte = if (actif) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .background(fond, RoundedCornerShape(11.dp))
            .then(
                if (actif) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = texte)
    }
}
