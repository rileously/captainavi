package com.captainavi.app.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassFilterTest {

    @Test
    fun testInitialReadingReturnsDirectly() {
        val filter = CompassFilter(smoothingFactor = 0.35f)
        val result = filter.update(45.0f)
        assertEquals(45.0f, result, 0.01f)
    }

    @Test
    fun testShortestDeltaCalculations() {
        // Normal clockwise step
        assertEquals(10f, CompassFilter.shortestDelta(10f, 20f), 0.01f)
        // Normal counter-clockwise step
        assertEquals(-10f, CompassFilter.shortestDelta(20f, 10f), 0.01f)
        // Across North (350 to 10 is +20)
        assertEquals(20f, CompassFilter.shortestDelta(350f, 10f), 0.01f)
        // Across North reverse (10 to 350 is -20)
        assertEquals(-20f, CompassFilter.shortestDelta(10f, 350f), 0.01f)
        // Exact opposite (180)
        assertEquals(-180f, CompassFilter.shortestDelta(0f, 180f), 0.01f)
    }

    @Test
    fun testNorthBoundaryWrappingSmoothConvergence() {
        val filter = CompassFilter(smoothingFactor = 0.5f)
        // Start near North on West side (355°)
        filter.update(355.0f)

        // Turn right past North to 5°
        val smoothed = filter.update(5.0f)

        // Shortest delta is +10°. With alpha=0.5, it should move +5° to 0° (or 360°), NOT jump through 180°!
        assertEquals(0.0f, smoothed, 0.01f)

        // Next reading still at 5°
        val smoothed2 = filter.update(5.0f)
        assertEquals(2.5f, smoothed2, 0.01f)
    }

    @Test
    fun testFastMovementAdaptiveTracking() {
        val filter = CompassFilter(smoothingFactor = 0.2f)
        filter.update(0f)

        // Sudden 90° turn
        val smoothed = filter.update(90f)

        // Should adaptively track faster than base 0.2 alpha (effective alpha is 0.4)
        assertTrue("Smoothed value should have moved by at least 30 degrees", smoothed >= 30.0f)
    }

    @Test
    fun testResetClearsPreviousHeading() {
        val filter = CompassFilter(smoothingFactor = 0.3f)
        filter.update(100f)
        filter.reset()

        // After reset, new reading starts immediately without interpolation
        val result = filter.update(270f)
        assertEquals(270f, result, 0.01f)
    }
}
