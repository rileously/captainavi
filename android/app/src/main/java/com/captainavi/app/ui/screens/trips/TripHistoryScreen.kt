package com.captainavi.app.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.local.entity.CatchLogEntity
import com.captainavi.app.data.local.entity.TripEntity
import com.captainavi.app.data.local.entity.TripStatus
import com.captainavi.app.data.repository.FishHabitat
import com.captainavi.app.data.repository.summarizeCatches
import com.captainavi.app.localization.LanguageManager
import com.captainavi.app.ui.components.ScreenHeader
import com.captainavi.app.ui.theme.MarineTheme
import com.captainavi.app.util.GpxExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripHistoryScreen(
    modifier: Modifier = Modifier,
    onLoadTrace: (String) -> Unit = {},
    onLogCatch: () -> Unit = {},
) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val trips by app.tripRepository.getAllTrips().collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            title = if (LanguageManager.isDhivehi) "ދަތުރުތަކުގެ ލޮގްބުކް" else "Logbook",
            trailing = "${trips.size}"
        )

        if (trips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (LanguageManager.isDhivehi) {
                        "އަދި އެއްވެސް ދަތުރެއް ރެކޯޑްކުރެވިފައެއް ނުވޭ.\nދަތުރު ފެށުމަށް ހެލްމް ބައްލަވާ."
                    } else {
                        "No trips yet.\nStart a fishing trip from the Helm to begin your logbook."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    TripStatsHeader(trips = trips)
                }
                items(trips, key = { it.id }) { trip ->
                    TripLogCard(
                        trip = trip,
                        onLoadTrace = onLoadTrace,
                        onLogCatch = onLogCatch,
                    )
                }
            }
        }
    }
}

@Composable
fun TripLogCard(
    trip: TripEntity,
    onLoadTrace: (String) -> Unit = {},
    onLogCatch: () -> Unit = {},
) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()
    val catches by app.catchLogRepository.getForTrip(trip.id).collectAsState(initial = emptyList())
    var expandedCatches by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val durationMinutes = if (trip.endTime != null) {
        ((trip.endTime - trip.startTime) / (60 * 1000L)).coerceAtLeast(1)
    } else 0
    val catchSummary = summarizeCatches(catches)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = if (trip.status == TripStatus.ACTIVE) colors.success else colors.home
                    Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (trip.status == TripStatus.ACTIVE) {
                            if (LanguageManager.isDhivehi) "ދަތުރު ކުރިއަށްދަނީ" else "Trip in progress"
                        } else {
                            if (LanguageManager.isDhivehi) "ދަތުރު ނިމިފައި" else "Completed"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
                Text(
                    text = dateFormat.format(Date(trip.startTime)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Distance", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(
                            text = "${"%.2f".format(trip.totalDistanceNm)} NM",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.accent
                        )
                    }
                    Column {
                        Text("Max speed", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(
                            text = "${"%.1f".format(trip.maxSpeedKnots)} kt",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.caution
                        )
                    }
                    Column {
                        Text("Duration", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(
                            text = if (durationMinutes > 60) "${durationMinutes / 60}h ${durationMinutes % 60}m" else "${durationMinutes}m",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                    }
                }
            }

            if (catchSummary.isNotBlank()) {
                Text(
                    text = catchSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.success,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { expandedCatches = !expandedCatches },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (expandedCatches) "Hide catches" else "Show catches (${catches.size})")
                }
            }

            if (expandedCatches && catches.isNotEmpty()) {
                CatchList(
                    catches = catches,
                    timeFormat = timeFormat,
                    onDelete = { id ->
                        scope.launch { app.catchLogRepository.delete(id) }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val breadcrumbs = app.database.breadcrumbDao().getBreadcrumbsForTripList(trip.id)
                            if (breadcrumbs.isEmpty()) {
                                Toast.makeText(context, "This trace has no recorded path points", Toast.LENGTH_LONG).show()
                            } else {
                                onLoadTrace(trip.id)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Load path")
                }

                if (trip.status == TripStatus.ACTIVE) {
                    IconButton(
                        onClick = onLogCatch,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SetMeal,
                            contentDescription = "Log catch",
                            tint = colors.success,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            val breadcrumbs = app.database.breadcrumbDao().getBreadcrumbsForTripList(trip.id)
                            GpxExporter.exportAndShareGpx(context, trip, breadcrumbs)
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = LanguageManager.shareGpx,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CatchList(
    catches: List<CatchLogEntity>,
    timeFormat: SimpleDateFormat,
    onDelete: (String) -> Unit,
) {
    val colors = MarineTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        catches.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val habitat = FishHabitat.fromId(entry.habitat)
                    Text(
                        "${entry.count} × ${entry.species}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    val detail = buildString {
                        append(habitat.label)
                        append(" · ")
                        append(timeFormat.format(Date(entry.timestamp)))
                        if (entry.notes.isNotBlank()) {
                            append(" · ")
                            append(entry.notes)
                        }
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (habitat) {
                            FishHabitat.OCEAN -> colors.accent
                            FishHabitat.REEF -> colors.success
                            FishHabitat.OTHER -> colors.textMuted
                        },
                    )
                }
                IconButton(onClick = { onDelete(entry.id) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete catch",
                        tint = colors.emergency,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
