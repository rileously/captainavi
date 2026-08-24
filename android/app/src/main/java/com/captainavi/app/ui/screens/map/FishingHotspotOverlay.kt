package com.captainavi.app.ui.screens.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import com.captainavi.app.data.repository.HotspotCell
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * A soft amber glow per grid cell from [com.captainavi.app.data.repository.FishingHotspotAnalyzer]
 * — brighter and larger where the vessel has actually lingered (drifting/trolling/anchored)
 * across every recorded trip, not just passed through. Read-only decoration; no tap handling.
 */
class FishingHotspotOverlay(context: Context) : Overlay() {
    var cells: List<HotspotCell> = emptyList()

    private val density = context.resources.displayMetrics.density
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hotspotRed = 246
    private val hotspotGreen = 185
    private val hotspotBlue = 74

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || cells.isEmpty() || mapView.zoomLevelDouble < MIN_VISIBLE_ZOOM) return

        val bounds = mapView.boundingBox
        val projection = mapView.projection
        val mapPoint = Point()

        // Bigger glow as you zoom in, so a cell reads as a real "spot" rather than a pinprick.
        val baseRadiusPx = (16f + 9f * (mapView.zoomLevelDouble - 12.0).coerceIn(0.0, 6.0)).toFloat() * density

        cells.asSequence()
            .filter { cell ->
                cell.latitude in bounds.latSouth..bounds.latNorth && cell.longitude in bounds.lonWest..bounds.lonEast
            }
            .forEach { cell ->
                projection.toPixels(GeoPoint(cell.latitude, cell.longitude), mapPoint)
                val radius = baseRadiusPx * (0.65f + 0.45f * cell.intensity.toFloat())
                val peakAlpha = (70 + cell.intensity * 140).toInt().coerceIn(0, 210)
                glowPaint.shader = RadialGradient(
                    mapPoint.x.toFloat(),
                    mapPoint.y.toFloat(),
                    radius.coerceAtLeast(1f),
                    Color.argb(peakAlpha, hotspotRed, hotspotGreen, hotspotBlue),
                    Color.argb(0, hotspotRed, hotspotGreen, hotspotBlue),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(mapPoint.x.toFloat(), mapPoint.y.toFloat(), radius, glowPaint)
            }
    }

    companion object {
        private const val MIN_VISIBLE_ZOOM = 9.5
    }
}
