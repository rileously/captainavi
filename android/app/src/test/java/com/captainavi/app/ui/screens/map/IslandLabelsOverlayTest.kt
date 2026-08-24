package com.captainavi.app.ui.screens.map

import org.junit.Assert.assertEquals
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
}
