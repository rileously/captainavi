package com.captainavi.app.marine

import com.captainavi.app.data.remote.MarineConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineConditionAdvisoriesTest {
    private fun conditions(
        waveHeight: Double = 1.0,
        gusts: Double = 12.0,
        visibility: Double = 10_000.0,
        weatherCode: Int = 1,
    ) = MarineConditions(
        latitude = 6.7472,
        longitude = 72.9333,
        forecastTime = "2026-08-21T22:00",
        fetchedAtMillis = 1L,
        waveHeightMeters = waveHeight,
        windGustKnots = gusts,
        visibilityMeters = visibility,
        weatherCode = weatherCode,
    )

    @Test
    fun ordinaryConditionsHaveNoAdvisory() {
        assertTrue(marineConditionAdvisories(conditions()).isEmpty())
    }

    @Test
    fun elevatedModelValuesAreCalledOutIndividually() {
        val advisories = marineConditionAdvisories(
            conditions(waveHeight = 2.7, gusts = 28.0, visibility = 1_500.0, weatherCode = 95)
        )

        assertEquals(4, advisories.size)
        assertTrue(advisories.any { "Wave height" in it })
        assertTrue(advisories.any { "Wind gusts" in it })
        assertTrue(advisories.any { "Visibility" in it })
        assertTrue(advisories.any { "Thunderstorm" in it })
    }

    @Test
    fun weatherCodesProduceReadableLabels() {
        assertEquals("Clear", weatherCodeLabel(0))
        assertEquals("Fog", weatherCodeLabel(45))
        assertEquals("Thunderstorm", weatherCodeLabel(99))
    }
}
