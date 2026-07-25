package com.pontat.registreboucles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontat.registreboucles.data.Capture
import com.pontat.registreboucles.data.extrait
import com.pontat.registreboucles.ui.theme.Mnemosyne
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Section « Origine » d'une boucle : les notes brutes qui l'ont fait naître.
 *
 * C'est la fonction mémorielle du lot AND-09 — pouvoir remonter, des mois plus
 * tard, d'une boucle active à la note griffonnée un mardi soir. D'où l'accès au
 * **texte intégral** : un extrait ne suffit pas quand on cherche à se rappeler
 * pourquoi on s'était engagé.
 *
 * Les identifiants déclarés mais absents de cet appareil sont **affichés** et non
 * masqués : une trace incomplète reste une trace, et la cacher laisserait croire
 * que la boucle est née de rien.
 */
@Composable
fun SectionOrigine(
    captures: List<Capture>,
    idsAbsents: List<String>,
    modifier: Modifier = Modifier
) {
    if (captures.isEmpty() && idsAbsents.isEmpty()) return
    var consultee by remember { mutableStateOf<Capture?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "ORIGINE",
            fontSize = 9.5.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
        captures.forEach { c ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { consultee = c }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    c.extrait(90),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${dateHeure(c.capturee)} · ${c.appareil}" +
                        (c.appSource?.let { " · $it" } ?: "") + " · appuyer pour lire",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        idsAbsents.forEach { id ->
            Text(
                "$id — capture déclarée, absente de cet appareil",
                fontSize = 10.5.sp,
                color = Mnemosyne.couleurs.bientot
            )
        }
    }

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
                    // Texte intégral, non modifiable : c'est la matière première.
                    Text(c.contenuBrut, fontSize = 13.sp)
                    Text(
                        "\nCapturée le ${dateHeure(c.capturee)} · ${c.appareil}" +
                            (c.appSource?.let { " · $it" } ?: ""),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { consultee = null }) { Text("Fermer") } }
        )
    }
}

private fun dateHeure(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(millis))
