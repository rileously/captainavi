package com.captainavi.app.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTraceGeometryTest {

    @Test
    fun headingEndpointProjectsNorthForOneNauticalMile() {
        val end = projectHeadingEndpoint(
            start = TraceCoordinate(0.0, 73.0),
            headingDegrees = 0.0,
            distanceMeters = 1_852.0,
        )

        assertEquals(0.01665, end.latitude, 0.0001)
        assertEquals(73.0, end.longitude, 0.0001)
    }

    @Test
    fun headingEndpointNormalizesEastwardHeadingAndLongitude() {
        val end = projectHeadingEndpoint(
            start = TraceCoordinate(0.0, 179.999),
            headingDegrees = 450.0,
            distanceMeters = 1_852.0,
        )

        assertEquals(0.0, end.latitude, 0.0001)
        assertTrue(end.longitude < -179.98)
    }

    @Test
    fun headingLineAlwaysExtendsBeyondViewportDiagonal() {
        val lineMeters = headingLineDistanceMeters(
            visibleNorthLatitude = 7.0,
            visibleSouthLatitude = 6.0,
            visibleEastLongitude = 74.0,
            visibleWestLongitude = 73.0,
        )

        assertTrue(lineMeters > 200_000.0)
    }

    @Test
    fun traceDistanceAddsEveryJourneyLeg() {
        val distance = traceDistanceNauticalMiles(
            listOf(
                TraceCoordinate(0.0, 73.0),
                TraceCoordinate(0.016655, 73.0),
                TraceCoordinate(0.016655, 73.016655),
            ),
        )

        assertEquals(2.0, distance, 0.01)
    }

    @Test
    fun elapsedHeaderUsesMinutesThenHours() {
        assertEquals("05m07s", formatTraceElapsed(307))
        assertEquals("2h03m", formatTraceElapsed(7_399))
    }
}
