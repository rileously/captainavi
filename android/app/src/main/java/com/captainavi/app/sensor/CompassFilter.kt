package com.captainavi.app.sensor

/**
 * Filter for magnetic and orientation sensor heading streams.
 * Handles 0°/360° circular boundary transitions seamlessly with adaptive response.
 */
class CompassFilter(
    private val smoothingFactor: Float = 0.35f
) {
    private var currentHeading: Float? = null

    /**
     * Updates the filter with a new raw heading reading (0..360) and returns the smoothed heading.
     */
    fun update(rawDegrees: Float): Float {
        val normalizedRaw = ((rawDegrees % 360f) + 360f) % 360f
        val prev = currentHeading
        if (prev == null) {
            currentHeading = normalizedRaw
            return normalizedRaw
        }

        // Shortest angular difference (-180..+180)
        val delta = ((normalizedRaw - prev + 540f) % 360f) - 180f

        // If the change is very large (e.g. quick whip of phone), increase tracking speed
        val effectiveAlpha = if (kotlin.math.abs(delta) > 45f) {
            (smoothingFactor * 2.0f).coerceAtMost(0.85f)
        } else {
            smoothingFactor
        }

        val smoothed = (prev + delta * effectiveAlpha + 360f) % 360f
        currentHeading = smoothed
        return smoothed
    }

    /**
     * Resets the filter state.
     */
    fun reset() {
        currentHeading = null
    }

    companion object {
        /**
         * Calculates shortest angular distance between fromDegrees and toDegrees in range [-180, 180]
         */
        fun shortestDelta(fromDegrees: Float, toDegrees: Float): Float {
            return ((toDegrees - fromDegrees + 540f) % 360f) - 180f
        }
    }
}
