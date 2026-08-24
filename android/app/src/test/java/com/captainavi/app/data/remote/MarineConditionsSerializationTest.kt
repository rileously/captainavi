package com.captainavi.app.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineConditionsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyCurrentConditionsCacheRemainsReadable() {
        val cached = json.decodeFromString<MarineConditions>(
            """{"latitude":6.7,"longitude":73.1,"forecastTime":"2026-08-23T03:00","fetchedAtMillis":1}""",
        )

        assertTrue(cached.hourlyForecast.isEmpty())
        assertTrue(cached.dailyForecast.isEmpty())
    }

    @Test
    fun hourlyAndDailyForecastsRoundTripThroughOfflineCache() {
        val conditions = MarineConditions(
            latitude = 6.7,
            longitude = 73.1,
            forecastTime = "2026-08-23T03:00",
            fetchedAtMillis = 1,
            hourlyForecast = listOf(
                MarineForecastHour(
                    time = "2026-08-23T03:00",
                    windSpeedKnots = 12.4,
                    waveHeightMeters = 1.1,
                    pressureMslHpa = 1012.0,
                ),
            ),
            dailyForecast = listOf(
                MarineForecastDay(
                    date = "2026-08-23",
                    sunrise = "2026-08-23T06:00",
                    sunset = "2026-08-23T18:18",
                ),
            ),
        )

        val restored = json.decodeFromString<MarineConditions>(json.encodeToString(conditions))

        assertEquals(12.4, restored.hourlyForecast.single().windSpeedKnots!!, 0.0)
        assertEquals(1.1, restored.hourlyForecast.single().waveHeightMeters!!, 0.0)
        assertEquals("2026-08-23T18:18", restored.dailyForecast.single().sunset)
    }
}
