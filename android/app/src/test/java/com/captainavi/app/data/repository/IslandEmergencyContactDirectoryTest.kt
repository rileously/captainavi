package com.captainavi.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandEmergencyContactDirectoryTest {
    @Test
    fun `parses official contact snapshot by island id`() {
        val directory = parseIslandEmergencyContactDirectory(SAMPLE_ASSET)

        assertEquals("2026-08-22", directory.snapshotDate)
        val naivaadhoo = directory.contactsByIslandId.getValue(655)
        assertEquals("6520039", naivaadhoo.council?.phones?.single())
        assertEquals("6520548", naivaadhoo.health?.phones?.single())
    }

    @Test
    fun `accepts local short codes and seven digit numbers only`() {
        assertTrue(isDialableMaldivesNumber("1401"))
        assertTrue(isDialableMaldivesNumber("6520548"))
        assertFalse(isDialableMaldivesNumber("+9606520548"))
        assertFalse(isDialableMaldivesNumber("12A4"))
    }

    private companion object {
        const val SAMPLE_ASSET = """
            {
              "version": 1,
              "snapshotDate": "2026-08-22",
              "count": 1,
              "contacts": [
                {
                  "islandId": 655,
                  "islandName": "Naivaadhoo",
                  "atoll": "Haa Dhaalu",
                  "council": {
                    "serviceLabel": "Council office",
                    "organization": "HDh. Naivaadhoo Council",
                    "phones": ["6520039"],
                    "sourceLabel": "Local Government Authority",
                    "sourceUrl": "https://www.lga.gov.mv/en/councils?state=4"
                  },
                  "health": {
                    "serviceLabel": "Health centre",
                    "organization": "Haa Dhaalu Naivaadhoo Health Centre",
                    "phones": ["6520548"],
                    "sourceLabel": "Ministry of Health",
                    "sourceUrl": "https://health.gov.mv/en/health-facilities"
                  }
                }
              ]
            }
        """
    }
}
