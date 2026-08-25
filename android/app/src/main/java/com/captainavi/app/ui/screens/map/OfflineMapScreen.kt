package com.captainavi.app.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.data.local.entity.WaypointType
import com.captainavi.app.data.repository.IslandPlace
import com.captainavi.app.data.repository.IslandEmergencyContactDirectory
import com.captainavi.app.data.repository.MarineActivityPoint
import com.captainavi.app.data.repository.MarineActivityPointRepository
import com.captainavi.app.data.repository.MarineActivityPointType
import com.captainavi.app.data.repository.ReefBoundaryRepository
import com.captainavi.app.data.repository.RtlMarineRouteRepository
import com.captainavi.app.data.remote.EndTripRequest
import com.captainavi.app.data.remote.FollowMePublicBoat
import com.captainavi.app.data.remote.FollowMePublicBoatProfile
import com.captainavi.app.data.remote.StartTripRequest
import com.captainavi.app.data.repository.searchMarineActivityPoints
import com.captainavi.app.data.repository.searchIslandPlaces
import com.captainavi.app.localization.LanguageManager
import com.captainavi.app.safety.AnchorWatchManager
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.safety.TripCalculator
import com.captainavi.app.service.ConnectivitySyncWorker
import com.captainavi.app.service.DestinationState
import com.captainavi.app.service.MarineLocationService
import com.captainavi.app.service.NavigationDestination
import com.captainavi.app.service.SavedTraceState
import com.captainavi.app.ui.theme.MarineTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import kotlin.math.roundToInt

