package com.captainavi.app.ui.screens.map

import com.captainavi.app.safety.NauticalMath
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal data class TraceCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/** Projects a point along a true bearing on a spherical earth. */
internal fun projectHeadingEndpoint(
    start: TraceCoordinate,
    headingDegrees: Double,
    distanceMeters: Double,
): TraceCoordinate {
    val angularDistance = distanceMeters.coerceAtLeast(0.0) / EARTH_RADIUS_METERS
    val bearingRadians = Math.toRadians(normalizeHeadingDegrees(headingDegrees))
    val latitudeRadians = Math.toRadians(start.latitude)
    val longitudeRadians = Math.toRadians(start.longitude)

    val projectedLatitude = asin(
        sin(latitudeRadians) * cos(angularDistance) +
            cos(latitudeRadians) * sin(angularDistance) * cos(bearingRadians),
    )
    val projectedLongitude = longitudeRadians + atan2(
        sin(bearingRadians) * sin(angularDistance) * cos(latitudeRadians),
        cos(angularDistance) - sin(latitudeRadians) * sin(projectedLatitude),
    )

    return TraceCoordinate(
        latitude = Math.toDegrees(projectedLatitude).coerceIn(-90.0, 90.0),
        longitude = normalizeLongitudeDegrees(Math.toDegrees(projectedLongitude)),
    )
}

/** Extends beyond the viewport diagonal so the predictor endpoint is always clipped. */
internal fun headingLineDistanceMeters(
    visibleNorthLatitude: Double,
    visibleSouthLatitude: Double,
    visibleEastLongitude: Double,
    visibleWestLongitude: Double,
): Double {
    val viewportDiagonalMeters = NauticalMath.distanceNauticalMiles(
        visibleNorthLatitude,
        visibleWestLongitude,
        visibleSouthLatitude,
        visibleEastLongitude,
    ) * METERS_PER_NAUTICAL_MILE
    return (viewportDiagonalMeters * HEADING_LINE_DIAGONAL_MULTIPLIER)
        .coerceIn(MIN_HEADING_LINE_METERS, MAX_HEADING_LINE_METERS)
}

internal fun traceDistanceNauticalMiles(points: List<TraceCoordinate>): Double {
    if (points.size < 2) return 0.0
    return points.zipWithNext().sumOf { (start, end) ->
        NauticalMath.distanceNauticalMiles(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
        )
    }
}

internal fun formatTraceElapsed(elapsedSeconds: Long): String {
    val safeSeconds = elapsedSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    val seconds = safeSeconds % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%dh%02dm", hours, minutes)
    } else {
        String.format(java.util.Locale.US, "%02dm%02ds", minutes, seconds)
    }
}

private fun normalizeHeadingDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

private fun normalizeLongitudeDegrees(value: Double): Double = ((value + 540.0) % 360.0) - 180.0

private const val EARTH_RADIUS_METERS = 6_371_008.8
private const val METERS_PER_NAUTICAL_MILE = 1_852.0
private const val HEADING_LINE_DIAGONAL_MULTIPLIER = 1.35
private const val MIN_HEADING_LINE_METERS = 500.0
private const val MAX_HEADING_LINE_METERS = 2_000_000.0
