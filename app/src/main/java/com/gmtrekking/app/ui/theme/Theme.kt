package com.gmtrekking.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Schema colori unico, sempre chiaro: su richiesta esplicita, l'app usa un
// tema bianco indipendentemente dalla modalità scura del sistema.
private val AppColors = lightColorScheme(
    primary = TrekGreen,
    onPrimary = TrekWhite,
    primaryContainer = TrekGreenLight,
    onPrimaryContainer = TrekGreenDark,
    secondary = TrekGreenDark,
    onSecondary = TrekWhite,
    background = TrekWhite,
    onBackground = TrekOnBackground,
    surface = TrekWhite,
    onSurface = TrekOnBackground,
    surfaceVariant = TrekSurfaceVariant,
    onSurfaceVariant = TrekOnBackground,
    outline = TrekOutline,
    error = TrekRedAlert,
    onError = TrekWhite,
    tertiary = TrekAmber,
    onTertiary = TrekWhite,
)

/**
 * Tema dell'app. Sempre chiaro/bianco per scelta di prodotto (leggibilità
 * all'aperto, coerenza visiva), non segue la modalità scura del sistema.
 */
@Composable
fun GMTrekkingTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = GMTrekkingTypography,
        content = content,
    )
}
