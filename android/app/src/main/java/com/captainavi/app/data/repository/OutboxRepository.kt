package com.captainavi.app.data.repository

import com.captainavi.app.data.local.dao.AlertEventDao
import com.captainavi.app.data.local.dao.BreadcrumbDao
import com.captainavi.app.data.local.dao.OutgoingEventDao
import com.captainavi.app.data.local.entity.AlertEventEntity
import com.captainavi.app.data.local.entity.AlertType
import com.captainavi.app.data.local.entity.BreadcrumbEntity
import com.captainavi.app.data.local.entity.OutgoingEventEntity
import com.captainavi.app.data.local.entity.OutgoingEventType
import com.captainavi.app.data.remote.BatchSyncAlertItem
import com.captainavi.app.data.remote.BatchSyncLocationItem
import com.captainavi.app.data.remote.BatchSyncRequest
import com.captainavi.app.data.remote.EndTripRequest
import com.captainavi.app.data.remote.RelayApiClient
import com.captainavi.app.data.remote.StartTripRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OutboxRepository(
    private val breadcrumbDao: BreadcrumbDao,
    private val alertEventDao: AlertEventDao,
    private val outgoingEventDao: OutgoingEventDao,
    private val relayApiClient: RelayApiClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getUnsyncedBreadcrumbCount(): Flow<Int> = breadcrumbDao.getUnsyncedCount()
    fun getUnsyncedAlertCount(): Flow<Int> = alertEventDao.getUnsyncedAlertCount()
    fun getPendingOutboxCount(): Flow<Int> = combine(
        breadcrumbDao.getUnsyncedCount(),
        alertEventDao.getUnsyncedAlertCount(),
        outgoingEventDao.getPendingCount(),
    ) { breadcrumbs, alerts, events -> breadcrumbs + alerts + events }

    suspend fun queueTripStart(request: StartTripRequest) {
        outgoingEventDao.insert(
            OutgoingEventEntity(
                tripId = request.tripId,
                eventType = OutgoingEventType.TRIP_START,
                payloadJson = json.encodeToString(request),
                timestamp = request.timestamp,
            ),
        )
    }

    suspend fun queueTripEnd(request: EndTripRequest) {
        outgoingEventDao.insert(
            OutgoingEventEntity(
                tripId = request.tripId,
                eventType = OutgoingEventType.TRIP_END,
                payloadJson = json.encodeToString(request),
                timestamp = request.endTime,
            ),
        )
    }

    suspend fun recordAlert(
        tripId: String,
        alertType: AlertType,
        message: String,
        latitude: Double,
        longitude: Double,
        batteryPct: Int
    ): AlertEventEntity {
        val alert = AlertEventEntity(
            tripId = tripId,
            alertType = alertType,
            message = message,
            latitude = latitude,
            longitude = longitude,
            batteryPct = batteryPct,
            timestamp = System.currentTimeMillis(),
            isSynced = false
        )
        alertEventDao.insertAlert(alert)
        return alert
    }

    suspend fun markAlertSynced(alertId: String) {
        alertEventDao.markAlertsAsSynced(listOf(alertId))
    }

    suspend fun syncPendingOutbox(captainName: String = "Captain"): Result<Int> {
        var syncedCount = 0
        val outgoingEvents = outgoingEventDao.getPending()
        for (event in outgoingEvents) {
            val result = runCatching {
                when (event.eventType) {
                    OutgoingEventType.TRIP_START -> relayApiClient.sendTripStart(
                        json.decodeFromString<StartTripRequest>(event.payloadJson),
                    ).getOrThrow()
                    OutgoingEventType.TRIP_END -> relayApiClient.sendTripEnd(
                        json.decodeFromString<EndTripRequest>(event.payloadJson),
                    ).getOrThrow()
                }
            }
            if (result.isFailure) {
                outgoingEventDao.incrementAttemptCount(event.id)
                return Result.failure(result.exceptionOrNull() ?: Exception("Failed to sync queued trip event"))
            }
            outgoingEventDao.deleteByIds(listOf(event.id))
            syncedCount++
        }

        val unsyncedBreadcrumbs = breadcrumbDao.getUnsyncedBreadcrumbs(limit = 100)
        val unsyncedAlerts = alertEventDao.getUnsyncedAlerts(limit = 50)

        if (unsyncedBreadcrumbs.isEmpty() && unsyncedAlerts.isEmpty()) {
            return Result.success(syncedCount)
        }

        val tripId = unsyncedBreadcrumbs.firstOrNull()?.tripId
            ?: unsyncedAlerts.firstOrNull()?.tripId
            ?: return Result.success(0)

        val batchRequest = BatchSyncRequest(
            tripId = tripId,
            captainName = captainName,
            syncTimestamp = System.currentTimeMillis(),
            queuedCount = unsyncedBreadcrumbs.size,
            locations = unsyncedBreadcrumbs.map {
                BatchSyncLocationItem(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    speedKnots = it.speedKnots,
                    batteryPct = it.batteryPct,
                    timestamp = it.timestamp
                )
            },
            alerts = unsyncedAlerts.map {
                BatchSyncAlertItem(
                    alertType = it.alertType.name,
                    message = it.message,
                    timestamp = it.timestamp,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            }
        )

        val result = relayApiClient.sendBatchSync(batchRequest)
        return if (result.isSuccess) {
            if (unsyncedBreadcrumbs.isNotEmpty()) {
                breadcrumbDao.markBreadcrumbsAsSynced(unsyncedBreadcrumbs.map { it.id })
            }
            if (unsyncedAlerts.isNotEmpty()) {
                alertEventDao.markAlertsAsSynced(unsyncedAlerts.map { it.id })
            }
            Result.success(syncedCount + unsyncedBreadcrumbs.size + unsyncedAlerts.size)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to sync batch to relay"))
        }
    }
}
