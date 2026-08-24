package com.captainavi.app.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NauticalMathTest {

    @Test
    fun testMetersPerSecondToKnots() {
        // 1 m/s ~= 1.94384 knots
        val knots = NauticalMath.metersPerSecondToKnots(10.0f)
        assertEquals(19.4384, knots, 0.01)

        // 0 m/s = 0 knots
        assertEquals(0.0, NauticalMath.metersPerSecondToKnots(0.0f), 0.001)
    }

    @Test
    fun testDistanceNauticalMiles() {
        // Distance between Malé (4.1755, 73.5093) and Hulhumalé (4.2150, 73.5400) ~ 3.0 NM
        val distNm = NauticalMath.distanceNauticalMiles(4.1755, 73.5093, 4.2150, 73.5400)
        assertTrue("Distance should be between 2.5 and 3.5 NM", distNm in 2.5..3.5)
    }

    @Test
    fun testBearingDegrees() {
        // Due North from (0,0) to (1,0) should be 0° (or 360°)
        val bearingNorth = NauticalMath.bearingDegrees(0.0, 0.0, 1.0, 0.0)
        assertEquals(0.0, bearingNorth, 0.5)

        // Due East from (0,0) to (0,1) should be 90°
        val bearingEast = NauticalMath.bearingDegrees(0.0, 0.0, 0.0, 1.0)
        assertEquals(90.0, bearingEast, 0.5)

        // Due South from (1,0) to (0,0) should be 180°
        val bearingSouth = NauticalMath.bearingDegrees(1.0, 0.0, 0.0, 0.0)
        assertEquals(180.0, bearingSouth, 0.5)
    }

    @Test
    fun testDegreesToCardinal() {
        assertEquals("North (N)", NauticalMath.degreesToCardinal(0.0))
        assertEquals("Northeast (NE)", NauticalMath.degreesToCardinal(45.0))
        assertEquals("East (E)", NauticalMath.degreesToCardinal(90.0))
        assertEquals("South (S)", NauticalMath.degreesToCardinal(180.0))
        assertEquals("West (W)", NauticalMath.degreesToCardinal(270.0))
    }

    @Test
    fun testDegreesToShortCardinal() {
        assertEquals("N", NauticalMath.degreesToShortCardinal(0.0))
        assertEquals("NE", NauticalMath.degreesToShortCardinal(45.0))
        assertEquals("E", NauticalMath.degreesToShortCardinal(90.0))
        assertEquals("SW", NauticalMath.degreesToShortCardinal(225.0))
    }

    @Test
    fun crossTrackErrorIsZeroOnLegAndSignedOffLeg() {
        val onLeg = NauticalMath.xteMeters(
            vesselLat = 0.5,
            vesselLon = 0.0,
            startLat = 0.0,
            startLon = 0.0,
            endLat = 1.0,
            endLon = 0.0,
        )
        val eastOfLeg = NauticalMath.xteMeters(
            vesselLat = 0.5,
            vesselLon = 0.01,
            startLat = 0.0,
            startLon = 0.0,
            endLat = 1.0,
            endLon = 0.0,
        )

        assertEquals(0.0, onLeg, 1.0)
        assertTrue(kotlin.math.abs(eastOfLeg) > 1_000.0)
    }
}
