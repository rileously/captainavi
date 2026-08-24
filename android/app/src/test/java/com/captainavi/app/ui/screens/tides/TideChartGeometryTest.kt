package com.captainavi.app.ui.screens.tides

import org.junit.Assert.assertEquals
import org.junit.Test

class TideChartGeometryTest {
    @Test
    fun adjacentDayCurvesMeetAtPageEdges() {
        val pageWidth = 1080f
        assertEquals(0f, tideChartX(0f, pageWidth), 0f)
        assertEquals(pageWidth, tideChartX(24f, pageWidth), 0f)
    }

    @Test
    fun noonRemainsCentered() {
        assertEquals(540f, tideChartX(12f, 1080f), 0f)
    }
}
