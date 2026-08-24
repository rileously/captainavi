package com.captainavi.app.safety

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveGpsIntervalPolicyTest {

    @Test
    fun underwayFarFromHazardAndNotAnchoredIsFast() {
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = 8.0,
            isAnchored = false,
            nearestHazardDistanceMeters = 5_000.0,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.FAST, profile)
    }

    @Test
    fun anchoredAndClearOfHazardsIsSlow() {
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = 0.0,
            isAnchored = true,
            nearestHazardDistanceMeters = 5_000.0,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.SLOW, profile)
    }

    @Test
    fun idleNotAnchoredNoHazardIsNormal() {
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = 0.5,
            isAnchored = false,
            nearestHazardDistanceMeters = null,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.NORMAL, profile)
    }

    @Test
    fun hazardProximityOverridesAnchoredState() {
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = 0.0,
            isAnchored = true,
            nearestHazardDistanceMeters = 100.0,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.FAST, profile)
    }

    @Test
    fun hazardExactlyAtFastZoneBoundaryIsFast() {
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = 0.0,
            isAnchored = false,
            nearestHazardDistanceMeters = AdaptiveGpsIntervalPolicy.HAZARD_FAST_ZONE_METERS,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.FAST, profile)
    }

    @Test
    fun hazardJustOutsideFastZoneDoesNotForceFast() {
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = 0.0,
            isAnchored = false,
            nearestHazardDistanceMeters = AdaptiveGpsIntervalPolicy.HAZARD_FAST_ZONE_METERS + 1.0,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.NORMAL, profile)
    }

    @Test
    fun negativeHazardDistanceMeansInsideReefAndIsFast() {
        // A negative value means the boat is already within the reef's danger radius.
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = 1.0,
            isAnchored = false,
            nearestHazardDistanceMeters = -20.0,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.FAST, profile)
    }

    @Test
    fun speedExactlyAtUnderwayThresholdIsFast() {
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = AdaptiveGpsIntervalPolicy.UNDERWAY_SPEED_KNOTS,
            isAnchored = false,
            nearestHazardDistanceMeters = null,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.FAST, profile)
    }

    @Test
    fun speedJustBelowUnderwayThresholdIsNormal() {
        val profile = AdaptiveGpsIntervalPolicy.choose(
            speedKnots = AdaptiveGpsIntervalPolicy.UNDERWAY_SPEED_KNOTS - 0.1,
            isAnchored = false,
            nearestHazardDistanceMeters = null,
        )

        assertEquals(AdaptiveGpsIntervalPolicy.NORMAL, profile)
    }
}
