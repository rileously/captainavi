package com.captainavi.app.ui.screens.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.tan

private const val MAX_MERCATOR_LATITUDE = 85.05112878
private const val COVERAGE_CELL_ZOOM = 10
private const val COVERAGE_PROBE_MIN_ZOOM = 8

data class SlippyMapTile(val zoom: Int, val x: Int, val y: Int)

/** Converts a position to the XYZ tile containing it. */
fun slippyMapTile(latitude: Double, longitude: Double, zoom: Int): SlippyMapTile {
    require(zoom in 0..29) { "Unsupported tile zoom: $zoom" }
    val latitudeRadians = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE) * PI / 180.0
    val tileCount = 2.0.pow(zoom)
    val x = floor(((longitude.coerceIn(-180.0, 180.0) + 180.0) / 360.0) * tileCount)
        .toInt()
        .coerceIn(0, tileCount.toInt() - 1)
    val y = floor((1.0 - asinh(tan(latitudeRadians)) / PI) / 2.0 * tileCount)
        .toInt()
        .coerceIn(0, tileCount.toInt() - 1)
    return SlippyMapTile(zoom, x, y)
}

fun MapTileType.hasVariableSatelliteCoverage(): Boolean =
    this == MapTileType.SATELLITE

/**
 * Esri imagery has a global declared max zoom, but native coverage varies by
 * location. Probe only the centre tile and cache by a coarse cell so operators
 * do not zoom into a run of 404/blank tiles around remote islands.
 *
 * A network or server failure returns null and leaves the source-declared limit
 * untouched. Only definite 404 responses reduce the local maximum.
 */
class MapTileCoverageProbe(
    private val connectTimeoutMs: Int = 2_500,
    private val readTimeoutMs: Int = 2_500,
) {
    private val cache = ConcurrentHashMap<String, Int>()

    fun cellKey(type: MapTileType, latitude: Double, longitude: Double): String {
        val tile = slippyMapTile(latitude, longitude, COVERAGE_CELL_ZOOM)
        return "${type.name}:${tile.x}:${tile.y}"
    }

    suspend fun findUsableMaxZoom(
        type: MapTileType,
        latitude: Double,
        longitude: Double,
    ): Int? {
        if (!type.hasVariableSatelliteCoverage()) return tileSourceFor(type).maximumZoomLevel
        val cacheKey = cellKey(type, latitude, longitude)
        cache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            val source = tileSourceFor(type)
            for (zoom in source.maximumZoomLevel downTo maxOf(source.minimumZoomLevel, COVERAGE_PROBE_MIN_ZOOM)) {
                val tile = slippyMapTile(latitude, longitude, zoom)
                val tileIndex = MapTileIndex.getTileIndex(zoom, tile.x, tile.y)
                val result = checkImage(source.getTileURLString(tileIndex))
                when (result) {
                    TileCheck.AVAILABLE -> {
                        cache[cacheKey] = zoom
                        return@withContext zoom
                    }
                    TileCheck.MISSING -> Unit
                    TileCheck.INDETERMINATE -> return@withContext null
                }
            }
            null
        }
    }

    private fun checkImage(url: String): TileCheck {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CaptainAvi/1.0 map-coverage-check")
            }
            val responseCode = connection.responseCode
            when {
                responseCode == HttpURLConnection.HTTP_NOT_FOUND -> TileCheck.MISSING
                responseCode in 200..299 && connection.contentType?.startsWith("image/") == true -> TileCheck.AVAILABLE
                else -> TileCheck.INDETERMINATE
            }
        } catch (_: Exception) {
            TileCheck.INDETERMINATE
        } finally {
            connection?.disconnect()
        }
    }

    private enum class TileCheck {
        AVAILABLE,
        MISSING,
        INDETERMINATE,
    }
}
