package com.pontat.registreboucles.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pontat.registreboucles.MainActivity
import com.pontat.registreboucles.R
import com.pontat.registreboucles.RegistreApplication
import com.pontat.registreboucles.data.AppDatabase
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.ui.couleurStatut
import com.pontat.registreboucles.ui.libelleStatut
import com.pontat.registreboucles.ui.theme.Alerte
import com.pontat.registreboucles.ui.theme.Blanc
import com.pontat.registreboucles.ui.theme.BrandSombre
import com.pontat.registreboucles.ui.theme.EncreClair
import com.pontat.registreboucles.ui.theme.EncreSombre
import com.pontat.registreboucles.ui.theme.FondClair
import com.pontat.registreboucles.ui.theme.FondSombre
import com.pontat.registreboucles.ui.theme.Marine
import com.pontat.registreboucles.ui.theme.Neutre
import com.pontat.registreboucles.ui.theme.SurfaceClair
import com.pontat.registreboucles.ui.theme.SurfaceSombre
import com.pontat.registreboucles.ui.theme.Teal
import com.pontat.registreboucles.ui.theme.Warn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Clé de l'id de boucle transmis à l'action de clôture. */
val boucleIdParam = ActionParameters.Key<String>("boucleId")

private val widgetDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

/** Palette du widget alignée sur la charte de l'app (clair / sombre). */
private class PaletteWidget(sombre: Boolean) {
    val fond = ColorProvider(if (sombre) FondSombre else FondClair)
    val surface = ColorProvider(if (sombre) SurfaceSombre else SurfaceClair)
    val onSurface = ColorProvider(if (sombre) EncreSombre else EncreClair)
    val primary = ColorProvider(if (sombre) BrandSombre else Marine)
    val secondary = ColorProvider(Neutre)
}

class BoucleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.get(context).boucleDao()
        val actives = dao.compterActives()
        val prochaines = dao.prochainesEcheances(3)
        val modifs = dao.dernieresModifsListe().associate { it.boucleId to it.derniere }
        val sombre = (context.applicationContext as RegistreApplication).repository.lireModeSombre()

        provideContent {
            Contenu(actives, prochaines, modifs, sombre)
        }
    }

    @Composable
    private fun Contenu(
        actives: Int,
        prochaines: List<Boucle>,
        modifs: Map<String, Long>,
        sombre: Boolean
    ) {
        val context = LocalContext.current
        val p = PaletteWidget(sombre)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(p.fond)
                .padding(10.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            Text(
                text = "Registre des Boucles — $actives active(s)",
                style = TextStyle(color = p.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            )
            Spacer(GlanceModifier.height(8.dp))

            if (prochaines.isEmpty()) {
                Text(text = "Aucune échéance", style = TextStyle(color = p.secondary))
            } else {
                prochaines.forEachIndexed { index, b ->
                    if (index > 0) Spacer(GlanceModifier.height(10.dp))
                    CarteBoucle(b, modifs[b.id] ?: b.creee, p)
                }
            }
        }
    }

    /** Carte reproduisant l'accordéon replié de l'app. */
    @Composable
    private fun CarteBoucle(b: Boucle, modif: Long, p: PaletteWidget) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(p.surface)
                .cornerRadius(16.dp)
                .padding(14.dp)
        ) {
            // Ligne 1 : ID · TYPE + badge statut.
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${b.id} · ${b.type}",
                        style = TextStyle(color = p.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    )
                    Text(
                        text = b.titre,
                        maxLines = 2,
                        style = TextStyle(color = p.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    )
                }
                Badge(b.statut)
            }

            Spacer(GlanceModifier.height(10.dp))

            // Ligne méta : Modifié le / Échéance (colorée selon l'urgence).
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "Modifié le ${formaterDate(modif)}",
                    maxLines = 1,
                    style = TextStyle(color = p.secondary, fontSize = 12.sp),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "Échéance ${formaterDate(b.echeance)}",
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(couleurEcheance(b.echeance)), fontSize = 12.sp)
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            // Boutons d'action : + (ouvre l'app pour ajouter un mouvement) et ✓ (clôture).
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                BoutonRond(
                    icone = R.drawable.ic_widget_add,
                    couleur = Marine,
                    action = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))
                )
                Spacer(GlanceModifier.width(8.dp))
                BoutonRond(
                    icone = R.drawable.ic_widget_check,
                    couleur = Teal,
                    action = actionRunCallback<ClotureActionCallback>(
                        actionParametersOf(boucleIdParam to b.id)
                    )
                )
            }
        }
    }

    @Composable
    private fun Badge(statut: String) {
        Box(
            modifier = GlanceModifier
                .background(ColorProvider(couleurStatut(statut)))
                .cornerRadius(50.dp)
                .padding(horizontal = 13.dp, vertical = 5.dp)
        ) {
            Text(
                text = libelleStatut(statut),
                style = TextStyle(color = ColorProvider(Blanc), fontWeight = FontWeight.Medium, fontSize = 11.sp)
            )
        }
    }

    @Composable
    private fun BoutonRond(
        icone: Int,
        couleur: Color,
        action: androidx.glance.action.Action
    ) {
        Box(
            modifier = GlanceModifier
                .size(34.dp)
                .background(ColorProvider(couleur))
                .cornerRadius(17.dp)
                .clickable(action),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(icone),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp)
            )
        }
    }

    private fun couleurEcheance(echeanceMillis: Long?): Color {
        val j = echeanceMillis?.let { joursRestants(it) } ?: return Neutre
        return when {
            j < 0 -> Alerte
            j <= 7 -> Warn
            else -> Neutre
        }
    }

    private fun joursRestants(echeanceMillis: Long): Long {
        val ech = Instant.ofEpochMilli(echeanceMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(LocalDate.now(ZoneId.systemDefault()), ech)
    }

    private fun formaterDate(epochMillis: Long?): String {
        if (epochMillis == null) return "—"
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(widgetDateFormat)
    }
}

/**
 * Clôture depuis le widget — même méthode que le bouton « Clôturer » de l'app
 * (repository.cloturer), puis rafraîchit ce widget.
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
