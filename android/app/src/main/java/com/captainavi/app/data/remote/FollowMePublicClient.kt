package com.captainavi.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class FollowMePublicBoat(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val speedKnots: Double,
    val headingDegrees: Double,
    val updatedAtEpochMillis: Long?,
    val distanceMeters: Double,
)

data class FollowMePublicBoatProfile(
    val deviceId: Int,
    val phoneNumber: String?,
    val vesselType: String?,
    val operatorName: String?,
    val currentArea: String?,
    val photoUrl: String?,
)

class FollowMePublicClient {
    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    suspend fun fetchNearbyBoats(
        anchorDeviceId: Int = ANCHOR_DEVICE_ID,
        radiusMeters: Int = MAX_RADIUS_METERS,
    ): Result<List<FollowMePublicBoat>> = try {
        val safeRadius = radiusMeters.coerceIn(500, MAX_RADIUS_METERS)
        val response = client.get(
            "https://m.followme.mv/public/api/neighbours.php?id=$anchorDeviceId&radius=$safeRadius"
        )
        if (!response.status.isSuccess()) {
            Result.failure(Exception("FollowMe returned HTTP ${response.status.value}"))
        } else {
            runCatching { parseFollowMePublicBoats(response.bodyAsText()) }
        }
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun fetchBoatProfile(deviceId: Int): Result<FollowMePublicBoatProfile> = try {
        val response = client.get("https://m.followme.mv/public/?s=$deviceId")
        if (!response.status.isSuccess()) {
            Result.failure(Exception("FollowMe returned HTTP ${response.status.value}"))
        } else {
            runCatching { parseFollowMePublicBoatProfile(response.bodyAsText(), deviceId) }
        }
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        const val ANCHOR_DEVICE_ID = 18482
        const val MAX_RADIUS_METERS = 20_000
    }
}

@Serializable
private data class PublicNeighboursResponse(
    val status: String,
    val data: PublicNeighboursData? = null,
    val error: String = "",
)

@Serializable
private data class PublicNeighboursData(
    val boats: List<PublicBoatPayload> = emptyList(),
)

@Serializable
private data class PublicBoatPayload(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Double = 0.0,
    val heading: Double = 0.0,
    @SerialName("updated_at") val updatedAt: String? = null,
    val distance: Double = 0.0,
)

private val publicJson = Json { ignoreUnknownKeys = true }
private val publicDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val maldivesZone = ZoneId.of("Indian/Maldives")

internal fun parseFollowMePublicBoats(payload: String): List<FollowMePublicBoat> {
    val response = publicJson.decodeFromString<PublicNeighboursResponse>(payload)
    require(response.status == "ok") { response.error.ifBlank { "FollowMe response was not successful" } }
    return response.data?.boats.orEmpty().mapNotNull { boat ->
        if (boat.latitude !in -90.0..90.0 || boat.longitude !in -180.0..180.0) return@mapNotNull null
        FollowMePublicBoat(
            id = boat.id,
            name = boat.name.trim().ifBlank { "FollowMe #${boat.id}" },
            latitude = boat.latitude,
            longitude = boat.longitude,
            speedKnots = boat.speed.coerceAtLeast(0.0),
            headingDegrees = ((boat.heading % 360.0) + 360.0) % 360.0,
            updatedAtEpochMillis = boat.updatedAt?.let { timestamp ->
                runCatching {
                    LocalDateTime.parse(timestamp, publicDateFormatter)
                        .atZone(maldivesZone)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()
            },
            distanceMeters = boat.distance.coerceAtLeast(0.0),
        )
    }.distinctBy(FollowMePublicBoat::id)
}

internal fun parseFollowMePublicBoatProfile(
    payload: String,
    deviceId: Int,
): FollowMePublicBoatProfile {
    val article = Regex(
        """<article\b(?=[^>]*\bdata-boat-id=["']$deviceId["'])[^>]*>.*?</article>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).find(payload)?.value ?: error("FollowMe profile #$deviceId was not found")

    fun firstGroup(pattern: String): String? = Regex(
        pattern,
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).find(article)?.groupValues?.getOrNull(1)?.let(::cleanPublicHtmlText)?.takeIf(String::isNotBlank)

    val phoneNumber = firstGroup("""\bdata-contact-number=["']([^"']+)["']""")
        ?.takeIf { number -> number.count(Char::isDigit) in 7..15 }

    return FollowMePublicBoatProfile(
        deviceId = deviceId,
        phoneNumber = phoneNumber,
        vesselType = firstGroup(
            """<a\b(?=[^>]*\bclass=["'][^"']*\bboat-meta-link\b[^"']*["'])[^>]*>.*?<span[^>]*>(.*?)</span>"""
        ),
        operatorName = firstGroup(
            """<p\b(?=[^>]*\bclass=["'][^"']*\boperator-name\b[^"']*["'])[^>]*>(.*?)</p>"""
        ),
        currentArea = firstGroup(
            """<div\b(?=[^>]*\bclass=["'][^"']*\bboat-status\b[^"']*["'])[^>]*>.*?<strong[^>]*>(.*?)</strong>"""
        ),
        photoUrl = firstGroup(
            """<img\b(?=[^>]*\bclass=["'][^"']*\bboat-photo\b[^"']*["'])[^>]*\bsrc=["']([^"']+)["']"""
        ),
    )
}

private fun cleanPublicHtmlText(value: String): String {
    val withoutTags = value.replace(Regex("<[^>]+>"), " ")
    val decodedNumbers = Regex("&#(x?[0-9a-fA-F]+);").replace(withoutTags) { match ->
        val encoded = match.groupValues[1]
        val codePoint = if (encoded.startsWith("x", ignoreCase = true)) {
            encoded.drop(1).toIntOrNull(16)
        } else {
            encoded.toIntOrNull()
        }
        codePoint?.let(Character::toChars)?.concatToString() ?: match.value
    }
    return decodedNumbers
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&#039;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()
}
