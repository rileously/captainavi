package com.captainavi.app.data.repository

import com.captainavi.app.data.local.dao.BreadcrumbPosition
import kotlin.math.cos
import kotlin.math.floor

/** One grid cell of the fishing hotspot heatmap: a centroid position and a 0..1 intensity. */
data class HotspotCell(
    val latitude: Double,
    val longitude: Double,
    val count: Int,
    val intensity: Double,
)

/**
 * Turns raw slow-speed breadcrumb positions (drifting/trolling/anchored, not transiting)
 * into a density grid: where a fisherman has actually spent time across every past trip,
 * not just where they passed through. Pure — no Android/DB dependencies — so the grid
 * math is unit-testable in isolation from [com.captainavi.app.data.local.dao.BreadcrumbDao].
 */
object FishingHotspotAnalyzer {
    const val DEFAULT_MAX_SPEED_KNOTS = 4.0
    const val DEFAULT_CELL_METERS = 60.0
    const val DEFAULT_MIN_POINTS_PER_CELL = 2

    private const val METERS_PER_LATITUDE_DEGREE = 110_574.0
    private const val METERS_PER_LONGITUDE_DEGREE = 111_320.0

    fun buildGrid(
        positions: List<BreadcrumbPosition>,
        cellMeters: Double = DEFAULT_CELL_METERS,
        minPointsPerCell: Int = DEFAULT_MIN_POINTS_PER_CELL,
    ): List<HotspotCell> {
        if (positions.isEmpty() || cellMeters <= 0.0) return emptyList()

        // A single reference latitude for the whole dataset keeps grid cells aligned
        // consistently; the small lat spread of one person's local fishing grounds makes
        // the cos(lat) longitude-scale approximation negligible in practice.
        val referenceLatitude = positions.sumOf { it.latitude } / positions.size
        val latStepDeg = cellMeters / METERS_PER_LATITUDE_DEGREE
        val lonScale = cos(Math.toRadians(referenceLatitude)).coerceAtLeast(0.15)
        val lonStepDeg = cellMeters / (METERS_PER_LONGITUDE_DEGREE * lonScale)

        val buckets = HashMap<Pair<Int, Int>, CellAccumulator>()
        for (position in positions) {
            val key = floor(position.latitude / latStepDeg).toInt() to floor(position.longitude / lonStepDeg).toInt()
            val accumulator = buckets.getOrPut(key) { CellAccumulator() }
            accumulator.count += 1
            accumulator.latitudeSum += position.latitude
            accumulator.longitudeSum += position.longitude
        }

        val qualifying = buckets.values.filter { it.count >= minPointsPerCell }
        val maxCount = qualifying.maxOfOrNull { it.count } ?: return emptyList()

        return qualifying.map { accumulator ->
            HotspotCell(
                latitude = accumulator.latitudeSum / accumulator.count,
                longitude = accumulator.longitudeSum / accumulator.count,
                count = accumulator.count,
                intensity = accumulator.count.toDouble() / maxCount,
            )
        }
    }

    private class CellAccumulator {
        var count: Int = 0
        var latitudeSum: Double = 0.0
        var longitudeSum: Double = 0.0
    }
}
