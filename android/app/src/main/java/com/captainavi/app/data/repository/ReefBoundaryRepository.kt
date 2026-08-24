package com.captainavi.app.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class ReefRing(
    val coordinatesE6: IntArray,
    val isHole: Boolean,
) {
    val pointCount: Int get() = coordinatesE6.size / 2
}

data class ReefBoundary(
    val id: String,
    val name: String,
    val atoll: String,
    val rings: List<ReefRing>,
    val minLatitudeE6: Int,
    val maxLatitudeE6: Int,
    val minLongitudeE6: Int,
    val maxLongitudeE6: Int,
    val labelLatitudeE6: Int,
    val labelLongitudeE6: Int,
) {
    val displayName: String
        get() = name.ifBlank {
            if (atoll.isBlank()) "Mapped reef $id" else "$atoll reef $id"
        }
}

data class ReefBoundaryState(
    val reefs: List<ReefBoundary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class ReefProximity(
    val reef: ReefBoundary,
    val distanceToBoundaryMeters: Double,
    val isInside: Boolean,
)

class ReefBoundaryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val loadMutex = Mutex()
    private val _state = MutableStateFlow(ReefBoundaryState())
    val state: StateFlow<ReefBoundaryState> = _state.asStateFlow()

    @Volatile
    private var indexedDataset: IndexedReefDataset? = null

    suspend fun loadIfNeeded(): List<ReefBoundary> {
        indexedDataset?.let { return it.reefs }
        return loadMutex.withLock {
            indexedDataset?.let { return@withLock it.reefs }
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    readDataset().also { indexedDataset = it }
                }
            }.fold(
                onSuccess = { dataset ->
                    _state.value = ReefBoundaryState(reefs = dataset.reefs)
                    dataset.reefs
                },
                onFailure = { error ->
                    _state.value = ReefBoundaryState(
                        errorMessage = error.message ?: "Unable to load bundled reef boundaries",
                    )
                    emptyList()
                },
            )
        }
    }

    suspend fun nearestReefWithin(
        latitude: Double,
        longitude: Double,
        warningBufferMeters: Double,
    ): ReefProximity? {
        loadIfNeeded()
        val dataset = indexedDataset ?: return null
        val safeBuffer = warningBufferMeters.coerceIn(MIN_WARNING_BUFFER_METERS, MAX_WARNING_BUFFER_METERS)
        val latitudeE6 = (latitude * E6).toInt()
        val longitudeE6 = (longitude * E6).toInt()
        val latitudeBufferE6 = ceil(safeBuffer / METERS_PER_LATITUDE_DEGREE * E6).toInt()
        val longitudeScale = max(0.15, cos(Math.toRadians(latitude)))
        val longitudeBufferE6 = ceil(safeBuffer / (METERS_PER_LONGITUDE_DEGREE * longitudeScale) * E6).toInt()

        var nearest: ReefProximity? = null
        dataset.candidateIndices(latitudeE6, longitudeE6).forEach { reefIndex ->
            val reef = dataset.reefs[reefIndex]
            if (latitudeE6 < reef.minLatitudeE6 - latitudeBufferE6 ||
                latitudeE6 > reef.maxLatitudeE6 + latitudeBufferE6 ||
                longitudeE6 < reef.minLongitudeE6 - longitudeBufferE6 ||
                longitudeE6 > reef.maxLongitudeE6 + longitudeBufferE6
            ) return@forEach

            val isInside = pointInReef(latitude, longitude, reef)
            val distance = distanceToReefBoundaryMeters(latitude, longitude, reef)
            if (!isInside && distance > safeBuffer) return@forEach

            val candidate = ReefProximity(reef, distance, isInside)
            val current = nearest
            if (current == null ||
                (candidate.isInside && !current.isInside) ||
                (candidate.isInside == current.isInside && candidate.distanceToBoundaryMeters < current.distanceToBoundaryMeters)
            ) {
                nearest = candidate
            }
        }
        return nearest
    }

    private fun readDataset(): IndexedReefDataset {
        DataInputStream(BufferedInputStream(appContext.assets.open(ASSET_NAME))).use { input ->
            val magic = ByteArray(4).also(input::readFully).decodeToString()
            require(magic == "CAVR") { "Invalid reef dataset header" }
            val version = input.readUnsignedShort()
            require(version == FORMAT_VERSION) { "Unsupported reef dataset version $version" }
            val featureCount = input.readInt()
            require(featureCount in 1..MAX_FEATURES) { "Invalid reef feature count $featureCount" }

            val reefs = ArrayList<ReefBoundary>(featureCount)
            repeat(featureCount) {
                val id = input.readAssetString()
                val name = input.readAssetString()
                val atoll = input.readAssetString()
                val ringCount = input.readUnsignedShort()
                require(ringCount in 1..MAX_RINGS_PER_FEATURE) { "Invalid ring count for $id" }
                val rings = ArrayList<ReefRing>(ringCount)
                var minLatitude = Int.MAX_VALUE
                var maxLatitude = Int.MIN_VALUE
                var minLongitude = Int.MAX_VALUE
                var maxLongitude = Int.MIN_VALUE

                repeat(ringCount) {
                    val isHole = input.readUnsignedByte() != 0
                    val pointCount = input.readInt()
                    require(pointCount in 3..MAX_POINTS_PER_RING) { "Invalid point count for $id" }
                    val coordinates = IntArray(pointCount * 2)
                    repeat(pointCount) { pointIndex ->
                        val latitudeE6 = input.readInt()
                        val longitudeE6 = input.readInt()
                        require(latitudeE6 in -90 * E6..90 * E6 && longitudeE6 in -180 * E6..180 * E6) {
                            "Invalid coordinate for $id"
                        }
                        coordinates[pointIndex * 2] = latitudeE6
                        coordinates[pointIndex * 2 + 1] = longitudeE6
                        minLatitude = min(minLatitude, latitudeE6)
                        maxLatitude = max(maxLatitude, latitudeE6)
                        minLongitude = min(minLongitude, longitudeE6)
                        maxLongitude = max(maxLongitude, longitudeE6)
                    }
                    rings += ReefRing(coordinates, isHole)
                }

                val labelRing = rings
                    .asSequence()
                    .filterNot { it.isHole }
                    .maxByOrNull(::absoluteRingArea)
                    ?: rings.first()
                val (labelLatitude, labelLongitude) = ringCentroid(labelRing)
                reefs += ReefBoundary(
                    id = id,
                    name = name,
                    atoll = atoll,
                    rings = rings,
                    minLatitudeE6 = minLatitude,
                    maxLatitudeE6 = maxLatitude,
                    minLongitudeE6 = minLongitude,
                    maxLongitudeE6 = maxLongitude,
                    labelLatitudeE6 = labelLatitude,
                    labelLongitudeE6 = labelLongitude,
                )
            }
            require(input.read() == -1) { "Unexpected trailing reef dataset content" }
            return IndexedReefDataset(reefs)
        }
    }

    private fun DataInputStream.readAssetString(): String {
        val length = readUnsignedShort()
        return ByteArray(length).also(::readFully).decodeToString()
    }

    private class IndexedReefDataset(val reefs: List<ReefBoundary>) {
        private val grid: Map<Long, IntArray> = buildMap {
            val working = mutableMapOf<Long, MutableList<Int>>()
            reefs.forEachIndexed { index, reef ->
                val minLatCell = Math.floorDiv(reef.minLatitudeE6, GRID_CELL_E6)
                val maxLatCell = Math.floorDiv(reef.maxLatitudeE6, GRID_CELL_E6)
                val minLonCell = Math.floorDiv(reef.minLongitudeE6, GRID_CELL_E6)
                val maxLonCell = Math.floorDiv(reef.maxLongitudeE6, GRID_CELL_E6)
                for (latCell in minLatCell..maxLatCell) {
                    for (lonCell in minLonCell..maxLonCell) {
                        working.getOrPut(cellKey(latCell, lonCell)) { mutableListOf() } += index
                    }
                }
            }
            working.forEach { (key, value) -> put(key, value.toIntArray()) }
        }

        fun candidateIndices(latitudeE6: Int, longitudeE6: Int): Set<Int> {
            val latCell = Math.floorDiv(latitudeE6, GRID_CELL_E6)
            val lonCell = Math.floorDiv(longitudeE6, GRID_CELL_E6)
            return buildSet {
                for (latOffset in -1..1) {
                    for (lonOffset in -1..1) {
                        grid[cellKey(latCell + latOffset, lonCell + lonOffset)]?.forEach(::add)
                    }
                }
            }
        }
    }

    companion object {
        const val SOURCE_ATTRIBUTION =
            "Contains information licensed under Data Usage License – Maldives Land and Survey Authority."
        const val SOURCE_ARCHIVE_DATE = "2025-12-24"
        const val LICENSE_URL = "https://readme.onemap.mv/pdf/Data%20Usage%20License.pdf"
        const val DEFAULT_WARNING_BUFFER_METERS = 300.0
        const val MIN_WARNING_BUFFER_METERS = 100.0
        const val MAX_WARNING_BUFFER_METERS = 2_000.0

        private const val ASSET_NAME = "reef_boundaries_v1.bin"
        private const val FORMAT_VERSION = 1
        private const val MAX_FEATURES = 10_000
        private const val MAX_RINGS_PER_FEATURE = 100
        private const val MAX_POINTS_PER_RING = 100_000
        private const val E6 = 1_000_000
        private const val GRID_CELL_E6 = 250_000
        private const val METERS_PER_LATITUDE_DEGREE = 110_574.0
        private const val METERS_PER_LONGITUDE_DEGREE = 111_320.0

        private fun cellKey(latitudeCell: Int, longitudeCell: Int): Long =
            (latitudeCell.toLong() shl 32) xor (longitudeCell.toLong() and 0xffffffffL)
    }
}

