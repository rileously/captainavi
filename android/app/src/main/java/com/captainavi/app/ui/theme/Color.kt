package com.captainavi.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// High-contrast marine palette. The surfaces are deliberately blue-black rather
// than pure black so cards remain easy to distinguish in harsh daylight.
val MarineDark = Color(0xFF06111F)
val MarineSurface = Color(0xFF0B1C2D)
val MarineCard = Color(0xFF12283B)
val MarineBorder = Color(0xFF2A455C)

val SafetyCyan = Color(0xFF36D7C4)
val SafetyAmber = Color(0xFFF6B94A)
val SafetyGreen = Color(0xFF4DD39D)
val EmergencyRed = Color(0xFFFF5D78)
val EmergencyRedDark = Color(0xFFC93655)

val TextPrimary = Color(0xFFF4F8FB)
val TextSecondary = Color(0xFFB7C7D3)
val TextMuted = Color(0xFF7890A2)

val ReefDangerRed = Color(0xFFE11D48)
val BreadcrumbPathColor = Color(0xFF2EC8D8)
val HomeMarkerGreen = Color(0xFF2BB673)
val DestinationTeal = Color(0xFF82B8FF)
val NavLineDashed = Color(0xCC82B8FF)
val MobOrange = Color(0xFFFF7A1A)

val NightBackground = Color(0xFF0A0000)
val NightSurface = Color(0xFF1A0505)
val NightCard = Color(0xFF2A0A0A)
val NightBorder = Color(0xFF4A1515)
val NightTextPrimary = Color(0xFFFF6B6B)
val NightTextSecondary = Color(0xFFC45C5C)
val NightTextMuted = Color(0xFF8A3A3A)
val NightAccent = Color(0xFFFF3B3B)

@Immutable
data class MarinePalette(
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val caution: Color,
    val success: Color,
    val emergency: Color,
    val emergencyDark: Color,
    val destination: Color,
    val home: Color,
    val mob: Color,
    val reef: Color,
    val isNight: Boolean
)

val DayMarinePalette = MarinePalette(
    background = MarineDark,
    surface = MarineSurface,
    card = MarineCard,
    border = MarineBorder,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    accent = SafetyCyan,
    onAccent = MarineDark,
    caution = SafetyAmber,
    success = SafetyGreen,
    emergency = EmergencyRed,
    emergencyDark = EmergencyRedDark,
    destination = DestinationTeal,
    home = HomeMarkerGreen,
    mob = MobOrange,
    reef = ReefDangerRed,
    isNight = false
)

val NightMarinePalette = MarinePalette(
    background = NightBackground,
    surface = NightSurface,
    card = NightCard,
    border = NightBorder,
    textPrimary = NightTextPrimary,
    textSecondary = NightTextSecondary,
    textMuted = NightTextMuted,
    accent = NightAccent,
    onAccent = NightBackground,
    caution = NightAccent,
    success = NightAccent,
    emergency = NightAccent,
    emergencyDark = Color(0xFF5C0000),
    destination = NightAccent,
    home = NightAccent,
    mob = NightAccent,
    reef = NightAccent,
    isNight = true
)

val LocalMarinePalette = staticCompositionLocalOf { DayMarinePalette }
