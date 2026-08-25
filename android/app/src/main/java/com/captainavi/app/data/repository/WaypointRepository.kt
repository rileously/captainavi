package com.captainavi.app.data.repository

import com.captainavi.app.data.local.dao.WaypointDao
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.data.local.entity.WaypointType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class WaypointRepository(
    private val waypointDao: WaypointDao
) {
    // Short-lived caches so the 1 Hz GPS loop doesn't hit SQLite for every fix
    @Volatile private var cachedHome: WaypointEntity? = null
    @Volatile private var homeCacheExpiry: Long = 0L
    @Volatile private var cachedReefs: List<WaypointEntity>? = null
    @Volatile private var reefsCacheExpiry: Long = 0L

    fun getAllWaypoints(): Flow<List<WaypointEntity>> = waypointDao.getAllWaypoints()

    fun getWaypointsByType(type: WaypointType): Flow<List<WaypointEntity>> =
        waypointDao.getWaypointsByType(type)

    fun getHomeWaypoint(): Flow<WaypointEntity?> = waypointDao.getHomeWaypoint()

    suspend fun getHomeWaypointSync(): WaypointEntity? = waypointDao.getHomeWaypointSync()

    suspend fun getHomeWaypointCached(ttlMs: Long = 30_000L): WaypointEntity? {
        val now = System.currentTimeMillis()
        if (now > homeCacheExpiry) {
            cachedHome = waypointDao.getHomeWaypointSync()
            homeCacheExpiry = now + ttlMs
        }
        return cachedHome
    }

    suspend fun getDangerReefs(): List<WaypointEntity> = waypointDao.getDangerReefs()

    suspend fun getDangerReefsCached(ttlMs: Long = 60_000L): List<WaypointEntity> {
        val now = System.currentTimeMillis()
        val cached = cachedReefs
        if (cached == null || now > reefsCacheExpiry) {
            val fresh = waypointDao.getDangerReefs()
            cachedReefs = fresh
            reefsCacheExpiry = now + ttlMs
            return fresh
        }
        return cached
    }

    private fun invalidateCaches() {
        homeCacheExpiry = 0L
        reefsCacheExpiry = 0L
    }

    suspend fun setHomeLocation(latitude: Double, longitude: Double, name: String = "Home Harbour"): WaypointEntity {
        invalidateCaches()
        val existingHome = waypointDao.getHomeWaypointSync()
        if (existingHome != null) {
            val updated = existingHome.copy(
                name = name,
                latitude = latitude,
                longitude = longitude,
                createdAt = System.currentTimeMillis()
            )
            waypointDao.updateWaypoint(updated)
            return updated
        } else {
            return addWaypoint(
                name = name,
                type = WaypointType.HOME,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = 500.0,
                description = "Base dock & island station"
            )
        }
    }

    suspend fun addWaypoint(waypoint: WaypointEntity): WaypointEntity {
        waypointDao.insertWaypoint(waypoint)
        invalidateCaches()
        return waypoint
    }

    suspend fun addWaypoint(
        name: String,
        type: WaypointType,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 200.0,
        description: String = "",
        targetSpecies: List<String> = emptyList(),
    ): WaypointEntity {
        val waypoint = WaypointEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            description = description,
            targetSpeciesJson = encodeTargetSpecies(
                if (type == WaypointType.FISHING_SPOT) targetSpecies else emptyList(),
            ),
        )
        waypointDao.insertWaypoint(waypoint)
        invalidateCaches()
        return waypoint
    }

    suspend fun updateWaypoint(waypoint: WaypointEntity) {
        waypointDao.updateWaypoint(waypoint)
        invalidateCaches()
    }

    suspend fun updateWaypointDetails(
        waypoint: WaypointEntity,
        name: String,
        type: WaypointType,
        latitude: Double,
        longitude: Double,
        description: String = "",
        targetSpecies: List<String> = emptyList(),
    ): WaypointEntity {
        val convertingToFishSpot =
            type == WaypointType.FISHING_SPOT && waypoint.type != WaypointType.FISHING_SPOT
        val updated = waypoint.copy(
            name = name,
            type = type,
            latitude = latitude,
            longitude = longitude,
            description = description,
            // Fish marks use a compact radius; reef danger circles keep theirs unless converting away.
            radiusMeters = when {
                convertingToFishSpot -> 200.0
                type == WaypointType.DANGER_REEF && waypoint.type != WaypointType.DANGER_REEF ->
                    waypoint.radiusMeters.coerceAtLeast(200.0)
                else -> waypoint.radiusMeters
            },
            targetSpeciesJson = encodeTargetSpecies(
                if (type == WaypointType.FISHING_SPOT) targetSpecies else emptyList(),
            ),
        )
        updateWaypoint(updated)
        return updated
    }

    suspend fun deleteWaypoint(waypoint: WaypointEntity) {
        waypointDao.deleteWaypoint(waypoint)
        invalidateCaches()
    }
}
