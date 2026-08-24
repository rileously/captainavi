package com.captainavi.app.safety

import com.captainavi.app.data.local.entity.AlertType
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.data.repository.OutboxRepository
import com.captainavi.app.data.repository.ReefBoundaryRepository
import com.captainavi.app.data.repository.WaypointRepository

sealed class SafetyAlertEvent(
    val type: AlertType,
    val title: String,
    val description: String,
    val isCritical: Boolean = false
) {
    class SosAlert(lat: Double, lon: Double) :
        SafetyAlertEvent(
            AlertType.SOS,
            "SOS TRIGGERED",
            "Emergency distress beacon active at ${"%.5f".format(lat)}, ${"%.5f".format(lon)}",
            true
        )

    class LowBattery(val pct: Int) :
        SafetyAlertEvent(AlertType.LOW_BATTERY, "LOW BATTERY ($pct%)", "Phone battery is running low. Connect power bank.")

    class GeofenceExit(val distanceNm: Double, val maxAllowedNm: Double) :
        SafetyAlertEvent(
            AlertType.GEOFENCE_EXIT,
            "FAR FROM HOME",
            "Distance: ${"%.1f".format(distanceNm)} NM (Limit: ${"%.1f".format(maxAllowedNm)} NM)"
        )

    class DangerReefProximity(val reefName: String, val distanceMeters: Double) :
        SafetyAlertEvent(
            AlertType.DANGER_ZONE_ENTRY,
            "DANGER REEF NEARBY",
            "$reefName is ${distanceMeters.toInt()}m away! Steer clear of shallow coral.",
            true
        )

    class OfficialReefBoundary(
        val reefName: String,
        val distanceMeters: Double,
        val isInsideBoundary: Boolean,
    ) : SafetyAlertEvent(
        AlertType.DANGER_ZONE_ENTRY,
        if (isInsideBoundary) "INSIDE MAPPED REEF" else "REEF BOUNDARY AHEAD",
        if (isInsideBoundary) {
            "GPS position is inside the mapped boundary of $reefName. Leave with extreme caution; verify against an official chart."
        } else {
            "$reefName boundary is ${distanceMeters.toInt()}m away. Slow down, keep watch, and verify safe water on an official chart."
        },
        true,
    )

    class StationaryBoat(val minutes: Int) :
        SafetyAlertEvent(
            AlertType.NO_MOVEMENT,
            "NO MOVEMENT ($minutes MINS)",
            "Boat has been stationary/drifting for $minutes minutes."
        )

    object GpsSignalLost :
        SafetyAlertEvent(
            AlertType.GPS_LOST,
            "GPS ACCURACY DEGRADED",
            "GPS signal is weak or obstructed. Move phone to clear sky."
        )
}

