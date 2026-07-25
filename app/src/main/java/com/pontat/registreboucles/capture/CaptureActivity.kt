package com.pontat.registreboucles.capture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.pontat.registreboucles.RegistreApplication
import com.pontat.registreboucles.data.BoucleRepository
import com.pontat.registreboucles.ui.theme.Mnemosyne
import com.pontat.registreboucles.ui.theme.RegistreBouclesTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Porte d'entrée depuis les autres applications : partage (ACTION_SEND) et
 * sélection de texte (ACTION_PROCESS_TEXT).
 *
 * Contrat d'ergonomie : une feuille basse, un bouton, `finish()` — deux taps et
 * retour immédiat à l'application d'origine. L'app complète ne s'ouvre pas
 * (`excludeFromRecents`, `taskAffinity=""` côté manifeste : la capture ne laisse
 * pas de fenêtre dans les récents et ne s'empile pas sur la tâche du registre).
 *
 * Ce que cette activité ne fait PAS : lire le texte pour en déduire quoi que ce
 * soit. Pas de mots-clés, pas de date détectée, pas de boucle créée. Le contenu
 * est stocké tel quel ; l'analyse est faite hors de l'app et supervisée
 * (invariant I15).
 */
class CaptureActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // `referrer` doit être lu ICI, avant tout démarrage d'activité : il désigne
        // l'appelant, et une navigation ultérieure le remplacerait.
        val appSource = referrer?.host
        val extrait = texteEntrant(intent)
        val titre = intent?.getStringExtra(Intent.EXTRA_SUBJECT)

        if (extrait == null) {
            // Action inconnue, extra absent, texte vide : on le dit et on sort
            // proprement — jamais de plantage sur une entrée inattendue.
            Toast.makeText(this, "Rien à capturer depuis cette application.", Toast.LENGTH_SHORT)
                .show()
            finish()
            return
        }

        val repository = (application as RegistreApplication).repository

        // La feuille suit le thème choisi dans l'app : la capture est brève, mais
        // elle ne doit pas apparaître en clair par-dessus une app en sombre.
        val sombre = repository.lireModeSombre()

        setContent {
            RegistreBouclesTheme(darkTheme = sombre) {
                var visible by remember { mutableStateOf(true) }
                var enCours by remember { mutableStateOf(false) }
                val etatFeuille = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                if (visible) {
                    ModalBottomSheet(
                        onDismissRequest = { visible = false; finish() },
                        sheetState = etatFeuille
                    ) {
                        FeuilleCapture(
                            texte = extrait,
                            titre = titre,
                            appSource = appSource,
                            enCours = enCours,
                            onAnnuler = { visible = false; finish() },
                            onEnregistrer = {
                                enCours = true
                                enregistrer(repository, extrait, titre, appSource)
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Texte à capturer selon l'action, ou null s'il n'y a rien d'exploitable.
     * ACTION_SEND porte EXTRA_TEXT (String), ACTION_PROCESS_TEXT porte
     * EXTRA_PROCESS_TEXT (CharSequence) — deux extras différents, pas
     * interchangeables.
     */
    private fun texteEntrant(intent: Intent?): String? {
        val brut = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        return brut?.takeIf { it.isNotBlank() }
    }

    /**
     * Enregistre puis termine, en disant ce qui s'est passé. Un doublon n'est pas
     * une erreur : on informe de la capture d'origine et on sort de la même façon.
     */
    private fun enregistrer(
        repository: BoucleRepository,
        texte: String,
        titre: String?,
        appSource: String?
    ) {
        lifecycleScope.launch {
            val message = try {
                when (val res = repository.enregistrerCapture(texte, titre, appSource)) {
                    is BoucleRepository.ResultatCapture.Ajoutee ->
                        "Capturé dans la boîte de réception (${res.capture.id})."
                    is BoucleRepository.ResultatCapture.Doublon ->
                        "Déjà capturé le ${dateLisible(res.existante.capturee)}" +
                            (res.existante.appSource?.let { " depuis $it" } ?: "") + "."
                    is BoucleRepository.ResultatCapture.Refusee -> res.motif
                }
            } catch (e: Exception) {
                "Capture impossible : ${e.message ?: e.javaClass.simpleName}"
            }
            Toast.makeText(this@CaptureActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun dateLisible(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(millis))
}

@Composable
private fun FeuilleCapture(
    texte: String,
    titre: String?,
    appSource: String?,
    enCours: Boolean,
    onAnnuler: () -> Unit,
    onEnregistrer: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Capturer cette note",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Le texte est enregistré tel quel dans la boîte de réception. Rien " +
                "n'entre dans le registre sans ton accord.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        titre?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = Mnemosyne.couleurs.accent
            )
        }
        Text(
            texte,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
        )
        appSource?.let {
            Text(
                "Source : $it",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onAnnuler, enabled = !enCours) { Text("Annuler") }
            Button(
                onClick = onEnregistrer,
                enabled = !enCours,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (enCours) "Enregistrement…" else "Enregistrer") }
        }
    }
}
