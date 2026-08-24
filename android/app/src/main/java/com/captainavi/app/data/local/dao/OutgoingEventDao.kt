package com.captainavi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.captainavi.app.data.local.entity.OutgoingEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutgoingEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: OutgoingEventEntity)

    @Query("SELECT * FROM outgoing_events ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 25): List<OutgoingEventEntity>

    @Query("DELETE FROM outgoing_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE outgoing_events SET attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun incrementAttemptCount(id: String)

    @Query("SELECT COUNT(*) FROM outgoing_events")
    fun getPendingCount(): Flow<Int>
}
