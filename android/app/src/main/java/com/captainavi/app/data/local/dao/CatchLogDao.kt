package com.captainavi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.captainavi.app.data.local.entity.CatchLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatchLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(catchLog: CatchLogEntity)

    @Query("SELECT * FROM catch_logs WHERE tripId = :tripId ORDER BY timestamp DESC")
    fun getForTrip(tripId: String): Flow<List<CatchLogEntity>>

    @Query("SELECT * FROM catch_logs WHERE tripId = :tripId ORDER BY timestamp DESC")
    suspend fun getForTripList(tripId: String): List<CatchLogEntity>

    @Query("SELECT COUNT(*) FROM catch_logs WHERE tripId = :tripId")
    fun countForTrip(tripId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(count), 0) FROM catch_logs WHERE tripId = :tripId")
    fun totalFishForTrip(tripId: String): Flow<Int>

    @Query("DELETE FROM catch_logs WHERE id = :id")
    suspend fun deleteById(id: String)
}
