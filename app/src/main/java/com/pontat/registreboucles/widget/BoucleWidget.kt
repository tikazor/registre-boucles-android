package com.pontat.registreboucles.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import com.pontat.registreboucles.MainActivity
import com.pontat.registreboucles.data.AppDatabase
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.ui.theme.EncreClair
import com.pontat.registreboucles.ui.theme.FondClair
import com.pontat.registreboucles.ui.theme.Marine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val widgetDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE)

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
        // Material You (couleurs système) si Android 12+, sinon palette charte 02 figée.
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
            if (prochaines.isEmpty()) {
                Text(text = "Aucune échéance", style = TextStyle(color = texte))
            } else {
                prochaines.forEach { b ->
                    Text(
                        text = "• ${formaterEcheance(b.echeance)}  ${b.titre}",
                        style = TextStyle(color = texte),
                        maxLines = 1
                    )
                }
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
