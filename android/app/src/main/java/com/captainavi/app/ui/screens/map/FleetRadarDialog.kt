package com.captainavi.app.ui.screens.map

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.remote.FollowMePublicBoat
import com.captainavi.app.data.remote.FollowMePublicBoatProfile
import com.captainavi.app.data.repository.FollowMePublicBoatRepository
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.service.MarineLocationService
import com.captainavi.app.service.NavigationDestination
import com.captainavi.app.ui.theme.MarineTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private data class RadarBlip(
    val boat: FollowMePublicBoat,
    val distanceNm: Double,
    val trueBearingDegrees: Double,
)

private val RANGE_PRESETS_NM = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)
private const val TAP_HIT_RADIUS_DP = 22f

/**
 * A sonar-style radar view of nearby FollowMe-broadcasting boats, centered on the
 * vessel's own live GPS position — not the flat map pins used elsewhere. FollowMe's
 * own `distanceMeters` is relative to a fixed anchor device, not this phone, so
 * distance/bearing here are computed independently from live telemetry.
 *
 * Tapping a blip opens the same [FollowMeBoatDetailsDialog] used on the flat map
 * (call/message/set course), so the radar isn't just a picture — it's a real way to
 * act on what's nearby.
 */
@Composable
fun FleetRadarDialog(onDismiss: () -> Unit) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()

    val telemetry by MarineLocationService.telemetry.collectAsState()
    val boatState by app.followMePublicBoatRepository.state.collectAsState()
    val isOnline by app.networkMonitor.isOnline.collectAsState()

    LaunchedEffect(isOnline) {
        while (isOnline) {
            app.followMePublicBoatRepository.refresh()
            delay(FollowMePublicBoatRepository.MIN_REFRESH_INTERVAL_MS)
        }
    }

    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(1000)
        }
    }

    var courseUp by remember { mutableStateOf(false) }
    var manualRangeIndex by remember { mutableStateOf<Int?>(null) }
    var selectedBoat by remember { mutableStateOf<FollowMePublicBoat?>(null) }
    var selectedProfile by remember { mutableStateOf<FollowMePublicBoatProfile?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedBoat?.id) {
        val deviceId = selectedBoat?.id ?: run {
            selectedProfile = null
            isLoadingProfile = false
            profileError = null
            return@LaunchedEffect
        }
        isLoadingProfile = true
        profileError = null
        app.followMePublicBoatRepository.getBoatProfile(deviceId).fold(
            onSuccess = { profile -> if (selectedBoat?.id == deviceId) selectedProfile = profile },
            onFailure = { error ->
                if (selectedBoat?.id == deviceId) {
                    profileError = error.message ?: "Could not load vessel details"
                }
            },
        )
        if (selectedBoat?.id == deviceId) isLoadingProfile = false
    }
    // Keep the selected boat's own fields (speed/heading/position) live as the feed refreshes.
    LaunchedEffect(boatState.boats, selectedBoat?.id) {
        val selectedId = selectedBoat?.id ?: return@LaunchedEffect
        val updated = boatState.boats.firstOrNull { it.id == selectedId }
        selectedBoat = updated
    }

    val blips = remember(boatState.boats, telemetry.hasGpsFix, telemetry.latitude, telemetry.longitude) {
        if (!telemetry.hasGpsFix) {
            emptyList()
        } else {
            boatState.boats.map { boat ->
                RadarBlip(
                    boat = boat,
                    distanceNm = NauticalMath.distanceNauticalMiles(
                        telemetry.latitude, telemetry.longitude, boat.latitude, boat.longitude,
                    ),
                    trueBearingDegrees = NauticalMath.bearingDegrees(
                        telemetry.latitude, telemetry.longitude, boat.latitude, boat.longitude,
                    ),
                )
            }
        }
    }
    val autoRangeNm = remember(blips) {
        val farthest = blips.maxOfOrNull { it.distanceNm } ?: 1.0
        RANGE_PRESETS_NM.firstOrNull { it >= farthest * 1.15 } ?: RANGE_PRESETS_NM.last()
    }
    val rangeNm = manualRangeIndex?.let { RANGE_PRESETS_NM[it] } ?: autoRangeNm
    val ownHeadingDegrees = if (telemetry.compassAvailable) telemetry.compassHeadingDegrees else telemetry.bearingDegrees

    fun dialBoat(phone: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
            .onFailure { Toast.makeText(context, "No phone app is available", Toast.LENGTH_LONG).show() }
    }
    fun messageBoat(phone: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null))) }
            .onFailure { Toast.makeText(context, "No messaging app is available", Toast.LENGTH_LONG).show() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrackChanges, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("FLEET RADAR", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text(
                                text = when {
                                    !telemetry.hasGpsFix -> "Waiting for GPS fix…"
                                    !isOnline -> "Offline — showing last known positions"
                                    else -> String.format(
                                        Locale.US,
                                        "%d boat%s · %s kt · hdg %03d°",
                                        blips.size,
                                        if (blips.size == 1) "" else "s",
                                        String.format(Locale.US, "%.1f", telemetry.speedKnots),
                                        ownHeadingDegrees.toInt().let { if (it < 0) it + 360 else it % 360 },
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadarIconToggle(
                            icon = Icons.Default.Explore,
                            selected = courseUp,
                            contentDescription = if (courseUp) "Switch to north-up" else "Switch to course-up",
                            onClick = { courseUp = !courseUp },
                        )
                        if (boatState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(horizontal = 11.dp), strokeWidth = 2.dp, color = colors.accent)
                        } else {
                            IconButton(onClick = { scope.launch { app.followMePublicBoatRepository.refresh() } }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh radar", tint = colors.accent)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close radar", tint = colors.textSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val currentIndex = manualRangeIndex ?: RANGE_PRESETS_NM.indexOf(autoRangeNm).coerceAtLeast(0)
                            manualRangeIndex = (currentIndex - 1).coerceAtLeast(0)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom in", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                    Surface(
                        onClick = { manualRangeIndex = null },
                        color = if (manualRangeIndex == null) colors.accent.copy(alpha = 0.16f) else colors.card,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (manualRangeIndex == null) colors.accent else colors.border),
                    ) {
                        Text(
                            text = "${formatNm(rangeNm)} NM${if (manualRangeIndex == null) " · AUTO" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (manualRangeIndex == null) colors.accent else colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            val currentIndex = manualRangeIndex ?: RANGE_PRESETS_NM.indexOf(autoRangeNm).coerceAtLeast(0)
                            manualRangeIndex = (currentIndex + 1).coerceAtMost(RANGE_PRESETS_NM.lastIndex)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom out", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (!telemetry.hasGpsFix) {
                        Text(
                            "Radar needs your own GPS position before it can center on you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        RadarScope(
                            blips = blips,
                            rangeNm = rangeNm,
                            ownHeadingDegrees = ownHeadingDegrees,
                            courseUp = courseUp,
                            selectedBoatId = selectedBoat?.id,
                            nowMillis = nowTick,
                            onBoatTapped = { boat -> selectedBoat = boat },
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    }
                }

                if (telemetry.hasGpsFix && blips.isEmpty() && !boatState.isLoading) {
                    Text(
                        text = if (isOnline) {
                            "No nearby boats are currently broadcasting on FollowMe."
                        } else {
                            "Offline — connect to fetch nearby boats."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }

                Text(
                    text = "FollowMe community positions only — not an AIS/radar sensor. Distances are computed from your live GPS. Tap a blip for details.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            selectedBoat?.let { boat ->
                val yourDistanceNm = if (telemetry.hasGpsFix) {
                    NauticalMath.distanceNauticalMiles(telemetry.latitude, telemetry.longitude, boat.latitude, boat.longitude)
                } else null
                FollowMeBoatDetailsDialog(
                    boat = boat,
                    profile = selectedProfile,
                    isLoadingProfile = isLoadingProfile,
                    profileError = profileError,
                    yourDistanceNm = yourDistanceNm,
                    onDial = ::dialBoat,
                    onMessage = ::messageBoat,
                    onNavigate = {
                        MarineLocationService.setDestination(
                            NavigationDestination(name = boat.name, latitude = boat.latitude, longitude = boat.longitude),
                        )
                        selectedBoat = null
                    },
                    onDismiss = { selectedBoat = null },
                )
            }
        }
    }
}

@Composable
private fun RadarIconToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = MarineTheme.colors
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = if (selected) colors.accent else colors.textSecondary)
    }
}

@Composable
private fun RadarScope(
    blips: List<RadarBlip>,
    rangeNm: Double,
    ownHeadingDegrees: Float,
    courseUp: Boolean,
    selectedBoatId: Int?,
    nowMillis: Long,
    onBoatTapped: (FollowMePublicBoat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    val density = LocalDensity.current

    // Course-up rotates the whole picture by folding the boat's own heading into every
    // displayed bearing, rather than rotating rendered pixels — this keeps the Canvas
    // geometry and the Compose label overlays in perfect agreement.
    fun displayBearing(trueBearing: Double): Double =
        if (courseUp) trueBearing - ownHeadingDegrees else trueBearing

    val sweepTransition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 3800, easing = LinearEasing)),
        label = "radarSweepAngle",
    )

    BoxWithConstraints(modifier = modifier) {
        val diameterPx = with(density) { maxWidth.toPx() }
        val radiusPx = diameterPx / 2f
        val centerPx = Offset(radiusPx, radiusPx)
        val tapHitPx = with(density) { TAP_HIT_RADIUS_DP.dp.toPx() }

        val blipScreenPoints = remember(blips, rangeNm, courseUp, ownHeadingDegrees, diameterPx) {
            blips.map { blip ->
                val fraction = (blip.distanceNm / rangeNm).toFloat().coerceIn(0f, 1f)
                blip to polarOffset(centerPx, radiusPx * fraction, displayBearing(blip.trueBearingDegrees))
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(blipScreenPoints) {
                    detectTapGestures { tapOffset ->
                        val nearest = blipScreenPoints.minByOrNull { (_, point) -> (point - tapOffset).getDistance() }
                        if (nearest != null && (nearest.second - tapOffset).getDistance() <= tapHitPx) {
                            onBoatTapped(nearest.first.boat)
                        }
                    }
                },
        ) {
            drawCircle(color = colors.card, radius = radiusPx, center = centerPx)

            listOf(1f / 3f, 2f / 3f, 1f).forEach { fraction ->
                drawCircle(
                    color = colors.border.copy(alpha = 0.55f),
                    radius = radiusPx * fraction,
                    center = centerPx,
                    style = Stroke(width = 1.5f),
                )
            }

            // Fine ticks every 30°, cardinal ticks longer/brighter.
            for (bearing in 0 until 360 step 30) {
                val isCardinal = bearing % 90 == 0
                val innerFraction = if (isCardinal) 0.92f else 0.96f
                val start = polarOffset(centerPx, radiusPx * innerFraction, displayBearing(bearing.toDouble()))
                val end = polarOffset(centerPx, radiusPx, displayBearing(bearing.toDouble()))
                drawLine(
                    color = colors.border.copy(alpha = if (isCardinal) 0.75f else 0.35f),
                    start = start,
                    end = end,
                    strokeWidth = if (isCardinal) 2f else 1f,
                    cap = StrokeCap.Round,
                )
            }

            rotate(degrees = sweepAngle, pivot = centerPx) {
                val sweepPath = Path().apply {
                    moveTo(centerPx.x, centerPx.y)
                    lineTo(centerPx.x, centerPx.y - radiusPx)
                    val arcRect = androidx.compose.ui.geometry.Rect(
                        centerPx.x - radiusPx, centerPx.y - radiusPx,
                        centerPx.x + radiusPx, centerPx.y + radiusPx,
                    )
                    arcTo(arcRect, startAngleDegrees = -90f, sweepAngleDegrees = 28f, forceMoveTo = false)
                    close()
                }
                drawPath(
                    path = sweepPath,
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                        colors = listOf(colors.accent.copy(alpha = 0f), colors.accent.copy(alpha = 0.28f)),
                        center = centerPx,
                    ),
                )
            }

            // Own vessel: a triangle pointing toward its own heading (straight up in course-up mode).
            val ownTriangleRotation = if (courseUp) 0f else ownHeadingDegrees
            rotate(degrees = ownTriangleRotation, pivot = centerPx) {
                val tip = Offset(centerPx.x, centerPx.y - 12f)
                val left = Offset(centerPx.x - 7f, centerPx.y + 7f)
                val right = Offset(centerPx.x + 7f, centerPx.y + 7f)
                drawPath(
                    path = Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(left.x, left.y)
                        lineTo(right.x, right.y)
                        close()
                    },
                    color = colors.success,
                )
            }

            blipScreenPoints.forEach { (blip, point) ->
                val ageMillis = blip.boat.updatedAtEpochMillis?.let { nowMillis - it }
                val staleAlpha = when {
                    ageMillis == null -> 1f
                    ageMillis < 5 * 60_000L -> 1f
                    ageMillis < 20 * 60_000L -> 0.6f
                    else -> 0.35f
                }
                val isSelected = blip.boat.id == selectedBoatId
                if (isSelected) {
                    drawCircle(color = colors.accent, radius = 15f, center = point, style = Stroke(width = 2.5f))
                }
                drawCircle(color = colors.accent.copy(alpha = staleAlpha), radius = if (isSelected) 7.5f else 6f, center = point)
                drawCircle(
                    color = colors.accent.copy(alpha = staleAlpha * 0.4f),
                    radius = 11f,
                    center = point,
                    style = Stroke(width = 2f),
                )
            }
        }

        // Range ring labels.
        listOf(1f / 3f, 2f / 3f, 1f).forEach { fraction ->
            Text(
                text = formatNm(rangeNm * fraction) + " NM",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textMuted,
                modifier = Modifier.align(Alignment.Center).offset(y = -(maxWidth / 2f * fraction)),
            )
        }

        // Cardinal labels, following the same course-up rotation as everything else.
        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (label, bearing) ->
            val offsetDp = polarOffsetDp(maxWidth / 2f, 1.04f, displayBearing(bearing))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.accent,
                modifier = Modifier.align(Alignment.Center).offset(x = offsetDp.first, y = offsetDp.second),
            )
        }

        // Every other boat is just a dot — a name/speed card per blip stacked into an
        // unreadable pile once more than a couple of boats were close together. Only the
        // selected boat (tap a dot to select) gets a label; tapping again opens full details.
        blips.firstOrNull { it.boat.id == selectedBoatId }?.let { blip ->
            val distanceFraction = (blip.distanceNm / rangeNm).toFloat().coerceIn(0f, 1f)
            val offsetDp = polarOffsetDp(maxWidth / 2f, distanceFraction, displayBearing(blip.trueBearingDegrees))
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = offsetDp.first + 10.dp, y = offsetDp.second - 8.dp),
                color = colors.accent.copy(alpha = 0.22f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, colors.accent),
            ) {
                Column(modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(blip.boat.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textPrimary)
                    Text(
                        String.format(Locale.US, "%.1f NM · %.0f kt", blip.distanceNm, blip.boat.speedKnots),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

private fun polarOffset(center: Offset, radiusPx: Float, bearingDegrees: Double): Offset {
    val rad = Math.toRadians(bearingDegrees)
    return Offset(
        x = center.x + radiusPx * sin(rad).toFloat(),
        y = center.y - radiusPx * cos(rad).toFloat(),
    )
}

/** dp-space equivalent of [polarOffset], for positioning Compose overlays alongside the Canvas. */
private fun polarOffsetDp(
    radiusDp: androidx.compose.ui.unit.Dp,
    distanceFraction: Float,
    bearingDegrees: Double,
): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    val rad = Math.toRadians(bearingDegrees)
    val r = radiusDp * distanceFraction
    return (r * sin(rad).toFloat()) to (-(r * cos(rad).toFloat()))
}

private fun formatNm(value: Double): String =
    if (value >= 10.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
