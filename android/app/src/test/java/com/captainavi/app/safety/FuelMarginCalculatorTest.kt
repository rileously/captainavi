package com.captainavi.app.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelMarginCalculatorTest {

    @Test
    fun freshDeparturePositiveMargin() {
        // 25 L per 11 NM reference; full tank, no distance travelled, home is close.
        val result = FuelMarginCalculator.evaluate(
            tankLiters = 50.0,
            distanceTraveledNm = 0.0,
            distanceToHomeNm = 1.0,
            referenceDistanceNm = 11.0,
            referenceFuelLiters = 25.0,
        )

        requireNotNull(result)
        assertEquals(25.0 / 11.0, result.burnRateLitersPerNm, 0.0001)
        assertEquals(0.0, result.fuelUsedLiters, 0.001)
        assertEquals(50.0, result.fuelRemainingLiters, 0.001)
        assertTrue("Expected a comfortable positive margin near departure", result.marginLiters > 40.0)
    }

    @Test
    fun farFromHomeLateInTripCanGoNegative() {
        val result = FuelMarginCalculator.evaluate(
            tankLiters = 50.0,
            distanceTraveledNm = 15.0,
            distanceToHomeNm = 15.0,
            referenceDistanceNm = 11.0,
            referenceFuelLiters = 25.0,
        )

        requireNotNull(result)
        // Used = 15 * (25/11) ~= 34.1 L, remaining ~= 15.9 L, needed for the same distance ~= 34.1 L -> deeply negative.
        assertTrue("Expected a negative margin", result.marginLiters < 0.0)
    }

    @Test
    fun fuelRemainingNeverGoesBelowZeroEvenIfOverBudget() {
        val result = FuelMarginCalculator.evaluate(
            tankLiters = 20.0,
            distanceTraveledNm = 100.0,
            distanceToHomeNm = 0.0,
            referenceDistanceNm = 11.0,
            referenceFuelLiters = 25.0,
        )

        requireNotNull(result)
        assertEquals(0.0, result.fuelRemainingLiters, 0.001)
    }

    @Test
    fun negativeDistancesAreTreatedAsZero() {
        val result = FuelMarginCalculator.evaluate(
            tankLiters = 50.0,
            distanceTraveledNm = -5.0,
            distanceToHomeNm = -2.0,
            referenceDistanceNm = 11.0,
            referenceFuelLiters = 25.0,
        )

        requireNotNull(result)
        assertEquals(0.0, result.fuelUsedLiters, 0.001)
        assertEquals(0.0, result.fuelNeededToReturnLiters, 0.001)
        assertEquals(50.0, result.marginLiters, 0.001)
    }

    @Test
    fun invalidCalibrationOrTankProducesNull() {
        assertNull(
            FuelMarginCalculator.evaluate(
                tankLiters = 0.0,
                distanceTraveledNm = 1.0,
                distanceToHomeNm = 1.0,
                referenceDistanceNm = 11.0,
                referenceFuelLiters = 25.0,
            ),
        )
        assertNull(
            FuelMarginCalculator.evaluate(
                tankLiters = 50.0,
                distanceTraveledNm = 1.0,
                distanceToHomeNm = 1.0,
                referenceDistanceNm = 0.0,
                referenceFuelLiters = 25.0,
            ),
        )
    }
}
