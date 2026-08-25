package com.captainavi.app.ui.screens.dashboard

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.captainavi.app.data.remote.EndTripRequest
import com.captainavi.app.data.remote.StartTripRequest
import com.captainavi.app.localization.LanguageManager
import com.captainavi.app.safety.AnchorWatchManager
import com.captainavi.app.safety.FuelMarginCalculator
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.safety.StormAlertEvaluator
import com.captainavi.app.safety.VoiceAlertManager
import com.captainavi.app.service.ConnectivitySyncWorker
import com.captainavi.app.service.DestinationState
import com.captainavi.app.service.MarineLocationService
import com.captainavi.app.service.NavigationDestination
import com.captainavi.app.ui.theme.MarineTheme
import com.captainavi.app.ui.theme.NightModeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    onNavigateToMap: () -> Unit,
    onNavigateToTides: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCatch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val voiceAlerts = remember { VoiceAlertManager(context) }
    DisposableEffect(Unit) {
        onDispose { voiceAlerts.shutdown() }
    }

    val telemetry by MarineLocationService.telemetry.collectAsState()
    val activeTrip by app.tripRepository.getActiveTrip().collectAsState(initial = null)
    val unsyncedCount by app.outboxRepository.getPendingOutboxCount().collectAsState(initial = 0)
    val anchorState by AnchorWatchManager.state.collectAsState()
    val allWaypoints by app.waypointRepository.getAllWaypoints().collectAsState(initial = emptyList())
    val marineConditionsState by app.marineConditionsRepository.state.collectAsState()
    val isOnline by app.networkMonitor.isOnline.collectAsState()
    val stormAlertsEnabled by app.settingsRepository.stormAlertsEnabled.collectAsState()
    val stormWaveHeightThresholdMeters by app.settingsRepository.stormWaveHeightThresholdMeters.collectAsState()
    val stormWindGustThresholdKnots by app.settingsRepository.stormWindGustThresholdKnots.collectAsState()
    val fuelTankLiters by app.settingsRepository.fuelTankLiters.collectAsState()
    val stormAlert = remember(
        marineConditionsState.conditions,
        stormAlertsEnabled,
        stormWaveHeightThresholdMeters,
        stormWindGustThresholdKnots,
    ) {
        marineConditionsState.conditions
            ?.takeIf { stormAlertsEnabled }
            ?.let { StormAlertEvaluator.evaluate(it, stormWaveHeightThresholdMeters, stormWindGustThresholdKnots) }
    }
    val voyageActive = activeTrip != null || telemetry.isTracking
    val voyageStartedAt = telemetry.tripStartTime.takeIf { it > 0L } ?: activeTrip?.startTime ?: 0L

    var showSosDialog by remember { mutableStateOf(false) }
    var showNavDialog by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var tripElapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(voyageActive, voyageStartedAt) {
        while (voyageActive && voyageStartedAt > 0L) {
            tripElapsedSeconds = (System.currentTimeMillis() - voyageStartedAt) / 1000L
            delay(1000)
        }
    }

    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(2000)
        }
    }
    val gpsFresh = telemetry.hasGpsFix && (nowTick - telemetry.lastUpdateTime) < 10_000L
    val forecastGridKey = if (telemetry.hasGpsFix) {
        (telemetry.latitude * 10).roundToInt() to (telemetry.longitude * 10).roundToInt()
    } else null

    LaunchedEffect(forecastGridKey) {
        if (forecastGridKey != null) {
            app.marineConditionsRepository.refresh(telemetry.latitude, telemetry.longitude)
        }
    }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        toastMessage = null
    }

    LaunchedEffect(anchorState.isDragging) {
        if (anchorState.isDragging) {
            voiceAlerts.speakAnchorDragging()
        }
    }

    if (showSosDialog) {
        SosDialog(
            isSosActive = telemetry.isSosActive,
            isOnline = isOnline,
            onConfirmSos = {
                context.startService(Intent(context, MarineLocationService::class.java).apply {
                    action = MarineLocationService.ACTION_TRIGGER_SOS
                })
            },
            onCancelSos = {
                context.startService(Intent(context, MarineLocationService::class.java).apply {
                    action = MarineLocationService.ACTION_CANCEL_SOS
                })
            },
            onDismiss = { showSosDialog = false }
        )
    }

    if (showNavDialog) {
        NavigateToDialog(
            waypoints = allWaypoints,
            currentLat = telemetry.latitude,
            currentLon = telemetry.longitude,
            onSelectDestination = { wp ->
                DestinationState.lockDestination(wp)
                showNavDialog = false
                toastMessage = "${LanguageManager.navigateToPrefix} ${wp.name}"
                voiceAlerts.speak("Navigating to ${wp.name}")
            },
            onNavigateToCoords = { lat, lon ->
                MarineLocationService.setDestination(
                    NavigationDestination(
                        name = "Custom (${String.format(java.util.Locale.US, "%.3f", lat)}, ${String.format(java.util.Locale.US, "%.3f", lon)})",
                        latitude = lat,
                        longitude = lon
                    )
                )
                showNavDialog = false
                toastMessage = "${LanguageManager.navigateToPrefix} custom coordinates"
            },
            onDismiss = { showNavDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VoyageStatusDock(
                isTracking = voyageActive,
                elapsedSeconds = tripElapsedSeconds,
                gpsFresh = gpsFresh,
                isOnline = isOnline,
                unsyncedCount = unsyncedCount,
                batteryPct = telemetry.batteryPct,
                onSettingsClick = onNavigateToSettings,
                isNightMode = NightModeState.isNightMode,
                onToggleNightMode = {
                    val enabled = !NightModeState.isNightMode
                    NightModeState.isNightMode = enabled
                    app.settingsRepository.setNightMode(enabled)
                },
                onSyncClick = {
                    if (isOnline) {
                        ConnectivitySyncWorker.enqueueImmediateSync(context)
                    } else {
                        toastMessage = "Offline — data is saved and will sync automatically"
                    }
                },
            )

            if (anchorState.isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (anchorState.isDragging) colors.emergency.copy(alpha = 0.22f) else colors.caution.copy(alpha = 0.12f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, if (anchorState.isDragging) colors.emergency else colors.caution, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Anchor,
                            contentDescription = null,
                            tint = if (anchorState.isDragging) colors.emergency else colors.caution,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (anchorState.isDragging) LanguageManager.anchorDragging else LanguageManager.anchorSet,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (anchorState.isDragging) colors.emergency else colors.caution
                            )
                            Text(
                                text = "Drift ${String.format(java.util.Locale.US, "%.0f", anchorState.currentDriftMeters)} m / ${String.format(java.util.Locale.US, "%.0f", anchorState.swingRadiusMeters)} m",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            AnchorWatchManager.weigh()
                            toastMessage = "Anchor weighed"
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = LanguageManager.weighAnchor, tint = colors.textSecondary)
                    }
                }
            }

            telemetry.activeSafetyAlert?.let { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.emergency.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        .border(1.dp, colors.emergency, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.emergency, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(alert.title, style = MaterialTheme.typography.titleSmall, color = colors.emergency)
                        Text(alert.description, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                    }
                }
            }

            val updateState by app.appUpdateManager.state.collectAsState()
            val availableUpdate = updateState as? com.captainavi.app.update.AppUpdateUiState.Available
            if (availableUpdate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.caution.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .border(1.dp, colors.caution.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .clickable { onNavigateToSettings() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = colors.caution, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Update ${availableUpdate.update.versionName} available",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.caution,
                        )
                        Text(
                            "Open Settings to download and install",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            val activeHeading = if (telemetry.compassAvailable) telemetry.compassHeadingDegrees else telemetry.bearingDegrees
            val activeCardinal = if (telemetry.compassAvailable) {
                NauticalMath.degreesToShortCardinal(telemetry.compassHeadingDegrees.toDouble())
            } else {
                telemetry.headingCardinal
            }

            CompassHud(
                currentHeadingDegrees = activeHeading,
                headingCardinal = activeCardinal,
                bearingToHomeDegrees = telemetry.bearingToHomeDegrees,
                distanceToHomeNm = telemetry.distanceToHomeNm,
                speedKnots = telemetry.speedKnots,
                activeDestination = telemetry.activeDestination,
                bearingToDestDegrees = telemetry.bearingToDestDegrees,
                distToDestNm = telemetry.distToDestNm,
                etaMinutes = telemetry.etaMinutes,
                vmgKnots = telemetry.vmgKnots,
                crossTrackErrorMeters = telemetry.crossTrackErrorMeters,
                cogDegrees = telemetry.bearingDegrees,
                headingSource = telemetry.headingSource,
                onClearDestination = {
                    DestinationState.clearDestination()
                    toastMessage = "Destination cleared"
                },
                modifier = Modifier.clickable { onNavigateToMap() }
            )

            if (telemetry.isTracking) {
                telemetry.fuelMarginLiters?.let { marginLiters ->
                    FuelMarginChip(
                        marginLiters = marginLiters,
                        remainingLiters = telemetry.fuelRemainingLiters,
                        neededLiters = telemetry.fuelNeededToReturnLiters,
                        tankLiters = fuelTankLiters,
                        distanceTraveledNm = telemetry.distanceTraveledNm,
                    )
                }
            }

            TideCard(
                nowMillis = nowTick,
                modifier = Modifier.clickable { onNavigateToTides() }
            )

            MarineConditionsCard(
                state = marineConditionsState,
                hasGpsFix = telemetry.hasGpsFix,
                nowMillis = nowTick,
                onRefresh = {
                    scope.launch {
                        app.marineConditionsRepository.refresh(
                            telemetry.latitude,
                            telemetry.longitude,
                            force = true,
                        )
                    }
                },
                stormAlert = stormAlert,
            )

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (voyageActive) "VOYAGE IN PROGRESS" else "VOYAGE CONTROLS")
                if (activeTrip == null && !telemetry.isTracking) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (!telemetry.hasGpsFix) {
                                    toastMessage = LanguageManager.waitingForGps
                                    return@launch
                                }
                                val trip = app.tripRepository.startTrip(telemetry.latitude, telemetry.longitude, "Fishing Voyage")
                                context.startForegroundService(Intent(context, MarineLocationService::class.java).apply {
                                    action = MarineLocationService.ACTION_START_TRIP
                                })
                                voiceAlerts.speakTripStarted()
                                app.outboxRepository.queueTripStart(
                                    StartTripRequest(
                                        trip.id,
                                        app.settingsRepository.captainName.value,
                                        telemetry.latitude,
                                        telemetry.longitude,
                                        telemetry.batteryPct,
                                        System.currentTimeMillis(),
                                    ),
                                )
                                ConnectivitySyncWorker.enqueueImmediateSync(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(LanguageManager.startFishingTrip, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                val tripId = activeTrip?.id ?: telemetry.tripId ?: ""
                                val finishedTrip = app.tripRepository.finishTrip(tripId, telemetry.latitude, telemetry.longitude)
                                context.startService(Intent(context, MarineLocationService::class.java).apply {
                                    action = MarineLocationService.ACTION_STOP_TRIP
                                })
                                voiceAlerts.speakTripEnded()
                                if (finishedTrip != null) {
                                    val breadcrumbs = app.database.breadcrumbDao().getBreadcrumbsForTripList(finishedTrip.id)
                                    val avgSpeed = app.database.breadcrumbDao().getAvgSpeedForTrip(finishedTrip.id) ?: 0.0
                                    app.outboxRepository.queueTripEnd(
                                        EndTripRequest(
                                            finishedTrip.id,
                                            app.settingsRepository.captainName.value,
                                            finishedTrip.startTime,
                                            finishedTrip.endTime ?: System.currentTimeMillis(),
                                            finishedTrip.totalDistanceNm,
                                            finishedTrip.maxSpeedKnots,
                                            avgSpeed,
                                            breadcrumbs.size,
                                            telemetry.latitude,
                                            telemetry.longitude,
                                        ),
                                    )
                                    ConnectivitySyncWorker.enqueueImmediateSync(context)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.caution, contentColor = colors.onAccent),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(LanguageManager.endTripReturnHome, style = MaterialTheme.typography.titleMedium)
                    }
                    if (activeTrip != null) {
                        OutlinedButton(
                            onClick = onNavigateToCatch,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.success),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.success),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.SetMeal, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log catch", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }

                if (telemetry.activeDestination != null) {
                    OutlinedButton(
                        onClick = {
                            DestinationState.clearDestination()
                            toastMessage = "Navigation cleared"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.emergency),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.emergency),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(LanguageManager.clearDestination, style = MaterialTheme.typography.titleSmall)
                    }
                } else {
                    OutlinedButton(
                        onClick = { showNavDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(LanguageManager.navigateToDestination, style = MaterialTheme.typography.titleSmall)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                if (!telemetry.hasGpsFix) {
                                    toastMessage = LanguageManager.noGpsFix
                                    return@launch
                                }
                                app.waypointRepository.setHomeLocation(telemetry.latitude, telemetry.longitude, "Home Harbour Base")
                                toastMessage = String.format(
                                    java.util.Locale.US,
                                    "Home locked at %.4f, %.4f",
                                    telemetry.latitude,
                                    telemetry.longitude
                                )
                                voiceAlerts.speak("Home base location set.")
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.home),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.home),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(LanguageManager.setHome, style = MaterialTheme.typography.labelLarge)
                    }

                    if (!anchorState.isActive) {
                        OutlinedButton(
                            onClick = {
                                if (!telemetry.hasGpsFix) {
                                    toastMessage = LanguageManager.noGpsFix
                                    return@OutlinedButton
                                }
                                AnchorWatchManager.dropAnchor(telemetry.latitude, telemetry.longitude)
                                toastMessage = "Anchor dropped — watching for drift"
                                voiceAlerts.speak("Anchor dropped. Anchor watch active.")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.caution),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.caution),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Anchor, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(LanguageManager.dropAnchor, style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Button(
                            onClick = {
                                AnchorWatchManager.weigh()
                                toastMessage = "Anchor weighed"
                                voiceAlerts.speak("Anchor weighed.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.caution, contentColor = colors.onAccent),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Anchor, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(LanguageManager.weighAnchor, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                SectionLabel("EMERGENCY ACTIONS")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!telemetry.hasGpsFix) {
                                toastMessage = LanguageManager.noGpsFix
                                return@Button
                            }
                            val mob = MarineLocationService.triggerMob()
                            scope.launch {
                                app.waypointRepository.addWaypoint(
                                    WaypointEntity(
                                        name = "MOB ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())}",
                                        type = WaypointType.DANGER_REEF,
                                        latitude = mob.latitude,
                                        longitude = mob.longitude,
                                        description = "Man overboard marker"
                                    )
                                )
                            }
                            toastMessage = "MOB marker dropped — navigating to rescue point"
                            voiceAlerts.speakMobActivated()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.mob, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(LanguageManager.mob, style = MaterialTheme.typography.titleSmall)
                    }
                    Button(
                        onClick = { showSosDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (telemetry.isSosActive) colors.emergencyDark else colors.emergency,
                            contentColor = colors.textPrimary
                        ),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(LanguageManager.sos, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
        )
    }
}

@Composable
private fun VoyageStatusDock(
    isTracking: Boolean,
    elapsedSeconds: Long,
    gpsFresh: Boolean,
    isOnline: Boolean,
    unsyncedCount: Int,
    batteryPct: Int,
    onSettingsClick: () -> Unit,
    isNightMode: Boolean,
    onToggleNightMode: () -> Unit,
    onSyncClick: () -> Unit
) {
    val colors = MarineTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("CAPTAIN AVI", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                Text(
                    text = if (isTracking) "Underway" else "Ready to sail",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTracking) {
                    Row(
                        modifier = Modifier
                            .background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = String.format(
                                java.util.Locale.US,
                                "%02d:%02d:%02d",
                                elapsedSeconds / 3600,
                                (elapsedSeconds % 3600) / 60,
                                elapsedSeconds % 60
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accent
                        )
                    }
                } else {
                    Text(
                        text = if (gpsFresh) "SYSTEMS READY" else "GPS ACQUIRING",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (gpsFresh) colors.success else colors.caution,
                        modifier = Modifier
                            .background(
                                (if (gpsFresh) colors.success else colors.caution).copy(alpha = 0.12f),
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isNightMode) colors.accent.copy(alpha = 0.18f) else colors.card,
                            RoundedCornerShape(12.dp),
                        )
                        .border(
                            1.dp,
                            if (isNightMode) colors.accent else colors.border.copy(alpha = 0.72f),
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onToggleNightMode, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = if (isNightMode) "Turn off night vision mode" else "Turn on night vision mode",
                            tint = if (isNightMode) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.card, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onSettingsClick, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Open configuration",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.55f))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusItem(
                icon = Icons.Default.GpsFixed,
                label = "GPS",
                value = if (gpsFresh) "Locked" else "No fix",
                tint = if (gpsFresh) colors.success else colors.emergency,
                modifier = Modifier.weight(1f)
            )
            StatusItem(
                icon = if (!isOnline || unsyncedCount > 0) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                label = "SYNC",
                value = when {
                    !isOnline && unsyncedCount > 0 -> "$unsyncedCount saved"
                    !isOnline -> "Offline"
                    unsyncedCount > 0 -> "$unsyncedCount queued"
                    else -> "Current"
                },
                tint = if (!isOnline || unsyncedCount > 0) colors.caution else colors.accent,
                modifier = Modifier.weight(1f).clickable(onClick = onSyncClick)
            )
            StatusItem(
                icon = Icons.Default.BatteryChargingFull,
                label = "POWER",
                value = "$batteryPct%",
                tint = if (batteryPct < 20) colors.emergency else colors.success,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FuelMarginChip(
    marginLiters: Double,
    remainingLiters: Double?,
    neededLiters: Double?,
    tankLiters: Double,
    distanceTraveledNm: Double,
) {
    val colors = MarineTheme.colors
    val warningThreshold = tankLiters * FuelMarginCalculator.WARNING_MARGIN_FRACTION
    val tint = when {
        marginLiters < 0.0 -> colors.emergency
        marginLiters <= warningThreshold -> colors.caution
        else -> colors.success
    }
    val statusLabel = when {
        marginLiters < 0.0 -> "SHORT"
        marginLiters <= warningThreshold -> "TIGHT"
        else -> "OK"
    }
    val marginText = if (marginLiters < 0.0) {
        String.format(java.util.Locale.US, "%.1f L short", -marginLiters)
    } else {
        String.format(java.util.Locale.US, "+%.1f L margin", marginLiters)
    }
    val detail = buildString {
        remainingLiters?.let { append(String.format(java.util.Locale.US, "%.0f L left", it)) }
        neededLiters?.let {
            if (isNotEmpty()) append(" · ")
            append(String.format(java.util.Locale.US, "%.0f L to home", it))
        }
        if (distanceTraveledNm > 0.05) {
            if (isNotEmpty()) append(" · ")
            append(String.format(java.util.Locale.US, "%.1f NM run", distanceTraveledNm))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.LocalGasStation,
                contentDescription = "Fuel margin",
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("FUEL RETURN MARGIN", style = MaterialTheme.typography.labelSmall, color = tint)
            Text(marginText, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier
                .background(tint.copy(alpha = 0.16f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun StatusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(7.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MarineTheme.colors.textMuted)
            Text(value, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = MarineTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textMuted,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}
