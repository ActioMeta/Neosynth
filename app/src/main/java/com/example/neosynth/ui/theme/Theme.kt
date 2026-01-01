package com.example.neosynth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AccentPurpleLight,    // Púrpura más fuerte para legibilidad
    onPrimary = Color.White,        // Texto blanco sobre botón púrpura
    background = LightBackground,   // Fondo superior claro
    onBackground = Color.Black,     // Texto negro en la parte superior
    surface = LightSurface,         // Fondo del formulario (gris clarito)
    onSurface = Color.Black,        // Texto negro dentro del formulario
    outline = Color(0xFF79747E)     // Color para los bordes de los campos
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentPurple,         // Tu Purple80
    onPrimary = Color.Black,
    background = DeepNoir,
    onBackground = MutedWhite,
    surface = SurfaceGrey,
    onSurface = MutedWhite,
    outline = Color.Gray
)

@Composable
fun NeoSynth_androidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}