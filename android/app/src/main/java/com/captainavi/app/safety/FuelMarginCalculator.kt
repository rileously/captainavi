package com.captainavi.app.safety

/**
 * Live fuel-margin estimate: given how far the boat has already travelled this trip
 * and how far it currently is from home, is there likely enough fuel left to get back?
 *
 * There's no fuel gauge input in this app — burn rate is derived from the existing
 * trip cost/fuel calibration (Settings: "N NM = X MVR = Y L"), scaled linearly, same
 * assumption [com.captainavi.app.safety.TripCalculator] already makes for one-way trip
 * estimates. This is an estimate, not a measurement: it assumes a full/configured tank
 * at departure and a constant burn rate, and doesn't account for currents, load, or
 * weather.
 */
data class FuelMarginResult(
    val burnRateLitersPerNm: Double,
    val fuelUsedLiters: Double,
    val fuelRemainingLiters: Double,
    val fuelNeededToReturnLiters: Double,
    val marginLiters: Double,
)

object FuelMarginCalculator {
    const val DEFAULT_TANK_LITERS = 50.0

    /** Below this fraction of tank capacity remaining after the return trip, warn. */
    const val WARNING_MARGIN_FRACTION = 0.20

    /** Re-arm the alert once margin recovers above this fraction (hysteresis, avoids flapping). */
    const val RECOVERY_MARGIN_FRACTION = 0.30

    fun evaluate(
        tankLiters: Double,
        distanceTraveledNm: Double,
        distanceToHomeNm: Double,
        referenceDistanceNm: Double,
        referenceFuelLiters: Double,
    ): FuelMarginResult? {
        if (tankLiters <= 0.0 || referenceDistanceNm <= 0.0 || referenceFuelLiters < 0.0) return null

        val burnRate = referenceFuelLiters / referenceDistanceNm
        val used = distanceTraveledNm.coerceAtLeast(0.0) * burnRate
        val remaining = (tankLiters - used).coerceAtLeast(0.0)
        val needed = distanceToHomeNm.coerceAtLeast(0.0) * burnRate
        val margin = remaining - needed

        return FuelMarginResult(
            burnRateLitersPerNm = burnRate,
            fuelUsedLiters = used,
            fuelRemainingLiters = remaining,
            fuelNeededToReturnLiters = needed,
            marginLiters = margin,
        )
    }
}
