package com.captainavi.app.data.remote

import com.captainavi.app.data.repository.IslandPlace
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/** Public Maldives OneMap island registry client. */
class IslandGazetteerClient {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        engine {
            config {
                connectTimeout(12, TimeUnit.SECONDS)
                readTimeout(20, TimeUnit.SECONDS)
            }
        }
    }

    suspend fun fetchAll(): List<IslandPlace> {
        val places = mutableListOf<IslandPlace>()
        var offset = 0
        do {
            val response: IslandFeatureResponse = client.get(ENDPOINT) {
                parameter("where", "1=1")
                parameter(
                    "outFields",
                    "OBJECTID,atoll,islandName,islandNa_1,category,capital",
                )
                parameter("returnGeometry", false)
                parameter("returnCentroid", true)
                parameter("outSR", 4326)
                parameter("orderByFields", "OBJECTID ASC")
                parameter("resultOffset", offset)
                parameter("resultRecordCount", PAGE_SIZE)
                parameter("f", "json")
            }.body()

            val page = response.features.mapNotNull { feature ->
                val centroid = feature.centroid ?: return@mapNotNull null
                val englishName = feature.attributes.englishName?.trim().orEmpty()
                if (englishName.isBlank() || !centroid.x.isFinite() || !centroid.y.isFinite()) {
                    return@mapNotNull null
                }
                IslandPlace(
                    id = feature.attributes.id,
                    englishName = englishName,
                    dhivehiName = feature.attributes.dhivehiName?.trim().orEmpty(),
                    atoll = feature.attributes.atoll?.trim().orEmpty(),
                    latitude = centroid.y,
                    longitude = centroid.x,
                    category = feature.attributes.category?.trim().orEmpty(),
                    isCapital = feature.attributes.capital.equals("Y", ignoreCase = true),
                )
            }
            places += page
            offset += response.features.size
            val hasMore = response.exceededTransferLimit && response.features.isNotEmpty()
        } while (hasMore && offset < MAX_RECORDS)

        return places.distinctBy(IslandPlace::id)
    }

    companion object {
        private const val ENDPOINT =
            "https://services7.arcgis.com/yvCbn3q8PPtPLZIM/arcgis/rest/services/island_20240509/FeatureServer/0/query"
        private const val PAGE_SIZE = 1000
        private const val MAX_RECORDS = 5000
    }
}

@Serializable
private data class IslandFeatureResponse(
    val features: List<IslandFeature> = emptyList(),
    val exceededTransferLimit: Boolean = false,
)

@Serializable
private data class IslandFeature(
    val attributes: IslandAttributes,
    val centroid: IslandCentroid? = null,
)

@Serializable
private data class IslandAttributes(
    @SerialName("OBJECTID") val id: Int,
    val atoll: String? = null,
    @SerialName("islandName") val englishName: String? = null,
    @SerialName("islandNa_1") val dhivehiName: String? = null,
    val category: String? = null,
    val capital: String? = null,
)

@Serializable
private data class IslandCentroid(
    val x: Double,
    val y: Double,
)
