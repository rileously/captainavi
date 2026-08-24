package com.captainavi.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.data.local.entity.WaypointType
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.ui.components.marineTextFieldColors
import com.captainavi.app.ui.theme.MarineTheme
import java.util.Locale

@Composable
fun NavigateToDialog(
    waypoints: List<WaypointEntity>,
    currentLat: Double,
    currentLon: Double,
    onSelectDestination: (WaypointEntity) -> Unit,
    onNavigateToCoords: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MarineTheme.colors
    var manualLatText by remember { mutableStateOf("") }
    var manualLonText by remember { mutableStateOf("") }

    val latVal = manualLatText.toDoubleOrNull()
    val lonVal = manualLonText.toDoubleOrNull()
    val isCoordValid = latVal != null && lonVal != null && latVal in -90.0..90.0 && lonVal in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
        containerColor = colors.background,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = colors.destination,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Navigate to",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.destination
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose a saved mark or enter coordinates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                if (waypoints.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved waypoints yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(waypoints, key = { it.id }) { waypoint ->
                            WaypointDestinationItem(
                                waypoint = waypoint,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                onClick = {
                                    onSelectDestination(waypoint)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.border)
                )

                Text(
                    text = "Manual coordinates",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualLatText,
                        onValueChange = { manualLatText = it },
                        label = { Text("Lat") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = marineTextFieldColors()
                    )

                    OutlinedTextField(
                        value = manualLonText,
                        onValueChange = { manualLonText = it },
                        label = { Text("Lon") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = marineTextFieldColors()
                    )

                    Button(
                        onClick = {
                            if (isCoordValid && latVal != null && lonVal != null) {
                                onNavigateToCoords(latVal, lonVal)
                                onDismiss()
                            }
                        },
                        enabled = isCoordValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.destination,
                            disabledContainerColor = colors.card,
                            contentColor = colors.onAccent,
                            disabledContentColor = colors.textMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Go", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun WaypointDestinationItem(
    waypoint: WaypointEntity,
    currentLat: Double,
    currentLon: Double,
    onClick: () -> Unit
) {
    val colors = MarineTheme.colors
    val (typeColor, typeIcon) = when (waypoint.type) {
        WaypointType.HOME -> colors.home to Icons.Default.Home
        WaypointType.HARBOUR -> colors.accent to Icons.Default.Anchor
        WaypointType.FISHING_SPOT -> colors.caution to Icons.Default.Place
        WaypointType.DANGER_REEF -> colors.emergency to Icons.Default.Warning
    }

    val hasGps = currentLat != 0.0 && currentLon != 0.0
    val distanceNm = if (hasGps) {
        NauticalMath.distanceNauticalMiles(currentLat, currentLon, waypoint.latitude, waypoint.longitude)
    } else 0.0

    val bearingDeg = if (hasGps) {
        NauticalMath.bearingDegrees(currentLat, currentLon, waypoint.latitude, waypoint.longitude).toInt()
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(typeColor.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, typeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = waypoint.type.name,
                        tint = typeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = waypoint.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${waypoint.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase(Locale.US) }} · ${String.format(Locale.US, "%.4f, %.4f", waypoint.latitude, waypoint.longitude)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", distanceNm)} NM",
                    style = MaterialTheme.typography.titleSmall,
                    color = typeColor
                )
                Text(
                    text = "BRG $bearingDeg°",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}
