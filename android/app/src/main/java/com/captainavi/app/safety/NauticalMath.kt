package com.captainavi.app.safety

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object NauticalMath {
    private const val EARTH_RADIUS_KM = 6371.0
    private const val KM_TO_NAUTICAL_MILES = 0.539957
    private const val MS_TO_KNOTS = 1.94384

    /**
     * Converts meters per second (Android Location speed) to Knots (nautical miles per hour)
     */
    fun metersPerSecondToKnots(speedMps: Float): Double {
        return (speedMps * MS_TO_KNOTS).coerceAtLeast(0.0)
    }

    /**
     * Calculates the great-circle distance between two coordinates in Nautical Miles (NM)
     */
    fun distanceNauticalMiles(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val targetLat = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(originLat) * cos(targetLat)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distanceKm = EARTH_RADIUS_KM * c

        return distanceKm * KM_TO_NAUTICAL_MILES
    }

    /**
     * Calculates distance in meters between two coordinates
     */
    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return distanceNauticalMiles(lat1, lon1, lat2, lon2) * 1852.0
    }

    /**
     * Calculates the initial bearing / heading (0° - 360°) from point 1 to point 2
     */
    fun bearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)

        val degrees = Math.toDegrees(theta)
        return (degrees + 360.0) % 360.0
    }

    /**
     * Converts a numeric heading in degrees to a 16-point cardinal compass direction (e.g. N, NNE, NE, ENE...)
     */
    fun degreesToCardinal(degrees: Double): String {
        val normalized = (degrees % 360.0 + 360.0) % 360.0
        val directions = arrayOf(
            "North (N)", "North-Northeast (NNE)", "Northeast (NE)", "East-Northeast (ENE)",
            "East (E)", "East-Southeast (ESE)", "Southeast (SE)", "South-Southeast (SSE)",
            "South (S)", "South-Southwest (SSW)", "Southwest (SW)", "West-Southwest (WSW)",
            "West (W)", "West-Northwest (WNW)", "Northwest (NW)", "North-Northwest (NNW)"
        )
        val index = ((normalized + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }

    /**
     * Short 2-letter cardinal abbreviation
     */
    fun degreesToShortCardinal(degrees: Double): String {
        val normalized = (degrees % 360.0 + 360.0) % 360.0
        val directions = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        val index = ((normalized + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }

    /**
     * Estimated time of arrival in minutes.
     * If speed < 0.1 knots, returns -1.0 (meaning stationary/unknown).
     */
    fun etaMinutes(distanceNm: Double, speedKnots: Double): Double {
        if (speedKnots < 0.1) return -1.0
        return (distanceNm / speedKnots) * 60.0
    }

    /**
     * Velocity Made Good = SOG * cos(COG - BTW).
     * Returns component of speed toward destination.
     */
    fun vmgKnots(sogKnots: Double, cogDegrees: Double, bearingToDestDegrees: Double): Double {
        val angleDiffRad = Math.toRadians(cogDegrees - bearingToDestDegrees)
        return sogKnots * cos(angleDiffRad)
    }

    /**
     * Cross Track Error (XTE) in meters.
     * The perpendicular distance in meters from the vessel's position to the great-circle route between start and end.
     * Standard formula: XTE = asin(sin(d13/R) * sin(θ13 - θ12)) * R
     */
    fun xteMeters(
        vesselLat: Double,
        vesselLon: Double,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Double {
        val earthRadiusMeters = EARTH_RADIUS_KM * 1000.0
        val d13 = distanceMeters(startLat, startLon, vesselLat, vesselLon)
        val theta13 = Math.toRadians(bearingDegrees(startLat, startLon, vesselLat, vesselLon))
        val theta12 = Math.toRadians(bearingDegrees(startLat, startLon, endLat, endLon))
        val angularDist = d13 / earthRadiusMeters
        val sinXte = sin(angularDist) * sin(theta13 - theta12)
        return asin(sinXte.coerceIn(-1.0, 1.0)) * earthRadiusMeters
    }

    /**
     * Format ETA as human readable string.
     * If etaMinutes < 0 returns "--".
     * If < 60 returns "XXm" (e.g. "42m").
     * If >= 60 returns "Xh XXm" (e.g. "2h 15m").
     * If > 24*60 returns ">24h".
     */
    fun formatEta(etaMinutes: Double): String {
        if (etaMinutes < 0.0 || etaMinutes.isNaN() || etaMinutes.isInfinite()) return "--"
        if (etaMinutes > 24.0 * 60.0) return ">24h"
        val totalMinutes = etaMinutes.toInt()
        return if (totalMinutes < 60) {
            "${totalMinutes}m"
        } else {
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            String.format(java.util.Locale.US, "%dh %02dm", hours, mins)
        }
    }
}
