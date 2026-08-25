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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
        style = Paint.Style.FILL
    }
    private val selectedBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 117, 78, 12)
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
            val labelTapPadding = 8f * density
            val dotTapRadius = 22f * density
            val dotBounds = RectF(
                screenPoint.x - dotTapRadius,
                screenPoint.y - dotTapRadius,
                screenPoint.x + dotTapRadius,
                screenPoint.y + dotTapRadius,
            )

            // Always keep a tappable dot, even when the text label is collision-culled.
            // Previously culled islands vanished from hit-testing, so taps near a visible
            // atoll island often did nothing.
            val labelCulled = !isSelected && occupied.any { RectF.intersects(it, collisionRect) }
            newHitTargets += IslandLabelHitTarget(
                island = island,
                labelBounds = if (labelCulled) null else RectF(screenRect).apply {
                    inset(-labelTapPadding, -labelTapPadding)
                },
                dotBounds = dotBounds,
                centerX = screenPoint.x.toFloat(),
                centerY = screenPoint.y.toFloat(),
            )

            val style = islandLabelStyle(island.category)
            dotPaint.color = style.dotColor
            canvas.drawCircle(mapPoint.x.toFloat(), mapPoint.y.toFloat(), 3f * density, dotPaint)
            if (labelCulled) return@forEach

            occupied += collisionRect

            // MapView rotates the overlay canvas. Counter-rotate labels around their
            // geographic anchor so both Latin and Thaana text remain screen-upright.
            val rect = RectF(screenRect).apply {
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
                backgroundPaint.color = style.backgroundColor
                titlePaint.color = style.titleColor
                dhivehiPaint.color = style.dhivehiColor
                canvas.drawRoundRect(
                    rect,
                    6f * density,
                    6f * density,
                    if (isSelected) selectedBackgroundPaint else backgroundPaint,
                )
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
        val island = resolveTappedIsland(event.x, event.y, mapView) ?: return false
        // Compose state updates must run on the main thread; post in case osmdroid
        // delivers the tap from a worker path on some devices.
        mapView.post {
            selectedIslandId = island.id
            onIslandTap(island)
            mapView.invalidate()
        }
        return true
    }

    internal fun resolveTappedIsland(tapX: Float, tapY: Float, mapView: MapView): IslandPlace? {
        val direct = hitTargets
            .filter { target ->
                target.labelBounds?.contains(tapX, tapY) == true ||
                    target.dotBounds.contains(tapX, tapY)
            }
            .minByOrNull { target ->
                val dx = tapX - target.centerX
                val dy = tapY - target.centerY
                dx * dx + dy * dy
            }
        if (direct != null) return direct.island

        // Fallback: nearest gazetteer island under the finger when labels are sparse
        // or the tap lands on the basemap island rather than the text pill.
        val zoom = mapView.zoomLevelDouble
        val maxMeters = maxIslandTapDistanceMeters(zoom)
        if (maxMeters <= 0.0 || islands.isEmpty()) return null

        val geo = mapView.projection.fromPixels(tapX.toInt(), tapY.toInt())
        return nearestIslandWithin(
            islands = islands,
            latitude = geo.latitude,
            longitude = geo.longitude,
            maxDistanceMeters = maxMeters,
            zoom = zoom,
            selectedIslandId = selectedIslandId,
        )
    }

    companion object {
        private const val MAX_LABEL_CANDIDATES = 120
    }
}

private data class IslandLabelHitTarget(
    val island: IslandPlace,
    val labelBounds: RectF?,
    val dotBounds: RectF,
    val centerX: Float,
    val centerY: Float,
)

internal fun counterRotationForMap(mapOrientation: Float): Float = -mapOrientation

/**
 * Category colors for island name "tooltip" pills.
 * Inhabited (Residential) islands use a distinct green so they read clearly
 * against tourism / uninhabited labels on the chart.
 */
internal data class IslandLabelStyle(
    val backgroundColor: Int,
    val titleColor: Int,
    val dhivehiColor: Int,
    val dotColor: Int,
)

internal fun islandLabelStyle(category: String): IslandLabelStyle = when (category) {
    "Residential Island" -> IslandLabelStyle(
        backgroundColor = argb(230, 8, 78, 52),
        titleColor = argb(255, 255, 255, 255),
        dhivehiColor = argb(255, 170, 245, 205),
        dotColor = argb(255, 46, 210, 130),
    )
    "Tourism Island" -> IslandLabelStyle(
        backgroundColor = argb(215, 72, 38, 12),
        titleColor = argb(255, 255, 255, 255),
        dhivehiColor = argb(255, 255, 210, 160),
        dotColor = argb(255, 255, 168, 72),
    )
    "Industrial Island", "Institutional Island" -> IslandLabelStyle(
        backgroundColor = argb(215, 48, 42, 18),
        titleColor = argb(255, 255, 255, 255),
        dhivehiColor = argb(255, 235, 220, 150),
        dotColor = argb(255, 210, 175, 70),
    )
    else -> IslandLabelStyle(
        // Uninhabited / unknown — muted slate cyan (previous default look)
        backgroundColor = argb(205, 6, 28, 45),
        titleColor = argb(255, 255, 255, 255),
        dhivehiColor = argb(255, 185, 235, 245),
        dotColor = argb(255, 46, 211, 225),
    )
}

/** Pack ARGB without android.graphics.Color so unit tests stay JVM-safe. */
internal fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    (a and 0xff shl 24) or (r and 0xff shl 16) or (g and 0xff shl 8) or (b and 0xff)

internal fun maxIslandTapDistanceMeters(zoom: Double): Double = when {
    zoom >= 14.0 -> 450.0
    zoom >= 12.0 -> 900.0
    zoom >= 10.0 -> 1_800.0
    zoom >= 8.5 -> 3_200.0
    zoom >= 7.5 -> 5_000.0
    else -> 0.0
}

internal fun nearestIslandWithin(
    islands: List<IslandPlace>,
    latitude: Double,
    longitude: Double,
    maxDistanceMeters: Double,
    zoom: Double,
    selectedIslandId: Int? = null,
): IslandPlace? {
    if (maxDistanceMeters <= 0.0) return null
    var best: IslandPlace? = null
    var bestMeters = maxDistanceMeters
    for (island in islands) {
        if (island.id != selectedIslandId && !island.shouldShowLabelAtZoom(zoom)) continue
        val meters = haversineMeters(latitude, longitude, island.latitude, island.longitude)
        if (meters < bestMeters) {
            bestMeters = meters
            best = island
        }
    }
    return best
}

private fun haversineMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
}
