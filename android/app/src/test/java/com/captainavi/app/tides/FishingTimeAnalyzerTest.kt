package com.captainavi.app.tides

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FishingTimeAnalyzerTest {

    @Test
    fun `tide rate is near zero right at an extreme and higher mid-cycle`() {
        val dayStart = Instant.parse("2026-08-21T19:00:00Z").toEpochMilli()
        val extremes = TidePredictor.extremaBetween(dayStart, dayStart + 48 * 3_600_000L)
        val high = extremes.first { it.isHigh }
        val next = extremes.first { it.epochMillis > high.epochMillis }
        val midCycle = high.epochMillis + (next.epochMillis - high.epochMillis) / 2

        val atExtreme = FishingTimeAnalyzer.scoreAt(high.epochMillis, sunriseEpochMillis = null, sunsetEpochMillis = null)
        val atMidCycle = FishingTimeAnalyzer.scoreAt(midCycle, sunriseEpochMillis = null, sunsetEpochMillis = null)

        assertTrue(
            "Expected mid-cycle tide rate (${atMidCycle.tideRateScore}) to exceed the extreme's (${atExtreme.tideRateScore})",
            atMidCycle.tideRateScore > atExtreme.tideRateScore,
        )
        assertTrue(atExtreme.tideRateScore < 0.2)
    }

    @Test
    fun `score at sunrise peaks the time-of-day component and flags dawn`() {
        val sunrise = Instant.parse("2026-08-22T01:00:00Z").toEpochMilli()

        val atSunrise = FishingTimeAnalyzer.scoreAt(sunrise, sunriseEpochMillis = sunrise, sunsetEpochMillis = null)

        assertEquals(1.0, atSunrise.timeOfDayScore, 0.001)
        assertTrue(atSunrise.isNearDawn)
        assertTrue(!atSunrise.isNearDusk)
    }

    @Test
    fun `time-of-day bump fades to zero well outside the golden hour window`() {
        val sunrise = Instant.parse("2026-08-22T01:00:00Z").toEpochMilli()
        val threeHoursLater = sunrise + 3 * 3_600_000L

        val sample = FishingTimeAnalyzer.scoreAt(threeHoursLater, sunriseEpochMillis = sunrise, sunsetEpochMillis = null)

        assertEquals(0.0, sample.timeOfDayScore, 0.001)
    }

    @Test
    fun `dusk wins over a farther-away dawn`() {
        val sunrise = Instant.parse("2026-08-22T01:00:00Z").toEpochMilli()
        val sunset = Instant.parse("2026-08-22T13:00:00Z").toEpochMilli()
        val closeToSunset = sunset - 20 * 60_000L

        val sample = FishingTimeAnalyzer.scoreAt(closeToSunset, sunriseEpochMillis = sunrise, sunsetEpochMillis = sunset)

        assertTrue(sample.isNearDusk)
        assertTrue(!sample.isNearDawn)
    }

    @Test
    fun `every score stays within 0 to 100`() {
        val start = Instant.parse("2026-08-22T00:00:00Z").toEpochMilli()
        for (hour in 0 until 48) {
            val t = start + hour * 3_600_000L
            val score = FishingTimeAnalyzer.scoreAt(t, sunriseEpochMillis = null, sunsetEpochMillis = null).score
            assertTrue("score $score out of range at hour $hour", score in 0..100)
        }
    }

    @Test
    fun `best windows are chronological, non-overlapping, and above threshold`() {
        val from = Instant.parse("2026-08-22T00:00:00Z").toEpochMilli()
        val to = from + 24 * 3_600_000L
        val sunrise = Instant.parse("2026-08-22T01:00:00Z").toEpochMilli()
        val sunset = Instant.parse("2026-08-22T13:00:00Z").toEpochMilli()

        val windows = FishingTimeAnalyzer.bestWindows(from, to, sunrise, sunset)

        windows.forEach { window ->
            assertTrue(window.peakScore >= 60)
            assertTrue(window.endEpochMillis > window.startEpochMillis)
            assertTrue(window.reasons.isNotEmpty())
        }
        for (i in 1 until windows.size) {
            assertTrue(windows[i].startEpochMillis >= windows[i - 1].endEpochMillis)
        }
    }

    @Test
    fun `an invalid range or step produces no windows`() {
        val t = Instant.parse("2026-08-22T00:00:00Z").toEpochMilli()

        assertTrue(FishingTimeAnalyzer.bestWindows(t, t, null, null).isEmpty())
        assertTrue(FishingTimeAnalyzer.bestWindows(t, t + 3_600_000L, null, null, stepMinutes = 0).isEmpty())
    }
}
