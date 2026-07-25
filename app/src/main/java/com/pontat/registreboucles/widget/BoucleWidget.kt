package com.pontat.registreboucles.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.pontat.registreboucles.data.derniereModification
import com.pontat.registreboucles.data.Boucle
import com.pontat.registreboucles.data.JournalType
import com.pontat.registreboucles.ui.couleurStatut
import com.pontat.registreboucles.ui.libelleStatut
import com.pontat.registreboucles.ui.theme.AccentClair
import com.pontat.registreboucles.ui.theme.AccentSombre
import com.pontat.registreboucles.ui.theme.BientotClair
import com.pontat.registreboucles.ui.theme.BientotSombre
import com.pontat.registreboucles.ui.theme.Pierre
import com.pontat.registreboucles.ui.theme.RetardClair
import com.pontat.registreboucles.ui.theme.RetardSombre
import com.pontat.registreboucles.ui.theme.StatutFermee
import com.pontat.registreboucles.ui.theme.TexteClair
import com.pontat.registreboucles.ui.theme.TexteDouxClair
import com.pontat.registreboucles.ui.theme.TexteDouxSombre
import com.pontat.registreboucles.ui.theme.TexteSombre
import com.pontat.registreboucles.ui.theme.FondClair
import com.pontat.registreboucles.ui.theme.FondSombre
import com.pontat.registreboucles.ui.theme.SurfaceClair
import com.pontat.registreboucles.ui.theme.SurfaceSombre
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

/** registreboucles://boucle/{id}?mvt=1 — ouvre l'app sur la boucle (dépliée) et,
 *  si mvt=1, ouvre directement le formulaire d'ajout de mouvement. */
private fun intentBoucle(context: Context, id: String, mouvement: Boolean): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("registreboucles://boucle/$id?mvt=${if (mouvement) 1 else 0}")
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

/** Palette du widget, alignée sur la piste « Encre & Patine » de l'application. */
private class PaletteWidget(sombre: Boolean) {
    // Couleurs brutes (le widget n'a pas accès au thème Compose de l'app).
    val accentC = if (sombre) AccentSombre else AccentClair
    val retardC = if (sombre) RetardSombre else RetardClair
    val bientotC = if (sombre) BientotSombre else BientotClair
    val neutreC = StatutFermee

    val fond = ColorProvider(if (sombre) FondSombre else FondClair)
    val surface = ColorProvider(if (sombre) SurfaceSombre else SurfaceClair)
    val onSurface = ColorProvider(if (sombre) TexteSombre else TexteClair)
    val primary = ColorProvider(accentC)
    val secondary = ColorProvider(if (sombre) TexteDouxSombre else TexteDouxClair)
}

class BoucleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.get(context).boucleDao()
        val actives = dao.compterActives()
        // Plus de 3 : la liste est désormais défilante.
        val boucles = dao.prochainesEcheances(30)
        val modifs = dao.dernieresModifsListe().associate { it.boucleId to it.derniere }
        val sombre = (context.applicationContext as RegistreApplication).repository.lireModeSombre()

        provideContent {
            Contenu(actives, boucles, modifs, sombre)
        }
    }

    @Composable
    private fun Contenu(
        actives: Int,
        boucles: List<Boucle>,
        modifs: Map<String, Long>,
        sombre: Boolean
    ) {
        val p = PaletteWidget(sombre)

        LazyColumn(modifier = GlanceModifier.fillMaxWidth().background(p.fond)) {
            item {
                Text(
                    text = "Register Mnemosyne — $actives active(s)",
                    style = TextStyle(color = p.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    modifier = GlanceModifier.padding(12.dp, 12.dp, 12.dp, 6.dp)
                )
            }
            items(boucles) { b ->
                Box(GlanceModifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    CarteBoucle(b, maxOf(b.derniereModification(), modifs[b.id] ?: 0L), p)
                }
            }
        }
    }

    /** Carte reproduisant l'accordéon replié de l'app, en plus grand. */
    @Composable
    private fun CarteBoucle(b: Boucle, modif: Long, p: PaletteWidget) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(p.surface)
                .cornerRadius(18.dp)
                .padding(18.dp)
                // Tap sur la carte : ouvre l'app avec cette boucle dépliée (détails).
                .clickable(actionStartActivity(intentBoucle(context, b.id, false)))
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${b.id} · ${b.type}",
                        style = TextStyle(color = p.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    )
                    Text(
                        text = b.titre,
                        maxLines = 3,
                        style = TextStyle(color = p.onSurface, fontWeight = FontWeight.Medium, fontSize = 17.sp)
                    )
                }
                Badge(b.statut)
            }

            Spacer(GlanceModifier.height(12.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "Modifié le ${formaterDate(modif)}",
                    maxLines = 1,
                    style = TextStyle(color = p.secondary, fontSize = 13.sp),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "Échéance ${formaterDate(b.echeance)}",
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(couleurEcheance(b.echeance, p)), fontSize = 13.sp)
                )
            }

            Spacer(GlanceModifier.height(14.dp))

            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                // + : ouvre l'app directement sur le formulaire d'ajout de mouvement.
                BoutonRond(
                    icone = R.drawable.ic_widget_add,
                    couleur = p.accentC,
                    action = actionStartActivity(intentBoucle(context, b.id, true))
                )
                Spacer(GlanceModifier.width(10.dp))
                // ✓ : clôture en place (même méthode que l'app).
                BoutonRond(
                    icone = R.drawable.ic_widget_check,
                    couleur = p.accentC,
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
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = libelleStatut(statut),
                style = TextStyle(color = ColorProvider(Pierre), fontWeight = FontWeight.Medium, fontSize = 12.sp)
            )
        }
    }

    @Composable
    private fun BoutonRond(icone: Int, couleur: Color, action: androidx.glance.action.Action) {
        Box(
            modifier = GlanceModifier
                .size(42.dp)
                .background(ColorProvider(couleur))
                .cornerRadius(21.dp)
                .clickable(action),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(icone),
                contentDescription = null,
                modifier = GlanceModifier.size(21.dp)
            )
        }
    }

    private fun couleurEcheance(echeanceMillis: Long?, p: PaletteWidget): Color {
        val j = echeanceMillis?.let { joursRestants(it) } ?: return p.neutreC
        return when {
            j < 0 -> p.retardC
            j <= 7 -> p.bientotC
            else -> p.neutreC
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

/** Clôture depuis le widget — même repository.cloturer que l'app, puis rafraîchit le widget. */
class ClotureActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val id = parameters[boucleIdParam] ?: return
        val repository = (context.applicationContext as RegistreApplication).repository
        // La clôture EXIGE un journal : depuis le widget, on consigne une déclaration par défaut.
        repository.cloturer(id, JournalType.DECLARATION, "Clôturé depuis le widget")
        BoucleWidget().update(context, glanceId)
    }
}
