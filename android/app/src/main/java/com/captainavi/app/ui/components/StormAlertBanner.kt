package com.captainavi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.captainavi.app.safety.StormAlert
import com.captainavi.app.safety.StormAlertKind
import com.captainavi.app.safety.StormAlertSeverity
import com.captainavi.app.ui.theme.MarineTheme
import java.util.Locale

/**
 * Shared storm/high-wave alert banner used wherever marine conditions are shown
 * (Dashboard, Tides/Marine Data). Caution color + trending-up icon for a [StormAlertSeverity.WATCH]
 * (building toward the threshold); emergency color + warning icon once it's a
 * [StormAlertSeverity.WARNING] (already crossed).
 */
@Composable
fun StormAlertBanner(alert: StormAlert, modifier: Modifier = Modifier) {
    val colors = MarineTheme.colors
    val bannerColor = if (alert.severity == StormAlertSeverity.WARNING) colors.emergency else colors.caution
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bannerColor.copy(alpha = 0.13f), RoundedCornerShape(10.dp))
            .border(1.dp, bannerColor.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (alert.severity == StormAlertSeverity.WARNING) Icons.Default.Warning else Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            tint = bannerColor,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stormAlertSummary(alert), style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
    }
}

fun stormAlertSummary(alert: StormAlert): String {
    val unit = if (alert.kind == StormAlertKind.WIND_GUST) "kt" else "m"
    val subject = when (alert.kind) {
        StormAlertKind.WAVE -> "Waves"
        StormAlertKind.SWELL -> "Swell"
        StormAlertKind.WIND_GUST -> "Gusts"
    }
    val peakText = String.format(Locale.US, "%.1f", alert.peakValue)
    val timeText = alert.peakTimeIso?.substringAfter('T')?.take(5)
    val whenText = if (timeText != null) " around $timeText" else " now"

    return if (alert.severity == StormAlertSeverity.WATCH) {
        val startText = alert.startValue?.let { String.format(Locale.US, "%.1f", it) }
        val trend = if (startText != null) "$startText → $peakText" else "up to $peakText"
        "$subject building: $trend $unit$whenText — worth watching before you commit to a trip."
    } else {
        "$subject up to $peakText $unit$whenText — rough seas forecast, check conditions before heading out."
    }
}
