package com.captainavi.app.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkReturnEvaluatorTest {

    private val now = 1_000_000_000_000L
    private val sunset = now + 3 * 3_600_000L // sunset 3 h from now

    @Test
    fun comfortableMarginIsSafe() {
        // 8 NM at 10 kt => 48 min ETA, ~2h12 margin.
        val result = DarkReturnEvaluator.evaluate(
            nowEpochMillis = now,
            sunsetEpochMillis = sunset,
            distanceToHomeNm = 8.0,
            currentSpeedKnots = 10.0,
            cruiseSpeedKnots = 8.0,
        )

        requireNotNull(result)
        assertEquals(DarkReturnState.SAFE, result.state)
        assertEquals(48.0, result.etaMinutes, 0.5)
        assertTrue(result.arrivalMarginMinutes > 2 * 60.0)
    }

    @Test
    fun arrivalInsideMarginWindowIsTight() {
        // 6 NM at 6 kt => 60 min ETA => margin 120 min: safe. Slow to 5 kt => 72 min ETA => 108 margin: still safe (>45).
        // Use 2h55m to sunset with a 3h ETA => after dark. Instead: 1h ETA with 1h20 to sunset => 20 min margin => TIGHT.
        val tightSunset = now + 80 * 60_000L
        val result = DarkReturnEvaluator.evaluate(
            nowEpochMillis = now,
            sunsetEpochMillis = tightSunset,
            distanceToHomeNm = 6.0,
            currentSpeedKnots = 6.0, // 60 min ETA
            cruiseSpeedKnots = 8.0,
        )

        requireNotNull(result)
        assertEquals(DarkReturnState.TIGHT, result.state)
        assertEquals(20.0, result.arrivalMarginMinutes, 0.5)
    }

    @Test
    fun etaAfterSunsetIsAfterDark() {
        // 10 NM at 5 kt => 120 min ETA against 60 min to sunset => 60 min after dark.
        val soonSunset = now + 60 * 60_000L
        val result = DarkReturnEvaluator.evaluate(
            nowEpochMillis = now,
            sunsetEpochMillis = soonSunset,
            distanceToHomeNm = 10.0,
            currentSpeedKnots = 5.0,
            cruiseSpeedKnots = 8.0,
        )

        requireNotNull(result)
        assertEquals(DarkReturnState.AFTER_DARK, result.state)
        assertEquals(-60.0, result.arrivalMarginMinutes, 0.5)
    }

    @Test
    fun driftingBoatFallsBackToCruiseSpeed() {
        // Anchored (0.4 kt) 8 NM out, cruise 8 kt => ETA 60 min.
        val result = DarkReturnEvaluator.evaluate(
            nowEpochMillis = now,
            sunsetEpochMillis = sunset,
            distanceToHomeNm = 8.0,
            currentSpeedKnots = 0.4,
            cruiseSpeedKnots = 8.0,
        )

        requireNotNull(result)
        assertEquals(8.0, result.effectiveSpeedKnots, 0.001)
        assertEquals(60.0, result.etaMinutes, 0.5)
    }

    @Test
    fun underwayFasterThanCruiseUsesCurrentSpeed() {
        val result = DarkReturnEvaluator.evaluate(
            nowEpochMillis = now,
            sunsetEpochMillis = sunset,
            distanceToHomeNm = 20.0,
            currentSpeedKnots = 20.0,
            cruiseSpeedKnots = 8.0,
        )

        requireNotNull(result)
        assertEquals(20.0, result.effectiveSpeedKnots, 0.001)
        assertEquals(60.0, result.etaMinutes, 0.5)
    }

    @Test
    fun basicallyHomeSkipsTheCheck() {
        assertNull(
            DarkReturnEvaluator.evaluate(
                nowEpochMillis = now,
                sunsetEpochMillis = sunset,
                distanceToHomeNm = 0.1,
                currentSpeedKnots = 1.0,
                cruiseSpeedKnots = 8.0,
            ),
        )
    }

    @Test
    fun unusableSpeedSkipsTheCheck() {
        assertNull(
            DarkReturnEvaluator.evaluate(
                nowEpochMillis = now,
                sunsetEpochMillis = sunset,
                distanceToHomeNm = 5.0,
                currentSpeedKnots = 0.0,
                cruiseSpeedKnots = 0.0,
            ),
        )
    }

    @Test
    fun sunsetAlreadyPassedIsAfterDark() {
        val pastSunset = now - 30 * 60_000L // sun went down half an hour ago
        val result = DarkReturnEvaluator.evaluate(
            nowEpochMillis = now,
            sunsetEpochMillis = pastSunset,
            distanceToHomeNm = 2.0,
            currentSpeedKnots = 10.0,
            cruiseSpeedKnots = 8.0,
        )

        requireNotNull(result)
        assertEquals(DarkReturnState.AFTER_DARK, result.state)
        assertTrue(result.arrivalMarginMinutes < 0.0)
    }
}
