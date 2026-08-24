package com.captainavi.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineActivityPointSearchTest {
    private val tunaFad = MarineActivityPoint(
        id = "fad-1",
        name = "Baarah Tuna FAD",
        type = MarineActivityPointType.TUNA_FAD,
        latitude = 6.8,
        longitude = 73.3,
        atoll = "HA",
        nearby = "Baarah",
        detail = "6 nautical miles east of Baarah",
        reference = "C09D01-01",
        source = MarineActivityPointSource.FISHERIES,
    )
    private val sportFad = MarineActivityPoint(
        id = "fad-2",
        name = "Ken'dhikulhudhoo Sport FAD",
        type = MarineActivityPointType.SPORT_FAD,
        latitude = 6.0,
        longitude = 73.4,
        atoll = "N",
        nearby = "Ken'dhikulhudhoo",
        reference = "E09V01",
        source = MarineActivityPointSource.FISHERIES,
    )
    private val diveSite = MarineActivityPoint(
        id = "dive-1",
        name = "Banana Reef",
        type = MarineActivityPointType.DIVE_SITE,
        latitude = 4.24,
        longitude = 73.53,
        atoll = "Kaafu",
        nearby = "Hulhumale",
        source = MarineActivityPointSource.OPENSTREETMAP,
    )
    private val points = listOf(tunaFad, sportFad, diveSite)

    @Test
    fun `station code finds its FAD first`() {
        assertEquals(listOf(tunaFad), searchMarineActivityPoints(points, "C09D01-01"))
    }

    @Test
    fun `nearby island search tolerates apostrophe punctuation`() {
        assertEquals(listOf(sportFad), searchMarineActivityPoints(points, "Ken’dhikulhudhoo"))
    }

    @Test
    fun `atoll and point type tokens can be combined`() {
        assertEquals(listOf(diveSite), searchMarineActivityPoints(points, "Kaafu dive"))
    }

    @Test
    fun `fishing filter excludes dive sites`() {
        val fishingTypes = setOf(
            MarineActivityPointType.TUNA_FAD,
            MarineActivityPointType.SPORT_FAD,
        )
        val results = searchMarineActivityPoints(points, "a", allowedTypes = fishingTypes)

        assertTrue(results.isNotEmpty())
        assertTrue(results.all(MarineActivityPoint::isFishingPoint))
    }

    @Test
    fun `blank query and non-positive limit return no results`() {
        assertTrue(searchMarineActivityPoints(points, "  ").isEmpty())
        assertTrue(searchMarineActivityPoints(points, "fad", limit = 0).isEmpty())
    }
}
