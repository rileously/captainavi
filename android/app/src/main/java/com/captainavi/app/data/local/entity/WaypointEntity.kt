package com.captainavi.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class WaypointType {
    HOME,
    HARBOUR,
    FISHING_SPOT,
    DANGER_REEF
}

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: WaypointType,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 200.0,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
