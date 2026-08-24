package com.captainavi.app.ui.screens.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.MotionEvent
import com.captainavi.app.data.repository.MarineActivityPoint
import com.captainavi.app.data.repository.MarineActivityPointType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class MarineActivityPointsOverlay(context: Context) : Overlay() {
    var points: List<MarineActivityPoint> = emptyList()
    var showFishingPoints: Boolean = true
    var showDivePoints: Boolean = true
    var selectedPointId: String? = null
    var onPointTap: (MarineActivityPoint) -> Unit = {}

    private val density = context.resources.displayMetrics.density
    private val markerRadius = 8f * density
    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(4, 22, 35)
        textAlign = Paint.Align.CENTER
        textSize = 9f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 5, 26, 42)
        style = Paint.Style.FILL
    }
    private val tunaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 183, 3)
        style = Paint.Style.FILL
    }
    private val sportPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(124, 214, 96)
        style = Paint.Style.FILL
    }
    private val divePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(49, 211, 226)
        style = Paint.Style.FILL
    }
    private var hitTargets: List<HitTarget> = emptyList()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || mapView.zoomLevelDouble < MIN_POINT_ZOOM || points.isEmpty()) {
            hitTargets = emptyList()
            return
        }

        val bounds = mapView.boundingBox
        val projection = mapView.projection
        val mapPoint = Point()
        val screenPoint = Point()
        val visiblePoints = points.asSequence()
            .filter(::isTypeVisible)
            .filter { point ->
                point.latitude in bounds.latSouth..bounds.latNorth &&
                    point.longitude in bounds.lonWest..bounds.lonEast
            }
            .sortedByDescending { it.id == selectedPointId }
            .take(MAX_VISIBLE_POINTS)
            .toList()

        val newHitTargets = ArrayList<HitTarget>(visiblePoints.size)
        val occupiedLabels = mutableListOf<RectF>()
        visiblePoints.forEach { point ->
            projection.toPixels(GeoPoint(point.latitude, point.longitude), mapPoint)
            projection.rotateAndScalePoint(mapPoint.x, mapPoint.y, screenPoint)
            val isSelected = point.id == selectedPointId
            val markerPaint = when (point.type) {
                MarineActivityPointType.TUNA_FAD -> tunaPaint
                MarineActivityPointType.SPORT_FAD -> sportPaint
                MarineActivityPointType.DIVE_SITE -> divePaint
            }
            val markerLetter = when (point.type) {
                MarineActivityPointType.TUNA_FAD -> "T"
                MarineActivityPointType.SPORT_FAD -> "S"
                MarineActivityPointType.DIVE_SITE -> "D"
            }

            val saveCount = canvas.save()
            canvas.rotate(
                counterRotationForMap(projection.orientation),
                mapPoint.x.toFloat(),
                mapPoint.y.toFloat(),
            )
            try {
                if (isSelected) {
                    canvas.drawCircle(
                        mapPoint.x.toFloat(),
                        mapPoint.y.toFloat(),
                        markerRadius + 5f * density,
                        selectedPaint,
                    )
                }
                canvas.drawCircle(mapPoint.x.toFloat(), mapPoint.y.toFloat(), markerRadius, markerPaint)
                canvas.drawCircle(mapPoint.x.toFloat(), mapPoint.y.toFloat(), markerRadius, markerStrokePaint)
                val baseline = mapPoint.y - (markerTextPaint.ascent() + markerTextPaint.descent()) / 2f
                canvas.drawText(markerLetter, mapPoint.x.toFloat(), baseline, markerTextPaint)
            } finally {
                canvas.restoreToCount(saveCount)
            }

            val tapRadius = 22f * density
            newHitTargets += HitTarget(
                point,
                RectF(
                    screenPoint.x - tapRadius,
                    screenPoint.y - tapRadius,
                    screenPoint.x + tapRadius,
                    screenPoint.y + tapRadius,
                ),
            )

            if (isSelected || mapView.zoomLevelDouble >= MIN_LABEL_ZOOM) {
                drawLabel(
                    canvas = canvas,
                    mapView = mapView,
                    point = point,
                    mapPoint = mapPoint,
                    screenPoint = screenPoint,
                    occupied = occupiedLabels,
                    force = isSelected,
                )
            }
        }
        hitTargets = newHitTargets
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val target = hitTargets.firstOrNull { it.screenBounds.contains(event.x, event.y) } ?: return false
        selectedPointId = target.point.id
        onPointTap(target.point)
        mapView.invalidate()
        return true
    }

    private fun isTypeVisible(point: MarineActivityPoint): Boolean = when (point.type) {
        MarineActivityPointType.TUNA_FAD,
        MarineActivityPointType.SPORT_FAD -> showFishingPoints
        MarineActivityPointType.DIVE_SITE -> showDivePoints
    }

    private fun drawLabel(
        canvas: Canvas,
        mapView: MapView,
        point: MarineActivityPoint,
        mapPoint: Point,
        screenPoint: Point,
        occupied: MutableList<RectF>,
        force: Boolean,
    ) {
        val horizontalPadding = 7f * density
        val verticalPadding = 4f * density
        val labelWidth = labelPaint.measureText(point.name) + horizontalPadding * 2
        val labelHeight = labelPaint.fontMetrics.run { bottom - top } + verticalPadding * 2
        val edgePadding = 4f * density
        val left = (screenPoint.x - labelWidth / 2f).coerceIn(
            edgePadding,
            maxOf(edgePadding, canvas.width - labelWidth - edgePadding),
        )
        val top = (screenPoint.y + markerRadius + 5f * density).coerceIn(
            edgePadding,
            maxOf(edgePadding, canvas.height - labelHeight - edgePadding),
        )
        val screenRect = RectF(left, top, left + labelWidth, top + labelHeight)
        val collisionRect = RectF(screenRect).apply { inset(-3f * density, -3f * density) }
        if (!force && occupied.any { RectF.intersects(it, collisionRect) }) return
        occupied += collisionRect

        // OsmDroid rotates the overlay canvas with the chart. Counter-rotate the
        // marker letters and labels around their geographic anchor so names stay
        // upright and readable in heading-up mode.
        val drawRect = RectF(screenRect).apply {
            offset(
                mapPoint.x.toFloat() - screenPoint.x,
                mapPoint.y.toFloat() - screenPoint.y,
            )
        }
        val saveCount = canvas.save()
        canvas.rotate(
            counterRotationForMap(mapView.projection.orientation),
            mapPoint.x.toFloat(),
            mapPoint.y.toFloat(),
        )
        try {
            canvas.drawRoundRect(drawRect, 6f * density, 6f * density, labelBackgroundPaint)
            val baseline = drawRect.top + verticalPadding - labelPaint.fontMetrics.top
            canvas.drawText(point.name, drawRect.centerX(), baseline, labelPaint)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private data class HitTarget(
        val point: MarineActivityPoint,
        val screenBounds: RectF,
    )

    companion object {
        private const val MIN_POINT_ZOOM = 5.5
        private const val MIN_LABEL_ZOOM = 10.5
        private const val MAX_VISIBLE_POINTS = 300
    }
}
