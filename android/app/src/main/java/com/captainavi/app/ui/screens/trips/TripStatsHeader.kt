package com.captainavi.app.ui.screens.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.captainavi.app.data.local.entity.TripEntity
import com.captainavi.app.data.local.entity.TripStatus
import com.captainavi.app.ui.theme.MarineTheme
import java.util.Calendar
import java.util.Locale

private const val RECENT_TRIP_COUNT = 8

/**
 * Aggregate stats + a small recent-distance trend for the trip logbook. Sits above the
 * flat trip list so the distance/speed/duration already recorded per trip adds up to
 * something at a glance instead of only being visible one card at a time.
 *
 * Stats are computed from completed trips only — the currently active trip's distance
 * is still accumulating and would understate a "last trip" bar.
 */
@Composable
fun TripStatsHeader(trips: List<TripEntity>, modifier: Modifier = Modifier) {
    val colors = MarineTheme.colors
    val completed = remember(trips) { trips.filter { it.status == TripStatus.COMPLETED } }
    if (completed.isEmpty()) return

    val totalDistanceNm = remember(completed) { completed.sumOf { it.totalDistanceNm } }
    val thisMonthDistanceNm = remember(completed) {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)
        completed.filter { trip ->
            val started = Calendar.getInstance().apply { timeInMillis = trip.startTime }
            started.get(Calendar.MONTH) == currentMonth && started.get(Calendar.YEAR) == currentYear
        }.sumOf { it.totalDistanceNm }
    }
    // Trips are queried newest-first; reverse the recent slice so the chart reads
    // chronologically left-to-right, like the trip list above it read top-to-bottom.
    val recentDistances = remember(completed) {
        completed.take(RECENT_TRIP_COUNT).reversed().map { it.totalDistanceNm }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("TRIPS", completed.size.toString(), Modifier.weight(1f))
            StatTile("TOTAL", String.format(Locale.US, "%.1f NM", totalDistanceNm), Modifier.weight(1f))
            StatTile("THIS MONTH", String.format(Locale.US, "%.1f NM", thisMonthDistanceNm), Modifier.weight(1f))
        }
        if (recentDistances.size >= 2) {
            Text(
                text = "RECENT TRIP DISTANCES",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
            TripDistanceSparkline(
                distances = recentDistances,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
            Text(
                text = String.format(Locale.US, "Last trip: %.1f NM", recentDistances.last()),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MarineTheme.colors
    Column(
        modifier = modifier
            .background(colors.card, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
    }
}

/**
 * A thin bar per trip, most recent on the right, rounded only at the top (the "data
 * end") and flush with the baseline at the bottom.
 */
@Composable
private fun TripDistanceSparkline(distances: List<Double>, modifier: Modifier = Modifier) {
    val accent = MarineTheme.colors.accent
    val maxValue = (distances.maxOrNull() ?: 0.0).coerceAtLeast(0.1)

    Canvas(modifier = modifier) {
        val barCount = distances.size
        val gapPx = 4.dp.toPx()
        val totalGap = gapPx * (barCount - 1).coerceAtLeast(0)
        val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(2f)
        val cornerRadius = 4.dp.toPx().coerceAtMost(barWidth / 2f)

        distances.forEachIndexed { index, value ->
            val barHeight = ((value / maxValue).toFloat() * size.height).coerceAtLeast(3f)
            val left = index * (barWidth + gapPx)
            val top = size.height - barHeight

            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, left + barWidth, size.height),
                        topLeft = CornerRadius(cornerRadius, cornerRadius),
                        topRight = CornerRadius(cornerRadius, cornerRadius),
                        bottomLeft = CornerRadius.Zero,
                        bottomRight = CornerRadius.Zero,
                    ),
                )
            }
            drawPath(path, color = accent)
        }
    }
}
