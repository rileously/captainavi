package com.captainavi.app.ui.screens.map

/**
 * Esri MapServer tiles use `{z}/{y}/{x}` (row/column), not OSM `{z}/{x}/{y}`.
 * Asking for a 404 on missing tiles lets osmdroid upsample a cached parent tile
 * instead of caching Esri's "Map data not yet available" placeholder JPEG.
 */
fun esriArcGisTileUrl(baseUrl: String, zoom: Int, tileX: Int, tileY: Int): String {
    val base = baseUrl.trimEnd('/')
    return "$base/$zoom/$tileY/$tileX?blankTile=false"
}

/**
 * Clamp an offline download to zooms the tile source actually publishes.
 */
fun offlineDownloadZoomRange(
    sourceMinZoom: Int,
    sourceMaxZoom: Int,
    preferredMinZoom: Int = 8,
    preferredMaxZoom: Int = 15,
): IntRange {
    val minZ = maxOf(preferredMinZoom, sourceMinZoom)
    val maxZ = minOf(preferredMaxZoom, sourceMaxZoom)
    return if (maxZ >= minZ) minZ..maxZ else sourceMaxZoom..sourceMaxZoom
}

/** Keep the active map zoom inside both the app and selected tile-source limits. */
fun clampMapZoom(
    currentZoom: Double,
    sourceMinZoom: Int,
    sourceMaxZoom: Int,
    appMinZoom: Double = 3.0,
): Double {
    val maxZoom = sourceMaxZoom.toDouble()
    val minZoom = minOf(maxOf(appMinZoom, sourceMinZoom.toDouble()), maxZoom)
    return currentZoom.coerceIn(minZoom, maxZoom)
}