internal fun pointInReef(latitude: Double, longitude: Double, reef: ReefBoundary): Boolean {
    var isInside = false
    reef.rings.forEach { ring ->
        if (pointInRing(latitude, longitude, ring)) isInside = !isInside
    }
    return isInside
}

private fun pointInRing(latitude: Double, longitude: Double, ring: ReefRing): Boolean {
    val coordinates = ring.coordinatesE6
    val count = ring.pointCount
    var inside = false
    var previous = count - 1
    for (current in 0 until count) {
        val currentLatitude = coordinates[current * 2] / 1_000_000.0
        val currentLongitude = coordinates[current * 2 + 1] / 1_000_000.0
        val previousLatitude = coordinates[previous * 2] / 1_000_000.0
        val previousLongitude = coordinates[previous * 2 + 1] / 1_000_000.0
        if ((currentLatitude > latitude) != (previousLatitude > latitude)) {
            val intersectionLongitude = (previousLongitude - currentLongitude) *
                (latitude - currentLatitude) / (previousLatitude - currentLatitude) + currentLongitude
            if (longitude < intersectionLongitude) inside = !inside
        }
        previous = current
    }
    return inside
}

internal fun distanceToReefBoundaryMeters(
    latitude: Double,
    longitude: Double,
    reef: ReefBoundary,
): Double {
    val metersPerLongitudeDegree = 111_320.0 * max(0.15, cos(Math.toRadians(latitude)))
    val metersPerLatitudeDegree = 110_574.0
    var nearest = Double.POSITIVE_INFINITY
    reef.rings.forEach { ring ->
        val coordinates = ring.coordinatesE6
        val count = ring.pointCount
        for (index in 0 until count) {
            val next = (index + 1) % count
            val startY = (coordinates[index * 2] / 1_000_000.0 - latitude) * metersPerLatitudeDegree
            val startX = (coordinates[index * 2 + 1] / 1_000_000.0 - longitude) * metersPerLongitudeDegree
            val endY = (coordinates[next * 2] / 1_000_000.0 - latitude) * metersPerLatitudeDegree
            val endX = (coordinates[next * 2 + 1] / 1_000_000.0 - longitude) * metersPerLongitudeDegree
            nearest = min(nearest, distanceFromOriginToSegment(startX, startY, endX, endY))
        }
    }
    return nearest
}

