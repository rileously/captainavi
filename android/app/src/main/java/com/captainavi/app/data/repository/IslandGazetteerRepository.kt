package com.captainavi.app.data.repository

import android.content.Context
import com.captainavi.app.data.remote.IslandGazetteerClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

@Serializable
data class IslandPlace(
    val id: Int,
    val englishName: String,
    val dhivehiName: String,
    val atoll: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val isCapital: Boolean,
) {
    val bilingualName: String
        get() = if (dhivehiName.isBlank()) englishName else "$englishName · $dhivehiName"
}

data class IslandGazetteerState(
    val islands: List<IslandPlace> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCompleteRegistry: Boolean = false,
)

class IslandGazetteerRepository(
    context: Context,
    private val client: IslandGazetteerClient = IslandGazetteerClient(),
    private val isOnline: () -> Boolean = { true },
) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("island_gazetteer_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()
    private val cached = prefs.getString(KEY_ISLANDS, null)?.let { encoded ->
        runCatching { json.decodeFromString<List<IslandPlace>>(encoded) }.getOrNull()
    }.orEmpty()
    private val bundled = loadBundledRegistry()
    private val initialIslands = when {
        cached.size >= MIN_COMPLETE_REGISTRY_SIZE -> cached
        bundled.size >= MIN_COMPLETE_REGISTRY_SIZE -> bundled
        else -> SEED_ISLANDS
    }

    private val _state = MutableStateFlow(
        IslandGazetteerState(
            islands = initialIslands,
            isCompleteRegistry = initialIslands.size > SEED_ISLANDS.size,
        )
    )
    val state: StateFlow<IslandGazetteerState> = _state.asStateFlow()

    suspend fun refresh(force: Boolean = false) {
        refreshMutex.withLock {
            if (!isOnline()) {
                _state.value = IslandGazetteerState(
                    islands = _state.value.islands.takeIf { it.size >= MIN_COMPLETE_REGISTRY_SIZE }
                        ?: bundled.ifEmpty { SEED_ISLANDS },
                    errorMessage = null,
                    isCompleteRegistry = _state.value.islands.size > SEED_ISLANDS.size || bundled.size > SEED_ISLANDS.size,
                )
                return@withLock
            }
            val lastFetch = prefs.getLong(KEY_FETCHED_AT, 0L)
            if (!force && _state.value.isCompleteRegistry &&
                System.currentTimeMillis() - lastFetch < CACHE_TTL_MILLIS
            ) return

            val existing = _state.value.islands
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                client.fetchAll().also { require(it.isNotEmpty()) { "OneMap returned no islands" } }
            }
                .onSuccess { fetched ->
                    val sorted = fetched.sortedWith(
                        compareBy<IslandPlace> { it.atoll }.thenBy { it.englishName }
                    )
                    prefs.edit()
                        .putString(KEY_ISLANDS, json.encodeToString(sorted))
                        .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                        .apply()
                    _state.value = IslandGazetteerState(
                        islands = sorted,
                        isCompleteRegistry = true,
                    )
                }
                .onFailure {
                    _state.value = IslandGazetteerState(
                        islands = existing.takeIf { it.size >= MIN_COMPLETE_REGISTRY_SIZE }
                            ?: bundled.ifEmpty { SEED_ISLANDS },
                        errorMessage = "Registry refresh unavailable — showing bundled names",
                        isCompleteRegistry = existing.size > SEED_ISLANDS.size || bundled.size > SEED_ISLANDS.size,
                    )
                }
        }
    }

    private fun loadBundledRegistry(): List<IslandPlace> = runCatching {
        appContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            val asset = json.decodeFromString<IslandGazetteerAsset>(reader.readText())
            require(asset.version == ASSET_FORMAT_VERSION) {
                "Unsupported island gazetteer version ${asset.version}"
            }
            require(asset.count == asset.islands.size && asset.islands.size >= MIN_COMPLETE_REGISTRY_SIZE) {
                "Incomplete bundled island gazetteer"
            }
            asset.islands
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_ISLANDS = "islands"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val CACHE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val ASSET_NAME = "island_gazetteer_v1.json"
        private const val ASSET_FORMAT_VERSION = 1
        private const val MIN_COMPLETE_REGISTRY_SIZE = 1000

        /** Small offline bootstrap near common operating areas; OneMap replaces it after sync. */
        val SEED_ISLANDS = listOf(
            IslandPlace(8, "Rasdhoo", "ރަސްދޫ", "Alifu Alifu", 4.26306486777495, 72.99185040942913, "Residential Island", true),
            IslandPlace(625, "Dhidhdhoo", "ދިއްދޫ", "Haa Alifu", 6.888798673828813, 73.1111583472134, "Residential Island", true),
            IslandPlace(655, "Naivaadhoo", "ނައިވާދޫ", "Haa Dhaalu", 6.746860996137769, 72.93499160415041, "Residential Island", false),
            IslandPlace(680, "Kulhudhuffushi", "ކުޅުދުއްފުށި", "Haa Dhaalu", 6.623258620561716, 73.06911831064959, "Residential Island", true),
            IslandPlace(684, "Hanimaadhoo", "ހަނިމާދޫ", "Haa Dhaalu", 6.756432505546139, 73.17324452033114, "Residential Island", false),
            IslandPlace(1075, "Un'goofaaru", "އުނގޫފާރު", "Raa", 5.668216877873508, 73.0301046714007, "Residential Island", true),
            IslandPlace(1443, "Malé", "މާލެ", "MLE", 4.174222891241048, 73.50949294163435, "Residential Island", true),
        )
    }
}

@Serializable
private data class IslandGazetteerAsset(
    val version: Int,
    val snapshotDate: String,
    val source: String,
    val count: Int,
    val islands: List<IslandPlace>,
)

fun searchIslandPlaces(
    islands: List<IslandPlace>,
    query: String,
    limit: Int = 30,
): List<IslandPlace> {
    val normalizedQuery = query.searchKey()
    if (normalizedQuery.isBlank()) return emptyList()
    val tokens = normalizedQuery.split(' ').filter(String::isNotBlank)

    return islands.asSequence()
        .mapNotNull { island ->
            val english = island.englishName.searchKey()
            val dhivehi = island.dhivehiName.searchKey()
            val atoll = island.atoll.searchKey()
            val combined = "$english $dhivehi $atoll"
            if (!tokens.all(combined::contains)) return@mapNotNull null
            val matchScore = when {
                english == normalizedQuery || dhivehi == normalizedQuery -> 0
                english.startsWith(normalizedQuery) || dhivehi.startsWith(normalizedQuery) -> 10
                english.contains(normalizedQuery) || dhivehi.contains(normalizedQuery) -> 20
                else -> 30
            }
            val priority = when {
                island.isCapital -> 0
                island.category == "Residential Island" -> 1
                island.category == "Tourism Island" -> 2
                else -> 3
            }
            Triple(matchScore, priority, island)
        }
        .sortedWith(compareBy<Triple<Int, Int, IslandPlace>> { it.first }
            .thenBy { it.second }
            .thenBy { it.third.englishName })
        .take(limit.coerceAtLeast(0))
        .map { it.third }
        .toList()
}

fun IslandPlace.shouldShowLabelAtZoom(zoom: Double): Boolean = when {
    zoom >= 11.0 -> true
    zoom >= 9.0 -> isCapital || category == "Residential Island"
    zoom >= 7.5 -> isCapital
    else -> false
}

private fun String.searchKey(): String = Normalizer
    .normalize(trim().lowercase(Locale.ROOT).replace('’', '\''), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .replace("[^\\p{L}\\p{N}']+".toRegex(), " ")
    .trim()
