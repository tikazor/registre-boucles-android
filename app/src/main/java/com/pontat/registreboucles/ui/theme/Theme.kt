package com.pontat.registreboucles.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Marine,
    onPrimary = Blanc,
    secondary = Teal,
    onSecondary = Blanc,
    background = FondClair,
    onBackground = EncreClair,
    surface = SurfaceClair,
    onSurface = EncreClair,
    surfaceVariant = Surface2Clair,
    onSurfaceVariant = EncreClair,
    outline = LigneClair,
    outlineVariant = LigneClair,
    error = Alerte,
    onError = Blanc
)

private val DarkColors = darkColorScheme(
    primary = BrandSombre,
    onPrimary = MarineFonce,
    secondary = Teal,
    onSecondary = Blanc,
    background = FondSombre,
    onBackground = EncreSombre,
    surface = SurfaceSombre,
    onSurface = EncreSombre,
    surfaceVariant = Surface2Sombre,
    onSurfaceVariant = EncreSombre,
    outline = LigneSombre,
    outlineVariant = LigneSombre,
    error = Alerte,
    onError = Blanc
)

/**
 * Thème piloté par un choix explicite (toggle du menu), pas par le système —
 * conforme au prototype. En-tête marine et badges gardent des couleurs fixes ;
 * seuls fonds / surfaces / texte / filets basculent.
 */
@Composable
fun RegistreBouclesTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
