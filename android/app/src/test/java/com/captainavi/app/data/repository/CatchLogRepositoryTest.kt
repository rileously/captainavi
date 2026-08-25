package com.captainavi.app.data.repository

import com.captainavi.app.data.local.entity.CatchLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchLogRepositoryTest {
    @Test
    fun `summarize empty catches is blank`() {
        assertEquals("", summarizeCatches(emptyList()))
    }

    @Test
    fun `summarize separates ocean and reef counts`() {
        val text = summarizeCatches(
            listOf(
                sample("Yellowfin tuna", 2, FishHabitat.OCEAN),
                sample("Green jobfish", 3, FishHabitat.REEF),
                sample("Skipjack tuna", 1, FishHabitat.OCEAN),
            ),
        )
        assertTrue(text.startsWith("6 fish"))
        assertTrue(text.contains("3 ocean"))
        assertTrue(text.contains("3 reef"))
        assertTrue(text.contains("Yellowfin tuna"))
    }

    @Test
    fun `fallback catalog splits tuna plan vs reef plan species`() {
        val ocean = MaldivesFishCatalog.FALLBACK.filter { it.habitat == FishHabitat.OCEAN }
        val reef = MaldivesFishCatalog.FALLBACK.filter { it.habitat == FishHabitat.REEF }
        assertTrue(ocean.any { it.commonName == "Skipjack tuna" })
        assertTrue(ocean.any { it.commonName == "Yellowfin tuna" })
        assertTrue(ocean.any { it.commonName == "Frigate tuna" })
        assertTrue(reef.any { it.commonName == "Green jobfish" })
        assertTrue(reef.any { it.commonName == "Two-spot red snapper" })
        assertTrue(reef.any { it.commonName == "Giant trevally" })
        assertTrue(reef.any { it.commonName == "Rusty jobfish" })
        assertTrue(ocean.any { it.commonName == "Swordfish" })
        // Official reef plan annex includes wahoo / dogtooth — not tuna-plan ocean
        assertTrue(reef.any { it.commonName == "Wahoo" })
        assertTrue(reef.any { it.commonName == "Dogtooth tuna" })
        assertTrue(ocean.none { it.commonName == "Wahoo" })
        assertTrue(MaldivesFishCatalog.FALLBACK.size >= 35)
    }

    @Test
    fun `fish habitat parses ids`() {
        assertEquals(FishHabitat.OCEAN, FishHabitat.fromId("OCEAN"))
        assertEquals(FishHabitat.REEF, FishHabitat.fromId("reef"))
        assertEquals(FishHabitat.OTHER, FishHabitat.fromId("nope"))
    }

    private fun sample(species: String, count: Int, habitat: FishHabitat) = CatchLogEntity(
        tripId = "trip-1",
        species = species,
        habitat = habitat.id,
        count = count,
    )
}
