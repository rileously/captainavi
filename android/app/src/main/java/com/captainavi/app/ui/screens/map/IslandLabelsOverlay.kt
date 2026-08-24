package com.captainavi.app.ui.screens.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.MotionEvent
import com.captainavi.app.data.repository.IslandPlace
import com.captainavi.app.data.repository.shouldShowLabelAtZoom
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class IslandLabelsOverlay(context: Context) : Overlay() {
    var islands: List<IslandPlace> = emptyList()
    var selectedIslandId: Int? = null
    var onIslandTap: (IslandPlace) -> Unit = {}

    @Volatile
    private var hitTargets: List<IslandLabelHitTarget> = emptyList()

    private val density = context.resources.displayMetrics.density
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val dhivehiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(185, 235, 245)
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 6, 28, 45)
        style = Paint.Style.FILL
    }
    private val selectedBackgroundPaint = Paint(backgroundPaint).apply {
        color = Color.argb(235, 117, 78, 12)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(46, 211, 225)
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val zoom = mapView.zoomLevelDouble
        if (zoom < 7.5 || islands.isEmpty()) {
            hitTargets = emptyList()
            return
        }

        val projection = mapView.projection
        val bounds = mapView.boundingBox
        val occupied = mutableListOf<RectF>()
        val newHitTargets = mutableListOf<IslandLabelHitTarget>()
        val mapPoint = Point()
        val screenPoint = Point()
        val candidates = islands.asSequence()
            .filter { island ->
                (island.id == selectedIslandId || island.shouldShowLabelAtZoom(zoom)) &&
                    island.latitude in bounds.latSouth..bounds.latNorth &&
                    island.longitude in bounds.lonWest..bounds.lonEast
            }
            .sortedWith(
                compareByDescending<IslandPlace> { it.id == selectedIslandId }
                    .thenByDescending { it.isCapital }
                    .thenByDescending { it.category == "Residential Island" }
            )
            .take(MAX_LABEL_CANDIDATES)

        candidates.forEach { island ->
            projection.toPixels(GeoPoint(island.latitude, island.longitude), mapPoint)
            projection.rotateAndScalePoint(mapPoint.x, mapPoint.y, screenPoint)
            val hasDhivehi = island.dhivehiName.isNotBlank()
            val textWidth = maxOf(
                titlePaint.measureText(island.englishName),
                if (hasDhivehi) dhivehiPaint.measureText(island.dhivehiName) else 0f,
            )
            val horizontalPadding = 7f * density
            val topPadding = 5f * density
            val lineGap = 2f * density
            val titleHeight = titlePaint.fontMetrics.run { bottom - top }
            val dhivehiHeight = if (hasDhivehi) dhivehiPaint.fontMetrics.run { bottom - top } else 0f
            val boxHeight = topPadding * 2 + titleHeight +
                if (hasDhivehi) lineGap + dhivehiHeight else 0f
            val boxWidth = textWidth + horizontalPadding * 2
            val edgePadding = 4f * density
            val left = (screenPoint.x - boxWidth / 2).coerceIn(
                edgePadding,
                maxOf(edgePadding, canvas.width - boxWidth - edgePadding),
            )
            val bottom = (screenPoint.y - 5f * density).coerceIn(
                boxHeight + edgePadding,
                canvas.height - edgePadding,
            )
            val screenRect = RectF(left, bottom - boxHeight, left + boxWidth, bottom)
            val collisionRect = RectF(screenRect).apply { inset(-3f * density, -3f * density) }
            val isSelected = island.id == selectedIslandId
            if (!isSelected && occupied.any { RectF.intersects(it, collisionRect) }) return@forEach
            occupied += collisionRect
            val labelTapPadding = 8f * density
            val dotTapRadius = 22f * density
            newHitTargets += IslandLabelHitTarget(
                island = island,
                labelBounds = RectF(screenRect).apply {
                    inset(-labelTapPadding, -labelTapPadding)
                },
                dotBounds = RectF(
                    screenPoint.x - dotTapRadius,
                    screenPoint.y - dotTapRadius,
                    screenPoint.x + dotTapRadius,
                    screenPoint.y + dotTapRadius,
                ),
            )

            // MapView rotates the overlay canvas. Counter-rotate labels around their
            // geographic anchor so both Latin and Thaana text remain screen-upright.
            // The rect is first clamped in screen coordinates, then translated back
            // into the rotated canvas coordinate space for drawing.
            val rect = RectF(screenRect).apply {
                offset(
                    mapPoint.x.toFloat() - screenPoint.x,
                    mapPoint.y.toFloat() - screenPoint.y,
                )
            }
            canvas.drawCircle(mapPoint.x.toFloat(), mapPoint.y.toFloat(), 3f * density, dotPaint)
            val saveCount = canvas.save()
            canvas.rotate(
                counterRotationForMap(projection.orientation),
                mapPoint.x.toFloat(),
                mapPoint.y.toFloat(),
            )
            try {
                canvas.drawRoundRect(rect, 6f * density, 6f * density,
                    if (isSelected) selectedBackgroundPaint else backgroundPaint)
                val titleBaseline = rect.top + topPadding - titlePaint.fontMetrics.top
                canvas.drawText(island.englishName, rect.centerX(), titleBaseline, titlePaint)
                if (hasDhivehi) {
                    val dhivehiBaseline = titleBaseline + lineGap + dhivehiHeight
                    canvas.drawText(island.dhivehiName, rect.centerX(), dhivehiBaseline, dhivehiPaint)
                }
            } finally {
                canvas.restoreToCount(saveCount)
            }
        }
        hitTargets = newHitTargets
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val target = hitTargets.firstOrNull {
            it.labelBounds.contains(event.x, event.y) || it.dotBounds.contains(event.x, event.y)
        } ?: return false
        selectedIslandId = target.island.id
        onIslandTap(target.island)
        mapView.invalidate()
        return true
    }

    companion object {
        private const val MAX_LABEL_CANDIDATES = 120
    }
}

private data class IslandLabelHitTarget(
    val island: IslandPlace,
    val labelBounds: RectF,
    val dotBounds: RectF,
)

internal fun counterRotationForMap(mapOrientation: Float): Float = -mapOrientation
