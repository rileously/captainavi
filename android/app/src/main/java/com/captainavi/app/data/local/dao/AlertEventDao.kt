package com.captainavi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.captainavi.app.data.local.entity.AlertEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEventEntity)

    @Query("SELECT * FROM alerts WHERE tripId = :tripId ORDER BY timestamp DESC")
    fun getAlertsForTrip(tripId: String): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alerts WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedAlerts(limit: Int = 50): List<AlertEventEntity>

    @Query("UPDATE alerts SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAlertsAsSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM alerts WHERE isSynced = 0")
    fun getUnsyncedAlertCount(): Flow<Int>
}
