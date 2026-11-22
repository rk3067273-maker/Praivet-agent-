package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisDarkColorScheme = darkColorScheme(
    primary = CyanArc,
    onPrimary = VoidObsidian,
    primaryContainer = Color(0xFF0A3347),
    onPrimaryContainer = CyanArc,
    secondary = NeuralViolet,
    onSecondary = VoidObsidian,
    secondaryContainer = Color(0xFF251E47),
    onSecondaryContainer = NeuralViolet,
    tertiary = PlasmaTeal,
    onTertiary = VoidObsidian,
    background = VoidObsidian,
    onBackground = TextPrimary,
    surface = DeepSpace,
    onSurface = TextPrimary,
    surfaceVariant = GlassNavy,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = VoidObsidian,
    surfaceContainerLow = DeepSpace,
    surfaceContainer = GlassNavy,
    surfaceContainerHigh = GlassSurfaceElevated,
    outline = GlassBorder,
    outlineVariant = Color(0x2294A3B8),
    error = AlertCrimson,
    onError = Color.White
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = JarvisTypography,
        content = content
    )
}
