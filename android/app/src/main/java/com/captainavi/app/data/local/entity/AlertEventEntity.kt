package com.captainavi.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class AlertType {
    SOS,
    LOW_BATTERY,
    GEOFENCE_EXIT,
    DANGER_ZONE_ENTRY,
    NO_MOVEMENT,
    GPS_LOST,
    LOW_FUEL
}

@Entity(
    tableName = "alerts",
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["isSynced"]),
        Index(value = ["timestamp"])
    ]
)
data class AlertEventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val alertType: AlertType,
    val message: String = "",
    val latitude: Double,
    val longitude: Double,
    val batteryPct: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
