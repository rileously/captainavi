package com.captainavi.app.data.repository

import com.captainavi.app.data.local.dao.BreadcrumbPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishingHotspotAnalyzerTest {

    @Test
    fun `no positions produce an empty grid`() {
        val grid = FishingHotspotAnalyzer.buildGrid(emptyList())

        assertTrue(grid.isEmpty())
    }

    @Test
    fun `a lone point below the minimum count is dropped`() {
        val positions = listOf(BreadcrumbPosition(4.1755, 73.5093))

        val grid = FishingHotspotAnalyzer.buildGrid(positions, minPointsPerCell = 2)

        assertTrue(grid.isEmpty())
    }

    @Test
    fun `repeated nearby points cluster into one cell at the centroid`() {
        val positions = listOf(
            BreadcrumbPosition(4.17550, 73.50930),
            BreadcrumbPosition(4.17551, 73.50931),
            BreadcrumbPosition(4.17549, 73.50929),
        )

        val grid = FishingHotspotAnalyzer.buildGrid(positions, cellMeters = 60.0, minPointsPerCell = 2)

        assertEquals(1, grid.size)
        val cell = grid.single()
        assertEquals(3, cell.count)
        assertEquals(1.0, cell.intensity, 0.001)
        assertEquals(4.17550, cell.latitude, 0.0001)
        assertEquals(73.50930, cell.longitude, 0.0001)
    }

    @Test
    fun `far-apart clusters land in separate cells with relative intensity`() {
        val hotSpot = List(6) { BreadcrumbPosition(4.1755, 73.5093) }
        val coolerSpot = List(2) { BreadcrumbPosition(4.2200, 73.5500) }

        val grid = FishingHotspotAnalyzer.buildGrid(hotSpot + coolerSpot, minPointsPerCell = 2)

        assertEquals(2, grid.size)
        val hot = grid.first { it.count == 6 }
        val cooler = grid.first { it.count == 2 }
        assertEquals(1.0, hot.intensity, 0.001)
        assertEquals(2.0 / 6.0, cooler.intensity, 0.001)
    }

    @Test
    fun `non-positive cell size yields no grid`() {
        val positions = listOf(BreadcrumbPosition(4.1755, 73.5093), BreadcrumbPosition(4.1755, 73.5093))

        val grid = FishingHotspotAnalyzer.buildGrid(positions, cellMeters = 0.0)

        assertTrue(grid.isEmpty())
    }
}
