package com.captainavi.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class OutgoingEventType {
    TRIP_START,
    TRIP_END,
}

@Entity(
    tableName = "outgoing_events",
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["timestamp"]),
    ],
)
data class OutgoingEventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val eventType: OutgoingEventType,
    val payloadJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
)
