package com.captainavi.app.service

import com.captainavi.app.data.local.entity.WaypointEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the currently locked "go-to" navigation destination.
 * Shared across the Helm (compass needle + ETA), Chart (guidance line)
 * and Marks (lock/unlock) screens.
 */
object DestinationState {
    private val _destination = MutableStateFlow<WaypointEntity?>(null)
    val destination: StateFlow<WaypointEntity?> = _destination.asStateFlow()

    fun lockDestination(waypoint: WaypointEntity) {
        _destination.value = waypoint
        MarineLocationService.setDestination(
            NavigationDestination(
                name = waypoint.name,
                latitude = waypoint.latitude,
                longitude = waypoint.longitude
            )
        )
    }

    fun clearDestination() {
        _destination.value = null
        MarineLocationService.clearDestination()
    }
}
