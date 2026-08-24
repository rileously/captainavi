package com.captainavi.app.data.repository

import com.captainavi.app.data.remote.FollowMePublicBoat
import com.captainavi.app.data.remote.FollowMePublicBoatProfile
import com.captainavi.app.data.remote.FollowMePublicClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FollowMePublicBoatState(
    val boats: List<FollowMePublicBoat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdatedEpochMillis: Long? = null,
)

class FollowMePublicBoatRepository(
    private val client: FollowMePublicClient,
    private val isOnline: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(FollowMePublicBoatState())
    val state: StateFlow<FollowMePublicBoatState> = _state.asStateFlow()
    private var lastRequestEpochMillis = 0L
    private val profileCache = mutableMapOf<Int, FollowMePublicBoatProfile>()

    suspend fun refresh() = mutex.withLock {
        if (!isOnline()) {
            _state.value = _state.value.copy(isLoading = false, error = "FollowMe boats require internet")
            return@withLock
        }
        val now = nowMillis()
        if (now - lastRequestEpochMillis < MIN_REFRESH_INTERVAL_MS) return@withLock
        lastRequestEpochMillis = now
        _state.value = _state.value.copy(isLoading = true, error = null)
        client.fetchNearbyBoats().fold(
            onSuccess = { boats ->
                _state.value = FollowMePublicBoatState(
                    boats = boats,
                    lastUpdatedEpochMillis = nowMillis(),
                )
            },
            onFailure = { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error.message ?: "Could not load FollowMe boats",
                )
            },
        )
    }

    suspend fun getBoatProfile(deviceId: Int): Result<FollowMePublicBoatProfile> {
        profileCache[deviceId]?.let { return Result.success(it) }
        if (!isOnline()) return Result.failure(Exception("FollowMe contact details require internet"))
        return client.fetchBoatProfile(deviceId).onSuccess { profile ->
            profileCache[deviceId] = profile
        }
    }

    companion object {
        const val MIN_REFRESH_INTERVAL_MS = 15_000L
    }
}
