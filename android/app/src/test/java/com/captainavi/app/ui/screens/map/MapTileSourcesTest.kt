package com.captainavi.app.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTileSourcesTest {

    @Test
    fun esriUrlUsesRowColumnOrderAndRejectsMissingTilePlaceholders() {
        val url = esriArcGisTileUrl(
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
            zoom = 10,
            tileX = 721,
            tileY = 500,
        )
        assertEquals(
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/10/500/721?blankTile=false",
            url,
        )
    }

    @Test
    fun satelliteDownloadUsesPreferredRange() {
        assertEquals(8..15, offlineDownloadZoomRange(sourceMinZoom = 0, sourceMaxZoom = 19))
    }

    @Test
    fun harbourDetailDownloadCanReachSatelliteZoom19() {
        assertEquals(
            8..19,
            offlineDownloadZoomRange(
                sourceMinZoom = 0,
                sourceMaxZoom = 19,
                preferredMaxZoom = 19,
            ),
        )
    }

    @Test
    fun satellitePublishesThroughMapZoom19() {
        assertEquals(19, SatelliteTileSource.maximumZoomLevel)
    }

    @Test
    fun streetPublishesThroughMapZoom19() {
        assertEquals(19, StreetTileSource.maximumZoomLevel)
    }

    @Test
    fun streetUsesUnfilteredOsmStandardTiles() {
        assertEquals("OsmStandardStreet", StreetTileSource.name())
        assertTrue(StreetTileSource.getBaseUrl().contains("tile.openstreetmap.org"))
        assertTrue(colorFilterFor(MapTileType.VOYAGER) == null)
        assertTrue(colorFilterFor(MapTileType.SATELLITE) == null)
    }

    @Test
    fun clampRespectsLowerSourceMaxZoom() {
        assertEquals(
            10.0,
            clampMapZoom(currentZoom = 14.0, sourceMinZoom = 0, sourceMaxZoom = 10),
            0.0,
        )
    }

    @Test
    fun satelliteAllowsZoom19WithoutOverzooming() {
        assertEquals(
            19.0,
            clampMapZoom(currentZoom = 19.0, sourceMinZoom = 0, sourceMaxZoom = 19),
            0.0,
        )
    }

    @Test
    fun satelliteMetadataWarnsAgainstNavigationUse() {
        val metadata = MapTileType.SATELLITE.metadata()
        assertTrue(metadata.navigationLimit.contains("soundings"))
    }

    @Test
    fun streetMetadataDescribesOsmStandard() {
        val metadata = MapTileType.VOYAGER.metadata()
        assertTrue(metadata.dataKind.contains("OpenStreetMap"))
        assertTrue(metadata.coverageNote.contains("roads"))
        assertTrue(metadata.navigationLimit.contains("hydrographic"))
    }

    @Test
    fun slippyTileUsesExpectedMaleTileAtZoom14() {
        assertEquals(
            SlippyMapTile(zoom = 14, x = 11537, y = 8001),
            slippyMapTile(latitude = 4.1755, longitude = 73.5093, zoom = 14),
        )
    }

    @Test
    fun slippyTileClampsMercatorAndLongitudeBounds() {
        assertEquals(SlippyMapTile(zoom = 3, x = 7, y = 0), slippyMapTile(90.0, 200.0, 3))
        assertEquals(SlippyMapTile(zoom = 3, x = 0, y = 7), slippyMapTile(-90.0, -200.0, 3))
    }

    @Test
    fun onlySatelliteSourcesNeedLocalCoverageProbe() {
        assertTrue(MapTileType.SATELLITE.hasVariableSatelliteCoverage())
        assertTrue(!MapTileType.VOYAGER.hasVariableSatelliteCoverage())
    }

    @Test
    fun onlySatelliteAndStreetBasemapsRemain() {
        assertEquals(listOf(MapTileType.SATELLITE, MapTileType.VOYAGER), MapTileType.entries)
    }
}
