package com.captainavi.app.ui.screens.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import com.captainavi.app.data.repository.IslandPlace
import com.captainavi.app.data.repository.ReefBoundary
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class ReefBoundaryOverlay(context: Context) : Overlay() {
    var reefs: List<ReefBoundary> = emptyList()
    var islands: List<IslandPlace> = emptyList()

    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(62, 255, 61, 79)
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 74, 89)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 67, 8, 20)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled || reefs.isEmpty()) return
        val zoom = mapView.zoomLevelDouble
        if (zoom < MIN_REEF_ZOOM) return

        val bounds = mapView.boundingBox
        val northE6 = (bounds.latNorth * E6).toInt()
        val southE6 = (bounds.latSouth * E6).toInt()
        val eastE6 = (bounds.lonEast * E6).toInt()
        val westE6 = (bounds.lonWest * E6).toInt()
        val projection = mapView.projection
        val mapPoint = Point()
        val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
        val visible = reefs.asSequence()
            .filter { reef ->
                reef.maxLatitudeE6 >= southE6 && reef.minLatitudeE6 <= northE6 &&
                    reef.maxLongitudeE6 >= westE6 && reef.minLongitudeE6 <= eastE6
            }
            .toList()

        visible.forEach { reef ->
            path.rewind()
            path.fillType = Path.FillType.EVEN_ODD
            reef.rings.forEach { ring ->
                val coordinates = ring.coordinatesE6
                for (index in 0 until ring.pointCount) {
                    val latitude = coordinates[index * 2] / E6
                    val longitude = coordinates[index * 2 + 1] / E6
                    projection.toPixels(GeoPoint(latitude, longitude), mapPoint)
                    if (index == 0) {
                        path.moveTo(mapPoint.x.toFloat(), mapPoint.y.toFloat())
                    } else {
                        path.lineTo(mapPoint.x.toFloat(), mapPoint.y.toFloat())
                    }
                }
                path.close()
            }
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, outlinePaint)
        }

        if (zoom >= MIN_REEF_LABEL_ZOOM) {
            drawLabels(
                canvas,
                mapView,
                visible.asSequence()
                    .filter { it.name.isNotBlank() && !it.containsRegisteredIsland() }
                    .toList(),
            )
        }
    }

    private fun ReefBoundary.containsRegisteredIsland(): Boolean = islands.any { island ->
        val latitudeE6 = (island.latitude * E6).toInt()
        val longitudeE6 = (island.longitude * E6).toInt()
        latitudeE6 in minLatitudeE6..maxLatitudeE6 &&
            longitudeE6 in minLongitudeE6..maxLongitudeE6
    }

    private fun drawLabels(canvas: Canvas, mapView: MapView, reefs: List<ReefBoundary>) {
        val projection = mapView.projection
        val occupied = mutableListOf<RectF>()
        val mapPoint = Point()
        val screenPoint = Point()
        val horizontalPadding = 7f * density
        val verticalPadding = 4f * density
        val edgePadding = 4f * density
        val textHeight = labelPaint.fontMetrics.run { bottom - top }

        reefs.asSequence()
            .sortedByDescending { reef ->
                (reef.maxLatitudeE6 - reef.minLatitudeE6).toLong() *
                    (reef.maxLongitudeE6 - reef.minLongitudeE6).toLong()
            }
            .take(MAX_VISIBLE_LABELS)
            .forEach { reef ->
                val latitude = reef.labelLatitudeE6 / E6
                val longitude = reef.labelLongitudeE6 / E6
                projection.toPixels(GeoPoint(latitude, longitude), mapPoint)
                projection.rotateAndScalePoint(mapPoint.x, mapPoint.y, screenPoint)

                val text = reef.name
                val width = labelPaint.measureText(text) + horizontalPadding * 2
                val height = textHeight + verticalPadding * 2
                val left = (screenPoint.x - width / 2).coerceIn(
                    edgePadding,
                    maxOf(edgePadding, canvas.width - width - edgePadding),
                )
                val top = (screenPoint.y - height / 2).coerceIn(
                    edgePadding,
                    maxOf(edgePadding, canvas.height - height - edgePadding),
                )
                val screenRect = RectF(left, top, left + width, top + height)
                val collisionRect = RectF(screenRect).apply { inset(-3f * density, -3f * density) }
                if (occupied.any { RectF.intersects(it, collisionRect) }) return@forEach
                occupied += collisionRect

                val drawRect = RectF(screenRect).apply {
                    offset(
                        mapPoint.x.toFloat() - screenPoint.x,
                        mapPoint.y.toFloat() - screenPoint.y,
                    )
                }
                val saveCount = canvas.save()
                canvas.rotate(
                    counterRotationForMap(projection.orientation),
                    mapPoint.x.toFloat(),
                    mapPoint.y.toFloat(),
                )
                try {
                    canvas.drawRoundRect(drawRect, 5f * density, 5f * density, labelBackgroundPaint)
                    val baseline = drawRect.centerY() - (labelPaint.fontMetrics.ascent + labelPaint.fontMetrics.descent) / 2
                    canvas.drawText(text, drawRect.centerX(), baseline, labelPaint)
                } finally {
                    canvas.restoreToCount(saveCount)
                }
            }
    }

    companion object {
        private const val E6 = 1_000_000.0
        private const val MIN_REEF_ZOOM = 7.0
        private const val MIN_REEF_LABEL_ZOOM = 11.0
        private const val MAX_VISIBLE_LABELS = 40
    }
}
