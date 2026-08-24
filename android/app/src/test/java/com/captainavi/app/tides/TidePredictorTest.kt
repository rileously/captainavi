package com.captainavi.app.tides

import com.captainavi.app.safety.NauticalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TidePredictorTest {

    @Test
    fun offlineCurveMatchesPublishedHanimaadhooPrediction() {
        // Maldives midnight, 09:00, 15:00 and 24:00 on 22 August 2026.
        val expectedLatHeights = listOf(
            "2026-08-21T19:00:00Z" to 0.365,
            "2026-08-22T04:00:00Z" to 0.885,
            "2026-08-22T10:00:00Z" to 0.841,
            "2026-08-22T19:00:00Z" to 0.413,
        )
        expectedLatHeights.forEach { (instant, expected) ->
            assertEquals(expected, TidePredictor.heightLatMeters(Instant.parse(instant).toEpochMilli()), 0.02)
        }
    }

    @Test
    fun snapshotFindsBothHighAndLowWithinADay() {
        val ts = Instant.parse("2026-08-22T00:00:00Z").toEpochMilli()
        val snap = TidePredictor.snapshot(ts)
        assertTrue(snap.nextHigh != null)
        assertTrue(snap.nextLow != null)
        assertTrue(snap.nextHigh!!.isHigh)
        assertTrue(!snap.nextLow!!.isHigh)
        assertTrue(snap.nextHigh!!.epochMillis > ts)
        assertTrue(snap.nextLow!!.epochMillis > ts)
    }

    @Test
    fun augustTwentySecondHasOneHighAndNoFalseAfternoonHigh() {
        val localDayStart = Instant.parse("2026-08-21T19:00:00Z").toEpochMilli()
        val highs = TidePredictor.extremaBetween(localDayStart, localDayStart + 24 * 3_600_000L)
            .filter { it.isHigh }
        assertEquals(1, highs.size)
        val localHour = (highs.single().epochMillis - localDayStart) / 3_600_000.0
        assertTrue("Hanimaadhoo high was at local hour $localHour", localHour in 8.5..9.5)
    }

    @Test
    fun naivaadhooIsAboutFourteenNmFromHanimaadhoo() {
        val nm = NauticalMath.distanceNauticalMiles(
            TideStation.latitude,
            TideStation.longitude,
            TideStation.naivaadhooLatitude,
            TideStation.naivaadhooLongitude,
        )
        assertEquals(14.0, nm, 1.5)
    }
}
