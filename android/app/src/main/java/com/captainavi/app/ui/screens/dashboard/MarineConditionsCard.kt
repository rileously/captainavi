package com.captainavi.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.captainavi.app.data.remote.MarineConditions
import com.captainavi.app.data.repository.MarineConditionsState
import com.captainavi.app.marine.marineConditionAdvisories
import com.captainavi.app.marine.weatherCodeLabel
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.safety.StormAlert
import com.captainavi.app.ui.components.StormAlertBanner
import com.captainavi.app.ui.theme.MarineTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MarineConditionsCard(
    state: MarineConditionsState,
    hasGpsFix: Boolean,
    nowMillis: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    stormAlert: StormAlert? = null,
) {
    val colors = MarineTheme.colors
    val conditions = state.conditions
    val stale = conditions != null && nowMillis - conditions.fetchedAtMillis > STALE_AFTER_MILLIS

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(colors.accent.copy(alpha = 0.13f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Waves, contentDescription = null, tint = colors.accent, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("MARINE CONDITIONS", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(
                        text = conditions?.let { weatherCodeLabel(it.weatherCode) } ?: "Forecast unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusText = when {
                    state.isLoading -> "UPDATING"
                    stale -> "CACHED"
                    conditions != null -> "MODEL"
                    else -> "OFFLINE"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stale || state.errorMessage != null) colors.caution else colors.success,
                )
                IconButton(onClick = onRefresh, enabled = hasGpsFix && !state.isLoading, modifier = Modifier.size(38.dp)) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh marine conditions", tint = colors.accent)
                    }
                }
            }
        }

        if (conditions == null) {
            Text(
                text = if (hasGpsFix) {
                    state.errorMessage ?: "Fetching forecast for the vessel position…"
                } else {
                    "Waiting for a GPS fix before requesting local sea conditions."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.errorMessage != null) colors.caution else colors.textSecondary,
            )
        } else {
            ConditionsGrid(conditions)
            stormAlert?.let { alert -> StormAlertBanner(alert) }
            val advisories = marineConditionAdvisories(conditions)
            if (advisories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.caution.copy(alpha = 0.13f), RoundedCornerShape(10.dp))
                        .border(1.dp, colors.caution.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.caution, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(advisories.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                }
            }
            state.errorMessage?.let { message ->
                Text("Refresh failed; showing the last saved forecast. $message", style = MaterialTheme.typography.bodySmall, color = colors.caution)
            }
            Text(
                text = "Open-Meteo model · ${formatAge(nowMillis - conditions.fetchedAtMillis)} · Advisory only; coastal accuracy is limited",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun ConditionsGrid(conditions: MarineConditions) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarineValue(Icons.Default.Waves, "WAVES", formatHeightDirection(conditions.waveHeightMeters, conditions.waveDirectionDegrees), Modifier.weight(1f))
            MarineValue(Icons.Default.Air, "WIND", formatSpeedDirection(conditions.windSpeedKnots, conditions.windDirectionDegrees), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarineValue(Icons.Default.Water, "SWELL", formatHeightPeriod(conditions.swellHeightMeters, conditions.swellPeriodSeconds), Modifier.weight(1f))
            MarineValue(Icons.Default.Speed, "CURRENT", formatSpeedDirection(conditions.oceanCurrentKnots, conditions.oceanCurrentDirectionDegrees), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarineValue(Icons.Default.Thermostat, "SEA / AIR", formatTemperatures(conditions), Modifier.weight(1f))
            MarineValue(Icons.Default.Visibility, "VISIBILITY", conditions.visibilityMeters?.let { String.format(Locale.US, "%.1f km", it / 1000.0) } ?: "--", Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarineValue(Icons.Default.Cloud, "GUST / RAIN", formatGustRain(conditions), Modifier.weight(1f))
            MarineValue(Icons.Default.Speed, "PRESSURE", conditions.pressureMslHpa?.let { "${it.roundToInt()} hPa" } ?: "--", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MarineValue(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MarineTheme.colors
    Row(
        modifier = modifier
            .background(colors.card, RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text(value, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
        }
    }
}

private fun formatHeightDirection(height: Double?, direction: Double?): String = when {
    height == null -> "--"
    direction == null -> String.format(Locale.US, "%.1f m", height)
    else -> String.format(Locale.US, "%.1f m · %s", height, NauticalMath.degreesToShortCardinal(direction))
}

private fun formatSpeedDirection(speed: Double?, direction: Double?): String = when {
    speed == null -> "--"
    direction == null -> String.format(Locale.US, "%.1f kt", speed)
    else -> String.format(Locale.US, "%.1f kt · %s", speed, NauticalMath.degreesToShortCardinal(direction))
}

private fun formatHeightPeriod(height: Double?, period: Double?): String = when {
    height == null -> "--"
    period == null -> String.format(Locale.US, "%.1f m", height)
    else -> String.format(Locale.US, "%.1f m · %.1f s", height, period)
}

private fun formatTemperatures(conditions: MarineConditions): String {
    val sea = conditions.seaSurfaceTemperatureCelsius?.let { String.format(Locale.US, "%.1f°", it) } ?: "--"
    val air = conditions.airTemperatureCelsius?.let { String.format(Locale.US, "%.1f°", it) } ?: "--"
    return "$sea / $air C"
}

private fun formatGustRain(conditions: MarineConditions): String {
    val gust = conditions.windGustKnots?.let { String.format(Locale.US, "%.1f kt", it) } ?: "--"
    val rain = conditions.precipitationMillimeters?.let { String.format(Locale.US, "%.1f mm", it) } ?: "--"
    return "$gust / $rain"
}

private fun formatAge(ageMillis: Long): String = when {
    ageMillis < 60_000L -> "updated now"
    ageMillis < 3_600_000L -> "updated ${ageMillis / 60_000L}m ago"
    else -> "updated ${ageMillis / 3_600_000L}h ago"
}

private const val STALE_AFTER_MILLIS = 60 * 60 * 1000L
