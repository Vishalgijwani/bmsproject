package com.rhythm.app.ui.theme

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

private val Dusk = Color(0xFF4C4C9D)
private val DuskLight = Color(0xFF9A96E8)
private val Noon = Color(0xFFE4933A)
private val Ink = Color(0xFF1B2138)
private val Paper = Color(0xFFF5F6FA)

private val LightColors = lightColorScheme(
    primary = Dusk,
    secondary = Color(0xFF6C63C7),
    tertiary = Noon,
    background = Paper,
    surface = Paper,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF6B7391),
    surfaceVariant = Color(0xFFDFE3EE)
)

private val DarkColors = darkColorScheme(
    primary = DuskLight,
    secondary = Color(0xFFB6B1F2),
    tertiary = Color(0xFFF0AE63),
    background = Color(0xFF12162A),
    surface = Color(0xFF1A2038),
    onBackground = Color(0xFFE4E6F0),
    onSurface = Color(0xFFE4E6F0),
    onSurfaceVariant = Color(0xFF9AA2BF),
    surfaceVariant = Color(0xFF2A3150)
)

@Composable
fun RhythmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
