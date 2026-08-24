package com.captainavi.app.safety

/** A GPS polling cadence: how often to ask for a fix and how far the boat must move to trigger an early one. */
data class GpsUpdateProfile(
    val label: String,
    val intervalMillis: Long,
    val minUpdateDistanceMeters: Float,
)

/**
 * Decides how often the foreground trip service should poll GPS, instead of one fixed
 * rate for the whole trip. Slowing down while anchored is the single biggest battery
 * lever available — a fishing stop can last hours — while speeding back up near a
 * charted hazard or while underway keeps safety-relevant detection (reef proximity,
 * anchor drag, drift) prompt.
 *
 * Pure decision logic only; no Android/location APIs here.
 */
object AdaptiveGpsIntervalPolicy {
    /** Baseline cadence, used whenever neither FAST nor SLOW applies (e.g. drifting near idle, not anchored). */
    val NORMAL = GpsUpdateProfile("NORMAL", 3_000L, 3.0f)

    /** Underway or close to a charted hazard: tighten back toward near-1Hz precision. */
    val FAST = GpsUpdateProfile("FAST", 1_500L, 1.0f)

    /** Intentionally anchored and clear of hazards: the big battery win. */
    val SLOW = GpsUpdateProfile("SLOW_ANCHORED", 12_000L, 8.0f)

    const val UNDERWAY_SPEED_KNOTS = 3.0
    const val HAZARD_FAST_ZONE_METERS = 500.0

    /**
     * [nearestHazardDistanceMeters] is the distance to the nearest charted hazard edge
     * (danger reef radius or official reef boundary), or null if none is known/enabled.
     * A hazard within [HAZARD_FAST_ZONE_METERS] always wins — safety overrides the
     * battery saving from being anchored or stationary.
     */
    fun choose(
        speedKnots: Double,
        isAnchored: Boolean,
        nearestHazardDistanceMeters: Double?,
    ): GpsUpdateProfile {
        if (nearestHazardDistanceMeters != null && nearestHazardDistanceMeters <= HAZARD_FAST_ZONE_METERS) {
            return FAST
        }
        if (isAnchored) return SLOW
        if (speedKnots >= UNDERWAY_SPEED_KNOTS) return FAST
        return NORMAL
    }
}
