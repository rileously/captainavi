package com.captainavi.app.ui.screens.waypoints

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.captainavi.app.data.local.entity.WaypointType
import com.captainavi.app.ui.components.marineTextFieldColors
import com.captainavi.app.ui.screens.map.StreetTileSource
import com.captainavi.app.ui.theme.MarineTheme
import java.util.Locale
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

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
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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

                    WaypointLocationMap(
                        latitude = latText.toDoubleOrNull() ?: initialLat,
                        longitude = lonText.toDoubleOrNull() ?: initialLon,
                        onLocationPicked = { lat, lon ->
                            latText = String.format(Locale.US, "%.6f", lat)
                            lonText = String.format(Locale.US, "%.6f", lon)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                    )

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

/**
 * A small interactive preview so a saved mark's position is confirmed visually, not
 * just as raw numbers. Tapping the map drops the pin there and reports it back;
 * editing the lat/lon fields moves the marker without recentering the camera (so
 * typing digit-by-digit doesn't fling the view around).
 */
@Composable
private fun WaypointLocationMap(
    latitude: Double,
    longitude: Double,
    onLocationPicked: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val activeMap = mapView
        if (activeMap == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> activeMap.onResume()
                    Lifecycle.Event.ON_PAUSE -> activeMap.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                activeMap.onResume()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                activeMap.onPause()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setMultiTouchControls(true)
                    setBackgroundColor(AndroidColor.rgb(3, 31, 48))
                    isTilesScaledToDpi = true
                    setTileSource(StreetTileSource)
                    minZoomLevel = 3.0
                    maxZoomLevel = StreetTileSource.maximumZoomLevel.toDouble()
                    controller.setZoom(14.0)
                    controller.setCenter(GeoPoint(latitude, longitude))

                    val pin = Marker(this).apply {
                        position = GeoPoint(latitude, longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "New mark"
                    }
                    overlays.add(pin)
                    marker = pin

                    overlays.add(
                        0,
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                if (p != null) {
                                    pin.position = p
                                    invalidate()
                                    onLocationPicked(p.latitude, p.longitude)
                                }
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        }),
                    )

                    contentDescription = "Tap to set the mark's position"
                    mapView = this
                }
            },
            update = { map ->
                marker?.let { pin ->
                    if (pin.position.latitude != latitude || pin.position.longitude != longitude) {
                        pin.position = GeoPoint(latitude, longitude)
                        map.invalidate()
                    }
                }
            },
            onRelease = { map ->
                if (mapView === map) mapView = null
                map.onPause()
                map.onDetach()
            },
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(colors.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Tap to set pin",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}
