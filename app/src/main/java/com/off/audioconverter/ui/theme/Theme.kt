package com.off.audioconverter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ----------------------------------------------------------------------------
 * Off — premium Material 3 theme
 * A warm "amber/sunset" accent system on a deep neutral canvas, with generous
 * corner radii and a tuned type scale.
 * ------------------------------------------------------------------------- */

val OffAmber = Color(0xFFFFB59E)
val OffAmberDeep = Color(0xFF561F0D)

private val OffDarkColors = darkColorScheme(
    primary = OffAmber,
    onPrimary = Color(0xFF561F0D),
    primaryContainer = Color(0xFF783820),
    onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Color(0xFF9A4525),

    secondary = Color(0xFFE9C4A8),
    onSecondary = Color(0xFF442B1A),
    secondaryContainer = Color(0xFF5E412C),
    onSecondaryContainer = Color(0xFFFFDCC2),

    tertiary = Color(0xFFB9CC9B),
    onTertiary = Color(0xFF253513),
    tertiaryContainer = Color(0xFF3B4C27),
    onTertiaryContainer = Color(0xFFD5E8B5),

    background = Color(0xFF111318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF2A2C33),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceTint = OffAmber,
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191B21),
    surfaceContainer = Color(0xFF1D1F25),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),

    outline = Color(0xFF91919B),
    outlineVariant = Color(0xFF45464F),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF2F3036),
    scrim = Color(0xFF000000)
)

private val OffLightColors = lightColorScheme(
    primary = Color(0xFF9A4525),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF77574A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160B),
    tertiary = Color(0xFF54633C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7E8B7),
    onTertiaryContainer = Color(0xFF131F04),
    background = Color(0xFFFCF8F7),
    onBackground = Color(0xFF1D1B1A),
    surface = Color(0xFFFCF8F7),
    onSurface = Color(0xFF1D1B1A),
    surfaceVariant = Color(0xFFF4DED7),
    onSurfaceVariant = Color(0xFF52443E)
)

private val OffTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}

private val OffShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun OffTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) OffDarkColors else OffLightColors,
        typography = OffTypography,
        shapes = OffShapes,
        content = content
    )
}
