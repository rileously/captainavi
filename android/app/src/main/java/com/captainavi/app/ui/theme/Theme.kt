package com.captainavi.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

object NightModeState {
    var isNightMode by mutableStateOf(false)
}

object MarineTheme {
    val colors: MarinePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalMarinePalette.current
}

private val MarineDarkColorScheme = darkColorScheme(
    primary = SafetyCyan,
    onPrimary = MarineDark,
    primaryContainer = MarineCard,
    onPrimaryContainer = SafetyCyan,
    secondary = SafetyAmber,
    onSecondary = MarineDark,
    tertiary = SafetyGreen,
    error = EmergencyRed,
    background = MarineDark,
    onBackground = TextPrimary,
    surface = MarineSurface,
    onSurface = TextPrimary,
    surfaceVariant = MarineCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = MarineSurface,
    surfaceContainerHigh = MarineCard,
    outline = MarineBorder,
    outlineVariant = MarineBorder.copy(alpha = 0.55f)
)

private val NightColorScheme = darkColorScheme(
    primary = NightAccent,
    onPrimary = NightBackground,
    primaryContainer = NightCard,
    onPrimaryContainer = NightAccent,
    secondary = NightAccent,
    onSecondary = NightBackground,
    tertiary = NightAccent,
    error = NightAccent,
    background = NightBackground,
    onBackground = NightTextPrimary,
    surface = NightSurface,
    onSurface = NightTextPrimary,
    surfaceVariant = NightCard,
    onSurfaceVariant = NightTextSecondary,
    outline = NightBorder
)

private val MarineShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun CaptainAviTheme(
    content: @Composable () -> Unit
) {
    val night = NightModeState.isNightMode
    val palette = if (night) NightMarinePalette else DayMarinePalette
    val colorScheme = if (night) NightColorScheme else MarineDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalMarinePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MarineTypography,
            shapes = MarineShapes,
            content = content
        )
    }
}
