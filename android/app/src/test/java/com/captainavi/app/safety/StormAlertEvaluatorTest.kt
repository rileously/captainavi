package com.captainavi.app.safety

import com.captainavi.app.data.remote.MarineConditions
import com.captainavi.app.data.remote.MarineForecastHour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StormAlertEvaluatorTest {

    private fun conditions(
        forecastTime: String = "2026-08-24T09:00",
        waveHeightMeters: Double? = null,
        swellHeightMeters: Double? = null,
        windGustKnots: Double? = null,
        hourlyForecast: List<MarineForecastHour> = emptyList(),
    ) = MarineConditions(
        latitude = 4.1755,
        longitude = 73.5093,
        forecastTime = forecastTime,
        fetchedAtMillis = 0L,
        waveHeightMeters = waveHeightMeters,
        swellHeightMeters = swellHeightMeters,
        windGustKnots = windGustKnots,
        hourlyForecast = hourlyForecast,
    )

    @Test
    fun calmForecastProducesNoAlert() {
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 0.8, windGustKnots = 10.0),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        assertNull(alert)
    }

    @Test
    fun currentWaveHeightAtOrAboveThresholdAlerts() {
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 2.5),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        requireNotNull(alert)
        assertEquals(StormAlertKind.WAVE, alert.kind)
        assertEquals(StormAlertSeverity.WARNING, alert.severity)
        assertEquals(2.5, alert.peakValue, 0.001)
        assertNull(alert.peakTimeIso)
    }

    @Test
    fun futureHourExceedingThresholdAlertsWithItsTime() {
        val hours = listOf(
            MarineForecastHour(time = "2026-08-24T08:00", waveHeightMeters = 3.0),
            MarineForecastHour(time = "2026-08-24T14:00", waveHeightMeters = 2.8),
        )
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(forecastTime = "2026-08-24T09:00", waveHeightMeters = 1.0, hourlyForecast = hours),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        requireNotNull(alert)
        assertEquals(StormAlertKind.WAVE, alert.kind)
        assertEquals(2.8, alert.peakValue, 0.001)
        assertEquals("2026-08-24T14:00", alert.peakTimeIso)
    }

    @Test
    fun pastHoursBeforeForecastTimeAreIgnored() {
        val hours = listOf(MarineForecastHour(time = "2026-08-24T02:00", waveHeightMeters = 5.0))
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(forecastTime = "2026-08-24T09:00", hourlyForecast = hours),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        assertNull(alert)
    }

    @Test
    fun windGustExceedanceReportsWindGustKind() {
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(windGustKnots = 30.0),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        requireNotNull(alert)
        assertEquals(StormAlertKind.WIND_GUST, alert.kind)
        assertEquals(30.0, alert.peakValue, 0.001)
    }

    @Test
    fun mostSevereRelativeExceedanceWins() {
        // Wave is 1.1x its threshold; wind gust is 1.5x its threshold — gust should win.
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 2.2, windGustKnots = 37.5),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        requireNotNull(alert)
        assertEquals(StormAlertKind.WIND_GUST, alert.kind)
    }

    @Test
    fun lookaheadHoursLimitsHowFarForecastIsChecked() {
        val hours = (1..30).map { hour ->
            MarineForecastHour(
                time = "2026-08-24T09:00".plusHours(hour),
                waveHeightMeters = if (hour == 30) 9.0 else 0.5,
            )
        }
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(forecastTime = "2026-08-24T09:00", hourlyForecast = hours),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
            lookaheadHours = 24,
        )

        assertNull(alert)
    }

    @Test
    fun nonPositiveThresholdsProduceNoAlert() {
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 10.0, windGustKnots = 60.0),
            waveHeightThresholdMeters = 0.0,
            windGustThresholdKnots = 0.0,
        )

        assertNull(alert)
    }

    @Test
    fun trendingWaveHeightApproachingThresholdProducesWatch() {
        val hours = listOf(
            MarineForecastHour(time = "2026-08-24T12:00", waveHeightMeters = 1.4),
            MarineForecastHour(time = "2026-08-24T18:00", waveHeightMeters = 1.8),
        )
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 1.0, hourlyForecast = hours),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        requireNotNull(alert)
        assertEquals(StormAlertSeverity.WATCH, alert.severity)
        assertEquals(StormAlertKind.WAVE, alert.kind)
        assertEquals(1.8, alert.peakValue, 0.001)
        assertEquals(1.0, alert.startValue!!, 0.001)
        assertEquals("2026-08-24T18:00", alert.peakTimeIso)
    }

    @Test
    fun risingButStillCalmForecastStaysQuiet() {
        // Rises meaningfully (0.2 -> 0.9) but never gets close to the 2.0 m threshold.
        val hours = listOf(MarineForecastHour(time = "2026-08-24T18:00", waveHeightMeters = 0.9))
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 0.2, hourlyForecast = hours),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        assertNull(alert)
    }

    @Test
    fun smallFluctuationNearThresholdDoesNotTriggerWatch() {
        // Close to the threshold already, but the rise itself is trivial (0.2 m).
        val hours = listOf(MarineForecastHour(time = "2026-08-24T18:00", waveHeightMeters = 1.5))
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 1.3, hourlyForecast = hours),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        assertNull(alert)
    }

    @Test
    fun crossingThresholdWithinWindowIsWarningNotWatch() {
        val hours = listOf(MarineForecastHour(time = "2026-08-24T18:00", waveHeightMeters = 2.4))
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 1.0, hourlyForecast = hours),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        requireNotNull(alert)
        assertEquals(StormAlertSeverity.WARNING, alert.severity)
        assertEquals(2.4, alert.peakValue, 0.001)
    }

    @Test
    fun windGustTrendQualifiesWhileTinyWaveRiseDoesNot() {
        val hours = listOf(
            // Wave rises only 0.2 m (below the rise threshold); gusts rise sharply and
            // approach their threshold — only the gust trend should qualify as a WATCH.
            MarineForecastHour(time = "2026-08-24T18:00", waveHeightMeters = 1.5, windGustKnots = 23.0),
        )
        val alert = StormAlertEvaluator.evaluate(
            conditions = conditions(waveHeightMeters = 1.3, windGustKnots = 8.0, hourlyForecast = hours),
            waveHeightThresholdMeters = 2.0,
            windGustThresholdKnots = 25.0,
        )

        requireNotNull(alert)
        assertEquals(StormAlertSeverity.WATCH, alert.severity)
        assertEquals(StormAlertKind.WIND_GUST, alert.kind)
    }

    /** Simple helper: appends N hours to an ISO "yyyy-MM-ddTHH:mm" test timestamp. */
    private fun String.plusHours(hours: Int): String {
        val datePart = substringBefore('T')
        val hourPart = substringAfter('T').substringBefore(':').toInt()
        val totalHours = hourPart + hours
        val newHour = totalHours % 24
        val day = datePart.substringAfterLast('-').toInt() + totalHours / 24
        val yearMonth = datePart.substringBeforeLast('-')
        return "%s-%02d".format(yearMonth, day) + "T%02d:00".format(newHour)
    }
}
