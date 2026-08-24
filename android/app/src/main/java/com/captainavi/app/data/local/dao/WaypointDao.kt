package com.captainavi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.data.local.entity.WaypointType
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: WaypointEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWaypoints(waypoints: List<WaypointEntity>)

    @Update
    suspend fun updateWaypoint(waypoint: WaypointEntity)

    @Delete
    suspend fun deleteWaypoint(waypoint: WaypointEntity)

    @Query("SELECT * FROM waypoints ORDER BY name ASC")
    fun getAllWaypoints(): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints ORDER BY name ASC")
    suspend fun getAllWaypointsList(): List<WaypointEntity>

    @Query("SELECT * FROM waypoints WHERE type = :type")
    fun getWaypointsByType(type: WaypointType): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints WHERE type = 'HOME' LIMIT 1")
    fun getHomeWaypoint(): Flow<WaypointEntity?>

    @Query("SELECT * FROM waypoints WHERE type = 'HOME' LIMIT 1")
    suspend fun getHomeWaypointSync(): WaypointEntity?

    @Query("SELECT * FROM waypoints WHERE type = 'DANGER_REEF'")
    suspend fun getDangerReefs(): List<WaypointEntity>
}
