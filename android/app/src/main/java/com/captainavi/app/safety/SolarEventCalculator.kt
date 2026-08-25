package com.captainavi.app.safety

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * On-device sunset computation from the NOAA solar equations (equation of time +
 * solar declination from the fractional year, hour angle at the standard 90.833°
 * zenith). Accurate to roughly ±2 minutes — plenty for a "will I be back before
 * dark" safety margin.
 *
 * Deliberately offline: the marine forecast also carries sunrise/sunset, but a
 * return-home safety check must work mid-ocean with no signal, so it cannot depend
 * on the last fetched forecast.
 */
object SolarEventCalculator {

    /** Official sunset zenith (accounts for atmospheric refraction + solar disc radius). */
    private const val SUNSET_ZENITH_DEGREES = 90.833

    /**
     * Sunset time for the UTC calendar day containing [epochMillis] at the given
     * position, as epoch millis. Returns null where no sunset occurs that day
     * (polar day / polar night) — callers should treat null as "no dark-return risk
     * can be computed" and skip the check.
     */
    fun sunsetEpochMillis(epochMillis: Long, latitude: Double, longitude: Double): Long? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochMillis
        }
        val dayOfYear = utc.get(Calendar.DAY_OF_YEAR)
        val year = utc.get(Calendar.YEAR)
        val isLeap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val yearLength = if (isLeap) 366.0 else 365.0

        // Fractional year (radians), evaluated at noon UTC.
        val gamma = 2.0 * Math.PI / yearLength * (dayOfYear - 1)

        val equationOfTimeMinutes = 229.18 * (
            0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
            )
        val declinationRadians =
            0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(latitude)
        val zenithRad = Math.toRadians(SUNSET_ZENITH_DEGREES)
        val cosHourAngle =
            cos(zenithRad) / (cos(latRad) * cos(declinationRadians)) -
                tan(latRad) * tan(declinationRadians)

        if (cosHourAngle > 1.0 || cosHourAngle < -1.0) return null // polar night / midnight sun

        val hourAngleDegrees = Math.toDegrees(acos(cosHourAngle))
        // NOAA convention: sunset = solarNoon + 4*hourAngle, solarNoon = 720 - 4*lon - eqtime.
        val sunsetMinutesUtc = 720.0 - 4.0 * (longitude - hourAngleDegrees) - equationOfTimeMinutes

        val midnightUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return midnightUtc.timeInMillis + (sunsetMinutesUtc * 60_000.0).toLong()
    }
}
