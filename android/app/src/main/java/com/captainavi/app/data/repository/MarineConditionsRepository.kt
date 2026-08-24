package com.captainavi.app.data.repository

import android.content.Context
import com.captainavi.app.data.remote.MarineConditions
import com.captainavi.app.data.remote.MarineConditionsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

data class MarineConditionsState(
    val conditions: MarineConditions? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class MarineConditionsRepository(
    context: Context,
    private val client: MarineConditionsClient = MarineConditionsClient(),
    private val isOnline: () -> Boolean = { true },
) {
    private val prefs = context.getSharedPreferences("marine_conditions_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()
    private val cached = prefs.getString(KEY_CACHED_CONDITIONS, null)?.let { encoded ->
        runCatching { json.decodeFromString<MarineConditions>(encoded) }.getOrNull()
    }

    private val _state = MutableStateFlow(MarineConditionsState(conditions = cached))
    val state: StateFlow<MarineConditionsState> = _state.asStateFlow()

    suspend fun refresh(latitude: Double, longitude: Double, force: Boolean = false) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        refreshMutex.withLock {
            val existing = _state.value.conditions
            if (!isOnline()) {
                _state.value = MarineConditionsState(
                    conditions = existing,
                    errorMessage = if (existing == null) {
                        "Offline — marine forecast requires a connection"
                    } else {
                        "Offline — showing the last saved marine forecast"
                    },
                )
                return@withLock
            }
            val now = System.currentTimeMillis()
            val isFreshAndNearby = existing != null &&
                now - existing.fetchedAtMillis < CACHE_TTL_MILLIS &&
                abs(existing.latitude - latitude) < LOCATION_REFRESH_DEGREES &&
                abs(existing.longitude - longitude) < LOCATION_REFRESH_DEGREES
            if (!force && isFreshAndNearby) return

            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            client.fetchResult(latitude, longitude)
                .onSuccess { conditions ->
                    prefs.edit().putString(KEY_CACHED_CONDITIONS, json.encodeToString(conditions)).apply()
                    _state.value = MarineConditionsState(conditions = conditions)
                }
                .onFailure { error ->
                    _state.value = MarineConditionsState(
                        conditions = existing,
                        errorMessage = if (error.message.isNullOrBlank()) {
                            "Marine forecast unavailable"
                        } else {
                            "Network forecast unavailable — retry when connected"
                        },
                    )
                }
        }
    }

    private suspend fun MarineConditionsClient.fetchResult(
        latitude: Double,
        longitude: Double,
    ): Result<MarineConditions> = runCatching { fetch(latitude, longitude) }

    companion object {
        private const val KEY_CACHED_CONDITIONS = "latest_conditions"
        private const val CACHE_TTL_MILLIS = 15 * 60 * 1000L
        private const val LOCATION_REFRESH_DEGREES = 0.1
    }
}
