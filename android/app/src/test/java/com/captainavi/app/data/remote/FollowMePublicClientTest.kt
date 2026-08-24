package com.captainavi.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FollowMePublicClientTest {
    @Test
    fun parsesOfficialNeighboursResponse() {
        val boats = parseFollowMePublicBoats(
            """
            {"status":"ok","data":{"radius":20000,"boats":[
              {"id":18482,"imei":"hidden","name":"UFAA 1","latitude":6.730398,"longitude":73.041878,"speed":9,"heading":60,"updated_at":"2026-08-22 16:42:47","distance":0},
              {"id":18775,"imei":"hidden","name":"Uruvaali","latitude":6.737845,"longitude":73.036295,"speed":0,"heading":210,"updated_at":"2026-08-22 16:41:57","distance":1032}
            ]},"error":""}
            """.trimIndent()
        )

        assertEquals(listOf("UFAA 1", "Uruvaali"), boats.map { it.name })
        assertEquals(9.0, boats.first().speedKnots, 0.001)
        assertEquals(60.0, boats.first().headingDegrees, 0.001)
        assertNotNull(boats.first().updatedAtEpochMillis)
    }

    @Test
    fun parsesPublicProfileForExactDevice() {
        val profile = parseFollowMePublicBoatProfile(
            """
            <article class="boat-card" data-boat-id="111" data-search="Wrong 111 7000000">
              <p class="operator-name">Wrong operator</p>
              <button data-contact-number="7000000"></button>
            </article>
            <article class="boat-card" data-boat-id="18482" data-search="UFAA 1 18482 Dhoni 7999290">
              <img class="boat-photo" src="https://cdn.example/boat.jpg" alt="">
              <p class="operator-name">A &amp; B Transport</p>
              <div class="boat-status"><strong>HD. Finey</strong></div>
              <a class="boat-meta-link"><span>Dhoni</span></a>
              <button data-contact-number="7999290"></button>
            </article>
            """.trimIndent(),
            deviceId = 18482,
        )

        assertEquals(18482, profile.deviceId)
        assertEquals("7999290", profile.phoneNumber)
        assertEquals("Dhoni", profile.vesselType)
        assertEquals("A & B Transport", profile.operatorName)
        assertEquals("HD. Finey", profile.currentArea)
        assertEquals("https://cdn.example/boat.jpg", profile.photoUrl)
    }

    @Test
    fun allowsProfileWithoutPublishedContact() {
        val profile = parseFollowMePublicBoatProfile(
            """
            <article class="boat-card" data-boat-id="9">
              <p class="operator-name empty">&nbsp;</p>
              <div class="boat-status"><strong>At sea</strong></div>
              <a class="boat-meta-link"><span>Fishing Dhoni</span></a>
            </article>
            """.trimIndent(),
            deviceId = 9,
        )

        assertNull(profile.phoneNumber)
        assertNull(profile.operatorName)
        assertEquals("Fishing Dhoni", profile.vesselType)
    }
}