class SafetyMonitor(
    private val waypointRepository: WaypointRepository,
    private val reefBoundaryRepository: ReefBoundaryRepository,
    private val outboxRepository: OutboxRepository,
    private val alarmManager: AudioAlarmManager
) {
    private var lastMovementTime: Long = System.currentTimeMillis()
    private var lastBatteryWarningPct: Int = 100
    private var isFarFromHomeAlerted: Boolean = false
    private val alertedReefs = mutableSetOf<String>()
    private var officialReefAlertState: OfficialReefAlertState? = null
    private var lastGpsLostAlertTime: Long = 0L

    fun resetTripState() {
        lastMovementTime = System.currentTimeMillis()
        lastBatteryWarningPct = 100
        isFarFromHomeAlerted = false
        alertedReefs.clear()
        officialReefAlertState = null
        lastGpsLostAlertTime = 0L
    }

    suspend fun evaluateSafety(
        tripId: String,
        latitude: Double,
        longitude: Double,
        speedKnots: Double,
        accuracyMeters: Float,
        batteryPct: Int,
        maxDistanceHomeNm: Double,
        stationaryMinutesThreshold: Int,
        reefWarningsEnabled: Boolean,
        reefWarningBufferMeters: Int,
        onAlertTriggered: (SafetyAlertEvent) -> Unit
    ): SafetyEvaluationResult {
        val now = System.currentTimeMillis()
        var nearestHazardMeters: Double? = null
        fun trackHazard(distanceMeters: Double) {
            nearestHazardMeters = nearestHazardMeters?.let { minOf(it, distanceMeters) } ?: distanceMeters
        }

        // 1. Check GPS Quality (throttled: at most one alarm every 5 minutes while signal stays bad)
        if (accuracyMeters > 50.0f) {
            if (now - lastGpsLostAlertTime >= 5 * 60 * 1000L) {
                lastGpsLostAlertTime = now
                val alert = SafetyAlertEvent.GpsSignalLost
                alarmManager.playWarningBeep(1)
                onAlertTriggered(alert)
            }
        }

        // 2. Check Low Battery Thresholds (critical 5% checked first; re-arms when charging above 20%)
        if (batteryPct > 20 && lastBatteryWarningPct <= 15) {
            lastBatteryWarningPct = batteryPct
        }
        if (batteryPct <= 5 && lastBatteryWarningPct > 5) {
            lastBatteryWarningPct = batteryPct
            val alert = SafetyAlertEvent.LowBattery(batteryPct)
            alarmManager.playWarningBeep(4)
            outboxRepository.recordAlert(
                tripId, AlertType.LOW_BATTERY, alert.description, latitude, longitude, batteryPct
            )
            onAlertTriggered(alert)
        } else if (batteryPct <= 15 && lastBatteryWarningPct > 15) {
            lastBatteryWarningPct = batteryPct
            val alert = SafetyAlertEvent.LowBattery(batteryPct)
            alarmManager.playWarningBeep(2)
            outboxRepository.recordAlert(
                tripId, AlertType.LOW_BATTERY, alert.description, latitude, longitude, batteryPct
            )
            onAlertTriggered(alert)
        }

        // 3. Check Distance to Home (Geofence safety boundary)
        val home = waypointRepository.getHomeWaypointCached()
        if (home != null) {
            val distToHome = NauticalMath.distanceNauticalMiles(
                latitude, longitude, home.latitude, home.longitude
            )
            if (distToHome > maxDistanceHomeNm && !isFarFromHomeAlerted) {
                isFarFromHomeAlerted = true
                val alert = SafetyAlertEvent.GeofenceExit(distToHome, maxDistanceHomeNm)
                alarmManager.playWarningBeep(2)
                outboxRepository.recordAlert(
                    tripId, AlertType.GEOFENCE_EXIT, alert.description, latitude, longitude, batteryPct
                )
                onAlertTriggered(alert)
            } else if (distToHome <= maxDistanceHomeNm * 0.9) {
                // Reset flag when returning safely inside
                isFarFromHomeAlerted = false
            }
        }

        // 4. Check Proximity to Dangerous Reefs
        val dangerReefs = waypointRepository.getDangerReefsCached()
        for (reef in dangerReefs) {
            val distMeters = NauticalMath.distanceMeters(
                latitude, longitude, reef.latitude, reef.longitude
            )
            trackHazard(distMeters - reef.radiusMeters)
            val warningThreshold = reef.radiusMeters + 300.0 // Warn 300m before the reef boundary
            if (distMeters <= warningThreshold) {
                if (!alertedReefs.contains(reef.id)) {
                    alertedReefs.add(reef.id)
                    val alert = SafetyAlertEvent.DangerReefProximity(reef.name, distMeters)
                    alarmManager.playWarningBeep(3)
                    outboxRepository.recordAlert(
                        tripId, AlertType.DANGER_ZONE_ENTRY, alert.description, latitude, longitude, batteryPct
                    )
                    onAlertTriggered(alert)
                }
            } else if (distMeters > warningThreshold * 1.5) {
                alertedReefs.remove(reef.id)
            }
        }

        // 5. Check the official OneMap reef polygons. These boundaries provide
        // situational awareness only and do not establish navigable water.
        if (reefWarningsEnabled) {
            val proximity = reefBoundaryRepository.nearestReefWithin(
                latitude = latitude,
                longitude = longitude,
                warningBufferMeters = reefWarningBufferMeters.toDouble(),
            )
            if (proximity == null) {
                officialReefAlertState = null
            } else {
                trackHazard(if (proximity.isInside) 0.0 else proximity.distanceToBoundaryMeters)
                val previous = officialReefAlertState
                val shouldAlert = previous?.reefId != proximity.reef.id ||
                    (proximity.isInside && !previous.wasInside)
                if (shouldAlert) {
                    officialReefAlertState = OfficialReefAlertState(proximity.reef.id, proximity.isInside)
                    val alert = SafetyAlertEvent.OfficialReefBoundary(
                        reefName = proximity.reef.displayName,
                        distanceMeters = proximity.distanceToBoundaryMeters,
                        isInsideBoundary = proximity.isInside,
                    )
                    alarmManager.playWarningBeep(if (proximity.isInside) 4 else 3)
                    outboxRepository.recordAlert(
                        tripId, AlertType.DANGER_ZONE_ENTRY, alert.description, latitude, longitude, batteryPct
                    )
                    onAlertTriggered(alert)
                }
            }
        } else {
            officialReefAlertState = null
        }

        // 6. Check Stationary / No Movement
        if (speedKnots >= 0.8) {
            lastMovementTime = now
        } else {
            val stationaryMillis = now - lastMovementTime
            val stationaryThresholdMillis = stationaryMinutesThreshold * 60 * 1000L
            if (stationaryMillis >= stationaryThresholdMillis) {
                val stationaryMins = (stationaryMillis / (60 * 1000L)).toInt()
                val alert = SafetyAlertEvent.StationaryBoat(stationaryMins)
                alarmManager.playWarningBeep(2)
                outboxRepository.recordAlert(
                    tripId, AlertType.NO_MOVEMENT, alert.description, latitude, longitude, batteryPct
                )
                onAlertTriggered(alert)
                // Reset movement clock slightly so it doesn't fire every single second, but every 10 mins thereafter
                lastMovementTime = now - (stationaryThresholdMillis - (10 * 60 * 1000L))
            }
        }

        return SafetyEvaluationResult(nearestHazardDistanceMeters = nearestHazardMeters)
    }

    private data class OfficialReefAlertState(
        val reefId: String,
        val wasInside: Boolean,
    )
}

/**
 * Distance to the nearest charted hazard edge (danger reef radius or official reef
 * boundary) found during a [SafetyMonitor.evaluateSafety] pass, or null if none is
 * known/enabled — feeds [AdaptiveGpsIntervalPolicy] so GPS polling can tighten up near
 * hazards without recomputing reef proximity a second time.
 */
data class SafetyEvaluationResult(
    val nearestHazardDistanceMeters: Double? = null,
)
