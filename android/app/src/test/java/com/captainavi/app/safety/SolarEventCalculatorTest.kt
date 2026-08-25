package com.captainavi.app.safety

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarEventCalculatorTest {

    private fun utcEpoch(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun utcClockMinutes(epochMillis: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = epochMillis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    @Test
    fun maleLateAugustSunsetAroundLocalSixFifteen() {
        // Malé (4.1755 N, 73.5093 E). Maldives is UTC+5; late-August sunset is
        // ~18:14 local => ~13:14 UTC. Assert a generous window.
        val sunset = SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 8, 26), 4.1755, 73.5093)

        requireNotNull(sunset)
        val minutes = utcClockMinutes(sunset)
        assertTrue("Sunset $minutes min UTC, expected 12:45–13:45 UTC", minutes in 765..825)
    }

    @Test
    fun maleJanuarySunsetAlsoEarlyEvening() {
        // Near the equator sunset barely moves all year; January Malé ~18:20 local.
        val sunset = SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 1, 15), 4.1755, 73.5093)

        requireNotNull(sunset)
        val minutes = utcClockMinutes(sunset)
        assertTrue("Sunset $minutes min UTC, expected 12:45–13:45 UTC", minutes in 765..825)
    }

    @Test
    fun londonWinterSunsetIsMidAfternoonUtc() {
        // 21 Dec in London (51.5 N, 0.1 W): sunset ~15:53 UTC.
        val sunset = SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 12, 21), 51.5074, -0.1278)

        requireNotNull(sunset)
        val minutes = utcClockMinutes(sunset)
        assertTrue("Sunset $minutes min UTC, expected 15:20–16:20 UTC", minutes in 920..980)
    }

    @Test
    fun londonSummerSunsetIsLateEveningUtc() {
        // 21 Jun in London: sunset ~21:21 local (BST = UTC+1) => ~20:21 UTC.
        val sunset = SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 6, 21), 51.5074, -0.1278)

        requireNotNull(sunset)
        val minutes = utcClockMinutes(sunset)
        assertTrue("Sunset $minutes min UTC, expected 19:55–20:45 UTC", minutes in 1195..1245)
    }

    @Test
    fun polarDayAndNightReturnNull() {
        // Tromsø (69.65 N): midnight sun in June, polar night in December.
        assertNull(SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 6, 21), 69.6492, 18.9560))
        assertNull(SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 12, 21), 69.6492, 18.9560))
    }

    @Test
    fun invalidCoordinatesReturnNull() {
        assertNull(SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 8, 26), 91.0, 73.0))
        assertNull(SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 8, 26), 4.0, 181.0))
    }

    @Test
    fun nearbyIslandsShareNearlyTheSameSunset() {
        val male = SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 8, 26), 4.1755, 73.5093)
        val addu = SolarEventCalculator.sunsetEpochMillis(utcEpoch(2026, 8, 26), -0.6413, 73.1580)

        assertNotNull(male)
        assertNotNull(addu)
        val diffMinutes = Math.abs(male!! - addu!!) / 60_000.0
        assertTrue("Malé vs Addu sunset differed by $diffMinutes min, expected < 25 min", diffMinutes < 25.0)
    }
}
