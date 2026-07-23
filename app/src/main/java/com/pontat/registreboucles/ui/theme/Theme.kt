package com.pontat.registreboucles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Brown,
    onPrimary = BlancCasse,
    secondary = Sage,
    onSecondary = BlancCasse,
    background = FillCream,
    onBackground = TexteFonce,
    surface = BlancCasse,
    onSurface = TexteFonce,
    surfaceVariant = FillCream,
    onSurfaceVariant = TexteFonce,
    outline = BorderBeige,
    outlineVariant = BorderBeige
)

private val DarkColors = darkColorScheme(
    primary = Brown,
    onPrimary = BlancCasse,
    secondary = Sage,
    onSecondary = BlancCasse,
    background = TexteFonce,
    onBackground = FillCream,
    surface = Color(0xFF2A211B),
    onSurface = FillCream,
    outline = Brown
)

@Composable
fun RegistreBouclesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Design figé : palette statique de la charte, pas de couleurs dynamiques
    // dans l'app (le Material You dynamique est réservé au widget, cf. étape 5).
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
