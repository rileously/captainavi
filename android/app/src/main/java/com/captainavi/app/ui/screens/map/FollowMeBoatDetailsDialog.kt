package com.captainavi.app.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.captainavi.app.data.remote.FollowMePublicBoat
import com.captainavi.app.data.remote.FollowMePublicBoatProfile
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.ui.theme.MarineTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun FollowMeBoatDetailsDialog(
    boat: FollowMePublicBoat,
    profile: FollowMePublicBoatProfile?,
    isLoadingProfile: Boolean,
    profileError: String?,
    yourDistanceNm: Double?,
    onDial: (String) -> Unit,
    onMessage: (String) -> Unit,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MarineTheme.colors
    val now = System.currentTimeMillis()
    val isFresh = boat.updatedAtEpochMillis?.let { now - it <= FollowMePublicBoatsOverlay.STALE_AFTER_MS } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(boat.name, color = colors.accent)
                Text(
                    "Follow Me device #${boat.id} · ${if (isFresh) "LIVE" else "STALE"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isFresh) colors.success else colors.caution,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailCard {
                    DetailRow("Phone", when {
                        isLoadingProfile -> "Loading…"
                        profile?.phoneNumber != null -> profile.phoneNumber
                        else -> "Not published"
                    })
                    DetailRow("Vessel type", profile?.vesselType ?: if (isLoadingProfile) "Loading…" else "Not published")
                    DetailRow("Operator", profile?.operatorName ?: if (isLoadingProfile) "Loading…" else "Not published")
                    DetailRow("Current area", profile?.currentArea ?: if (isLoadingProfile) "Loading…" else "Not published")

                    if (isLoadingProfile) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }

                    profile?.phoneNumber?.let { phone ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onDial(phone) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.success),
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Call", color = colors.onAccent)
                            }
                            OutlinedButton(
                                onClick = { onMessage(phone) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, tint = colors.accent, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Message", color = colors.accent)
                            }
                        }
                    }

                    if (!profileError.isNullOrBlank()) {
                        Text(
                            profileError,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.caution,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }

                DetailCard {
                    DetailRow("Speed", String.format(Locale.US, "%.1f kt", boat.speedKnots))
                    DetailRow(
                        "Heading",
                        String.format(
                            Locale.US,
                            "%.0f° %s",
                            boat.headingDegrees,
                            NauticalMath.degreesToShortCardinal(boat.headingDegrees),
                        ),
                    )
                    yourDistanceNm?.let { distance ->
                        DetailRow("Your distance", formatNauticalDistance(distance))
                    }
                    if (yourDistanceNm == null) {
                        DetailRow(
                            "Follow Me reference distance",
                            formatNauticalDistance(boat.distanceMeters / METERS_PER_NAUTICAL_MILE),
                        )
                    }
                    DetailRow("Latitude", String.format(Locale.US, "%.6f", boat.latitude))
                    DetailRow("Longitude", String.format(Locale.US, "%.6f", boat.longitude))
                    DetailRow("Last position", formatFollowMeTime(boat.updatedAtEpochMillis))
                    DetailRow("Position age", formatPositionAge(boat.updatedAtEpochMillis, now))
                }

                Text(
                    "Position and public contact information are supplied by Follow Me. A phone number appears only when the vessel owner has published it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onNavigate,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Set course", color = colors.onAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.accent)
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    val colors = MarineTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        content = { content() },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = MarineTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Text(
            value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
    }
}

private fun formatNauticalDistance(distanceNm: Double): String = when {
    !distanceNm.isFinite() -> "—"
    distanceNm < 0.54 -> String.format(Locale.US, "%.0f m", distanceNm * METERS_PER_NAUTICAL_MILE)
    else -> String.format(Locale.US, "%.2f NM", distanceNm)
}

private fun formatFollowMeTime(epochMillis: Long?): String {
    if (epochMillis == null) return "Unknown"
    return FOLLOW_ME_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis)) + " MVT"
}

private fun formatPositionAge(epochMillis: Long?, nowMillis: Long): String {
    if (epochMillis == null) return "Unknown"
    val totalMinutes = ((nowMillis - epochMillis).coerceAtLeast(0L) / 60_000L)
    return when {
        totalMinutes < 1 -> "Just now"
        totalMinutes < 60 -> "$totalMinutes min ago"
        totalMinutes < 24 * 60 -> "${totalMinutes / 60} h ${totalMinutes % 60} min ago"
        else -> {
            val days = totalMinutes / (24 * 60)
            "$days ${if (days == 1L) "day" else "days"} ago"
        }
    }
}

private const val METERS_PER_NAUTICAL_MILE = 1852.0
private val FOLLOW_ME_TIME_FORMATTER = DateTimeFormatter
    .ofPattern("dd MMM yyyy, HH:mm", Locale.US)
    .withZone(ZoneId.of("Indian/Maldives"))

@Composable
internal fun FollowMeBoatPickerDialog(
    boats: List<FollowMePublicBoat>,
    onSelect: (FollowMePublicBoat) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MarineTheme.colors
    val now = System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Choose vessel", color = colors.accent)
                Text(
                    "${boats.size} Follow Me vessels are close together",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(boats, key = FollowMePublicBoat::id) { boat ->
                    val isFresh = boat.updatedAtEpochMillis?.let {
                        now - it <= FollowMePublicBoatsOverlay.STALE_AFTER_MS
                    } == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.background.copy(alpha = 0.76f), RoundedCornerShape(10.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .clickable { onSelect(boat) }
                            .padding(horizontal = 13.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                boat.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                "Device #${boat.id} · ${formatPositionAge(boat.updatedAtEpochMillis, now)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (isFresh) "LIVE" else "STALE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isFresh) colors.success else colors.caution,
                            )
                            Text(
                                String.format(Locale.US, "%.1f kt", boat.speedKnots),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.accent)
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(12.dp),
    )
}
