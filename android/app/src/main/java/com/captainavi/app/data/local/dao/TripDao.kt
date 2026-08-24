package com.captainavi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.captainavi.app.data.local.entity.TripEntity
import com.captainavi.app.data.local.entity.TripStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY startTime DESC LIMIT 1")
    fun getActiveTrip(status: TripStatus = TripStatus.ACTIVE): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveTripSync(status: TripStatus = TripStatus.ACTIVE): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("UPDATE trips SET endTime = :endTime, status = :status, totalDistanceNm = :distance, maxSpeedKnots = :maxSpeed, endLatitude = :endLat, endLongitude = :endLon WHERE id = :tripId")
    suspend fun finishTrip(
        tripId: String,
        endTime: Long,
        status: TripStatus = TripStatus.COMPLETED,
        distance: Double,
        maxSpeed: Double,
        endLat: Double,
        endLon: Double
    )
}
