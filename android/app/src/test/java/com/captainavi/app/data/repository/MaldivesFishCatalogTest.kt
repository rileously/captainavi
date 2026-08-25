package com.captainavi.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class MaldivesFishCatalogTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `bundled asset text parses into ocean and reef groups`() {
        // Read the same JSON content as the asset (kept in sync via source tree path).
        val text = javaClass.classLoader!!
            .getResourceAsStream("maldives_fish_species_v1.json")
            ?.bufferedReader()
            ?.readText()
            ?: java.io.File(
                "src/main/assets/maldives_fish_species_v1.json",
            ).takeIf { it.exists() }?.readText()
            ?: error("maldives_fish_species_v1.json not found on test classpath or assets path")

        val asset = json.decodeFromString<MaldivesFishCatalogAsset>(text)
        assertEquals(2, asset.version)
        assertTrue(asset.source.contains("Tuna Fishery Management Plan"))
        assertTrue(asset.source.contains("Reef Fishery Management Plan"))
        val ocean = asset.habitats.first { it.id == "OCEAN" }.species.map { it.commonName }
        val reef = asset.habitats.first { it.id == "REEF" }.species.map { it.commonName }
        assertTrue(ocean.contains("Skipjack tuna"))
        assertTrue(ocean.contains("Yellowfin tuna"))
        assertTrue(ocean.contains("Swordfish"))
        assertTrue(reef.contains("Green jobfish"))
        assertTrue(reef.contains("Giant trevally"))
        assertTrue(reef.contains("Wahoo"))
        assertTrue(reef.contains("Dogtooth tuna"))
        assertTrue(!ocean.contains("Wahoo"))
        assertTrue(reef.size >= 25)
    }
}
