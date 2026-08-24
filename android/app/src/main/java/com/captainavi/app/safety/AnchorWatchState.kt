package com.captainavi.app.safety

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnchorState(
    val isActive: Boolean = false,
    val anchorLat: Double = 0.0,
    val anchorLon: Double = 0.0,
    val swingRadiusMeters: Double = 50.0,
    val currentDriftMeters: Double = 0.0,
    val isDragging: Boolean = false
)

object AnchorWatchManager {
    private val _state = MutableStateFlow(AnchorState())
    val state: StateFlow<AnchorState> = _state.asStateFlow()

    fun dropAnchor(lat: Double, lon: Double, radiusMeters: Double = 50.0) {
        _state.value = AnchorState(
            isActive = true,
            anchorLat = lat,
            anchorLon = lon,
            swingRadiusMeters = radiusMeters
        )
    }

    fun weigh() {
        _state.value = AnchorState()
    }

    fun updateDrift(currentLat: Double, currentLon: Double): Boolean {
        val current = _state.value
        if (!current.isActive) return false
        val driftMeters = NauticalMath.distanceMeters(current.anchorLat, current.anchorLon, currentLat, currentLon)
        val dragging = driftMeters > current.swingRadiusMeters
        _state.value = current.copy(currentDriftMeters = driftMeters, isDragging = dragging)
        return dragging
    }
}
