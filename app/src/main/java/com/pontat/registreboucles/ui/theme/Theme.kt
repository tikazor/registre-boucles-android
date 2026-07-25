package com.pontat.registreboucles.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Accents de la piste « Encre & Patine » sans équivalent direct dans les
 * emplacements Material 3. Contrairement à la charte v2, ils DIFFÈRENT entre
 * clair et sombre (patine foncée vs patine claire, retard #B3544A vs #D08379…) :
 * ils sont donc fournis par le thème, et non déclarés en constantes globales.
 */
@Immutable
data class CouleursMnemosyne(
    val barre: Color,          // fond de la barre de titre
    val surBarre: Color,       // texte et icônes de la barre
    val logoBarre: Color,      // marque dans la barre
    val accent: Color,         // patine : actions, échéance non urgente
    val accentDoux: Color,     // patine diluée : fonds de pastilles et pilules
    val retard: Color,         // échéance dépassée
    val bientot: Color,        // échéance ≤ 7 jours
    val texteDoux: Color,      // métadonnées, libellés secondaires
    val separateur: Color,     // filets internes aux cartes
    val fab: Color,
    val surFab: Color
)

private val CouleursClair = CouleursMnemosyne(
    barre = BarreClair,
    surBarre = Pierre,
    logoBarre = LogoBarreClair,
    accent = AccentClair,
    accentDoux = PatineClaire.copy(alpha = 0.16f),
    retard = RetardClair,
    bientot = BientotClair,
    texteDoux = TexteDouxClair,
    separateur = SeparateurClair,
    fab = FabClair,
    surFab = SurFabClair
)

private val CouleursSombre = CouleursMnemosyne(
    barre = BarreSombre,
    surBarre = Pierre,
    logoBarre = LogoBarreSombre,
    accent = AccentSombre,
    accentDoux = PatineClaire.copy(alpha = 0.14f),
    retard = RetardSombre,
    bientot = BientotSombre,
    texteDoux = TexteDouxSombre,
    separateur = SeparateurSombre,
    fab = FabSombre,
    surFab = SurFabSombre
)

private val LocalCouleurs = staticCompositionLocalOf { CouleursClair }

/** Accès aux accents de la marque : `Mnemosyne.couleurs.accent`. */
object Mnemosyne {
    val couleurs: CouleursMnemosyne
        @Composable @ReadOnlyComposable get() = LocalCouleurs.current
}

private val LightColors = lightColorScheme(
    primary = AccentClair,
    onPrimary = Color.White,
    secondary = AccentClair,
    onSecondary = Color.White,
    background = FondClair,
    onBackground = TexteClair,
    surface = SurfaceClair,
    onSurface = TexteClair,
    surfaceVariant = Surface2Clair,
    onSurfaceVariant = TexteDouxClair,
    outline = LigneClair,
    outlineVariant = LigneClair,
    error = RetardClair,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = AccentSombre,
    onPrimary = Encre,
    secondary = AccentSombre,
    onSecondary = Encre,
    background = FondSombre,
    onBackground = TexteSombre,
    surface = SurfaceSombre,
    onSurface = TexteSombre,
    surfaceVariant = Surface2Sombre,
    onSurfaceVariant = TexteDouxSombre,
    outline = LigneSombre,
    outlineVariant = LigneSombre,
    error = RetardSombre,
    onError = Encre
)

/**
 * Thème piloté par un choix explicite (bascule du menu), pas par le système.
 * Piste graphique « 1a — Encre & Patine » : la barre reprend l'Encre du fond de
 * l'icône, les actions passent en patine, les échéances proches en bronze.
 */
@Composable
fun RegistreBouclesTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalCouleurs provides if (darkTheme) CouleursSombre else CouleursClair
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
