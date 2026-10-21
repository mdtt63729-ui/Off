package com.off.audioconverter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OffDarkColors = darkColorScheme(
    primary = Color(0xFFFFB59E),
    onPrimary = Color(0xFF561F0D),
    primaryContainer = Color(0xFF783820),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFD9C2B9),
    onSecondary = Color(0xFF3D2D28),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90919B),
    error = Color(0xFFFFB4AB)
)

@Composable
fun OffTheme(content: @Composable () -> Unit) {
    // v1 is intentionally dark-first; isSystemInDarkTheme is retained for future theme support.
    val ignoredSystemTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = OffDarkColors,
        content = content
    )
}
