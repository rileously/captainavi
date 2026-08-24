package com.captainavi.app.ui.screens.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import com.captainavi.app.data.remote.RtlMarineRoute
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class RtlMarineRoutesOverlay(context: Context) : Overlay() {
    var routes: List<RtlMarineRoute> = emptyList()

    private val density = context.resources.displayMetrics.density
    private val routePaints = mutableMapOf<String, Paint>()
    private val casingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 2, 26, 42)
        style = Paint.Style.STROKE
        strokeWidth = 3.6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val stopFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val stopStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(4, 47, 70)
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 3, 36, 54)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled || routes.isEmpty() || mapView.zoomLevelDouble < MIN_ROUTE_ZOOM) return
        val bounds = mapView.boundingBox
        val visibleRoutes = routes.filter { route ->
            val minLat = route.stops.minOf { it.latitude }
            val maxLat = route.stops.maxOf { it.latitude }
            val minLon = route.stops.minOf { it.longitude }
            val maxLon = route.stops.maxOf { it.longitude }
            maxLat >= bounds.latSouth && minLat <= bounds.latNorth &&
                maxLon >= bounds.lonWest && minLon <= bounds.lonEast
        }
        if (visibleRoutes.isEmpty()) return

        val projection = mapView.projection
        val point = Point()
        val path = Path()
        visibleRoutes.forEach { route ->
            path.rewind()
            route.stops.forEachIndexed { index, stop ->
                projection.toPixels(GeoPoint(stop.latitude, stop.longitude), point)
                if (index == 0) path.moveTo(point.x.toFloat(), point.y.toFloat())
                else path.lineTo(point.x.toFloat(), point.y.toFloat())
            }
            canvas.drawPath(path, casingPaint)
            canvas.drawPath(path, routePaint(route.colorHex))
        }

        if (mapView.zoomLevelDouble >= MIN_STOP_ZOOM) {
            visibleRoutes.asSequence()
                .flatMap { it.stops.asSequence() }
                .distinctBy { it.code }
                .forEach { stop ->
                    projection.toPixels(GeoPoint(stop.latitude, stop.longitude), point)
                    canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 3.2f * density, stopFill)
                    canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 3.2f * density, stopStroke)
                }
        }

        if (mapView.zoomLevelDouble >= MIN_LABEL_ZOOM) {
            drawRouteLabels(canvas, mapView, visibleRoutes)
        }
    }

    private fun drawRouteLabels(canvas: Canvas, mapView: MapView, routes: List<RtlMarineRoute>) {
        val projection = mapView.projection
        val point = Point()
        val occupied = mutableListOf<RectF>()
        val horizontalPadding = 6f * density
        val verticalPadding = 3f * density
        val edge = 5f * density
        val metrics = labelPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent

        routes.sortedByDescending { it.stops.size }.forEach { route ->
            val stop = route.stops[route.stops.size / 2]
            projection.toPixels(GeoPoint(stop.latitude, stop.longitude), point)
            val text = route.name
            val width = labelPaint.measureText(text) + horizontalPadding * 2
            val height = textHeight + verticalPadding * 2
            val rect = RectF(
                point.x - width / 2,
                point.y - height - 5f * density,
                point.x + width / 2,
                point.y - 5f * density,
            )
            if (rect.left < edge || rect.top < edge || rect.right > canvas.width - edge ||
                rect.bottom > canvas.height - edge || occupied.any { RectF.intersects(it, rect) }
            ) return@forEach

            canvas.drawRoundRect(rect, 5f * density, 5f * density, labelBackground)
            val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2
            canvas.drawText(text, rect.centerX(), baseline, labelPaint)
            occupied += RectF(rect).apply { inset(-3f * density, -3f * density) }
        }
    }

    private fun routePaint(colorHex: String): Paint = routePaints.getOrPut(colorHex) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.rgb(54, 207, 226))
            alpha = 235
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    companion object {
        private const val MIN_ROUTE_ZOOM = 7.0
        private const val MIN_STOP_ZOOM = 8.5
        private const val MIN_LABEL_ZOOM = 12.0
    }
}
