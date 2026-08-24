package com.captainavi.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtlMarineRouteClientTest {

    @Test
    fun parserKeepsLongestOfficialStopSequenceAndAcceptsStringCoordinates() {
        val routes = parseRtlMarineRoutes(
            """{
                "zoneList":[{
                    "zoneCode":"101",
                    "zoneName":"Zone 1",
                    "routeList":[{
                        "routeCode":"170",
                        "routeName":"R1C7",
                        "color":"#c56503",
                        "sourceStop":"Hdh.Hanimaadhoo",
                        "destinationStop":"Hdh.Kulhudhuffushi",
                        "scheduleList":[
                            {"stopTiming":[
                                {"stopCode":"103","stopName":"Hdh.Hanimaadhoo","dvstopName":"A","latitude":"6.76686","longitude":"73.17379"},
                                {"stopCode":"111","stopName":"Hdh.Nolhivaranfaru","dvstopName":"B","latitude":"6.70666","longitude":"73.11883"},
                                {"stopCode":"104","stopName":"Hdh.Kulhudhuffushi","dvstopName":"C","latitude":"6.61791","longitude":"73.06470"}
                            ]},
                            {"stopTiming":[
                                {"stopCode":"104","stopName":"Hdh.Kulhudhuffushi","latitude":6.61791,"longitude":73.06470},
                                {"stopCode":"103","stopName":"Hdh.Hanimaadhoo","latitude":6.76686,"longitude":73.17379}
                            ]}
                        ]
                    }]
                }]
            }""".trimIndent()
        )

        assertEquals(1, routes.size)
        assertEquals("170", routes.single().code)
        assertEquals("R1C7", routes.single().name)
        assertEquals(3, routes.single().stops.size)
        assertEquals("Hdh.Hanimaadhoo", routes.single().stops.first().name)
        assertEquals(73.06470, routes.single().stops.last().longitude, 0.0)
    }

    @Test
    fun parserDropsInvalidRoutesAndUsesSafeFallbackColour() {
        val routes = parseRtlMarineRoutes(
            """{
                "zoneList":[{"routeList":[
                    {"routeCode":"bad","scheduleList":[{"stopTiming":[
                        {"stopCode":"1","stopName":"Only stop","latitude":"4.1","longitude":"73.5"}
                    ]}]},
                    {"routeCode":"ok","routeName":"RTL Test","color":"not-a-colour","scheduleList":[{"stopTiming":[
                        {"stopCode":"1","stopName":"A","latitude":"4.1","longitude":"73.5"},
                        {"stopCode":"2","stopName":"B","latitude":"4.2","longitude":"73.6"}
                    ]}]}
                ]}]
            }""".trimIndent()
        )

        assertEquals(listOf("ok"), routes.map { it.code })
        assertEquals("#36CFE2", routes.single().colorHex)
        assertTrue(routes.single().stops.all { it.latitude in -90.0..90.0 })
    }
}
