package com.pontat.registreboucles.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pontat.registreboucles.MainActivity
import com.pontat.registreboucles.RegistreApplication
import com.pontat.registreboucles.data.AppDatabase
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.ui.theme.Blanc
import com.pontat.registreboucles.ui.theme.EncreClair
import com.pontat.registreboucles.ui.theme.FillCream
import com.pontat.registreboucles.ui.theme.FondClair
import com.pontat.registreboucles.ui.theme.Marine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clé de l'id de boucle transmis à l'action de clôture. */
val boucleIdParam = ActionParameters.Key<String>("boucleId")

private val widgetDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

class BoucleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.get(context).boucleDao()
        val actives = dao.compterActives()
        val prochaines = dao.prochainesEcheances(3)

        provideContent {
            Contenu(actives, prochaines)
        }
    }

    @Composable
    private fun Contenu(actives: Int, prochaines: List<Boucle>) {
        val context = LocalContext.current
        val dynamique = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val primary: ColorProvider =
            if (dynamique) ColorProvider(android.R.color.system_accent1_600) else ColorProvider(Marine)
        val fond: ColorProvider =
            if (dynamique) ColorProvider(android.R.color.system_neutral1_50) else ColorProvider(FondClair)
        val texte: ColorProvider =
            if (dynamique) ColorProvider(android.R.color.system_neutral1_900) else ColorProvider(EncreClair)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(fond)
                .padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            Text(
                text = "Registre des Boucles",
                style = TextStyle(color = primary, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "$actives active(s)",
                style = TextStyle(color = texte)
            )
            Spacer(GlanceModifier.height(8.dp))

            if (prochaines.isEmpty()) {
                Text(text = "Aucune échéance", style = TextStyle(color = texte))
            } else {
                prochaines.forEachIndexed { index, b ->
                    if (index > 0) Spacer(GlanceModifier.height(8.dp))
                    CarteEcheance(b)
                }
            }
        }
    }

    /** Carte crème (charte 02) : titre + échéance à gauche, bouton Clôturer à droite. */
    @Composable
    private fun CarteEcheance(b: Boucle) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(FillCream))
                .cornerRadius(12.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = b.titre,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Marine), fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "Échéance ${formaterEcheance(b.echeance)}",
                    style = TextStyle(color = ColorProvider(EncreClair))
                )
            }
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Marine))
                    .cornerRadius(8.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(
                        actionRunCallback<ClotureActionCallback>(
                            actionParametersOf(boucleIdParam to b.id)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Clôturer",
                    style = TextStyle(color = ColorProvider(Blanc), fontWeight = FontWeight.Medium)
                )
            }
        }
    }

    private fun formaterEcheance(epochMillis: Long?): String {
        if (epochMillis == null) return "—"
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(widgetDateFormat)
    }
}

/**
 * Clôture depuis le widget. Appelle EXACTEMENT la même méthode que le bouton
 * « Clôturer » de l'app (repository.cloturer) — un seul endroit sait clore une
 * boucle — puis rafraîchit ce widget pour montrer le nouvel état.
 */
class ClotureActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val id = parameters[boucleIdParam] ?: return
        val repository = (context.applicationContext as RegistreApplication).repository
        repository.cloturer(id)
        BoucleWidget().update(context, glanceId)
    }
}
