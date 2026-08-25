package com.captainavi.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaypointSpeciesTest {
    @Test
    fun `encode and decode round trip`() {
        val encoded = encodeTargetSpecies(listOf("Yellowfin tuna", "Skipjack tuna", "Yellowfin tuna", " "))
        assertEquals("""["Yellowfin tuna","Skipjack tuna"]""", encoded)
        assertEquals(listOf("Yellowfin tuna", "Skipjack tuna"), decodeTargetSpecies(encoded))
    }

    @Test
    fun `decode empty and invalid`() {
        assertTrue(decodeTargetSpecies(null).isEmpty())
        assertTrue(decodeTargetSpecies("[]").isEmpty())
        assertTrue(decodeTargetSpecies("not-json").isEmpty())
    }

    @Test
    fun `summarize truncates with more count`() {
        val text = summarizeTargetSpecies(
            listOf("A", "B", "C", "D", "E"),
            limit = 3,
        )
        assertEquals("A, B, C +2", text)
    }
}
