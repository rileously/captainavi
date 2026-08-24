package com.captainavi.app.ui.screens.waypoints

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.data.local.entity.WaypointType
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.service.DestinationState
import com.captainavi.app.service.MarineLocationService
import com.captainavi.app.ui.components.ScreenHeader
import com.captainavi.app.ui.theme.MarineTheme
import kotlinx.coroutines.launch

@Composable
fun WaypointsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()

    val telemetry by MarineLocationService.telemetry.collectAsState()
    val waypoints by app.waypointRepository.getAllWaypoints().collectAsState(initial = emptyList())
    val destination by DestinationState.destination.collectAsState()
    val colors = MarineTheme.colors
    var showAddDialog by remember { mutableStateOf(false) }
    var sortByNearest by remember { mutableStateOf(false) }
    val sortedWaypoints = remember(waypoints, sortByNearest, telemetry.hasGpsFix, telemetry.latitude, telemetry.longitude) {
        if (sortByNearest && telemetry.hasGpsFix) {
            waypoints.sortedBy { NauticalMath.distanceNauticalMiles(telemetry.latitude, telemetry.longitude, it.latitude, it.longitude) }
        } else {
            waypoints
        }
    }

    if (showAddDialog) {
        AddWaypointDialog(
            initialLat = telemetry.latitude,
            initialLon = telemetry.longitude,
            onSave = { name, type, lat, lon, desc ->
                scope.launch {
                    app.waypointRepository.addWaypoint(
                        name = name,
                        type = type,
                        latitude = lat,
                        longitude = lon,
                        description = desc
                    )
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScreenHeader(
                title = "Saved marks",
                trailing = "${waypoints.size}"
            )

            // Locked destination banner
            destination?.let { dest ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, colors.accent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Navigating",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Navigating to",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent
                            )
                            Text(
                                text = dest.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { DestinationState.clearDestination() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, "Clear destination", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (waypoints.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No marks yet. Tap + to save a fishing mark, harbour, or reef.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SortChip(
                        label = "Name",
                        icon = Icons.Default.SortByAlpha,
                        selected = !sortByNearest,
                        onClick = { sortByNearest = false },
                        modifier = Modifier.weight(1f),
                    )
                    SortChip(
                        label = "Nearest",
                        icon = Icons.Default.NearMe,
                        selected = sortByNearest,
                        enabled = telemetry.hasGpsFix,
                        onClick = { sortByNearest = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedWaypoints) { waypoint ->
                        WaypointCard(
                            waypoint = waypoint,
                            boatLat = telemetry.latitude,
                            boatLon = telemetry.longitude,
                            isDestination = destination?.id == waypoint.id,
                            onToggleNavigate = {
                                if (destination?.id == waypoint.id) {
                                    DestinationState.clearDestination()
                                } else {
                                    DestinationState.lockDestination(waypoint)
                                }
                            },
                            onDelete = {
                                scope.launch { app.waypointRepository.deleteWaypoint(waypoint) }
                            }
                        )
                    }
                }
            }
        }

        // Add Mark Floating Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Add Waypoint")
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MarineTheme.colors
    val background = if (selected) colors.accent.copy(alpha = 0.16f) else colors.card
    val border = if (selected) colors.accent else colors.border
    val contentColor = when {
        !enabled -> colors.textMuted.copy(alpha = 0.5f)
        selected -> colors.accent
        else -> colors.textSecondary
    }
    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(10.dp))
            .border(1.dp, border.copy(alpha = if (selected) 1f else 0.6f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

@Composable
fun WaypointCard(
    waypoint: WaypointEntity,
    boatLat: Double,
    boatLon: Double,
    isDestination: Boolean,
    onToggleNavigate: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MarineTheme.colors
    val (typeColor, typeLabel) = when (waypoint.type) {
        WaypointType.HOME -> colors.home to "Home"
        WaypointType.HARBOUR -> colors.accent to "Harbour"
        WaypointType.FISHING_SPOT -> colors.caution to "Fishing mark"
        WaypointType.DANGER_REEF -> colors.reef to "Danger reef"
    }

    val distanceNm = if (boatLat != 0.0) {
        NauticalMath.distanceNauticalMiles(boatLat, boatLon, waypoint.latitude, waypoint.longitude)
    } else 0.0

    val bearing = if (boatLat != 0.0) {
        NauticalMath.bearingDegrees(boatLat, boatLon, waypoint.latitude, waypoint.longitude).toInt()
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(typeColor, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = waypoint.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$typeLabel · ${"%.4f".format(waypoint.latitude)}, ${"%.4f".format(waypoint.longitude)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
                if (waypoint.description.isNotBlank()) {
                    Text(
                        text = waypoint.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }

            // Distance & Bearing calculation
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${"%.1f".format(distanceNm)} NM",
                    style = MaterialTheme.typography.titleMedium,
                    color = typeColor
                )
                Text(
                    text = "Bearing $bearing°",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleNavigate,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            if (isDestination) "Unlock destination" else "Navigate here",
                            tint = if (isDestination) colors.accent else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (waypoint.type != WaypointType.HOME) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                    }
                }
            }
        }
    }
}
