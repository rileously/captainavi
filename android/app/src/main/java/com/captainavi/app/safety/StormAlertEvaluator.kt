package com.captainavi.app.safety

import com.captainavi.app.data.remote.MarineConditions
import com.captainavi.app.data.remote.MarineForecastHour

/** Which forecast field triggered the alert. */
enum class StormAlertKind { WAVE, SWELL, WIND_GUST }

/**
 * How urgent the alert is: [WARNING] means the threshold is already crossed (now or
 * within the lookahead window); [WATCH] means it hasn't crossed yet but the forecast is
 * trending toward it — an earlier, softer heads-up.
 */
enum class StormAlertSeverity { WATCH, WARNING }

/**
 * The single most severe finding from [StormAlertEvaluator.evaluate]. [peakTimeIso] is
 * the forecast hour's local ISO time string, or null for a current reading. [startValue]
 * is only set for a [StormAlertSeverity.WATCH] — what the trend is rising from.
 */
data class StormAlert(
    val kind: StormAlertKind,
    val severity: StormAlertSeverity,
    val peakValue: Double,
    val peakTimeIso: String?,
    val thresholdExceeded: Double,
    val startValue: Double? = null,
)

/**
 * Pure evaluation of a [MarineConditions] forecast against rough-sea thresholds. No
 * networking or Android APIs here — this only decides whether a storm/high-wave alert
 * is warranted, not how it's delivered.
 */
object StormAlertEvaluator {
    const val DEFAULT_WAVE_HEIGHT_THRESHOLD_METERS = 2.0
    const val DEFAULT_WIND_GUST_THRESHOLD_KNOTS = 25.0
    const val DEFAULT_LOOKAHEAD_HOURS = 24

    /** A WATCH needs the peak to reach at least this fraction of the threshold... */
    const val WATCH_APPROACH_FRACTION = 0.7

    /** ...and to have risen by at least this fraction of the threshold from the start value. */
    const val WATCH_RISE_FRACTION = 0.3

    /**
     * Checks the current reading and the next [lookaheadHours] forecast hours for wave
     * height, swell height, or wind gusts.
     *
     * - If anything is at or above its threshold now or within the window, returns the
     *   worst such [StormAlertSeverity.WARNING] (largest value-over-threshold ratio).
     * - Otherwise, if a field is trending upward — rising by [WATCH_RISE_FRACTION] of its
     *   threshold and reaching [WATCH_APPROACH_FRACTION] of it — returns the worst such
     *   [StormAlertSeverity.WATCH] instead, so a building sea gets flagged before it's
     *   actually rough, not just after.
     * - Returns null if neither applies.
     */
    fun evaluate(
        conditions: MarineConditions,
        waveHeightThresholdMeters: Double,
        windGustThresholdKnots: Double,
        lookaheadHours: Int = DEFAULT_LOOKAHEAD_HOURS,
    ): StormAlert? {
        if (waveHeightThresholdMeters <= 0.0 && windGustThresholdKnots <= 0.0) return null

        val window = conditions.hourlyForecast
            .filter { conditions.forecastTime.isBlank() || it.time >= conditions.forecastTime }
            .take(lookaheadHours.coerceAtLeast(0))

        val warning = findWarning(conditions, window, waveHeightThresholdMeters, windGustThresholdKnots)
        if (warning != null) return warning

        return findWatch(conditions, window, waveHeightThresholdMeters, windGustThresholdKnots)
    }

    private fun findWarning(
        conditions: MarineConditions,
        window: List<MarineForecastHour>,
        waveHeightThresholdMeters: Double,
        windGustThresholdKnots: Double,
    ): StormAlert? {
        val candidates = mutableListOf<StormAlert>()
        fun consider(kind: StormAlertKind, value: Double?, threshold: Double, time: String?) {
            if (value == null || !value.isFinite() || threshold <= 0.0) return
            if (value >= threshold) {
                candidates += StormAlert(kind, StormAlertSeverity.WARNING, value, time, threshold)
            }
        }

        consider(StormAlertKind.WAVE, conditions.waveHeightMeters, waveHeightThresholdMeters, null)
        consider(StormAlertKind.SWELL, conditions.swellHeightMeters, waveHeightThresholdMeters, null)
        consider(StormAlertKind.WIND_GUST, conditions.windGustKnots, windGustThresholdKnots, null)

        window.forEach { hour ->
            consider(StormAlertKind.WAVE, hour.waveHeightMeters, waveHeightThresholdMeters, hour.time)
            consider(StormAlertKind.SWELL, hour.swellHeightMeters, waveHeightThresholdMeters, hour.time)
            consider(StormAlertKind.WIND_GUST, hour.windGustKnots, windGustThresholdKnots, hour.time)
        }

        return candidates.maxByOrNull { it.peakValue / it.thresholdExceeded }
    }

    private fun findWatch(
        conditions: MarineConditions,
        window: List<MarineForecastHour>,
        waveHeightThresholdMeters: Double,
        windGustThresholdKnots: Double,
    ): StormAlert? {
        val candidates = mutableListOf<StormAlert>()

        fun considerTrend(
            kind: StormAlertKind,
            startValue: Double?,
            hourlyValues: List<Pair<String, Double?>>,
            threshold: Double,
        ) {
            if (threshold <= 0.0) return
            val start = startValue?.takeIf { it.isFinite() } ?: hourlyValues.firstNotNullOfOrNull { it.second }
            if (start == null || !start.isFinite()) return

            val peakEntry = hourlyValues
                .mapNotNull { (time, value) -> value?.takeIf { it.isFinite() }?.let { time to it } }
                .maxByOrNull { it.second } ?: return
            val (peakTime, peakValue) = peakEntry

            val rise = peakValue - start
            val approachesThreshold = peakValue >= threshold * WATCH_APPROACH_FRACTION
            val isRisingMeaningfully = rise >= threshold * WATCH_RISE_FRACTION
            if (approachesThreshold && isRisingMeaningfully) {
                candidates += StormAlert(
                    kind = kind,
                    severity = StormAlertSeverity.WATCH,
                    peakValue = peakValue,
                    peakTimeIso = peakTime,
                    thresholdExceeded = threshold,
                    startValue = start,
                )
            }
        }

        considerTrend(
            StormAlertKind.WAVE,
            conditions.waveHeightMeters,
            window.map { it.time to it.waveHeightMeters },
            waveHeightThresholdMeters,
        )
        considerTrend(
            StormAlertKind.SWELL,
            conditions.swellHeightMeters,
            window.map { it.time to it.swellHeightMeters },
            waveHeightThresholdMeters,
        )
        considerTrend(
            StormAlertKind.WIND_GUST,
            conditions.windGustKnots,
            window.map { it.time to it.windGustKnots },
            windGustThresholdKnots,
        )

        return candidates.maxByOrNull { it.peakValue / it.thresholdExceeded }
    }
}
