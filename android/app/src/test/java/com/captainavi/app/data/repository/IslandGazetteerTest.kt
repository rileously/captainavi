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
        assertFalse(capital.shouldShowLabelAtZoom(7.0))
        assertTrue(capital.shouldShowLabelAtZoom(8.0))
        assertFalse(regular.shouldShowLabelAtZoom(8.0))
        assertTrue(regular.shouldShowLabelAtZoom(9.0))
    }
}
