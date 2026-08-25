package com.captainavi.app.safety

/**
 * Dark-return risk: will the boat get back to home before local sunset?
 *
 * Pure decision logic (no Android, no network) so it is unit-testable. Sunset is
 * computed separately by [SolarEventCalculator] — on-device, so this works
 * mid-ocean without a signal.
 *
 * Speed handling: ETA uses the current speed while genuinely underway
 * (≥ [MIN_UNDERWAY_KNOTS]); when drifting or anchored it falls back to the
 * configured typical cruise speed, otherwise a stationary fisherman would get an
 * infinite ETA and a permanent alarm.
 */
enum class DarkReturnState {
    /** Arrives home with at least the configured margin before sunset. */
    SAFE,

    /** Arrives before sunset but inside the margin window — leave soon. */
    TIGHT,

    /** ETA is after sunset (or sunset has already passed) — underway in darkness. */
    AFTER_DARK,
}

data class DarkReturnResult(
    val state: DarkReturnState,
    val etaMinutes: Double,
    /** Minutes between ETA home and sunset. Negative = arrival after sunset. */
    val arrivalMarginMinutes: Double,
    val sunsetEpochMillis: Long,
    /** Speed the ETA was computed with (current when underway, cruise otherwise). */
    val effectiveSpeedKnots: Double,
)

object DarkReturnEvaluator {

    /** Warn when arrival is inside this many minutes of sunset. */
    const val DEFAULT_MARGIN_MINUTES = 45.0

    /** Default assumed cruise speed when drifting/anchored (small dhoni). */
    const val DEFAULT_CRUISE_SPEED_KNOTS = 8.0

    /** Below this speed the boat is treated as drifting/anchored, not underway. */
    const val MIN_UNDERWAY_KNOTS = 3.0

    /** Closer to home than this and there is nothing meaningful to warn about. */
    const val MIN_RETURN_DISTANCE_NM = 0.25

    fun evaluate(
        nowEpochMillis: Long,
        sunsetEpochMillis: Long,
        distanceToHomeNm: Double,
        currentSpeedKnots: Double,
        cruiseSpeedKnots: Double,
        marginMinutes: Double = DEFAULT_MARGIN_MINUTES,
    ): DarkReturnResult? {
        if (distanceToHomeNm < MIN_RETURN_DISTANCE_NM) return null

        val effectiveSpeed = if (currentSpeedKnots >= MIN_UNDERWAY_KNOTS) {
            currentSpeedKnots
        } else {
            cruiseSpeedKnots
        }
        if (!effectiveSpeed.isFinite() || effectiveSpeed <= 0.0) return null

        val etaMinutes = distanceToHomeNm / effectiveSpeed * 60.0
        val arrivalMillis = nowEpochMillis + (etaMinutes * 60_000.0).toLong()
        val arrivalMarginMinutes = (sunsetEpochMillis - arrivalMillis) / 60_000.0

        val state = when {
            arrivalMarginMinutes < 0.0 -> DarkReturnState.AFTER_DARK
            arrivalMarginMinutes < marginMinutes -> DarkReturnState.TIGHT
            else -> DarkReturnState.SAFE
        }

        return DarkReturnResult(
            state = state,
            etaMinutes = etaMinutes,
            arrivalMarginMinutes = arrivalMarginMinutes,
            sunsetEpochMillis = sunsetEpochMillis,
            effectiveSpeedKnots = effectiveSpeed,
        )
    }
}