private fun distanceFromOriginToSegment(startX: Double, startY: Double, endX: Double, endY: Double): Double {
    val deltaX = endX - startX
    val deltaY = endY - startY
    val lengthSquared = deltaX * deltaX + deltaY * deltaY
    if (lengthSquared == 0.0) return hypot(startX, startY)
    val fraction = (-(startX * deltaX + startY * deltaY) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(startX + fraction * deltaX, startY + fraction * deltaY)
}

private fun absoluteRingArea(ring: ReefRing): Double {
    val coordinates = ring.coordinatesE6
    var twiceArea = 0.0
    for (index in 0 until ring.pointCount) {
        val next = (index + 1) % ring.pointCount
        val x1 = coordinates[index * 2 + 1].toDouble()
        val y1 = coordinates[index * 2].toDouble()
        val x2 = coordinates[next * 2 + 1].toDouble()
        val y2 = coordinates[next * 2].toDouble()
        twiceArea += x1 * y2 - x2 * y1
    }
    return kotlin.math.abs(twiceArea)
}

private fun ringCentroid(ring: ReefRing): Pair<Int, Int> {
    val coordinates = ring.coordinatesE6
    var crossSum = 0.0
    var longitudeSum = 0.0
    var latitudeSum = 0.0
    for (index in 0 until ring.pointCount) {
        val next = (index + 1) % ring.pointCount
        val longitude1 = coordinates[index * 2 + 1].toDouble()
        val latitude1 = coordinates[index * 2].toDouble()
        val longitude2 = coordinates[next * 2 + 1].toDouble()
        val latitude2 = coordinates[next * 2].toDouble()
        val cross = longitude1 * latitude2 - longitude2 * latitude1
        crossSum += cross
        longitudeSum += (longitude1 + longitude2) * cross
        latitudeSum += (latitude1 + latitude2) * cross
    }
    if (kotlin.math.abs(crossSum) < 1.0) {
        val latitudes = coordinates.indices.filter { it % 2 == 0 }.map { coordinates[it] }
        val longitudes = coordinates.indices.filter { it % 2 == 1 }.map { coordinates[it] }
        return latitudes.average().toInt() to longitudes.average().toInt()
    }
    return (latitudeSum / (3.0 * crossSum)).toInt() to
        (longitudeSum / (3.0 * crossSum)).toInt()
}
