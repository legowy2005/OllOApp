package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OlloCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF8CEEFF),
    secondary = OlloTeal,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004D5A),
    onSecondaryContainer = Color(0xFFA6EEFF),
    tertiary = OlloGreen,
    onTertiary = Color(0xFF003923),
    background = OlloDarkBg,
    onBackground = OlloTextPrimary,
    surface = OlloDarkSurface,
    onSurface = OlloTextPrimary,
    surfaceVariant = OlloDarkSurfaceCard,
    onSurfaceVariant = OlloTextSecondary,
    outline = OlloBorder,
    error = OlloRed,
    onError = Color(0xFF690005)
)

private val LightColorScheme = DarkColorScheme // Default dark theme by design for AR OLED companion

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default as per specification
    dynamicColor: Boolean = false, // Keep signature cyan/OLED aesthetic consistent
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
