package com.captainavi.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TripStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: TripStatus = TripStatus.ACTIVE,
    val totalDistanceNm: Double = 0.0,
    val maxSpeedKnots: Double = 0.0,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val notes: String = ""
)
