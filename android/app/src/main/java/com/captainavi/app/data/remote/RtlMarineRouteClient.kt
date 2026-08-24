package com.captainavi.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class RtlMarineRouteStop(
    val code: String,
    val name: String,
    val dhivehiName: String,
    val latitude: Double,
    val longitude: Double,
)

data class RtlMarineRoute(
    val code: String,
    val name: String,
    val zoneCode: String,
    val zoneName: String,
    val sourceStop: String,
    val destinationStop: String,
    val colorHex: String,
    val stops: List<RtlMarineRouteStop>,
)

data class RtlMarineRouteDownload(
    val payload: String,
    val routes: List<RtlMarineRoute>,
)

class RtlMarineRouteClient {
    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    suspend fun fetchRoutes(): Result<RtlMarineRouteDownload> = try {
        val response = client.get(ROUTES_URL) {
            header(HttpHeaders.UserAgent, "CaptainAvi/1.0")
        }
        if (!response.status.isSuccess()) {
            Result.failure(Exception("RTL returned HTTP ${response.status.value}"))
        } else {
            val payload = response.bodyAsText()
            runCatching {
                val routes = parseRtlMarineRoutes(payload)
                require(routes.isNotEmpty()) { "RTL returned no usable marine routes" }
                RtlMarineRouteDownload(payload = payload, routes = routes)
            }
        }
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        const val ROUTES_URL =
            "https://bo.rtl.mv:4455/maldives/api/booking/v1/boatTracking/allRoutes"
    }
}

@Serializable
private data class RtlRoutesPayload(
    val zoneList: List<RtlZonePayload> = emptyList(),
)

@Serializable
private data class RtlZonePayload(
    val zoneCode: String = "",
    val zoneName: String = "",
    val routeList: List<RtlRoutePayload> = emptyList(),
)

@Serializable
private data class RtlRoutePayload(
    val routeCode: String = "",
    val routeName: String = "",
    val color: String = "",
    val sourceStop: String = "",
    val destinationStop: String = "",
    val scheduleList: List<RtlSchedulePayload> = emptyList(),
)

@Serializable
private data class RtlSchedulePayload(
    val stopTiming: List<RtlStopPayload> = emptyList(),
)

@Serializable
private data class RtlStopPayload(
    val stopCode: String = "",
    val stopName: String = "",
    val dvstopName: String = "",
    val latitude: JsonElement? = null,
    val longitude: JsonElement? = null,
)

private val rtlRouteJson = Json { ignoreUnknownKeys = true }
private val validRouteColor = Regex("^#[0-9a-fA-F]{6}$")

internal fun parseRtlMarineRoutes(payload: String): List<RtlMarineRoute> {
    val response = rtlRouteJson.decodeFromString<RtlRoutesPayload>(payload)
    return response.zoneList.flatMap { zone ->
        zone.routeList.mapNotNull { route ->
            val bestStops = route.scheduleList
                .map { schedule ->
                    schedule.stopTiming.mapNotNull(::toMarineRouteStop)
                        .fold(mutableListOf<RtlMarineRouteStop>()) { unique, stop ->
                            if (unique.lastOrNull()?.code != stop.code) unique += stop
                            unique
                        }
                }
                .filter { it.size >= 2 }
                .maxWithOrNull(
                    compareBy<List<RtlMarineRouteStop>> { it.size }
                        .thenBy { stops ->
                            if (stops.first().name.equals(route.sourceStop, ignoreCase = true)) 1 else 0
                        }
                )
                ?: return@mapNotNull null

            val routeCode = route.routeCode.trim()
            if (routeCode.isBlank()) return@mapNotNull null
            RtlMarineRoute(
                code = routeCode,
                name = route.routeName.trim().ifBlank { "RTL $routeCode" },
                zoneCode = zone.zoneCode.trim(),
                zoneName = zone.zoneName.trim().ifBlank { "RTL" },
                sourceStop = route.sourceStop.trim().ifBlank { bestStops.first().name },
                destinationStop = route.destinationStop.trim().ifBlank { bestStops.last().name },
                colorHex = route.color.takeIf(validRouteColor::matches) ?: DEFAULT_ROUTE_COLOR,
                stops = bestStops,
            )
        }
    }.distinctBy(RtlMarineRoute::code)
        .sortedWith(compareBy(RtlMarineRoute::zoneCode, RtlMarineRoute::name))
}

private fun toMarineRouteStop(stop: RtlStopPayload): RtlMarineRouteStop? {
    val latitude = stop.latitude?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return null
    val longitude = stop.longitude?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    val name = stop.stopName.trim().ifBlank { return null }
    return RtlMarineRouteStop(
        code = stop.stopCode.trim().ifBlank { "$latitude,$longitude" },
        name = name,
        dhivehiName = stop.dvstopName.trim(),
        latitude = latitude,
        longitude = longitude,
    )
}

private const val DEFAULT_ROUTE_COLOR = "#36CFE2"
