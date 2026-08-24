package com.captainavi.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "breadcrumbs",
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["isSynced"]),
        Index(value = ["timestamp"])
    ]
)
data class BreadcrumbEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val speedKnots: Double = 0.0,
    val bearingDegrees: Float = 0f,
    val accuracyMeters: Float = 0f,
    val batteryPct: Int = 100,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
