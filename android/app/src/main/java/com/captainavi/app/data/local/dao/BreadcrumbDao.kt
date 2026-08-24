package com.captainavi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.captainavi.app.data.local.entity.BreadcrumbEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreadcrumbDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreadcrumb(breadcrumb: BreadcrumbEntity)

    @Query("SELECT * FROM breadcrumbs WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getBreadcrumbsForTrip(tripId: String): Flow<List<BreadcrumbEntity>>

    @Query("SELECT * FROM breadcrumbs WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getBreadcrumbsForTripList(tripId: String): List<BreadcrumbEntity>

    @Query("SELECT * FROM breadcrumbs WHERE tripId = :tripId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBreadcrumb(tripId: String): BreadcrumbEntity?

    @Query("SELECT * FROM breadcrumbs WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedBreadcrumbs(limit: Int = 100): List<BreadcrumbEntity>

    @Query("UPDATE breadcrumbs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markBreadcrumbsAsSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM breadcrumbs WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT MAX(speedKnots) FROM breadcrumbs WHERE tripId = :tripId")
    suspend fun getMaxSpeedForTrip(tripId: String): Double?

    @Query("SELECT AVG(speedKnots) FROM breadcrumbs WHERE tripId = :tripId")
    suspend fun getAvgSpeedForTrip(tripId: String): Double?
}