@Composable
fun OfflineMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()
    val quickSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val result = if (granted) {
            app.offlineSmsMessenger.sendConfiguredLocation()
        } else {
            com.captainavi.app.sms.OfflineSmsSendResult(false, "SMS permission was not enabled")
        }
        Toast.makeText(context, result.message, if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }
    val islandEmergencyDirectory = remember(context.applicationContext) {
        IslandEmergencyContactDirectory(context.applicationContext)
    }

    val telemetry by MarineLocationService.telemetry.collectAsState()
    val marineConditionsState by app.marineConditionsRepository.state.collectAsState()
    val islandGazetteerState by app.islandGazetteerRepository.state.collectAsState()
    val marineActivityPointState by app.marineActivityPointRepository.state.collectAsState()
    val reefBoundaryState by app.reefBoundaryRepository.state.collectAsState()
    val rtlMarineRouteState by app.rtlMarineRouteRepository.state.collectAsState()
    val isOnline by app.networkMonitor.isOnline.collectAsState()
    val isReefOverlayEnabled by app.settingsRepository.reefOverlayEnabled.collectAsState()
    val reefWarningsEnabled by app.settingsRepository.reefWarningsEnabled.collectAsState()
    val reefWarningBufferMeters by app.settingsRepository.reefWarningBufferMeters.collectAsState()
    val fishingPointsEnabled by app.settingsRepository.fishingPointsEnabled.collectAsState()
    val divePointsEnabled by app.settingsRepository.divePointsEnabled.collectAsState()
    val rtlMarineRoutesEnabled by app.settingsRepository.rtlMarineRoutesEnabled.collectAsState()
    val tripReferenceDistanceNm by app.settingsRepository.tripReferenceDistanceNm.collectAsState()
    val tripReferenceCostMvr by app.settingsRepository.tripReferenceCostMvr.collectAsState()
    val tripReferenceFuelLiters by app.settingsRepository.tripReferenceFuelLiters.collectAsState()
    val followMePublicBoatState by app.followMePublicBoatRepository.state.collectAsState()
    val anchorState by AnchorWatchManager.state.collectAsState()
    val activeTrip by app.tripRepository.getActiveTrip().collectAsState(initial = null)
    val loadedTraceTripId by SavedTraceState.selectedTripId.collectAsState()
    val currentTraceTripId = activeTrip?.id ?: telemetry.tripId
    val visibleTraceTripId = currentTraceTripId ?: loadedTraceTripId.orEmpty()
    val breadcrumbs by remember(visibleTraceTripId) {
        app.tripRepository.getBreadcrumbsForTrip(visibleTraceTripId)
    }.collectAsState(initial = emptyList())
    val waypoints by app.waypointRepository.getAllWaypoints().collectAsState(initial = emptyList())

    val colors = MarineTheme.colors
    val accentArgb = colors.accent.toArgb()
    val headingArgb = colors.caution.toArgb()
    val destArgb = colors.destination.toArgb()
    val destinationTripEstimate = TripCalculator.estimate(
        distanceNauticalMiles = telemetry.distToDestNm,
        referenceDistanceNauticalMiles = tripReferenceDistanceNm,
        referenceCostMvr = tripReferenceCostMvr,
        referenceFuelLiters = tripReferenceFuelLiters,
    )

    var mapTileType by rememberSaveable { mutableStateOf(MapTileType.SATELLITE) }
    var savedMapLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var savedMapLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var savedMapZoom by rememberSaveable { mutableStateOf(14.0) }
    var savedMapOrientation by rememberSaveable { mutableStateOf(0f) }
    val mapRuntime = remember { MapRuntimeRefs() }
    var hasCenteredOnStartupLocation by remember { mutableStateOf(telemetry.hasGpsFix) }
    val mapCoverageProbe = remember { MapTileCoverageProbe() }
    var effectiveMapMaxZoom by remember { mutableStateOf(tileSourceFor(mapTileType).maximumZoomLevel) }
    var isCheckingMapCoverage by remember { mutableStateOf(false) }
    var isSeamarkEnabled by rememberSaveable { mutableStateOf(false) }
    var flowLayerMode by rememberSaveable { mutableStateOf(FlowLayerMode.OFF) }
    var headingUpMode by rememberSaveable { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showDataInfo by remember { mutableStateOf(false) }
    var showFollowMeBoatsOverlay by rememberSaveable { mutableStateOf(false) }
    var showFishingHotspots by rememberSaveable { mutableStateOf(false) }
    var hotspotCells by remember { mutableStateOf<List<com.captainavi.app.data.repository.HotspotCell>>(emptyList()) }
    var isLoadingHotspots by remember { mutableStateOf(false) }
    var selectedFollowMeBoat by remember { mutableStateOf<FollowMePublicBoat?>(null) }
    var followMeBoatChoices by remember { mutableStateOf<List<FollowMePublicBoat>>(emptyList()) }
    var selectedFollowMeProfile by remember { mutableStateOf<FollowMePublicBoatProfile?>(null) }
    var isLoadingFollowMeProfile by remember { mutableStateOf(false) }
    var followMeProfileError by remember { mutableStateOf<String?>(null) }
    var showMapSourcePicker by rememberSaveable { mutableStateOf(false) }
    var showFleetRadar by rememberSaveable { mutableStateOf(false) }
    var showIslandSearch by rememberSaveable { mutableStateOf(false) }
    var islandQuery by rememberSaveable { mutableStateOf("") }
    var placeSearchFilter by rememberSaveable { mutableStateOf(PlaceSearchFilter.ALL) }
    var selectedIslandId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedIslandForContacts by remember { mutableStateOf<IslandPlace?>(null) }
    var selectedMarinePointId by rememberSaveable { mutableStateOf<String?>(null) }
    var showMarinePointDetails by rememberSaveable { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadHarbourDetail by rememberSaveable { mutableStateOf(false) }
    var showMoreChartTools by rememberSaveable { mutableStateOf(false) }
    var traceControlBusy by remember { mutableStateOf(false) }
    var traceClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val traceActive = activeTrip != null || telemetry.isTracking
    val savedTraceLoaded = !traceActive && !loadedTraceTripId.isNullOrBlank()
    val traceStartedAt = telemetry.tripStartTime.takeIf { it > 0L } ?: activeTrip?.startTime ?: 0L
    LaunchedEffect(traceActive, traceStartedAt) {
        traceClockMillis = System.currentTimeMillis()
        while (traceActive && traceStartedAt > 0L) {
            delay(1_000L)
            traceClockMillis = System.currentTimeMillis()
        }
    }
    val traceElapsedSeconds = if (traceActive && traceStartedAt > 0L) {
        ((traceClockMillis - traceStartedAt) / 1_000L).coerceAtLeast(0L)
    } else if (breadcrumbs.size >= 2) {
        ((breadcrumbs.last().timestamp - breadcrumbs.first().timestamp) / 1_000L).coerceAtLeast(0L)
    } else {
        0L
    }
    val traceCoordinates = remember(
        breadcrumbs,
        traceActive,
        telemetry.hasGpsFix,
        telemetry.latitude,
        telemetry.longitude,
    ) {
        buildList<TraceCoordinate> {
            breadcrumbs.forEach { add(TraceCoordinate(it.latitude, it.longitude)) }
            if (traceActive && telemetry.hasGpsFix) {
                val livePosition = TraceCoordinate(telemetry.latitude, telemetry.longitude)
                val last = this.lastOrNull()
                if (last == null || last.latitude != livePosition.latitude || last.longitude != livePosition.longitude) {
                    add(livePosition)
                }
            }
        }
    }
    val traceDistanceNm = remember(traceCoordinates) {
        traceDistanceNauticalMiles(traceCoordinates)
    }

    fun saveViewport(map: MapView) {
        savedMapLatitude = map.mapCenter.latitude
        savedMapLongitude = map.mapCenter.longitude
        savedMapZoom = map.zoomLevelDouble
        savedMapOrientation = map.mapOrientation
    }

    fun selectMapSource(type: MapTileType) {
        val source = tileSourceFor(type)
        val currentZoom = mapRuntime.mapView?.zoomLevelDouble ?: savedMapZoom
        mapTileType = type
        effectiveMapMaxZoom = source.maximumZoomLevel
        savedMapZoom = clampMapZoom(
            currentZoom = currentZoom,
            sourceMinZoom = source.minimumZoomLevel,
            sourceMaxZoom = source.maximumZoomLevel,
        )
        showMapSourcePicker = false
    }

    fun focusIsland(island: IslandPlace) {
        val source = tileSourceFor(mapTileType)
        val targetZoom = clampMapZoom(
            currentZoom = maxOf(mapRuntime.mapView?.zoomLevelDouble ?: savedMapZoom, 14.0),
            sourceMinZoom = source.minimumZoomLevel,
            sourceMaxZoom = source.maximumZoomLevel,
        )
        selectedIslandId = island.id
        selectedIslandForContacts = island
        savedMapLatitude = island.latitude
        savedMapLongitude = island.longitude
        savedMapZoom = targetZoom
        mapRuntime.islandLabelsOverlay?.selectedIslandId = island.id
        mapRuntime.mapView?.let { map ->
            map.controller.setZoom(targetZoom)
            map.controller.animateTo(GeoPoint(island.latitude, island.longitude))
            map.invalidate()
        }
        showIslandSearch = false
    }

    fun openPhoneDialer(phone: String) {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
        runCatching { context.startActivity(dialIntent) }
            .onFailure {
                Toast.makeText(context, "No phone app is available", Toast.LENGTH_LONG).show()
            }
    }

    fun openSmsComposer(phone: String) {
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null))
        runCatching { context.startActivity(smsIntent) }
            .onFailure {
                Toast.makeText(context, "No messaging app is available", Toast.LENGTH_LONG).show()
            }
    }

    fun openFollowMeSelection(boats: List<FollowMePublicBoat>) {
        val uniqueBoats = boats.distinctBy(FollowMePublicBoat::id)
        selectedIslandForContacts = null
        if (uniqueBoats.size == 1) {
            followMeBoatChoices = emptyList()
            selectedFollowMeBoat = uniqueBoats.first()
        } else if (uniqueBoats.isNotEmpty()) {
            selectedFollowMeBoat = null
            followMeBoatChoices = uniqueBoats
        }
    }

    fun focusMarinePoint(point: MarineActivityPoint, showDetails: Boolean = true) {
        if (point.isFishingPoint && !fishingPointsEnabled) {
            app.settingsRepository.setFishingPointsEnabled(true)
        } else if (!point.isFishingPoint && !divePointsEnabled) {
            app.settingsRepository.setDivePointsEnabled(true)
        }
        val source = tileSourceFor(mapTileType)
        val targetZoom = clampMapZoom(
            currentZoom = maxOf(mapRuntime.mapView?.zoomLevelDouble ?: savedMapZoom, 13.0),
            sourceMinZoom = source.minimumZoomLevel,
            sourceMaxZoom = source.maximumZoomLevel,
        )
        selectedMarinePointId = point.id
        savedMapLatitude = point.latitude
        savedMapLongitude = point.longitude
        savedMapZoom = targetZoom
        mapRuntime.marineActivityPointsOverlay?.selectedPointId = point.id
        mapRuntime.mapView?.let { map ->
            map.controller.setZoom(targetZoom)
            map.controller.animateTo(GeoPoint(point.latitude, point.longitude))
            map.invalidate()
        }
        showIslandSearch = false
        showMarinePointDetails = showDetails
    }

    fun startJourneyTrace() {
        if (traceControlBusy) return
        if (!telemetry.hasGpsFix) {
            Toast.makeText(context, LanguageManager.waitingForGps, Toast.LENGTH_LONG).show()
            return
        }
        SavedTraceState.clear()
        scope.launch {
            traceControlBusy = true
            runCatching {
                val trip = app.tripRepository.startTrip(
                    telemetry.latitude,
                    telemetry.longitude,
                    "Journey trace",
                )
                context.startForegroundService(Intent(context, MarineLocationService::class.java).apply {
                    action = MarineLocationService.ACTION_START_TRIP
                })
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
            }.onSuccess {
                Toast.makeText(context, "Journey trace started", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, "Could not start trace: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
            traceControlBusy = false
        }
    }

    fun saveJourneyTrace() {
        if (traceControlBusy) return
        val tripId = activeTrip?.id ?: telemetry.tripId
        if (tripId.isNullOrBlank()) {
            Toast.makeText(context, "No active journey trace to save", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            traceControlBusy = true
            runCatching {
                if (telemetry.hasGpsFix) {
                    app.tripRepository.recordBreadcrumb(
                        tripId = tripId,
                        latitude = telemetry.latitude,
                        longitude = telemetry.longitude,
                        speedKnots = telemetry.speedKnots,
                        bearingDegrees = telemetry.bearingDegrees,
                        accuracyMeters = telemetry.accuracyMeters,
                        batteryPct = telemetry.batteryPct,
                    )
                }
                val finishedTrip = app.tripRepository.finishTrip(
                    tripId,
                    telemetry.latitude,
                    telemetry.longitude,
                )
                context.startService(Intent(context, MarineLocationService::class.java).apply {
                    action = MarineLocationService.ACTION_STOP_TRIP
                })
                if (finishedTrip != null) {
                    SavedTraceState.load(finishedTrip.id)
                    val savedBreadcrumbs = app.database.breadcrumbDao().getBreadcrumbsForTripList(finishedTrip.id)
                    val averageSpeed = app.database.breadcrumbDao().getAvgSpeedForTrip(finishedTrip.id) ?: 0.0
                    app.outboxRepository.queueTripEnd(
                        EndTripRequest(
                            finishedTrip.id,
                            app.settingsRepository.captainName.value,
                            finishedTrip.startTime,
                            finishedTrip.endTime ?: System.currentTimeMillis(),
                            finishedTrip.totalDistanceNm,
                            finishedTrip.maxSpeedKnots,
                            averageSpeed,
                            savedBreadcrumbs.size,
                            telemetry.latitude,
                            telemetry.longitude,
                        ),
                    )
                    ConnectivitySyncWorker.enqueueImmediateSync(context)
                }
                finishedTrip
            }.onSuccess { finishedTrip ->
                val distance = finishedTrip?.totalDistanceNm ?: traceDistanceNm
                Toast.makeText(
                    context,
                    "Trace saved to Log · ${String.format(java.util.Locale.US, "%.2f", distance)} NM",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                Toast.makeText(context, "Could not save trace: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
            traceControlBusy = false
        }
    }

    val forecastGridKey = if (telemetry.hasGpsFix) {
        (telemetry.latitude * 10).roundToInt() to (telemetry.longitude * 10).roundToInt()
    } else null
    LaunchedEffect(forecastGridKey) {
        if (forecastGridKey != null) {
            app.marineConditionsRepository.refresh(telemetry.latitude, telemetry.longitude)
        }
    }
    LaunchedEffect(Unit) {
        app.islandGazetteerRepository.refresh()
        app.marineActivityPointRepository.loadIfNeeded()
        app.reefBoundaryRepository.loadIfNeeded()
    }
    LaunchedEffect(rtlMarineRoutesEnabled, isOnline) {
        if (rtlMarineRoutesEnabled) app.rtlMarineRouteRepository.refresh()
    }
    LaunchedEffect(showFishingHotspots) {
        if (!showFishingHotspots || hotspotCells.isNotEmpty()) return@LaunchedEffect
        isLoadingHotspots = true
        val positions = app.tripRepository.getSlowSpeedPositions(
            com.captainavi.app.data.repository.FishingHotspotAnalyzer.DEFAULT_MAX_SPEED_KNOTS,
        )
        hotspotCells = com.captainavi.app.data.repository.FishingHotspotAnalyzer.buildGrid(positions)
        isLoadingHotspots = false
    }
    LaunchedEffect(showFollowMeBoatsOverlay, isOnline) {
        if (!showFollowMeBoatsOverlay || !isOnline) return@LaunchedEffect
        while (true) {
            app.followMePublicBoatRepository.refresh()
            delay(com.captainavi.app.data.repository.FollowMePublicBoatRepository.MIN_REFRESH_INTERVAL_MS)
        }
    }
    LaunchedEffect(showFollowMeBoatsOverlay, followMePublicBoatState.error) {
        if (showFollowMeBoatsOverlay) {
            followMePublicBoatState.error?.let { error ->
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(selectedFollowMeBoat?.id) {
        val deviceId = selectedFollowMeBoat?.id ?: run {
            selectedFollowMeProfile = null
            isLoadingFollowMeProfile = false
            followMeProfileError = null
            return@LaunchedEffect
        }
        selectedFollowMeProfile = null
        followMeProfileError = null
        isLoadingFollowMeProfile = true
        app.followMePublicBoatRepository.getBoatProfile(deviceId).fold(
            onSuccess = { profile ->
                if (selectedFollowMeBoat?.id == deviceId) selectedFollowMeProfile = profile
            },
            onFailure = { error ->
                if (selectedFollowMeBoat?.id == deviceId) {
                    followMeProfileError = when {
                        error.message?.contains("was not found", ignoreCase = true) == true ->
                            "No public phone or vessel profile is listed for this device."
                        else -> "Could not refresh public contact details."
                    }
                }
            },
        )
        if (selectedFollowMeBoat?.id == deviceId) isLoadingFollowMeProfile = false
    }
    LaunchedEffect(followMePublicBoatState.boats, selectedFollowMeBoat?.id) {
        val selectedId = selectedFollowMeBoat?.id ?: return@LaunchedEffect
        followMePublicBoatState.boats.firstOrNull { it.id == selectedId }?.let { updatedBoat ->
            selectedFollowMeBoat = updatedBoat
        }
    }
    LaunchedEffect(showFollowMeBoatsOverlay) {
        if (!showFollowMeBoatsOverlay) {
            selectedFollowMeBoat = null
            followMeBoatChoices = emptyList()
        }
    }

    val coverageLatitude = savedMapLatitude
        ?: telemetry.latitude.takeIf { telemetry.hasGpsFix }
        ?: 4.1755
    val coverageLongitude = savedMapLongitude
        ?: telemetry.longitude.takeIf { telemetry.hasGpsFix }
        ?: 73.5093
    val coverageCellKey = mapCoverageProbe.cellKey(mapTileType, coverageLatitude, coverageLongitude)
    LaunchedEffect(mapTileType, coverageCellKey, isOnline) {
        val source = tileSourceFor(mapTileType)
        if (!mapTileType.hasVariableSatelliteCoverage()) {
            effectiveMapMaxZoom = source.maximumZoomLevel
            isCheckingMapCoverage = false
            return@LaunchedEffect
        }
        if (!isOnline) {
            isCheckingMapCoverage = false
            return@LaunchedEffect
        }

        effectiveMapMaxZoom = source.maximumZoomLevel

        delay(450)
        isCheckingMapCoverage = true
        try {
            mapCoverageProbe.findUsableMaxZoom(
                type = mapTileType,
                latitude = coverageLatitude,
                longitude = coverageLongitude,
            )?.let { detectedMax ->
                effectiveMapMaxZoom = detectedMax.coerceAtMost(source.maximumZoomLevel)
            }
        } finally {
            isCheckingMapCoverage = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapRuntime.mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapRuntime.mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapRuntime.mapView?.let(::saveViewport)
            // Navigation save/restore can retain the AndroidView after this effect
            // leaves composition. MapView.onDetach() is terminal, so release it
            // only from AndroidView.onRelease instead of invalidating a retained map.
        }
    }

    LaunchedEffect(
        telemetry.hasGpsFix,
        telemetry.latitude,
        telemetry.longitude,
    ) {
        if (!hasCenteredOnStartupLocation && telemetry.hasGpsFix) {
            mapRuntime.mapView?.let { map ->
                val vesselPosition = GeoPoint(telemetry.latitude, telemetry.longitude)
                savedMapLatitude = telemetry.latitude
                savedMapLongitude = telemetry.longitude
                map.controller.setCenter(vesselPosition)
                map.invalidate()
                hasCenteredOnStartupLocation = true
            }
        }
    }

    selectedIslandForContacts?.let { island ->
        IslandEmergencyContactsDialog(
            island = island,
            contacts = islandEmergencyDirectory.contactsFor(island),
            snapshotDate = islandEmergencyDirectory.snapshotDate,
            onDial = ::openPhoneDialer,
            onNavigate = { targetIsland ->
                MarineLocationService.setDestination(
                    NavigationDestination(
                        name = targetIsland.englishName,
                        latitude = targetIsland.latitude,
                        longitude = targetIsland.longitude,
                    )
                )
            },
            onDismiss = { selectedIslandForContacts = null },
        )
    }

    selectedFollowMeBoat?.let { boat ->
        val yourDistanceNm = if (telemetry.hasGpsFix) {
            NauticalMath.distanceNauticalMiles(
                telemetry.latitude,
                telemetry.longitude,
                boat.latitude,
                boat.longitude,
            )
        } else null
        FollowMeBoatDetailsDialog(
            boat = boat,
            profile = selectedFollowMeProfile,
            isLoadingProfile = isLoadingFollowMeProfile,
            profileError = followMeProfileError,
            yourDistanceNm = yourDistanceNm,
            onDial = ::openPhoneDialer,
            onMessage = ::openSmsComposer,
            onNavigate = {
                MarineLocationService.setDestination(
                    NavigationDestination(
                        name = boat.name,
                        latitude = boat.latitude,
                        longitude = boat.longitude,
                    )
                )
                selectedFollowMeBoat = null
                mapRuntime.followMePublicBoatsOverlay?.selectedBoatId = null
                mapRuntime.mapView?.invalidate()
            },
            onDismiss = {
                selectedFollowMeBoat = null
                mapRuntime.followMePublicBoatsOverlay?.selectedBoatId = null
                mapRuntime.mapView?.invalidate()
            },
        )
    }

    if (followMeBoatChoices.isNotEmpty()) {
        FollowMeBoatPickerDialog(
            boats = followMeBoatChoices,
            onSelect = { boat ->
                followMeBoatChoices = emptyList()
                selectedFollowMeBoat = boat
                mapRuntime.followMePublicBoatsOverlay?.selectedBoatId = boat.id
                mapRuntime.mapView?.invalidate()
            },
            onDismiss = {
                followMeBoatChoices = emptyList()
                mapRuntime.followMePublicBoatsOverlay?.selectedBoatId = null
                mapRuntime.mapView?.invalidate()
            },
        )
    }

    if (showFleetRadar) {
        FleetRadarDialog(onDismiss = { showFleetRadar = false })
    }

    if (showIslandSearch) {
        val islandResults = remember(islandQuery, islandGazetteerState.islands, placeSearchFilter) {
            if (placeSearchFilter == PlaceSearchFilter.ALL || placeSearchFilter == PlaceSearchFilter.ISLANDS) {
                searchIslandPlaces(islandGazetteerState.islands, islandQuery, limit = 20)
            } else emptyList()
        }
        val marineResults = remember(islandQuery, marineActivityPointState.points, placeSearchFilter) {
            val allowedTypes = when (placeSearchFilter) {
                PlaceSearchFilter.ALL -> MarineActivityPointType.entries.toSet()
                PlaceSearchFilter.ISLANDS -> emptySet()
                PlaceSearchFilter.FISHING -> setOf(
                    MarineActivityPointType.TUNA_FAD,
                    MarineActivityPointType.SPORT_FAD,
                )
                PlaceSearchFilter.DIVING -> setOf(MarineActivityPointType.DIVE_SITE)
            }
            searchMarineActivityPoints(
                points = marineActivityPointState.points,
                query = islandQuery,
                allowedTypes = allowedTypes,
                limit = 30,
            )
        }
        AlertDialog(
            onDismissRequest = { showIslandSearch = false },
            title = { Text("Find Maldives places", color = colors.accent) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = islandQuery,
                        onValueChange = { islandQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Island, FAD, dive site, or atoll") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (islandQuery.isNotBlank()) {
                            {
                                IconButton(onClick = { islandQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear place search")
                                }
                            }
                        } else null,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = placeSearchFilter == PlaceSearchFilter.ALL,
                            onClick = { placeSearchFilter = PlaceSearchFilter.ALL },
                            label = { Text("All") },
                        )
                        FilterChip(
                            selected = placeSearchFilter == PlaceSearchFilter.ISLANDS,
                            onClick = { placeSearchFilter = PlaceSearchFilter.ISLANDS },
                            label = { Text("Islands") },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = placeSearchFilter == PlaceSearchFilter.FISHING,
                            onClick = { placeSearchFilter = PlaceSearchFilter.FISHING },
                            label = { Text("Fishing FADs") },
                        )
                        FilterChip(
                            selected = placeSearchFilter == PlaceSearchFilter.DIVING,
                            onClick = { placeSearchFilter = PlaceSearchFilter.DIVING },
                            label = { Text("Dive sites") },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { app.settingsRepository.setFishingPointsEnabled(!fishingPointsEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show active FADs", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            Text(
                                "${marineActivityPointState.activeFadCount} official active points",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Switch(
                            checked = fishingPointsEnabled,
                            onCheckedChange = app.settingsRepository::setFishingPointsEnabled,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { app.settingsRepository.setDivePointsEnabled(!divePointsEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show dive sites", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            Text(
                                "${marineActivityPointState.diveSiteCount} open-data points",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Switch(
                            checked = divePointsEnabled,
                            onCheckedChange = app.settingsRepository::setDivePointsEnabled,
                        )
                    }
                    Text(
                        text = "${islandGazetteerState.islands.size} island names · ${marineActivityPointState.points.size} marine points available offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    if (islandGazetteerState.isLoading || marineActivityPointState.isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Loading Maldives place data…", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                    islandGazetteerState.errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.caution)
                    }
                    marineActivityPointState.errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.caution)
                    }
                    when {
                        islandQuery.isBlank() -> Text(
                            "Search in English or Thaana. Available FAD station codes and nearby islands are searchable too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        islandResults.isEmpty() && marineResults.isEmpty() &&
                            !islandGazetteerState.isLoading && !marineActivityPointState.isLoading -> Text(
                            "No matching place found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(islandResults, key = { "island-${it.id}" }) { island ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.background.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                                        .clickable { focusIsland(island) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(island.englishName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                    if (island.dhivehiName.isNotBlank()) {
                                        Text(island.dhivehiName, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
                                    }
                                    Text(
                                        "${island.atoll} · ${island.category.removeSuffix(" Island")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = islandCategoryAccent(island.category),
                                    )
                                }
                            }
                            items(marineResults, key = { "marine-${it.id}" }) { point ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.background.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                                        .clickable { focusMarinePoint(point) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(point.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                    Text(
                                        buildString {
                                            append(point.typeLabel)
                                            if (point.atoll.isNotBlank()) append(" · ${point.atoll}")
                                            if (point.reference.isNotBlank()) append(" · ${point.reference}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (point.isFishingPoint) colors.caution else colors.accent,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIslandSearch = false }) {
                    Text("Close", color = colors.accent)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(12.dp),
        )
    }

    if (showMarinePointDetails) {
        marineActivityPointState.points.firstOrNull { it.id == selectedMarinePointId }?.let { point ->
            AlertDialog(
                onDismissRequest = { showMarinePointDetails = false },
                title = { Text(point.name, color = colors.accent) },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            point.typeLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (point.isFishingPoint) colors.caution else colors.accent,
                        )
                        if (point.atoll.isNotBlank() || point.nearby.isNotBlank()) {
                            Text(
                                buildString {
                                    if (point.atoll.isNotBlank()) append(point.atoll)
                                    if (point.nearby.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append("Near ${point.nearby}")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                        }
                        if (point.detail.isNotBlank()) {
                            Text(point.detail, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        }
                        if (point.reference.isNotBlank()) {
                            Text("Reference: ${point.reference}", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        }
                        Text(
                            String.format(
                                java.util.Locale.US,
                                "Position: %.6f, %.6f",
                                point.latitude,
                                point.longitude,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            "Source: ${point.sourceLabel}${if (!point.isFishingPoint) " · ODbL" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                        Text(
                            if (point.isFishingPoint) {
                                "Active FAD status and position can change or drift. Recheck the official Fisheries list. This is not a safe-mooring or fish guarantee."
                            } else {
                                "Community dive data may be approximate or outdated. Confirm currents, depth, access, weather, training, and permits with a qualified local operator."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.caution,
                        )
                        Text(
                            "Not for primary navigation or obstruction clearance.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.emergency,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            MarineLocationService.setDestination(
                                NavigationDestination(
                                    name = point.name,
                                    latitude = point.latitude,
                                    longitude = point.longitude,
                                )
                            )
                            showMarinePointDetails = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    ) {
                        Text("Navigate", color = colors.onAccent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        focusMarinePoint(point, showDetails = false)
                        showMarinePointDetails = false
                    }) {
                        Text("Show on map", color = colors.accent)
                    }
                },
                containerColor = colors.surface,
                shape = RoundedCornerShape(12.dp),
            )
        }
    }

    if (showMapSourcePicker) {
        AlertDialog(
            onDismissRequest = { showMapSourcePicker = false },
            title = { Text("Maps & layers", color = colors.accent) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapTileType.entries.forEach { type ->
                        val source = tileSourceFor(type)
                        val metadata = type.metadata()
                        val selected = type == mapTileType
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) colors.accent.copy(alpha = 0.14f) else colors.background,
                                    RoundedCornerShape(10.dp),
                                )
                                .border(
                                    1.dp,
                                    if (selected) colors.accent else colors.border,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { selectMapSource(type) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    type.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) colors.accent else colors.textPrimary,
                                )
                                Text(
                                    if (selected && effectiveMapMaxZoom < source.maximumZoomLevel) {
                                        "usable here: z$effectiveMapMaxZoom"
                                    } else {
                                        "z${source.minimumZoomLevel}–${source.maximumZoomLevel}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) colors.caution else colors.textSecondary,
                                )
                            }
                            Text(metadata.dataKind, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                            Text(metadata.coverageNote, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        }
                    }
                    Text(
                        "OVERLAYS",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("RTL marine routes", color = colors.textPrimary)
                            Text(
                                when {
                                    rtlMarineRouteState.isLoading -> "Loading official ferry connections…"
                                    rtlMarineRouteState.routes.isEmpty() -> "Official Maldives ferry connections"
                                    else -> "${rtlMarineRouteState.routes.size} routes · cached for offline viewing"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Switch(
                            checked = rtlMarineRoutesEnabled,
                            onCheckedChange = app.settingsRepository::setRtlMarineRoutesEnabled,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Official reef boundaries", color = colors.textPrimary)
                            Text(
                                "Maldives OneMap polygons · available offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Switch(
                            checked = isReefOverlayEnabled,
                            onCheckedChange = app.settingsRepository::setReefOverlayEnabled,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sea marks", color = colors.textPrimary)
                            Text(
                                "Optional OpenSeaMap point overlay",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Switch(checked = isSeamarkEnabled, onCheckedChange = { isSeamarkEnabled = it })
                    }
                    Text(
                        "MAP DATA",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Basemaps: satellite and street (standard OpenStreetMap — bright water, land, and roads, unfiltered). Overlays: official Maldives reefs, official RTL ferry connections, and optional OpenSeaMap sea marks.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        "RTL lines connect published ferry stops; they are not surveyed navigation tracks. These maps are not official electronic navigational charts.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.emergency,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMapSourcePicker = false }) {
                    Text("Close", color = colors.accent)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(12.dp),
        )
    }

    if (showDataInfo) {
        val source = tileSourceFor(mapTileType)
        val metadata = mapTileType.metadata()
        AlertDialog(
            onDismissRequest = { showDataInfo = false },
            title = { Text("Marine data status", color = colors.accent) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${mapTileType.title} · usable zoom ${source.minimumZoomLevel}–$effectiveMapMaxZoom" +
                            if (isCheckingMapCoverage) " · checking local imagery…" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(metadata.dataKind, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    Text(metadata.coverageNote, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    Text(metadata.navigationLimit, style = MaterialTheme.typography.bodySmall, color = colors.caution)
                    Text(
                        "Island labels: ${islandGazetteerState.islands.size} bilingual records from Maldives OneMap / Geomatics registry; bundled for offline search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        "Reef awareness: ${reefBoundaryState.reefs.size} OneMap reef polygons, source archive ${ReefBoundaryRepository.SOURCE_ARCHIVE_DATE}; bundled for offline use. " +
                            if (reefWarningsEnabled) "Approach warning: ${reefWarningBufferMeters}m." else "Approach warnings are disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        ReefBoundaryRepository.SOURCE_ATTRIBUTION,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    reefBoundaryState.errorMessage?.let { error ->
                        Text("Reef dataset unavailable: $error", style = MaterialTheme.typography.bodySmall, color = colors.emergency)
                    }
                    Text(
                        "RTL ferry connections: ${rtlMarineRouteState.routes.size} official service routes" +
                            if (rtlMarineRouteState.isCached) " · last downloaded copy" else " · current online response",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        RtlMarineRouteRepository.SOURCE_ATTRIBUTION,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    rtlMarineRouteState.error?.let { error ->
                        Text("RTL route update unavailable: $error", style = MaterialTheme.typography.bodySmall, color = colors.caution)
                    }
                    Text(
                        "RTL route lines only join published transport stops. They do not describe safe water, channels, traffic separation, or a course to steer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.emergency,
                    )
                    Text(
                        "Reef polygons contain no surveyed depths, safety contours, chart datum, under-keel clearance, or navigation corrections.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.caution,
                    )
                    Text(
                        "Vessel aids: the solid amber predictor shows compass heading, or GPS course while moving. The solid cyan line is your recorded journey trace and is saved locally in Log when you tap Save trace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        "Depth: unavailable until a real NMEA/depth-sounder source is connected. A phone GPS and a bathymetry map cannot measure the live depth beneath the boat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.caution,
                    )
                    Text(
                        "Fishing points: ${marineActivityPointState.activeFadCount} unique active Fisheries FADs, snapshot ${marineActivityPointState.snapshotDate.ifBlank { "unavailable" }}. " +
                            "Dive points: ${marineActivityPointState.diveSiteCount} filtered OSM/OpenDiveMap sites; OSM snapshot ${marineActivityPointState.osmSnapshotDate.ifBlank { "unavailable" }}. Both layers work offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        MarineActivityPointRepository.SOURCE_ATTRIBUTION,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    marineActivityPointState.errorMessage?.let { error ->
                        Text("Fishing/dive dataset unavailable: $error", style = MaterialTheme.typography.bodySmall, color = colors.emergency)
                    }
                    Text(
                        "FADs can drift, be damaged, or be removed. Dive points can be approximate and do not confirm safe present conditions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.caution,
                    )
                    Text(
                        "Sea marks are optional OpenSeaMap community point data. AIS traffic and official ENC cells are not connected because they require an authorized provider.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        "Use current official charts, Notices to Mariners, and local warnings for navigation decisions.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.emergency,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDataInfo = false }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) {
                    Text("Understood", color = colors.onAccent)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(12.dp),
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = {
                Text(
                    text = LanguageManager.downloadOfflineMap,
                    color = colors.accent
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (LanguageManager.isDhivehi)
                            "މެޕުގެ މެދުގައި ހުރި ސަރަޙައްދުގެ ޓައިލްތައް ފޯނަށް ސޭވްކުރަންވީތަ؟"
                        else
                            "Download ${mapTileType.title} tiles around the current map centre. Choose cruising coverage or close harbour detail.",
                        color = colors.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !downloadHarbourDetail,
                            onClick = { downloadHarbourDetail = false },
                            label = { Text("10 NM · z15") },
                        )
                        FilterChip(
                            selected = downloadHarbourDetail,
                            onClick = { downloadHarbourDetail = true },
                            label = { Text("2 NM · detail") },
                        )
                    }
                    Text(
                        text = if (downloadHarbourDetail) {
                            "Harbour detail downloads up to this source's usable maximum zoom."
                        } else {
                            "Cruising coverage downloads zoom 8–15. Download each map source separately."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    if (isSeamarkEnabled) {
                        Text(
                            "The optional seamark layer will be saved with this area.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.caution,
                        )
                    }
                    if (!isOnline) {
                        Text(
                            "Connect to the internet before downloading. Existing saved tiles remain usable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.emergency,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = isOnline,
                    onClick = {
                        showDownloadDialog = false
                        mapRuntime.mapView?.let { mv ->
                            val lat = mv.mapCenter.latitude
                            val lon = mv.mapCenter.longitude
                            val delta = if (downloadHarbourDetail) 0.03 else 0.15
                            val bbox = BoundingBox(lat + delta, lon + delta, lat - delta, lon - delta)
                            val src = mv.tileProvider.tileSource
                            val requestedMaxZoom = if (downloadHarbourDetail) effectiveMapMaxZoom else 15
                            val zoomRange = offlineDownloadZoomRange(
                                sourceMinZoom = src.minimumZoomLevel,
                                sourceMaxZoom = minOf(src.maximumZoomLevel, effectiveMapMaxZoom),
                                preferredMaxZoom = requestedMaxZoom,
                            )
                            val cacheMgr = CacheManager(mv)
                            isDownloading = true
                            Toast.makeText(context, "Starting offline chart download…", Toast.LENGTH_SHORT).show()
                            if (isSeamarkEnabled) {
                                mapRuntime.seamarkProvider?.tileWriter?.let { writer ->
                                    val seamarkZoomRange = offlineDownloadZoomRange(
                                        sourceMinZoom = OpenSeaMapTileSource.minimumZoomLevel,
                                        sourceMaxZoom = OpenSeaMapTileSource.maximumZoomLevel,
                                        preferredMaxZoom = minOf(requestedMaxZoom, OpenSeaMapTileSource.maximumZoomLevel),
                                    )
                                    CacheManager(OpenSeaMapTileSource, writer, seamarkZoomRange.first, seamarkZoomRange.last).downloadAreaAsync(
                                        context, bbox, seamarkZoomRange.first, seamarkZoomRange.last,
                                        object : CacheManager.CacheManagerCallback {
                                            override fun onTaskComplete() {}
                                            override fun onTaskFailed(errors: Int) {}
                                            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                                            override fun downloadStarted() {}
                                            override fun setPossibleTilesInArea(total: Int) {}
                                        }
                                    )
                                }
                            }
                            cacheMgr.downloadAreaAsync(context, bbox, zoomRange.first, zoomRange.last, object : CacheManager.CacheManagerCallback {
                                override fun onTaskComplete() {
                                    isDownloading = false
                                    Toast.makeText(context, "Offline chart download complete", Toast.LENGTH_LONG).show()
                                }
                                override fun onTaskFailed(errors: Int) {
                                    isDownloading = false
                                    Toast.makeText(context, "Download finished with $errors tile retries", Toast.LENGTH_SHORT).show()
                                }
                                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                                override fun downloadStarted() {}
                                override fun setPossibleTilesInArea(total: Int) {}
                            })
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text(if (LanguageManager.isDhivehi) "ޑައުންލޯޑް" else "Download", color = colors.onAccent)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDownloadDialog = false }) {
                    Text(if (LanguageManager.isDhivehi) "ކެންސަލް" else "Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    setUseDataConnection(isOnline)
                    setBackgroundColor(AndroidColor.rgb(3, 31, 48))
                    isTilesScaledToDpi = true
                    val restoredSource = tileSourceFor(mapTileType)
                    setTileSource(restoredSource)
                    overlayManager.tilesOverlay.setColorFilter(colorFilterFor(mapTileType))
                    overlayManager.tilesOverlay.loadingBackgroundColor = AndroidColor.rgb(3, 31, 48)
                    overlayManager.tilesOverlay.loadingLineColor = AndroidColor.rgb(8, 52, 74)
                    minZoomLevel = maxOf(3.0, restoredSource.minimumZoomLevel.toDouble())
                    maxZoomLevel = effectiveMapMaxZoom.toDouble()
                    controller.setZoom(
                        clampMapZoom(
                            currentZoom = savedMapZoom,
                            sourceMinZoom = restoredSource.minimumZoomLevel,
                            sourceMaxZoom = effectiveMapMaxZoom,
                        )
                    )
                    val startPoint = if (telemetry.hasGpsFix) {
                        GeoPoint(telemetry.latitude, telemetry.longitude)
                    } else if (savedMapLatitude != null && savedMapLongitude != null) {
                        GeoPoint(savedMapLatitude!!, savedMapLongitude!!)
                    } else GeoPoint(4.1755, 73.5093)
                    controller.setCenter(startPoint)
                    mapOrientation = savedMapOrientation
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            saveViewport(this@apply)
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            saveViewport(this@apply)
                            return false
                        }
                    })
                    val seaMarks = MapTileProviderBasic(ctx, OpenSeaMapTileSource)
                    seaMarks.setUseDataConnection(isOnline)
                    mapRuntime.seamarkProvider = seaMarks
                    val seaMarkTiles = TilesOverlay(seaMarks, ctx).apply {
                        isEnabled = isSeamarkEnabled
                        loadingBackgroundColor = AndroidColor.TRANSPARENT
                        loadingLineColor = AndroidColor.TRANSPARENT
                    }
                    mapRuntime.seamarkOverlay = seaMarkTiles
                    overlayManager.add(seaMarkTiles)
                    val fishingHotspots = FishingHotspotOverlay(ctx).apply {
                        cells = hotspotCells
                        isEnabled = showFishingHotspots
                    }
                    mapRuntime.fishingHotspotOverlay = fishingHotspots
                    overlayManager.add(fishingHotspots)
                    val rtlRoutes = RtlMarineRoutesOverlay(ctx).apply {
                        routes = rtlMarineRouteState.routes
                        isEnabled = rtlMarineRoutesEnabled
                    }
                    mapRuntime.rtlMarineRoutesOverlay = rtlRoutes
                    overlayManager.add(rtlRoutes)
                    val reefBoundaries = ReefBoundaryOverlay(ctx).apply {
                        reefs = reefBoundaryState.reefs
                        islands = islandGazetteerState.islands
                        isEnabled = isReefOverlayEnabled
                    }
                    mapRuntime.reefBoundaryOverlay = reefBoundaries
                    overlayManager.add(reefBoundaries)
                    val marinePoints = MarineActivityPointsOverlay(ctx).apply {
                        points = marineActivityPointState.points
                        showFishingPoints = fishingPointsEnabled
                        showDivePoints = divePointsEnabled
                        selectedPointId = selectedMarinePointId
                        onPointTap = { point ->
                            selectedMarinePointId = point.id
                            showMarinePointDetails = true
                        }
                    }
                    mapRuntime.marineActivityPointsOverlay = marinePoints
                    overlayManager.add(marinePoints)
                    val followMeBoats = FollowMePublicBoatsOverlay(ctx).apply {
                        boats = followMePublicBoatState.boats
                        isEnabled = showFollowMeBoatsOverlay
                        selectedBoatId = selectedFollowMeBoat?.id
                        onBoatsTap = ::openFollowMeSelection
                    }
                    mapRuntime.followMePublicBoatsOverlay = followMeBoats

                    val flowOverlay = MarineFlowOverlay(ctx, this).apply {
                        flowMode = flowLayerMode
                        marineConditionsState.conditions?.let { c ->
                            c.windSpeedKnots?.let { windSpeedKnots = it }
                            c.windDirectionDegrees?.let { windDirectionDegrees = it }
                            c.oceanCurrentKnots?.let { oceanCurrentKnots = it }
                            c.oceanCurrentDirectionDegrees?.let { oceanCurrentDirectionDegrees = it }
                        }
                    }
                    mapRuntime.flowOverlay = flowOverlay
                    overlayManager.add(flowOverlay)
                    overlayManager.add(ScaleBarOverlay(this).apply {
                        setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.nautical)
                        setAlignBottom(true)
                        setAlignRight(true)
                        setScaleBarOffset((14 * resources.displayMetrics.density).roundToInt(), (14 * resources.displayMetrics.density).roundToInt())
                        setTextSize(12 * resources.displayMetrics.density * resources.configuration.fontScale)
                        setLineWidth(2 * resources.displayMetrics.density)
                        drawLatitudeScale(true)
                        drawLongitudeScale(false)
                        setEnableAdjustLength(true)
                        barPaint.color = AndroidColor.WHITE
                        textPaint.color = AndroidColor.WHITE
                        setBackgroundPaint(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = AndroidColor.argb(170, 2, 22, 34)
                            style = Paint.Style.FILL
                        })
                    })
                    overlayManager.add(RotationGestureOverlay(this).apply { isEnabled = true })

                    // Long-press to navigate to a point on the map
                    overlayManager.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                MarineLocationService.setDestination(NavigationDestination(
                                    name = "Map (${String.format(java.util.Locale.US, "%.3f", p.latitude)}, ${String.format(java.util.Locale.US, "%.3f", p.longitude)})",
                                    latitude = p.latitude, longitude = p.longitude
                                ))
                            }
                            return true
                        }
                    }))

                    // Recorded journey trace
                    val polyline = Polyline(this).apply {
                        outlinePaint.color = accentArgb; outlinePaint.strokeWidth = 8f
                    }
                    overlayManager.add(polyline)
                    mapRuntime.routePolyline = polyline

                    // Forward heading predictor: dark casing plus a solid amber line.
                    val headingShadow = Polyline(this).apply {
                        outlinePaint.color = AndroidColor.argb(180, 1, 17, 28)
                        outlinePaint.strokeWidth = 12f
                        isEnabled = false
                    }
                    overlayManager.add(headingShadow)
                    mapRuntime.headingLineShadow = headingShadow
                    val headingLine = Polyline(this).apply {
                        outlinePaint.color = headingArgb
                        outlinePaint.strokeWidth = 7f
                        isEnabled = false
                    }
                    overlayManager.add(headingLine)
                    mapRuntime.headingLine = headingLine

                    // Navigation line (dashed magenta)
                    val nLine = Polyline(this).apply {
                        outlinePaint.color = destArgb
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 15f), 0f)
                    }
                    overlayManager.add(nLine)
                    mapRuntime.navLine = nLine

                    // Anchor watch circle
                    val aCircle = Polygon(this).apply {
                        fillPaint.color = AndroidColor.argb(40, 255, 183, 3)
                        outlinePaint.color = AndroidColor.rgb(255, 183, 3)
                        outlinePaint.strokeWidth = 3f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
                    }
                    overlayManager.add(aCircle)
                    mapRuntime.anchorCircle = aCircle

                    // Destination marker
                    val dMkr = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createCircleMarker(ctx, destArgb, 28)
                        title = "Destination"
                    }
                    overlayManager.add(dMkr)
                    mapRuntime.destMarker = dMkr

                    // Boat
                    val bMarker = Marker(this).apply {
                        position = startPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setFlat(true)
                        icon = createBoatArrowMarker(ctx, 40)
                        title = "Your Fishing Boat"
                    }
                    overlayManager.add(bMarker)
                    mapRuntime.boatMarker = bMarker

                    // Large vessel hit zones make the crowded harbour easy to tap.
                    // Island labels remain above them so tapping visible island text
                    // always opens the island instead of a nearby vessel.
                    overlayManager.add(followMeBoats)

                    val islandLabels = IslandLabelsOverlay(ctx).apply {
                        islands = islandGazetteerState.islands
                        selectedIslandId = selectedIslandId
                        onIslandTap = { island ->
                            selectedFollowMeBoat = null
                            followMeBoatChoices = emptyList()
                            selectedIslandId = island.id
                            selectedIslandForContacts = island
                        }
                    }
                    mapRuntime.islandLabelsOverlay = islandLabels
                    overlayManager.add(islandLabels)

                    mapRuntime.mapView = this
                }
            },
            update = { map ->
                val expectedSource = tileSourceFor(mapTileType)
                val desiredMaxZoom = minOf(expectedSource.maximumZoomLevel, effectiveMapMaxZoom)
                if (map.tileProvider.tileSource.name() != expectedSource.name()) {
                    map.setTileSource(expectedSource)
                }
                map.overlayManager.tilesOverlay.setColorFilter(colorFilterFor(mapTileType))
                if (map.useDataConnection() != isOnline) {
                    map.setUseDataConnection(isOnline)
                }
                mapRuntime.seamarkProvider?.let { provider ->
                    if (provider.useDataConnection() != isOnline) {
                        provider.setUseDataConnection(isOnline)
                    }
                }
                val targetZoom = clampMapZoom(
                    currentZoom = map.zoomLevelDouble,
                    sourceMinZoom = expectedSource.minimumZoomLevel,
                    sourceMaxZoom = desiredMaxZoom,
                )
                map.minZoomLevel = maxOf(3.0, expectedSource.minimumZoomLevel.toDouble())
                map.maxZoomLevel = desiredMaxZoom.toDouble()
                if (map.zoomLevelDouble != targetZoom) {
                    map.controller.setZoom(targetZoom)
                    savedMapZoom = targetZoom
                }

                mapRuntime.seamarkOverlay?.let { overlay ->
                    if (overlay.isEnabled != isSeamarkEnabled) {
                        overlay.isEnabled = isSeamarkEnabled
                    }
                }
                mapRuntime.fishingHotspotOverlay?.let { overlay ->
                    overlay.cells = hotspotCells
                    overlay.isEnabled = showFishingHotspots
                }
                mapRuntime.rtlMarineRoutesOverlay?.let { overlay ->
                    overlay.routes = rtlMarineRouteState.routes
                    overlay.isEnabled = rtlMarineRoutesEnabled
                }
                mapRuntime.islandLabelsOverlay?.let { overlay ->
                    overlay.islands = islandGazetteerState.islands
                    overlay.selectedIslandId = selectedIslandId
                    overlay.onIslandTap = { island ->
                        selectedFollowMeBoat = null
                        followMeBoatChoices = emptyList()
                        selectedIslandId = island.id
                        selectedIslandForContacts = island
                    }
                }
                mapRuntime.reefBoundaryOverlay?.let { overlay ->
                    overlay.reefs = reefBoundaryState.reefs
                    overlay.islands = islandGazetteerState.islands
                    overlay.isEnabled = isReefOverlayEnabled
                }
                mapRuntime.marineActivityPointsOverlay?.let { overlay ->
                    overlay.points = marineActivityPointState.points
                    overlay.showFishingPoints = fishingPointsEnabled
                    overlay.showDivePoints = divePointsEnabled
                    overlay.selectedPointId = selectedMarinePointId
                    overlay.onPointTap = { point ->
                        selectedMarinePointId = point.id
                        showMarinePointDetails = true
                    }
                }
                mapRuntime.followMePublicBoatsOverlay?.let { overlay ->
                    overlay.boats = followMePublicBoatState.boats
                    overlay.isEnabled = showFollowMeBoatsOverlay
                    overlay.selectedBoatId = selectedFollowMeBoat?.id
                    overlay.onBoatsTap = ::openFollowMeSelection
                }
                mapRuntime.flowOverlay?.let { flow ->
                    flow.flowMode = flowLayerMode
                    marineConditionsState.conditions?.let { c ->
                        c.windSpeedKnots?.let { flow.windSpeedKnots = it }
                        c.windDirectionDegrees?.let { flow.windDirectionDegrees = it }
                        c.oceanCurrentKnots?.let { flow.oceanCurrentKnots = it }
                        c.oceanCurrentDirectionDegrees?.let { flow.oceanCurrentDirectionDegrees = it }
                    }
                }

                val currentHeading = if (telemetry.compassAvailable) telemetry.compassHeadingDegrees else telemetry.bearingDegrees
                val boatPos = GeoPoint(telemetry.latitude, telemetry.longitude)
                mapRuntime.boatMarker?.isEnabled = telemetry.hasGpsFix
                mapRuntime.boatMarker?.position = boatPos
                mapRuntime.boatMarker?.rotation = -currentHeading

                mapRuntime.routePolyline?.let { route ->
                    route.isEnabled = traceCoordinates.size >= 2
                    if (route.isEnabled) {
                        route.setPoints(traceCoordinates.map { GeoPoint(it.latitude, it.longitude) })
                    }
                }

                if (
                    savedTraceLoaded &&
                    loadedTraceTripId == visibleTraceTripId &&
                    mapRuntime.fittedTraceTripId != loadedTraceTripId &&
                    traceCoordinates.isNotEmpty()
                ) {
                    val tracePoints = traceCoordinates.map { GeoPoint(it.latitude, it.longitude) }
                    if (tracePoints.size == 1) {
                        map.controller.setZoom(maxOf(map.zoomLevelDouble, 15.0))
                        map.controller.animateTo(tracePoints.first())
                    } else {
                        val boundingBox = BoundingBox(
                            tracePoints.maxOf(GeoPoint::getLatitude),
                            tracePoints.maxOf(GeoPoint::getLongitude),
                            tracePoints.minOf(GeoPoint::getLatitude),
                            tracePoints.minOf(GeoPoint::getLongitude),
                        )
                        map.zoomToBoundingBox(
                            boundingBox,
                            true,
                            (56 * map.resources.displayMetrics.density).roundToInt(),
                        )
                    }
                    mapRuntime.fittedTraceTripId = loadedTraceTripId
                }

                val headingLineAvailable = telemetry.hasGpsFix && (
                    telemetry.compassAvailable || telemetry.speedKnots >= MIN_GPS_COURSE_SPEED_KNOTS
                    )
                if (headingLineAvailable) {
                    val predictorDistanceMeters = headingLineDistanceMeters(
                        visibleNorthLatitude = map.boundingBox.latNorth,
                        visibleSouthLatitude = map.boundingBox.latSouth,
                        visibleEastLongitude = map.boundingBox.lonEast,
                        visibleWestLongitude = map.boundingBox.lonWest,
                    )
                    val endpoint = projectHeadingEndpoint(
                        start = TraceCoordinate(telemetry.latitude, telemetry.longitude),
                        headingDegrees = currentHeading.toDouble(),
                        distanceMeters = predictorDistanceMeters,
                    )
                    val headingPoints = listOf(boatPos, GeoPoint(endpoint.latitude, endpoint.longitude))
                    mapRuntime.headingLineShadow?.apply {
                        isEnabled = true
                        setPoints(headingPoints)
                    }
                    mapRuntime.headingLine?.apply {
                        isEnabled = true
                        setPoints(headingPoints)
                    }
                } else {
                    mapRuntime.headingLineShadow?.isEnabled = false
                    mapRuntime.headingLine?.isEnabled = false
                }

                // Navigation bearing line to destination
                val dest = telemetry.activeDestination
                if (dest != null && dest.latitude != 0.0) {
                    mapRuntime.navLine?.isEnabled = true
                    mapRuntime.navLine?.outlinePaint?.color = destArgb
                    mapRuntime.navLine?.setPoints(listOf(boatPos, GeoPoint(dest.latitude, dest.longitude)))
                    mapRuntime.destMarker?.isEnabled = true
                    mapRuntime.destMarker?.position = GeoPoint(dest.latitude, dest.longitude)
                    mapRuntime.destMarker?.title = dest.name
                    mapRuntime.destMarker?.snippet = String.format(
                        java.util.Locale.US,
                        "%.1f NM · %,.0f MVR · %.1f L · ETA %s",
                        destinationTripEstimate.distanceNauticalMiles,
                        destinationTripEstimate.costMvr,
                        destinationTripEstimate.fuelLiters,
                        NauticalMath.formatEta(telemetry.etaMinutes),
                    )
                } else {
                    mapRuntime.navLine?.isEnabled = false
                    mapRuntime.destMarker?.isEnabled = false
                }

                // Anchor watch circle
                if (anchorState.isActive) {
                    val anchorPos = GeoPoint(anchorState.anchorLat, anchorState.anchorLon)
                    mapRuntime.anchorCircle?.isEnabled = true
                    mapRuntime.anchorCircle?.points = Polygon.pointsAsCircle(anchorPos, anchorState.swingRadiusMeters)
                    if (anchorState.isDragging) {
                        mapRuntime.anchorCircle?.outlinePaint?.color = AndroidColor.RED
                        mapRuntime.anchorCircle?.fillPaint?.color = AndroidColor.argb(50, 255, 0, 0)
                    } else {
                        mapRuntime.anchorCircle?.outlinePaint?.color = AndroidColor.rgb(255, 183, 3)
                        mapRuntime.anchorCircle?.fillPaint?.color = AndroidColor.argb(40, 255, 183, 3)
                    }
                } else {
                    mapRuntime.anchorCircle?.isEnabled = false
                }

                if (headingUpMode && (telemetry.compassAvailable || (telemetry.hasGpsFix && telemetry.speedKnots > 1.0))) {
                    val targetOrientation = -currentHeading
                    if (kotlin.math.abs(map.mapOrientation - targetOrientation) > 0.5f) {
                        map.mapOrientation = targetOrientation
                    }
                }

                if (mapRuntime.renderedWaypointList !== waypoints) {
                    mapRuntime.waypointOverlays.forEach { map.overlayManager.remove(it) }
                    mapRuntime.waypointOverlays = buildWaypointOverlays(
                        context = context,
                        mapView = map,
                        waypoints = waypoints,
                        onWaypointTap = DestinationState::lockDestination,
                    )
                    mapRuntime.renderedWaypointList = waypoints
                }
                map.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { map ->
                if (mapRuntime.mapView === map) {
                    mapRuntime.seamarkProvider?.detach()
                    mapRuntime.clear()
                }
                map.onDetach()
            },
        )

        TripTelemetryHeader(
            elapsedSeconds = traceElapsedSeconds,
            speedKnots = telemetry.speedKnots,
            distanceNauticalMiles = traceDistanceNm,
            traceActive = traceActive,
            destinationName = telemetry.activeDestination?.name,
            distanceToDestinationNm = telemetry.distToDestNm,
            etaMinutes = telemetry.etaMinutes,
            estimatedCostMvr = destinationTripEstimate.costMvr,
            estimatedFuelLiters = destinationTripEstimate.fuelLiters,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 98.dp, end = 12.dp, bottom = 72.dp),
            color = colors.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.72f)),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(6.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MapActionButton(
                onClick = { showIslandSearch = true },
                contentColor = colors.accent,
                contentDescription = "Search islands, fishing FADs, and dive sites"
            ) {
                Icon(Icons.Default.Search, null)
            }
                MapActionButton(
                onClick = {
                    headingUpMode = !headingUpMode
                    if (!headingUpMode) {
                        mapRuntime.mapView?.mapOrientation = 0f
                        mapRuntime.mapView?.invalidate()
                    }
                },
                selected = headingUpMode,
                selectedColor = colors.caution,
                contentDescription = "Heading up"
            ) {
                Icon(if (headingUpMode) Icons.Default.Navigation else Icons.Default.Explore, null)
            }
                MapActionButton(
                onClick = { showMapSourcePicker = true },
                contentColor = colors.caution,
                contentDescription = "Choose map source"
            ) {
                Icon(Icons.Default.Layers, null)
            }
                MapActionButton(
                onClick = { showFleetRadar = true },
                contentColor = colors.accent,
                contentDescription = "Open fleet radar"
            ) {
                Icon(Icons.Default.TrackChanges, null)
            }
                MapActionButton(
                onClick = { showMoreChartTools = !showMoreChartTools },
                selected = showMoreChartTools,
                selectedColor = colors.accent,
                contentDescription = if (showMoreChartTools) "Hide additional chart tools" else "Show additional chart tools",
            ) {
                Icon(Icons.Default.MoreVert, null)
            }

                if (showMoreChartTools) {
                    MapActionButton(
                    onClick = {
                        if (app.settingsRepository.trustedSmsNumber.value.isBlank()) {
                            Toast.makeText(
                                context,
                                "Add a trusted SMS number in Configuration first",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else if (app.offlineSmsMessenger.hasSendPermission()) {
                            val result = app.offlineSmsMessenger.sendConfiguredLocation()
                            Toast.makeText(
                                context,
                                result.message,
                                if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            quickSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    },
                    contentColor = colors.success,
                    contentDescription = "Send offline location SMS",
                ) {
                    Icon(Icons.Default.Sms, null)
                }
                    MapActionButton(
                    onClick = {
                        if (isOnline) {
                            showDownloadDialog = true
                        } else {
                            Toast.makeText(context, "Offline: using saved map tiles", Toast.LENGTH_SHORT).show()
                        }
                    },
                    selected = isDownloading,
                    selectedColor = colors.caution,
                    contentDescription = "Download offline chart",
                ) {
                    Icon(Icons.Default.CloudDownload, null)
                }
                    MapActionButton(
                    onClick = {
                        val next = flowLayerMode.next()
                        flowLayerMode = next
                        mapRuntime.flowOverlay?.flowMode = next
                        mapRuntime.mapView?.invalidate()
                        val msg = when (next) {
                            FlowLayerMode.WIND -> "Wind flow active"
                            FlowLayerMode.CURRENT -> "Ocean current flow active"
                            FlowLayerMode.BOTH -> "Wind & Current flow active"
                            FlowLayerMode.OFF -> "Flow overlay off"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    selected = flowLayerMode != FlowLayerMode.OFF,
                    selectedColor = when (flowLayerMode) {
                        FlowLayerMode.CURRENT -> colors.success
                        FlowLayerMode.BOTH -> colors.caution
                        else -> colors.accent
                    },
                    contentDescription = "Toggle wind and ocean current flow animation",
                ) {
                    Icon(
                        when (flowLayerMode) {
                            FlowLayerMode.CURRENT -> Icons.Default.Waves
                            else -> Icons.Default.Air
                        },
                        null,
                    )
                }
                    MapActionButton(
                    onClick = {
                        if (showFollowMeBoatsOverlay) {
                            showFollowMeBoatsOverlay = false
                        } else if (isOnline) {
                            showFollowMeBoatsOverlay = true
                        } else {
                            Toast.makeText(context, "FollowMe public boats require internet", Toast.LENGTH_LONG).show()
                        }
                    },
                    selected = showFollowMeBoatsOverlay,
                    selectedColor = colors.success,
                    contentDescription = "Toggle FollowMe public boats on chart",
                ) {
                    Icon(Icons.Default.DirectionsBoat, null)
                }
                    MapActionButton(
                    onClick = { showFishingHotspots = !showFishingHotspots },
                    selected = showFishingHotspots,
                    selectedColor = colors.caution,
                    contentDescription = if (showFishingHotspots) {
                        "Hide fishing hotspot heatmap from your trip history"
                    } else {
                        "Show fishing hotspot heatmap from your trip history"
                    },
                ) {
                    if (isLoadingHotspots) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.caution)
                    } else {
                        Icon(Icons.Default.Whatshot, null)
                    }
                }
                    MapActionButton(
                    onClick = { showDataInfo = true },
                    contentColor = colors.textSecondary,
                    contentDescription = "Marine data status",
                ) {
                    Icon(Icons.Default.Info, null)
                }
                }

                if (telemetry.activeDestination != null) {
                    MapActionButton(
                    onClick = {
                        DestinationState.clearDestination()
                        Toast.makeText(context, "Destination marker cleared", Toast.LENGTH_SHORT).show()
                    },
                    selected = true,
                    selectedColor = colors.emergency,
                    contentDescription = "Cancel destination marker",
                ) {
                    Icon(Icons.Default.Close, null)
                }
                }

                MapActionButton(
                onClick = {
                    mapRuntime.mapView?.controller?.animateTo(GeoPoint(telemetry.latitude, telemetry.longitude))
                },
                selected = true,
                selectedColor = colors.accent,
                contentDescription = "Center on vessel"
                ) {
                    Icon(Icons.Default.MyLocation, null)
                }
            }
        }

        JourneyTraceControl(
            traceActive = traceActive,
            savedTraceLoaded = savedTraceLoaded,
            busy = traceControlBusy,
            onClick = {
                when {
                    traceActive -> saveJourneyTrace()
                    savedTraceLoaded -> {
                        SavedTraceState.clear()
                        mapRuntime.fittedTraceTripId = null
                        mapRuntime.mapView?.invalidate()
                    }
                    else -> startJourneyTrace()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

private enum class PlaceSearchFilter {
    ALL,
    ISLANDS,
    FISHING,
    DIVING,
}


private class MapRuntimeRefs {
    var mapView: MapView? = null
    var seamarkProvider: MapTileProviderBasic? = null
    var seamarkOverlay: TilesOverlay? = null
    var rtlMarineRoutesOverlay: RtlMarineRoutesOverlay? = null
    var reefBoundaryOverlay: ReefBoundaryOverlay? = null
    var marineActivityPointsOverlay: MarineActivityPointsOverlay? = null
    var islandLabelsOverlay: IslandLabelsOverlay? = null
    var followMePublicBoatsOverlay: FollowMePublicBoatsOverlay? = null
    var fishingHotspotOverlay: FishingHotspotOverlay? = null
    var flowOverlay: MarineFlowOverlay? = null
    var fittedTraceTripId: String? = null
    var boatMarker: Marker? = null
    var routePolyline: Polyline? = null
    var headingLineShadow: Polyline? = null
    var headingLine: Polyline? = null
    var navLine: Polyline? = null
    var destMarker: Marker? = null
    var anchorCircle: Polygon? = null
    var waypointOverlays: List<Overlay> = emptyList()
    var renderedWaypointList: List<WaypointEntity>? = null

    fun clear() {
        mapView = null
        seamarkProvider = null
        seamarkOverlay = null
        rtlMarineRoutesOverlay = null
        reefBoundaryOverlay = null
        marineActivityPointsOverlay = null
        islandLabelsOverlay = null
        followMePublicBoatsOverlay = null
        flowOverlay = null
        fittedTraceTripId = null
        boatMarker = null
        routePolyline = null
        headingLineShadow = null
        headingLine = null
        navLine = null
        destMarker = null
        anchorCircle = null
        waypointOverlays = emptyList()
        renderedWaypointList = null
    }
}

private const val MIN_GPS_COURSE_SPEED_KNOTS = 0.8

@Composable
private fun TripTelemetryHeader(
    elapsedSeconds: Long,
    speedKnots: Double,
    distanceNauticalMiles: Double,
    traceActive: Boolean,
    destinationName: String?,
    distanceToDestinationNm: Double,
    etaMinutes: Double,
    estimatedCostMvr: Double,
    estimatedFuelLiters: Double,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    val navigationActive = !destinationName.isNullOrBlank()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp),
        color = colors.surface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationActive) {
            NavigationInstrumentCell(
                value = NauticalMath.formatEta(etaMinutes),
                label = "ETA",
                accent = colors.destination,
                modifier = Modifier.weight(1f),
            )
            InstrumentDivider(colors.border)
            NavigationInstrumentCell(
                value = String.format(java.util.Locale.US, "%.1f", speedKnots),
                unit = "kt",
                label = "SPEED",
                accent = colors.accent,
                modifier = Modifier.weight(1f),
            )
            InstrumentDivider(colors.border)
            NavigationInstrumentCell(
                value = String.format(java.util.Locale.US, "%.1f", distanceToDestinationNm),
                unit = "NM",
                label = "DISTANCE",
                accent = colors.destination,
                modifier = Modifier.weight(1f),
            )
            InstrumentDivider(colors.border)
            FuelCostInstrumentCell(
                fuelLiters = estimatedFuelLiters,
                costMvr = estimatedCostMvr,
                modifier = Modifier.weight(1f),
            )
            } else {
            InstrumentCell(
                value = formatTraceElapsed(elapsedSeconds),
                unit = null,
                label = "TOTAL TIME",
                accent = if (traceActive) colors.success else colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            InstrumentDivider(colors.border)
            InstrumentCell(
                value = String.format(java.util.Locale.US, "%.1f", speedKnots),
                unit = "kt",
                label = "SPEED",
                accent = colors.accent,
                modifier = Modifier.weight(1f),
            )
            InstrumentDivider(colors.border)
            InstrumentCell(
                value = String.format(java.util.Locale.US, "%.2f", distanceNauticalMiles),
                unit = "NM",
                label = "DISTANCE",
                accent = colors.caution,
                modifier = Modifier.weight(1f),
            )
            }
        }
    }
}

@Composable
private fun FuelCostInstrumentCell(
    fuelLiters: Double,
    costMvr: Double,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    Column(
        modifier = modifier
            .semantics {
                contentDescription = String.format(
                    java.util.Locale.US,
                    "Fuel %.1f Liters, Cost %,.0f MVR",
                    fuelLiters,
                    costMvr,
                )
            }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%,.1f", fuelLiters),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "L",
                style = MaterialTheme.typography.labelSmall,
                color = colors.success,
                modifier = Modifier.padding(bottom = 2.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(1.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%,.0f", costMvr),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "MVR",
                style = MaterialTheme.typography.labelSmall,
                color = colors.caution,
                modifier = Modifier.padding(bottom = 1.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = "FUEL · COST",
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun InstrumentDivider(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(color))
}

@Composable
private fun NavigationInstrumentCell(
    value: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    Column(
        modifier = modifier
            .semantics {
                contentDescription = buildString {
                    append(label)
                    append(' ')
                    append(value)
                    if (!unit.isNullOrBlank()) {
                        append(' ')
                        append(unit)
                    }
                }
            }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = if (value.length > 5) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MarineTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            unit?.let {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MarineTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun InstrumentCell(
    value: String,
    unit: String?,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    compactValue: Boolean = false,
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = if (compactValue) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
                color = MarineTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            unit?.let {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MarineTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun JourneyTraceControl(
    traceActive: Boolean,
    savedTraceLoaded: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = modifier
            .width(172.dp)
            .height(54.dp)
            .semantics {
                contentDescription = when {
                    traceActive -> "Save journey trace to Log"
                    savedTraceLoaded -> "Close loaded saved path"
                    else -> "Start journey trace"
                }
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                traceActive -> colors.caution
                savedTraceLoaded -> colors.home
                else -> colors.accent
            },
            contentColor = colors.onAccent,
            disabledContainerColor = colors.card,
            disabledContentColor = colors.textSecondary,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.textPrimary,
            )
        } else {
            Icon(
                imageVector = when {
                    traceActive -> Icons.Default.Save
                    savedTraceLoaded -> Icons.Default.Close
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                traceActive -> "Save trace"
                savedTraceLoaded -> "Close path"
                else -> "Start trace"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MapActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    selected: Boolean = false,
    selectedColor: androidx.compose.ui.graphics.Color? = null,
    contentColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit
) {
    val colors = MarineTheme.colors
    val activeColor = selectedColor ?: colors.accent
    val iconColor = contentColor ?: colors.accent
    Box(
        modifier = Modifier
            .size(46.dp)
            .semantics { this.contentDescription = contentDescription }
            .background(
                if (selected) activeColor.copy(alpha = 0.2f) else colors.card.copy(alpha = 0.72f),
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                if (selected) activeColor.copy(alpha = 0.85f) else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides if (selected) activeColor else iconColor
            ) {
                Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) { content() }
            }
        }
    }
}

private fun buildWaypointOverlays(
    context: Context,
    mapView: MapView,
    waypoints: List<WaypointEntity>,
    onWaypointTap: (WaypointEntity) -> Unit,
): List<org.osmdroid.views.overlay.Overlay> {
    val added = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    waypoints.forEach { wp ->
        val pos = GeoPoint(wp.latitude, wp.longitude)
        if (wp.type == WaypointType.DANGER_REEF) {
            val circlePolygon = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(pos, wp.radiusMeters)
                fillPaint.color = AndroidColor.argb(80, 255, 30, 80)
                outlinePaint.color = AndroidColor.RED; outlinePaint.strokeWidth = 3f
                title = wp.name; snippet = wp.description
            }
            mapView.overlayManager.add(circlePolygon); added.add(circlePolygon)
        } else {
            val color = when (wp.type) {
                WaypointType.HOME -> AndroidColor.rgb(0, 230, 118)
                WaypointType.HARBOUR -> AndroidColor.CYAN
                WaypointType.FISHING_SPOT -> AndroidColor.rgb(255, 183, 3)
                else -> AndroidColor.WHITE
            }
            val marker = Marker(mapView).apply {
                position = pos; title = wp.name; snippet = wp.description
                icon = createCircleMarker(context, color, 24); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { tappedMarker, _ ->
                    onWaypointTap(wp)
                    tappedMarker.showInfoWindow()
                    true
                }
            }
            mapView.overlayManager.add(marker); added.add(marker)
        }
    }
    return added
}

private fun createBoatArrowMarker(context: Context, sizeDp: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(0, 229, 255); style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 2 * density; strokeJoin = Paint.Join.ROUND }
    val hull = android.graphics.Path().apply { moveTo(px * 0.5f, px * 0.08f); lineTo(px * 0.82f, px * 0.85f); lineTo(px * 0.5f, px * 0.62f); lineTo(px * 0.18f, px * 0.85f); close() }
    canvas.drawPath(hull, fill); canvas.drawPath(hull, stroke)
    return BitmapDrawable(context.resources, bitmap)
}

private fun createCircleMarker(context: Context, color: Int, sizeDp: Int): Drawable {
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = AndroidColor.WHITE; style = Paint.Style.STROKE; strokeWidth = (2 * context.resources.displayMetrics.density) }
    val radius = (px / 2f) - (2 * context.resources.displayMetrics.density)
    canvas.drawCircle(px / 2f, px / 2f, radius, paint); canvas.drawCircle(px / 2f, px / 2f, radius, strokePaint)
    return BitmapDrawable(context.resources, bitmap)
}
