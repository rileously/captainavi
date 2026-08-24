package com.captainavi.app.safety

import org.junit.Assert.assertEquals
import org.junit.Test

class TripCalculatorTest {
    @Test
    fun referenceTripReturnsConfiguredCostAndFuel() {
        val estimate = TripCalculator.estimate(
            distanceNauticalMiles = 11.0,
            referenceDistanceNauticalMiles = 11.0,
            referenceCostMvr = 1_000.0,
            referenceFuelLiters = 25.0,
        )

        assertEquals(11.0, estimate.distanceNauticalMiles, 0.001)
        assertEquals(1_000.0, estimate.costMvr, 0.001)
        assertEquals(25.0, estimate.fuelLiters, 0.001)
    }

    @Test
    fun estimatesScaleWithDestinationDistance() {
        val estimate = TripCalculator.estimate(
            distanceNauticalMiles = 22.0,
            referenceDistanceNauticalMiles = 11.0,
            referenceCostMvr = 1_000.0,
            referenceFuelLiters = 25.0,
        )

        assertEquals(2_000.0, estimate.costMvr, 0.001)
        assertEquals(50.0, estimate.fuelLiters, 0.001)
    }

    @Test
    fun invalidReferenceDistanceProducesSafeZeroEstimates() {
        val estimate = TripCalculator.estimate(
            distanceNauticalMiles = 11.0,
            referenceDistanceNauticalMiles = 0.0,
            referenceCostMvr = 1_000.0,
            referenceFuelLiters = 25.0,
        )

        assertEquals(0.0, estimate.costMvr, 0.001)
        assertEquals(0.0, estimate.fuelLiters, 0.001)
    }
}
