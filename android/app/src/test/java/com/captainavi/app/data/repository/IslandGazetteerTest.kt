package com.captainavi.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandGazetteerTest {
    private val islands = IslandGazetteerRepository.SEED_ISLANDS

    @Test
    fun searchMatchesEnglishWithoutAccent() {
        assertEquals("Malé", searchIslandPlaces(islands, "male").single().englishName)
    }

    @Test
    fun searchMatchesDhivehi() {
        assertEquals("Naivaadhoo", searchIslandPlaces(islands, "ނައިވާދޫ").single().englishName)
    }

    @Test
    fun searchMatchesAtollAndIslandTokens() {
        assertEquals("Hanimaadhoo", searchIslandPlaces(islands, "Haa Dhaalu Hani").single().englishName)
    }

    @Test
    fun labelsUseProgressiveZoomDetail() {
        val capital = islands.first { it.englishName == "Malé" }
        val regular = islands.first { it.englishName == "Naivaadhoo" }
        val tourism = IslandPlace(
            id = 9999,
            englishName = "Resort Test",
            dhivehiName = "",
            atoll = "Test",
            latitude = 4.0,
            longitude = 73.0,
            category = "Tourism Island",
            isCapital = false,
        )
        assertFalse(capital.shouldShowLabelAtZoom(6.9))
        assertTrue(capital.shouldShowLabelAtZoom(7.0))
        assertFalse(regular.shouldShowLabelAtZoom(8.4))
        assertTrue(regular.shouldShowLabelAtZoom(8.5))
        assertFalse(tourism.shouldShowLabelAtZoom(9.9))
        assertTrue(tourism.shouldShowLabelAtZoom(10.0))
    }
}
