package com.captainavi.app.ui.screens.map

import android.graphics.ColorFilter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

private class EsriArcGisTileSource(
    name: String,
    zoomMinLevel: Int,
    zoomMaxLevel: Int,
    baseUrl: String,
    copyright: String,
) : OnlineTileSourceBase(
    name,
    zoomMinLevel,
    zoomMaxLevel,
    256,
    "",
    arrayOf(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"),
    copyright,
) {
    override fun getTileURLString(pMapTileIndex: Long): String = esriArcGisTileUrl(
        getBaseUrl(),
        MapTileIndex.getZoom(pMapTileIndex),
        MapTileIndex.getX(pMapTileIndex),
        MapTileIndex.getY(pMapTileIndex),
    )
}

/**
 * Standard OpenStreetMap ("Mapnik") raster tiles — the classic bright look: light-blue
 * water, green vegetation, cream/white built-up areas, and visible road detail. Used
 * as-is with no color grading so it matches OSM's own rendering.
 */
val StreetTileSource = XYTileSource(
    "OsmStandardStreet", 0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "© OpenStreetMap contributors",
)

val SatelliteTileSource: OnlineTileSourceBase = EsriArcGisTileSource(
    name = "EsriSatellite",
    zoomMinLevel = 0,
    zoomMaxLevel = 19,
    baseUrl = "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
    copyright = "© Esri World Imagery",
)

val OpenSeaMapTileSource = XYTileSource(
    "OpenSeaMap", 0, 18, 256, ".png",
    arrayOf("https://tiles.openseamap.org/seamark/"),
    "© OpenSeaMap",
)

enum class MapTileType(val title: String) {
    SATELLITE("Satellite"),
    VOYAGER("Street"),
}

data class MapSourceMetadata(
    val dataKind: String,
    val coverageNote: String,
    val navigationLimit: String,
)

fun MapTileType.metadata(): MapSourceMetadata = when (this) {
    MapTileType.SATELLITE -> MapSourceMetadata(
        dataKind = "Esri satellite imagery",
        coverageNote = "Visual coastline reference; imagery date and native detail vary by location.",
        navigationLimit = "No soundings, safety contours, chart datum, or navigation corrections.",
    )
    MapTileType.VOYAGER -> MapSourceMetadata(
        dataKind = "OpenStreetMap standard street map",
        coverageNote = "Standard OSM tiles: light-blue water, green land, and full detail on roads, unfiltered.",
        navigationLimit = "Not a hydrographic chart and does not establish safe water.",
    )
}

fun tileSourceFor(type: MapTileType) = when (type) {
    MapTileType.SATELLITE -> SatelliteTileSource
    MapTileType.VOYAGER -> StreetTileSource
}

/** Neither basemap is color-graded — both render with their source tiles as-is. */
fun colorFilterFor(type: MapTileType): ColorFilter? = when (type) {
    MapTileType.VOYAGER -> null
    MapTileType.SATELLITE -> null
}
