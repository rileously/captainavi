package com.captainavi.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "catch_logs",
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["timestamp"]),
        Index(value = ["habitat"]),
    ],
)
data class CatchLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val species: String,
    /** OCEAN / REEF / OTHER — Maldives fishery-plan habitat split. */
    val habitat: String = "OTHER",
    val count: Int = 1,
    val notes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
