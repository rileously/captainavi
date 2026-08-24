package com.captainavi.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class StartTripRequest(
    val tripId: String,
    val captainName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val batteryPct: Int,
    val timestamp: Long,
    val statusMessageId: Long? = null,
)

@Serializable
data class LocationUpdateRequest(
    val tripId: String,
    val captainName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val speedKnots: Double,
    val headingDegrees: Double,
    val headingCardinal: String,
    val batteryPct: Int,
    val timestamp: Long,
    val accuracyMeters: Float? = null,
    val distanceFromHomeNm: Double? = null,
    val statusMessageId: Long? = null,
)

@Serializable
data class AlertRequest(
    val tripId: String,
    val captainName: String? = null,
    val alertType: String,
    val message: String? = null,
    val latitude: Double,
    val longitude: Double,
    val batteryPct: Int,
    val timestamp: Long
)

@Serializable
data class BatchSyncLocationItem(
    val latitude: Double,
    val longitude: Double,
    val speedKnots: Double,
    val batteryPct: Int,
    val timestamp: Long
)

@Serializable
data class BatchSyncAlertItem(
    val alertType: String,
    val message: String? = null,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class BatchSyncRequest(
    val tripId: String,
    val captainName: String? = null,
    val syncTimestamp: Long,
    val queuedCount: Int,
    val locations: List<BatchSyncLocationItem>,
    val alerts: List<BatchSyncAlertItem>,
    val statusMessageId: Long? = null,
)

@Serializable
data class EndTripRequest(
    val tripId: String,
    val captainName: String? = null,
    val startTime: Long,
    val endTime: Long,
    val totalDistanceNm: Double,
    val maxSpeedKnots: Double,
    val avgSpeedKnots: Double,
    val totalBreadcrumbs: Int,
    val finalLatitude: Double,
    val finalLongitude: Double,
    val statusMessageId: Long? = null,
)

@Serializable
data class GenericApiResponse(
    val success: Boolean = false,
    val message: String? = null,
    val messageId: Long? = null,
)
