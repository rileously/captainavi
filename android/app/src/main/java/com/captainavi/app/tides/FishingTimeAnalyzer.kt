package com.captainavi.app.tides

import kotlin.math.abs
import kotlin.math.min

/** One scored instant: how good the tide/light/moon combination looks right then. */
data class FishingScoreSample(
    val epochMillis: Long,
    val score: Int,
    val tideRateScore: Double,
    val timeOfDayScore: Double,
    val moonScore: Double,
    val isNearDawn: Boolean,
    val isNearDusk: Boolean,
)

/** A contiguous stretch of time scoring at/above the "good" threshold. */
data class FishingWindow(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val peakScore: Int,
    val reasons: List<String>,
)

/**
 * A transparent, explainable heuristic for "when's a good time to fish" — not a
 * scientific model. Combines three classic, well-known factors that are already
 * computable from data this app already has:
 *
 * - **Tide movement** (50% weight): faster-moving water means more current, which
 *   concentrates bait and gets fish feeding. Computed as the rate of change of
 *   [TidePredictor]'s harmonic tide height, normalized against a fixed reference
 *   rate rather than the window's own max, so scores stay comparable day to day.
 * - **Dawn/dusk** (35% weight): the classic "golden hour" bump, using sunrise/sunset
 *   times already fetched with the marine forecast.
 * - **Moon phase** (15% weight): near-new or near-full moon means a bigger spring
 *   tide (stronger currents) — a coarse proxy for solunar theory using the moon
 *   phase this app already computes, without a full lunar-transit calculation.
 *
 * All Android/network-free — takes plain epoch millis and forecast sunrise/sunset,
 * so it's unit-testable against the real [TidePredictor] like the rest of this package.
 */
object FishingTimeAnalyzer {
    private const val TIDE_RATE_WEIGHT = 0.5
    private const val TIME_OF_DAY_WEIGHT = 0.35
    private const val MOON_WEIGHT = 0.15

    /** A ~1.2 m semidiurnal Maldivian tide moves this fast at its steepest — used as the "full score" reference. */
    private const val REFERENCE_MAX_RATE_M_PER_HOUR = 0.35
    private const val RATE_SAMPLE_HALF_WINDOW_MILLIS = 15 * 60_000L
    private const val GOLDEN_HOUR_HALF_WINDOW_MILLIS = 90 * 60_000L
    private const val GOOD_SCORE_THRESHOLD = 60

    fun scoreAt(
        epochMillis: Long,
        sunriseEpochMillis: Long?,
        sunsetEpochMillis: Long?,
    ): FishingScoreSample {
        val before = TidePredictor.heightMslMeters(epochMillis - RATE_SAMPLE_HALF_WINDOW_MILLIS)
        val after = TidePredictor.heightMslMeters(epochMillis + RATE_SAMPLE_HALF_WINDOW_MILLIS)
        val rateMetersPerHour = abs(after - before) / (2.0 * RATE_SAMPLE_HALF_WINDOW_MILLIS / 3_600_000.0)
        val tideRateScore = (rateMetersPerHour / REFERENCE_MAX_RATE_M_PER_HOUR).coerceIn(0.0, 1.0)

        val dawnBump = sunriseEpochMillis?.let { triangularBump(epochMillis, it) } ?: 0.0
        val duskBump = sunsetEpochMillis?.let { triangularBump(epochMillis, it) } ?: 0.0
        val timeOfDayScore = maxOf(dawnBump, duskBump)

        val illuminationPct = TidePredictor.getMoonPhase(epochMillis).illuminationPct
        val distanceFromSpringTide = min(illuminationPct, 100 - illuminationPct)
        val moonScore = (1.0 - distanceFromSpringTide / 50.0).coerceIn(0.0, 1.0)

        val total = (tideRateScore * TIDE_RATE_WEIGHT + timeOfDayScore * TIME_OF_DAY_WEIGHT + moonScore * MOON_WEIGHT) * 100.0

        return FishingScoreSample(
            epochMillis = epochMillis,
            score = total.toInt().coerceIn(0, 100),
            tideRateScore = tideRateScore,
            timeOfDayScore = timeOfDayScore,
            moonScore = moonScore,
            isNearDawn = dawnBump >= duskBump && dawnBump > 0.0,
            isNearDusk = duskBump > dawnBump,
        )
    }

    /** Triangular bump: 1.0 exactly at [center], decaying linearly to 0 at ±[GOLDEN_HOUR_HALF_WINDOW_MILLIS]. */
    private fun triangularBump(t: Long, center: Long): Double =
        (1.0 - abs(t - center).toDouble() / GOLDEN_HOUR_HALF_WINDOW_MILLIS).coerceIn(0.0, 1.0)

    /**
     * Scores the range in [stepMinutes] increments and merges contiguous
     * good-scoring (>= [GOOD_SCORE_THRESHOLD]) samples into windows, sorted
     * chronologically. [sunriseEpochMillis]/[sunsetEpochMillis] apply to the whole
     * range — pass the day's own values for a single-day query.
     */
    fun bestWindows(
        fromMillis: Long,
        toMillis: Long,
        sunriseEpochMillis: Long?,
        sunsetEpochMillis: Long?,
        stepMinutes: Int = 15,
    ): List<FishingWindow> {
        if (toMillis <= fromMillis || stepMinutes <= 0) return emptyList()
        val stepMillis = stepMinutes * 60_000L

        val samples = generateSequence(fromMillis) { it + stepMillis }
            .takeWhile { it <= toMillis }
            .map { scoreAt(it, sunriseEpochMillis, sunsetEpochMillis) }
            .toList()

        val windows = mutableListOf<FishingWindow>()
        var windowStart: FishingScoreSample? = null
        var windowSamples = mutableListOf<FishingScoreSample>()

        fun closeWindow(endMillis: Long) {
            val start = windowStart ?: return
            if (windowSamples.isNotEmpty()) {
                windows += FishingWindow(
                    startEpochMillis = start.epochMillis,
                    endEpochMillis = endMillis,
                    peakScore = windowSamples.maxOf { it.score },
                    reasons = reasonsFor(windowSamples.maxByOrNull { it.score } ?: start),
                )
            }
            windowStart = null
            windowSamples = mutableListOf()
        }

        samples.forEachIndexed { index, sample ->
            if (sample.score >= GOOD_SCORE_THRESHOLD) {
                if (windowStart == null) windowStart = sample
                windowSamples.add(sample)
            } else if (windowStart != null) {
                closeWindow(samples[index - 1].epochMillis + stepMillis / 2)
            }
        }
        if (windowStart != null) closeWindow(toMillis)

        return windows
    }

    private fun reasonsFor(peak: FishingScoreSample): List<String> = buildList {
        if (peak.tideRateScore >= 0.55) add("Fast-moving tide")
        if (peak.isNearDawn) add("Near dawn")
        if (peak.isNearDusk) add("Near dusk")
        if (peak.moonScore >= 0.6) add("Spring tide (near new/full moon)")
        if (isEmpty()) add("Favorable conditions")
    }
}
