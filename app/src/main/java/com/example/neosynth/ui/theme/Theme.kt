package com.example.neosynth.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.neosynth.data.preferences.AppColorPalette

// 1. Material You (Default M3 Color Scheme Fallbacks)
private val MaterialYouLightColorScheme = lightColorScheme(
    primary = AccentPurpleLight,
    onPrimary = Color.White,
    background = LightBackground,
    onBackground = Color.Black,
    surface = LightSurface,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0)
)

private val MaterialYouDarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = Color.Black,
    background = DeepNoir,
    onBackground = MutedWhite,
    surface = SurfaceGrey,
    onSurface = MutedWhite,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color.Gray,
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930)
)

// 2. NeoSynth (Soft Lavender & Lila Accent with Deep Dark Surface)
private val NeosynthDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),          // Soft Lavender / Lila Accent
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),        // Soft Lila Secondary
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    background = Color(0xFF0D0B14),       // Deep Elegant Dark
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF191624),          // Dark Lila Surface
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF282436),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF6B6578),
    surfaceContainerLow = Color(0xFF13101E),
    surfaceContainer = Color(0xFF1F1C2B),
    surfaceContainerHigh = Color(0xFF2B273A)
)

private val NeosynthLightColorScheme = lightColorScheme(
    primary = Color(0xFF7B2CBF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0E5FF),
    onPrimaryContainer = Color(0xFF240046),
    secondary = Color(0xFFA259FF),
    onSecondary = Color.White,
    tertiary = Color(0xFF5A189A),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF14131C),
    surface = Color(0xFFF2ECFF),
    onSurface = Color(0xFF14131C),
    surfaceContainerLow = Color(0xFFFAF6FF),
    surfaceContainer = Color(0xFFF0E6FF),
    surfaceContainerHigh = Color(0xFFE5D5FF)
)

// 3. Tokyo Night (Deep Navy Slate & Pastel Blue/Lavender)
private val TokyoNightDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7AA2F7),          // Tokyo Pastel Blue
    onPrimary = Color(0xFF0F141D),
    primaryContainer = Color(0xFF28344E),
    onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFFBB9AF7),        // Tokyo Soft Lavender
    onSecondary = Color(0xFF1D1430),
    secondaryContainer = Color(0xFF3B2E58),
    onSecondaryContainer = Color(0xFFE0AF68),
    tertiary = Color(0xFF7DCFFF),         // Tokyo Neon Cyan
    onTertiary = Color(0xFF0F2B38),
    tertiaryContainer = Color(0xFF1F4A5E),
    onTertiaryContainer = Color(0xFFB4F9F8),
    background = Color(0xFF1A1B26),       // Tokyo Deep Slate Navy
    onBackground = Color(0xFFC0CAF5),
    surface = Color(0xFF24283B),          // Tokyo Slate Card
    onSurface = Color(0xFFC0CAF5),
    surfaceVariant = Color(0xFF292E42),
    onSurfaceVariant = Color(0xFFA9B1D6),
    outline = Color(0xFF565F89),
    surfaceContainerLow = Color(0xFF1F2335),
    surfaceContainer = Color(0xFF24283B),
    surfaceContainerHigh = Color(0xFF2F354F)
)

private val TokyoNightLightColorScheme = lightColorScheme(
    primary = Color(0xFF3D59A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0D7EC),
    onPrimaryContainer = Color(0xFF0F1934),
    secondary = Color(0xFF7A5CCC),
    background = Color(0xFFE1E2E7),
    onBackground = Color(0xFF1F2335),
    surface = Color(0xFFD5D6DB),
    onSurface = Color(0xFF1F2335),
    surfaceContainerLow = Color(0xFFE8E9EE),
    surfaceContainer = Color(0xFFDFE0E5),
    surfaceContainerHigh = Color(0xFFD5D6DB)
)

@Composable
fun NeoSynth_androidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPalette: AppColorPalette = AppColorPalette.MATERIAL_YOU,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when (colorPalette) {
        AppColorPalette.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) MaterialYouDarkColorScheme else MaterialYouLightColorScheme
            }
        }
        AppColorPalette.NEOSYNTH -> {
            if (darkTheme) NeosynthDarkColorScheme else NeosynthLightColorScheme
        }
        AppColorPalette.TOKYO_NIGHT -> {
            if (darkTheme) TokyoNightDarkColorScheme else TokyoNightLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}