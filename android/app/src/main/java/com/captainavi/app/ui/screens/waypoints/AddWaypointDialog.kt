package com.captainavi.app.ui.screens.waypoints

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.captainavi.app.data.local.entity.WaypointType
import com.captainavi.app.ui.components.marineTextFieldColors
import com.captainavi.app.ui.theme.MarineTheme

@Composable
fun AddWaypointDialog(
    initialLat: Double,
    initialLon: Double,
    onSave: (name: String, type: WaypointType, lat: Double, lon: Double, desc: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MarineTheme.colors
    var name by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf(if (initialLat != 0.0) initialLat.toString() else "4.1755") }
    var lonText by remember { mutableStateOf(if (initialLon != 0.0) initialLon.toString() else "73.5093") }
    var selectedType by remember { mutableStateOf(WaypointType.FISHING_SPOT) }
    var description by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .background(colors.background, RoundedCornerShape(16.dp))
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Save mark",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = marineTextFieldColors()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            WaypointType.FISHING_SPOT to "Fishing",
                            WaypointType.DANGER_REEF to "Reef",
                            WaypointType.HARBOUR to "Harbour",
                            WaypointType.HOME to "Home"
                        ).forEach { (type, label) ->
                            val isSelected = selectedType == type
                            Button(
                                onClick = { selectedType = type },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) colors.caution else colors.surface,
                                    contentColor = if (isSelected) colors.onAccent else colors.textSecondary
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(text = label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitude") },
                            modifier = Modifier.weight(1f),
                            colors = marineTextFieldColors()
                        )
                        OutlinedTextField(
                            value = lonText,
                            onValueChange = { lonText = it },
                            label = { Text("Longitude") },
                            modifier = Modifier.weight(1f),
                            colors = marineTextFieldColors()
                        )
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = marineTextFieldColors()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val lat = latText.toDoubleOrNull() ?: initialLat
                            val lon = lonText.toDoubleOrNull() ?: initialLon
                            val markName = if (name.isNotBlank()) name else "Fishing mark"
                            onSave(markName, selectedType, lat, lon, description)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save waypoint", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
