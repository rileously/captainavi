package com.captainavi.app.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

@Serializable
enum class MarineActivityPointType {
    TUNA_FAD,
    SPORT_FAD,
    DIVE_SITE,
}

@Serializable
enum class MarineActivityPointSource {
    FISHERIES,
    OPENSTREETMAP,
    OPENDIVEMAP,
}

@Serializable
data class MarineActivityPoint(
    val id: String,
    val name: String,
    val type: MarineActivityPointType,
    val latitude: Double,
    val longitude: Double,
    val atoll: String = "",
    val nearby: String = "",
    val detail: String = "",
    val reference: String = "",
    val source: MarineActivityPointSource,
) {
    val isFishingPoint: Boolean
        get() = type == MarineActivityPointType.TUNA_FAD || type == MarineActivityPointType.SPORT_FAD

    val typeLabel: String
        get() = when (type) {
            MarineActivityPointType.TUNA_FAD -> "Tuna / pole-and-line FAD"
            MarineActivityPointType.SPORT_FAD -> "Sport FAD"
            MarineActivityPointType.DIVE_SITE -> "Dive site"
        }

    val sourceLabel: String
        get() = when (source) {
            MarineActivityPointSource.FISHERIES -> "Maldives Fisheries Information System"
            MarineActivityPointSource.OPENSTREETMAP -> "OpenStreetMap contributors"
            MarineActivityPointSource.OPENDIVEMAP -> "OpenDiveMap contributors"
        }
}

data class MarineActivityPointState(
    val points: List<MarineActivityPoint> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val snapshotDate: String = "",
    val osmSnapshotDate: String = "",
    val activeFadCount: Int = 0,
    val diveSiteCount: Int = 0,
)

class MarineActivityPointRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val loadMutex = Mutex()
    private var loaded = false
    private val _state = MutableStateFlow(MarineActivityPointState())
    val state: StateFlow<MarineActivityPointState> = _state.asStateFlow()

    suspend fun loadIfNeeded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                        json.decodeFromString<MarinePointAsset>(reader.readText()).also { asset ->
                            require(asset.version == FORMAT_VERSION) {
                                "Unsupported marine point dataset version ${asset.version}"
                            }
                            require(asset.points.all { point ->
                                point.latitude in MALDIVES_MIN_LATITUDE..MALDIVES_MAX_LATITUDE &&
                                    point.longitude in MALDIVES_MIN_LONGITUDE..MALDIVES_MAX_LONGITUDE
                            }) { "Marine point dataset contains coordinates outside Maldives bounds" }
                        }
                    }
                }
            }.onSuccess { asset ->
                loaded = true
                _state.value = MarineActivityPointState(
                    points = asset.points,
                    snapshotDate = asset.snapshotDate,
                    osmSnapshotDate = asset.osmSnapshotDate,
                    activeFadCount = asset.counts.activeFads,
                    diveSiteCount = asset.counts.diveSites,
                )
            }.onFailure { error ->
                _state.value = MarineActivityPointState(
                    errorMessage = error.message ?: "Unable to load bundled fishing and dive points",
                )
            }
        }
    }

    companion object {
        const val FAD_SOURCE_URL = "https://keyolhu.mv/home/fadlist"
        const val DIVE_SOURCE_URL = "https://www.openstreetmap.org"
        const val OPEN_DIVE_SOURCE_URL = "https://opendivemap.com"
        const val SOURCE_ATTRIBUTION =
            "FADs: Maldives Fisheries Information System. Dive sites: OpenStreetMap and OpenDiveMap contributors, ODbL. Nearby island/atoll: Maldives OneMap."

        private const val ASSET_NAME = "marine_activity_points_v1.json"
        private const val FORMAT_VERSION = 1
        private const val MALDIVES_MIN_LATITUDE = -1.5
        private const val MALDIVES_MAX_LATITUDE = 8.0
        private const val MALDIVES_MIN_LONGITUDE = 72.0
        private const val MALDIVES_MAX_LONGITUDE = 75.5
    }
}

@Serializable
private data class MarinePointAsset(
    val version: Int,
    val snapshotDate: String,
    val osmSnapshotDate: String,
    val counts: MarinePointCounts,
    val points: List<MarineActivityPoint>,
)

@Serializable
private data class MarinePointCounts(
    val activeFads: Int,
    val diveSites: Int,
)

fun searchMarineActivityPoints(
    points: List<MarineActivityPoint>,
    query: String,
    allowedTypes: Set<MarineActivityPointType> = MarineActivityPointType.entries.toSet(),
    limit: Int = 40,
): List<MarineActivityPoint> {
    val normalizedQuery = query.marineSearchKey()
    if (normalizedQuery.isBlank() || allowedTypes.isEmpty() || limit <= 0) return emptyList()
    val tokens = normalizedQuery.split(' ').filter(String::isNotBlank)

    return points.asSequence()
        .filter { it.type in allowedTypes }
        .mapNotNull { point ->
            val name = point.name.marineSearchKey()
            val reference = point.reference.marineSearchKey()
            val atoll = point.atoll.marineSearchKey()
            val nearby = point.nearby.marineSearchKey()
            val type = point.typeLabel.marineSearchKey()
            val combined = "$name $reference $atoll $nearby $type"
            if (!tokens.all(combined::contains)) return@mapNotNull null
            val score = when {
                name == normalizedQuery || reference == normalizedQuery -> 0
                name.startsWith(normalizedQuery) || reference.startsWith(normalizedQuery) -> 10
                name.contains(normalizedQuery) || reference.contains(normalizedQuery) -> 20
                nearby.startsWith(normalizedQuery) || atoll == normalizedQuery -> 30
                else -> 40
            }
            score to point
        }
        .sortedWith(compareBy<Pair<Int, MarineActivityPoint>> { it.first }
            .thenBy { it.second.name })
        .take(limit)
        .map { it.second }
        .toList()
}

private fun String.marineSearchKey(): String = Normalizer
    .normalize(trim().lowercase(Locale.ROOT).replace('’', '\''), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .replace("[^\\p{L}\\p{N}']+".toRegex(), " ")
    .trim()
