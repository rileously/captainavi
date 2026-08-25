package com.captainavi.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.MainActivity
import com.captainavi.app.R
import com.captainavi.app.data.local.entity.AlertType
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.safety.AdaptiveGpsIntervalPolicy
import com.captainavi.app.safety.AnchorWatchManager
import com.captainavi.app.safety.AudioAlarmManager
import com.captainavi.app.safety.FuelMarginCalculator
import com.captainavi.app.safety.GpsUpdateProfile
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.safety.SafetyAlertEvent
import com.captainavi.app.safety.SafetyMonitor
import com.captainavi.app.sms.OfflineSmsPosition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private fun calculateCrossTrackErrorMeters(
    destination: NavigationDestination?,
    vesselLatitude: Double,
    vesselLongitude: Double,
): Double {
    val startLatitude = destination?.legStartLatitude ?: return 0.0
    val startLongitude = destination.legStartLongitude ?: return 0.0
    return NauticalMath.xteMeters(
        vesselLat = vesselLatitude,
        vesselLon = vesselLongitude,
        startLat = startLatitude,
        startLon = startLongitude,
        endLat = destination.latitude,
        endLon = destination.longitude,
    )
}

data class NavigationDestination(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isMob: Boolean = false,
    val legStartLatitude: Double? = null,
    val legStartLongitude: Double? = null,
)

data class MarineTelemetry(
    val isTracking: Boolean = false,
    val isSosActive: Boolean = false,
    val hasGpsFix: Boolean = false,
    val tripId: String? = null,
    val tripStartTime: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedKnots: Double = 0.0,
    val bearingDegrees: Float = 0f,
    val headingCardinal: String = "N",
    val accuracyMeters: Float = 0f,
    val batteryPct: Int = 100,
    // Home navigation
    val distanceToHomeNm: Double = 0.0,
    val bearingToHomeDegrees: Double = 0.0,
    val distanceTraveledNm: Double = 0.0,
    /** Estimated liters of fuel margin after a return-to-home trip; null when not underway or uncalibrated. */
    val fuelMarginLiters: Double? = null,
    val fuelRemainingLiters: Double? = null,
    val fuelNeededToReturnLiters: Double? = null,
    // Active destination navigation
    val activeDestination: NavigationDestination? = null,
    val distToDestNm: Double = 0.0,
    val bearingToDestDegrees: Double = 0.0,
    val etaMinutes: Double = -1.0,
    val vmgKnots: Double = 0.0,
    val crossTrackErrorMeters: Double = 0.0,
    // Compass Sensor Fusion
    val compassHeadingDegrees: Float = 0f,
    val compassAvailable: Boolean = false,
    val headingSource: String = "GPS",
    // Safety
    val activeSafetyAlert: SafetyAlertEvent? = null,
    val lastUpdateTime: Long = 0L
)

class MarineLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var safetyMonitor: SafetyMonitor
    private lateinit var alarmManager: AudioAlarmManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var locationCallback: LocationCallback? = null
    private var activeGpsProfile: GpsUpdateProfile = AdaptiveGpsIntervalPolicy.NORMAL

    private var activeTripId: String? = null
    private var activeTripStartTime: Long = 0L
    private var isSosActive: Boolean = false
    private var lastRecordedBreadcrumbTime: Long = 0L
    private var lastSharedBreadcrumbTime: Long = 0L
    private var lastTraceLatitude: Double? = null
    private var lastTraceLongitude: Double? = null
    // Continuous (every fix, not throttled to the breadcrumb interval) running total for
    // the fuel-margin estimate — a coarser distance would understate fuel already burned.
    private var lastFuelTrackLatitude: Double? = null
    private var lastFuelTrackLongitude: Double? = null
    private var tripDistanceTraveledNm: Double = 0.0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                currentBatteryPct = (level * 100) / scale
            }
        }
    }
    private var currentBatteryPct: Int = 100

    companion object {
        const val ACTION_START_TRIP = "ACTION_START_TRIP"
        const val ACTION_STOP_TRIP = "ACTION_STOP_TRIP"
        const val ACTION_TRIGGER_SOS = "ACTION_TRIGGER_SOS"
        const val ACTION_CANCEL_SOS = "ACTION_CANCEL_SOS"

        const val CHANNEL_ID = "marine_tracking_channel"
        const val NOTIFICATION_ID = 1001

        private const val LOCAL_TRACE_INTERVAL_MS = 5_000L
        private const val STATIONARY_TRACE_INTERVAL_MS = 60_000L
        private const val MIN_TRACE_MOVEMENT_METERS = 5.0

        // GPS cadence is adaptive (see AdaptiveGpsIntervalPolicy) — NORMAL is the baseline
        // used before a trip starts and whenever neither FAST nor SLOW applies. It replaced
        // a flat 1 Hz / 0.5 m poll that drained the battery hard over a multi-hour trip.
        val GPS_NORMAL_PROFILE: GpsUpdateProfile = AdaptiveGpsIntervalPolicy.NORMAL

        private val _telemetry = MutableStateFlow(MarineTelemetry())
        val telemetry: StateFlow<MarineTelemetry> = _telemetry.asStateFlow()

        // Navigation destination management
        fun setDestination(dest: NavigationDestination?) {
            val current = _telemetry.value
            val destinationWithLeg = if (
                dest != null && current.hasGpsFix &&
                (dest.legStartLatitude == null || dest.legStartLongitude == null)
            ) {
                dest.copy(
                    legStartLatitude = current.latitude,
                    legStartLongitude = current.longitude,
                )
            } else dest

            val dist = if (destinationWithLeg != null && (current.latitude != 0.0 || current.longitude != 0.0)) {
                NauticalMath.distanceNauticalMiles(current.latitude, current.longitude, destinationWithLeg.latitude, destinationWithLeg.longitude)
            } else 0.0
            val bearing = if (destinationWithLeg != null && (current.latitude != 0.0 || current.longitude != 0.0)) {
                NauticalMath.bearingDegrees(current.latitude, current.longitude, destinationWithLeg.latitude, destinationWithLeg.longitude)
            } else 0.0
            val eta = if (destinationWithLeg != null) NauticalMath.etaMinutes(dist, current.speedKnots) else -1.0
            val vmg = if (destinationWithLeg != null) NauticalMath.vmgKnots(current.speedKnots, current.bearingDegrees.toDouble(), bearing) else 0.0
            val xte = calculateCrossTrackErrorMeters(destinationWithLeg, current.latitude, current.longitude)

            _telemetry.value = current.copy(
                activeDestination = destinationWithLeg,
                distToDestNm = dist,
                bearingToDestDegrees = bearing,
                etaMinutes = eta,
                vmgKnots = vmg,
                crossTrackErrorMeters = xte,
            )
        }

        fun clearDestination() {
            _telemetry.value = _telemetry.value.copy(
                activeDestination = null,
                distToDestNm = 0.0,
                bearingToDestDegrees = 0.0,
                etaMinutes = -1.0,
                vmgKnots = 0.0,
                crossTrackErrorMeters = 0.0,
            )
        }

        // MOB: drop a man-overboard marker at current position
        fun triggerMob(): NavigationDestination {
            val current = _telemetry.value
            val mob = NavigationDestination(
                name = "MOB MARKER",
                latitude = current.latitude,
                longitude = current.longitude,
                isMob = true,
                legStartLatitude = current.latitude,
                legStartLongitude = current.longitude,
            )
            _telemetry.value = current.copy(activeDestination = mob)
            return mob
        }

        fun updateCompassHeading(heading: Float, cardinal: String, isAvailable: Boolean = true) {
            val current = _telemetry.value
            _telemetry.value = current.copy(
                compassHeadingDegrees = heading,
                compassAvailable = isAvailable,
                headingCardinal = if (isAvailable) cardinal else current.headingCardinal,
                headingSource = if (isAvailable) "COMPASS" else "GPS"
            )
        }

        fun updateRealLocation(lat: Double, lon: Double, speedKnots: Double, bearing: Float, accuracy: Float, app: CaptainAviApp, batteryPct: Int = 100) {
            val cardinal = NauticalMath.degreesToShortCardinal(bearing.toDouble())
            val current = _telemetry.value

            CoroutineScope(Dispatchers.IO).launch {
                val home = app.waypointRepository.getHomeWaypointCached()
                val distToHome = if (home != null && home.latitude != 0.0) {
                    NauticalMath.distanceNauticalMiles(lat, lon, home.latitude, home.longitude)
                } else 0.0
                val bearingToHome = if (home != null && home.latitude != 0.0) {
                    NauticalMath.bearingDegrees(lat, lon, home.latitude, home.longitude)
                } else 0.0

                // Calculate destination nav data
                val dest = current.activeDestination
                val distToDest = if (dest != null) NauticalMath.distanceNauticalMiles(lat, lon, dest.latitude, dest.longitude) else 0.0
                val bearingToDest = if (dest != null) NauticalMath.bearingDegrees(lat, lon, dest.latitude, dest.longitude) else 0.0
                val eta = if (dest != null) NauticalMath.etaMinutes(distToDest, speedKnots) else -1.0
                val vmg = if (dest != null) NauticalMath.vmgKnots(speedKnots, bearing.toDouble(), bearingToDest) else 0.0
                val crossTrackError = calculateCrossTrackErrorMeters(dest, lat, lon)

                // Update anchor watch drift
                AnchorWatchManager.updateDrift(lat, lon)

                val updatedTelemetry = current.copy(
                    hasGpsFix = true,
                    latitude = lat,
                    longitude = lon,
                    speedKnots = speedKnots,
                    bearingDegrees = bearing,
                    headingCardinal = cardinal,
                    accuracyMeters = accuracy,
                    batteryPct = batteryPct,
                    distanceToHomeNm = distToHome,
                    bearingToHomeDegrees = bearingToHome,
                    distToDestNm = distToDest,
                    bearingToDestDegrees = bearingToDest,
                    etaMinutes = eta,
                    vmgKnots = vmg,
                    crossTrackErrorMeters = crossTrackError,
                    lastUpdateTime = System.currentTimeMillis()
                )
                _telemetry.value = updatedTelemetry
                app.offlineSmsLocationStore.save(updatedTelemetry.toOfflineSmsPosition())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as CaptainAviApp
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        alarmManager = AudioAlarmManager(this)
        safetyMonitor = SafetyMonitor(
            app.waypointRepository,
            app.reefBoundaryRepository,
            app.outboxRepository,
            alarmManager,
        )
        createNotificationChannel()
        try { registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Exception) {}
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        // Created here but only acquired for the duration of an active trip (see
        // startTracking/stopTracking) — holding it from service creation regardless of
        // whether a trip is running wasted battery between trips.
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CaptainAvi::TrackingWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRIP -> startTracking()
            ACTION_STOP_TRIP -> stopTracking()
            ACTION_TRIGGER_SOS -> triggerSos()
            ACTION_CANCEL_SOS -> cancelSos()
        }
        return START_STICKY
    }

    private fun startTracking() {
        val app = application as CaptainAviApp
        serviceScope.launch {
            val trip = app.tripRepository.getActiveTripSync() ?: return@launch
            activeTripId = trip.id
            activeTripStartTime = trip.startTime
            lastRecordedBreadcrumbTime = 0L
            lastSharedBreadcrumbTime = 0L
            lastTraceLatitude = null
            lastTraceLongitude = null
            lastFuelTrackLatitude = null
            lastFuelTrackLongitude = null
            tripDistanceTraveledNm = 0.0
            safetyMonitor.resetTripState()
            alarmManager.playConfirmTone()
            wakeLock?.let { if (!it.isHeld) it.acquire(24 * 60 * 60 * 1000L) }
            val notification = buildForegroundNotification("Fishing Trip Active", "Tracking live marine GPS...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            requestRealLocationUpdates()
        }
    }

    private fun stopTracking() {
        alarmManager.stopAlarm()
        alarmManager.playConfirmTone()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        wakeLock?.let { if (it.isHeld) it.release() }
        activeTripId = null
        isSosActive = false
        _telemetry.value = _telemetry.value.copy(
            isTracking = false,
            speedKnots = 0.0,
            fuelMarginLiters = null,
            fuelRemainingLiters = null,
            fuelNeededToReturnLiters = null,
            distanceTraveledNm = 0.0,
            activeSafetyAlert = null,
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun triggerSos() {
        isSosActive = true
        alarmManager.startEmergencySiren()
        val current = _telemetry.value
        _telemetry.value = current.copy(isSosActive = true)
        val app = application as CaptainAviApp
        activeTripId?.let { tripId ->
            serviceScope.launch {
                val recordedAlert = app.outboxRepository.recordAlert(
                    tripId,
                    AlertType.SOS,
                    "Emergency Distress Beacon Activated",
                    current.latitude,
                    current.longitude,
                    currentBatteryPct,
                )
                val alertReq = com.captainavi.app.data.remote.AlertRequest(
                    tripId = tripId, captainName = app.settingsRepository.captainName.value,
                    alertType = "SOS", message = "EMERGENCY DISTRESS BEACON ACTIVATED",
                    latitude = current.latitude, longitude = current.longitude,
                    batteryPct = currentBatteryPct, timestamp = System.currentTimeMillis()
                )
                if (app.relayApiClient.sendAlert(alertReq).isSuccess) {
                    app.outboxRepository.markAlertSynced(recordedAlert.id)
                }
                ConnectivitySyncWorker.enqueueImmediateSync(this@MarineLocationService)
            }
        }
    }

    private fun cancelSos() {
        isSosActive = false
        alarmManager.stopAlarm()
        alarmManager.playConfirmTone()
        _telemetry.value = _telemetry.value.copy(isSosActive = false)
    }

    @SuppressLint("MissingPermission")
    private fun requestRealLocationUpdates(profile: GpsUpdateProfile = AdaptiveGpsIntervalPolicy.NORMAL) {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        activeGpsProfile = profile
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, profile.intervalMillis)
            .setMinUpdateIntervalMillis(profile.intervalMillis)
            .setMinUpdateDistanceMeters(profile.minUpdateDistanceMeters)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val speed = NauticalMath.metersPerSecondToKnots(loc.speed)
                    processRealTelemetry(loc.latitude, loc.longitude, speed, loc.bearing, loc.accuracy)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
    }

    private fun processRealTelemetry(lat: Double, lon: Double, speedKnots: Double, bearing: Float, accuracy: Float) {
        val app = application as CaptainAviApp
        serviceScope.launch {
            val home = app.waypointRepository.getHomeWaypointCached()
            val distToHome = if (home != null && home.latitude != 0.0) NauticalMath.distanceNauticalMiles(lat, lon, home.latitude, home.longitude) else 0.0
            val bearingToHome = if (home != null && home.latitude != 0.0) NauticalMath.bearingDegrees(lat, lon, home.latitude, home.longitude) else 0.0

            val current = _telemetry.value
            val dest = current.activeDestination
            val distToDest = if (dest != null) NauticalMath.distanceNauticalMiles(lat, lon, dest.latitude, dest.longitude) else 0.0
            val bearingToDest = if (dest != null) NauticalMath.bearingDegrees(lat, lon, dest.latitude, dest.longitude) else 0.0
            val eta = if (dest != null) NauticalMath.etaMinutes(distToDest, speedKnots) else -1.0
            val vmg = if (dest != null) NauticalMath.vmgKnots(speedKnots, bearing.toDouble(), bearingToDest) else 0.0
            val crossTrackError = calculateCrossTrackErrorMeters(dest, lat, lon)

            // Anchor watch
            val anchorDragging = AnchorWatchManager.updateDrift(lat, lon)
            if (anchorDragging) {
                alarmManager.startEmergencySiren()
            }

            if (activeTripId != null) {
                val lastLat = lastFuelTrackLatitude
                val lastLon = lastFuelTrackLongitude
                if (lastLat != null && lastLon != null) {
                    tripDistanceTraveledNm += NauticalMath.distanceNauticalMiles(lastLat, lastLon, lat, lon)
                }
                lastFuelTrackLatitude = lat
                lastFuelTrackLongitude = lon
            }

            val settings = app.settingsRepository
            val fuelEstimate = if (activeTripId != null && home != null && home.latitude != 0.0) {
                FuelMarginCalculator.evaluate(
                    tankLiters = settings.fuelTankLiters.value,
                    distanceTraveledNm = tripDistanceTraveledNm,
                    distanceToHomeNm = distToHome,
                    referenceDistanceNm = settings.tripReferenceDistanceNm.value,
                    referenceFuelLiters = settings.tripReferenceFuelLiters.value,
                )
            } else {
                null
            }

            val updatedTelemetry = MarineTelemetry(
                isTracking = true, isSosActive = isSosActive, hasGpsFix = true,
                tripId = activeTripId, tripStartTime = activeTripStartTime,
                latitude = lat, longitude = lon, speedKnots = speedKnots,
                bearingDegrees = bearing, headingCardinal = NauticalMath.degreesToShortCardinal(bearing.toDouble()),
                accuracyMeters = accuracy, batteryPct = currentBatteryPct,
                distanceToHomeNm = distToHome, bearingToHomeDegrees = bearingToHome,
                distanceTraveledNm = tripDistanceTraveledNm,
                fuelMarginLiters = fuelEstimate?.marginLiters,
                fuelRemainingLiters = fuelEstimate?.fuelRemainingLiters,
                fuelNeededToReturnLiters = fuelEstimate?.fuelNeededToReturnLiters,
                activeDestination = dest, distToDestNm = distToDest,
                bearingToDestDegrees = bearingToDest, etaMinutes = eta, vmgKnots = vmg,
                crossTrackErrorMeters = crossTrackError,
                compassHeadingDegrees = current.compassHeadingDegrees,
                compassAvailable = current.compassAvailable,
                headingSource = current.headingSource,
                activeSafetyAlert = current.activeSafetyAlert,
                lastUpdateTime = System.currentTimeMillis()
            )
            _telemetry.value = updatedTelemetry
            app.offlineSmsLocationStore.save(updatedTelemetry.toOfflineSmsPosition())

            updateForegroundNotification(speedKnots, NauticalMath.degreesToShortCardinal(bearing.toDouble()), distToHome, currentBatteryPct)

            activeTripId?.let { tripId ->
                val now = System.currentTimeMillis()
                val elapsedSinceTracePoint = now - lastRecordedBreadcrumbTime
                val movementMeters = if (lastTraceLatitude != null && lastTraceLongitude != null) {
                    NauticalMath.distanceNauticalMiles(
                        lastTraceLatitude!!,
                        lastTraceLongitude!!,
                        lat,
                        lon,
                    ) * 1_852.0
                } else {
                    Double.POSITIVE_INFINITY
                }
                val tracePointDue = lastRecordedBreadcrumbTime == 0L ||
                    (elapsedSinceTracePoint >= LOCAL_TRACE_INTERVAL_MS && movementMeters >= MIN_TRACE_MOVEMENT_METERS) ||
                    elapsedSinceTracePoint >= STATIONARY_TRACE_INTERVAL_MS
                if (tracePointDue) {
                    val shareThreshold = settings.telegramUpdateIntervalMinutes.value
                        .coerceIn(
                            com.captainavi.app.data.repository.SettingsRepository.MIN_TELEGRAM_UPDATE_MINUTES,
                            com.captainavi.app.data.repository.SettingsRepository.MAX_TELEGRAM_UPDATE_MINUTES,
                        ) * 60 * 1000L
                    val needsSync = lastSharedBreadcrumbTime == 0L || now - lastSharedBreadcrumbTime >= shareThreshold
                    lastRecordedBreadcrumbTime = now
                    lastTraceLatitude = lat
                    lastTraceLongitude = lon
                    if (needsSync) lastSharedBreadcrumbTime = now
                    app.tripRepository.recordBreadcrumb(
                        tripId,
                        lat,
                        lon,
                        0.0,
                        speedKnots,
                        bearing,
                        accuracy,
                        currentBatteryPct,
                        needsSync = needsSync,
                    )
                    if (needsSync) {
                        ConnectivitySyncWorker.enqueueImmediateSync(this@MarineLocationService)
                    }
                }
                val safetyResult = safetyMonitor.evaluateSafety(
                    tripId = tripId,
                    latitude = lat,
                    longitude = lon,
                    speedKnots = speedKnots,
                    accuracyMeters = accuracy,
                    batteryPct = currentBatteryPct,
                    maxDistanceHomeNm = settings.maxDistanceHomeNm.value,
                    stationaryMinutesThreshold = settings.stationaryThresholdMinutes.value,
                    reefWarningsEnabled = settings.reefWarningsEnabled.value,
                    reefWarningBufferMeters = settings.reefWarningBufferMeters.value,
                    fuelTankLiters = settings.fuelTankLiters.value,
                    distanceTraveledNm = tripDistanceTraveledNm,
                    tripReferenceDistanceNm = settings.tripReferenceDistanceNm.value,
                    tripReferenceFuelLiters = settings.tripReferenceFuelLiters.value,
                    darkReturnWarningEnabled = settings.darkReturnWarningEnabled.value,
                    cruiseSpeedKnots = settings.cruiseSpeedKnots.value,
                ) { alertEvent ->
                    _telemetry.value = _telemetry.value.copy(activeSafetyAlert = alertEvent)
                    ConnectivitySyncWorker.enqueueImmediateSync(this@MarineLocationService)
                }

                val desiredGpsProfile = AdaptiveGpsIntervalPolicy.choose(
                    speedKnots = speedKnots,
                    isAnchored = AnchorWatchManager.state.value.isActive,
                    nearestHazardDistanceMeters = safetyResult.nearestHazardDistanceMeters,
                )
                if (desiredGpsProfile != activeGpsProfile) {
                    requestRealLocationUpdates(desiredGpsProfile)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Marine Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(title).setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher).setOngoing(true).setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun updateForegroundNotification(speedKnots: Double, heading: String, distToHomeNm: Double, battery: Int) {
        val dest = _telemetry.value.activeDestination
        val title = if (isSosActive) "SOS active" else if (dest != null) "Nav · ${dest.name}" else "Fishing trip"
        val destInfo = if (dest != null) " · ${String.format(java.util.Locale.US, "%.1f", _telemetry.value.distToDestNm)} NM" else ""
        val content = "${String.format(java.util.Locale.US, "%.1f", speedKnots)} kt · $heading · ${String.format(java.util.Locale.US, "%.1f", distToHomeNm)} NM home$destInfo · $battery%"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(title, content))
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        wakeLock?.let { if (it.isHeld) it.release() }
        alarmManager.stopAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private fun MarineTelemetry.toOfflineSmsPosition() = OfflineSmsPosition(
    latitude = latitude,
    longitude = longitude,
    speedKnots = speedKnots,
    bearingDegrees = bearingDegrees,
    headingCardinal = headingCardinal,
    accuracyMeters = accuracyMeters,
    batteryPct = batteryPct,
    recordedAtMillis = lastUpdateTime,
)
