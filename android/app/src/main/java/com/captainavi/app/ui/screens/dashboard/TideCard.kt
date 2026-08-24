package com.captainavi.app.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.captainavi.app.localization.LanguageManager
import com.captainavi.app.tides.TideExtreme
import com.captainavi.app.tides.TidePredictor
import com.captainavi.app.ui.theme.MarineTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun TideCard(
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    val minute = nowMillis / 60_000L
    val tide = remember(minute) { TidePredictor.snapshot(minute * 60_000L) }
    val timeFmt = remember {
        SimpleDateFormat("HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Indian/Maldives")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(10.dp))
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Waves, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    LanguageManager.tides,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent
                )
                Text(
                    LanguageManager.hanimaadhooNearNaivaadhoo,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (tide.rising) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (tide.rising) colors.success else colors.caution,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (tide.rising) LanguageManager.tideRising else LanguageManager.tideFalling,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (tide.rising) colors.success else colors.caution
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    formatHeight(tide.heightMslMeters),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
                Text(
                    LanguageManager.meanSeaLevel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
            TideSparkline(
                nowMillis = tide.epochMillis,
                modifier = Modifier
                    .width(132.dp)
                    .height(36.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TideExtremeCell(
                label = LanguageManager.highTide,
                extreme = tide.nextHigh,
                timeFmt = timeFmt,
                valueColor = colors.accent,
                modifier = Modifier.weight(1f)
            )
            TideExtremeCell(
                label = LanguageManager.lowTide,
                extreme = tide.nextLow,
                timeFmt = timeFmt,
                valueColor = colors.caution,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TideExtremeCell(
    label: String,
    extreme: TideExtreme?,
    timeFmt: SimpleDateFormat,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        if (extreme == null) {
            Text("--", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
        } else {
            Text(
                "${timeFmt.format(Date(extreme.epochMillis))}  ${formatHeight(extreme.heightMslMeters)}",
                style = MaterialTheme.typography.titleSmall,
                color = valueColor
            )
        }
    }
}

@Composable
private fun TideSparkline(nowMillis: Long, modifier: Modifier = Modifier) {
    val colors = MarineTheme.colors
    val lineColor = colors.accent
    val nowColor = colors.caution
    val samples = remember(nowMillis / 60_000L) {
        val n = 24
        val span = 12 * 3_600_000L
        List(n) { i ->
            val t = nowMillis + span * i / (n - 1)
            TidePredictor.heightMslMeters(t)
        }
    }
    Canvas(modifier) {
        val minH = samples.minOrNull() ?: 0.0
        val maxH = samples.maxOrNull() ?: 1.0
        val spanH = (maxH - minH).coerceAtLeast(0.15)
        fun xAt(i: Int) = size.width * i / (samples.lastIndex.coerceAtLeast(1)).toFloat()
        fun yAt(h: Double) = size.height - ((h - minH) / spanH).toFloat() * size.height

        val path = Path()
        samples.forEachIndexed { i, h ->
            val x = xAt(i)
            val y = yAt(h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
        drawCircle(nowColor, radius = 4.5f, center = Offset(xAt(0), yAt(samples.first())))
    }
}

private fun formatHeight(meters: Double): String {
    val sign = if (meters >= 0) "+" else ""
    return String.format(Locale.US, "%s%.2f m", sign, meters)
}
