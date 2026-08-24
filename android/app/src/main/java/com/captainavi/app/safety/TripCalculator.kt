package com.captainavi.app.safety

data class TripEstimate(
    val distanceNauticalMiles: Double,
    val costMvr: Double,
    val fuelLiters: Double,
)

object TripCalculator {
    const val DEFAULT_REFERENCE_DISTANCE_NM = 11.0
    const val DEFAULT_REFERENCE_COST_MVR = 1_000.0
    const val DEFAULT_REFERENCE_FUEL_LITERS = 25.0

    /**
     * Scales a known one-way trip cost and fuel use by straight-line nautical distance.
     */
    fun estimate(
        distanceNauticalMiles: Double,
        referenceDistanceNauticalMiles: Double,
        referenceCostMvr: Double,
        referenceFuelLiters: Double,
    ): TripEstimate {
        val safeDistance = distanceNauticalMiles
            .takeIf { it.isFinite() && it > 0.0 }
            ?: 0.0
        if (!referenceDistanceNauticalMiles.isFinite() || referenceDistanceNauticalMiles <= 0.0) {
            return TripEstimate(safeDistance, 0.0, 0.0)
        }

        val safeReferenceCost = referenceCostMvr.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeReferenceFuel = referenceFuelLiters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val distanceRatio = safeDistance / referenceDistanceNauticalMiles
        return TripEstimate(
            distanceNauticalMiles = safeDistance,
            costMvr = safeReferenceCost * distanceRatio,
            fuelLiters = safeReferenceFuel * distanceRatio,
        )
    }
}
