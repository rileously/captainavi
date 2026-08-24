package com.captainavi.app.data.repository

import android.content.Context
import com.captainavi.app.data.remote.RtlMarineRoute
import com.captainavi.app.data.remote.RtlMarineRouteClient
import com.captainavi.app.data.remote.parseRtlMarineRoutes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RtlMarineRouteState(
    val routes: List<RtlMarineRoute> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdatedEpochMillis: Long? = null,
    val isCached: Boolean = false,
)

class RtlMarineRouteRepository(
    context: Context,
    private val client: RtlMarineRouteClient,
    private val isOnline: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RtlMarineRouteState())
    val state: StateFlow<RtlMarineRouteState> = _state.asStateFlow()
    private var lastRequestEpochMillis = 0L

    suspend fun refresh() = mutex.withLock {
        if (_state.value.routes.isEmpty()) loadCache()

        if (!isOnline()) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = if (_state.value.routes.isEmpty()) "RTL routes require internet for the first load" else null,
                isCached = _state.value.routes.isNotEmpty(),
            )
            return@withLock
        }

        val now = nowMillis()
        if (_state.value.routes.isNotEmpty() && now - lastRequestEpochMillis < MIN_REFRESH_INTERVAL_MS) {
            return@withLock
        }
        lastRequestEpochMillis = now
        _state.value = _state.value.copy(isLoading = true, error = null)
        client.fetchRoutes().fold(
            onSuccess = { download ->
                withContext(Dispatchers.IO) {
                    runCatching { cacheFile.writeText(download.payload) }
                }
                _state.value = RtlMarineRouteState(
                    routes = download.routes,
                    lastUpdatedEpochMillis = nowMillis(),
                )
            },
            onFailure = { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error.message ?: "Could not load RTL marine routes",
                )
            },
        )
    }

    private suspend fun loadCache() {
        val cached = withContext(Dispatchers.IO) {
            runCatching {
                cacheFile.takeIf(File::isFile)?.let { file ->
                    parseRtlMarineRoutes(file.readText()) to file.lastModified()
                }
            }.getOrNull()
        } ?: return
        if (cached.first.isNotEmpty()) {
            _state.value = RtlMarineRouteState(
                routes = cached.first,
                lastUpdatedEpochMillis = cached.second,
                isCached = true,
            )
        }
    }

    companion object {
        const val SOURCE_ATTRIBUTION =
            "Raajje Transport Link (RTL) / Maldives Transport and Contracting Company (MTCC)"
        const val MIN_REFRESH_INTERVAL_MS = 60 * 60_000L
        private const val CACHE_FILE_NAME = "rtl_marine_routes.json"
    }
}
