package com.captainavi.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReefBoundaryGeometryTest {
    private val reef = ReefBoundary(
        id = "RFTEST",
        name = "Test Reef",
        atoll = "Test Atoll",
        rings = listOf(
            ring(
                isHole = false,
                0.0 to 0.0,
                0.0 to 0.01,
                0.01 to 0.01,
                0.01 to 0.0,
            ),
            ring(
                isHole = true,
                0.004 to 0.004,
                0.004 to 0.006,
                0.006 to 0.006,
                0.006 to 0.004,
            ),
        ),
        minLatitudeE6 = 0,
        maxLatitudeE6 = 10_000,
        minLongitudeE6 = 0,
        maxLongitudeE6 = 10_000,
        labelLatitudeE6 = 2_000,
        labelLongitudeE6 = 2_000,
    )

    @Test
    fun `point inside outer reef is hazardous`() {
        assertTrue(pointInReef(latitude = 0.002, longitude = 0.002, reef = reef))
    }

    @Test
    fun `point in lagoon hole is not inside reef`() {
        assertFalse(pointInReef(latitude = 0.005, longitude = 0.005, reef = reef))
    }

    @Test
    fun `point outside all rings is not inside reef`() {
        assertFalse(pointInReef(latitude = 0.005, longitude = -0.002, reef = reef))
    }

    @Test
    fun `distance uses nearest polygon boundary`() {
        val distance = distanceToReefBoundaryMeters(
            latitude = 0.005,
            longitude = -0.001,
            reef = reef,
        )
        assertTrue("Expected about 111m, got $distance", distance in 109.0..113.0)
    }

    private fun ring(isHole: Boolean, vararg points: Pair<Double, Double>): ReefRing =
        ReefRing(
            coordinatesE6 = points.flatMap { (latitude, longitude) ->
                listOf((latitude * 1_000_000).toInt(), (longitude * 1_000_000).toInt())
            }.toIntArray(),
            isHole = isHole,
        )
}
