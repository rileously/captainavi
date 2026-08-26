package com.captainavi.app.ui.screens.map

import com.captainavi.app.data.repository.IslandPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandLabelsOverlayTest {
    @Test
    fun `labels counter rotate clockwise map orientation`() {
        assertEquals(-90f, counterRotationForMap(90f), 0f)
    }

    @Test
    fun `labels counter rotate heading-up orientation`() {
        assertEquals(47.5f, counterRotationForMap(-47.5f), 0f)
    }

    @Test
    fun `north-up labels need no counter rotation`() {
        assertEquals(0f, counterRotationForMap(0f), 0f)
    }

    @Test
    fun `tap distance widens at lower zoom and disables below label zoom`() {
        assertEquals(0.0, maxIslandTapDistanceMeters(6.9), 0.0)
        assertTrue(maxIslandTapDistanceMeters(7.0) > 0.0)
        assertTrue(maxIslandTapDistanceMeters(14.0) < maxIslandTapDistanceMeters(9.0))
    }

    @Test
    fun `nearest island within radius is returned`() {
        val islands = listOf(
            sampleIsland(1, "Near", 5.0, 73.0),
            sampleIsland(2, "Far", 5.5, 73.5),
        )
        val hit = nearestIslandWithin(
            islands = islands,
            latitude = 5.001,
            longitude = 73.001,
            maxDistanceMeters = 5_000.0,
            zoom = 12.0,
        )
        assertNotNull(hit)
        assertEquals(1, hit!!.id)
    }

    @Test
    fun `nearest island ignores candidates beyond max distance`() {
        val islands = listOf(sampleIsland(1, "Far", 5.2, 73.2))
        val hit = nearestIslandWithin(
            islands = islands,
            latitude = 5.0,
            longitude = 73.0,
            maxDistanceMeters = 500.0,
            zoom = 12.0,
        )
        assertNull(hit)
    }

    @Test
    fun `inhabited residential tooltips use distinct green`() {
        val residential = islandLabelStyle("Residential Island")
        val uninhabited = islandLabelStyle("Uninhabited Island")
        val tourism = islandLabelStyle("Tourism Island")
        assertTrue(residential.backgroundColor != uninhabited.backgroundColor)
        assertTrue(residential.dotColor != uninhabited.dotColor)
        assertTrue(residential.dotColor != tourism.dotColor)
        // Green channel should dominate for inhabited markers
        val r = (residential.dotColor shr 16) and 0xff
        val g = (residential.dotColor shr 8) and 0xff
        val b = residential.dotColor and 0xff
        assertTrue(g > r && g > b)
    }

    @Test
    fun `tourism and uninhabited keep separate tooltip colors`() {
        assertTrue(
            islandLabelStyle("Tourism Island").backgroundColor !=
                islandLabelStyle("Uninhabited Island").backgroundColor,
        )
    }

    private fun sampleIsland(id: Int, name: String, lat: Double, lon: Double) = IslandPlace(
        id = id,
        englishName = name,
        dhivehiName = "",
        atoll = "Test",
        latitude = lat,
        longitude = lon,
        category = "Residential Island",
        isCapital = false,
    )
}
