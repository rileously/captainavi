package com.captainavi.app.data.repository

import com.captainavi.app.data.local.dao.BreadcrumbDao
import com.captainavi.app.data.local.dao.TripDao
import com.captainavi.app.data.local.entity.BreadcrumbEntity
import com.captainavi.app.data.local.entity.TripEntity
import com.captainavi.app.data.local.entity.TripStatus
import com.captainavi.app.safety.NauticalMath
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TripRepository(
    private val tripDao: TripDao,
    private val breadcrumbDao: BreadcrumbDao
) {
    fun getActiveTrip(): Flow<TripEntity?> = tripDao.getActiveTrip()

    suspend fun getActiveTripSync(): TripEntity? = tripDao.getActiveTripSync()

    fun getAllTrips(): Flow<List<TripEntity>> = tripDao.getAllTrips()

    fun getBreadcrumbsForTrip(tripId: String): Flow<List<BreadcrumbEntity>> =
        breadcrumbDao.getBreadcrumbsForTrip(tripId)

    suspend fun startTrip(startLat: Double, startLon: Double, notes: String = ""): TripEntity {
        val trip = TripEntity(
            id = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis(),
            status = TripStatus.ACTIVE,
            startLatitude = startLat,
            startLongitude = startLon,
            notes = notes
        )
        tripDao.insertTrip(trip)
        return trip
    }

    suspend fun recordBreadcrumb(
        tripId: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double = 0.0,
        speedKnots: Double = 0.0,
        bearingDegrees: Float = 0f,
        accuracyMeters: Float = 0f,
        batteryPct: Int = 100,
        needsSync: Boolean = true,
    ): BreadcrumbEntity {
        val breadcrumb = BreadcrumbEntity(
            tripId = tripId,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            speedKnots = speedKnots,
            bearingDegrees = bearingDegrees,
            accuracyMeters = accuracyMeters,
            batteryPct = batteryPct,
            timestamp = System.currentTimeMillis(),
            isSynced = !needsSync,
        )
        breadcrumbDao.insertBreadcrumb(breadcrumb)
        return breadcrumb
    }

    suspend fun finishTrip(tripId: String, endLat: Double, endLon: Double): TripEntity? {
        val breadcrumbs = breadcrumbDao.getBreadcrumbsForTripList(tripId)
        var totalDistanceNm = 0.0
        var maxSpeedKnots = 0.0

        for (i in 0 until breadcrumbs.size - 1) {
            val p1 = breadcrumbs[i]
            val p2 = breadcrumbs[i + 1]
            totalDistanceNm += NauticalMath.distanceNauticalMiles(
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude
            )
            if (p1.speedKnots > maxSpeedKnots) maxSpeedKnots = p1.speedKnots
        }
        if (breadcrumbs.isNotEmpty() && breadcrumbs.last().speedKnots > maxSpeedKnots) {
            maxSpeedKnots = breadcrumbs.last().speedKnots
        }

        val endTime = System.currentTimeMillis()
        tripDao.finishTrip(
            tripId = tripId,
            endTime = endTime,
            status = TripStatus.COMPLETED,
            distance = totalDistanceNm,
            maxSpeed = maxSpeedKnots,
            endLat = endLat,
            endLon = endLon
        )
        return tripDao.getTripById(tripId)
    }
}
