package com.captainavi.app.ui.screens.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.view.MotionEvent
import com.captainavi.app.data.remote.FollowMePublicBoat
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class FollowMePublicBoatsOverlay(context: Context) : Overlay() {
    var boats: List<FollowMePublicBoat> = emptyList()
    var selectedBoatId: Int? = null
    var onBoatsTap: (List<FollowMePublicBoat>) -> Unit = {}

    private val density = context.resources.displayMetrics.density
    private val freshFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 235, 118)
        style = Paint.Style.FILL
    }
    private val staleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 82, 82)
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(2, 32, 50)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 3, 25, 40)
        style = Paint.Style.FILL
    }
    private val selectedLabelBackground = Paint(labelBackground).apply {
        color = Color.argb(240, 0, 105, 92)
    }
    private val selectedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private var hitTargets: List<HitTarget> = emptyList()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled || boats.isEmpty()) {
            hitTargets = emptyList()
            return
        }
        val projection = mapView.projection
        val markerSize = 9f * density
        val point = Point()
        val screenPoint = Point()
        val now = System.currentTimeMillis()
        val visibleBoats = boats.mapNotNull { boat ->
            projection.toPixels(GeoPoint(boat.latitude, boat.longitude), point)
            if (point.x < -markerSize || point.x > mapView.width + markerSize ||
                point.y < -markerSize || point.y > mapView.height + markerSize
            ) {
                null
            } else {
                projection.rotateAndScalePoint(point.x, point.y, screenPoint)
                ProjectedBoat(
                    boat = boat,
                    x = point.x.toFloat(),
                    y = point.y.toFloat(),
                    screenX = screenPoint.x.toFloat(),
                    screenY = screenPoint.y.toFloat(),
                )
            }
        }

        val tapRadius = 32f * density
        val newHitTargets = visibleBoats.map { projected ->
            HitTarget(
                boat = projected.boat,
                markerBounds = RectF(
                    projected.screenX - tapRadius,
                    projected.screenY - tapRadius,
                    projected.screenX + tapRadius,
                    projected.screenY + tapRadius,
                ),
                centerX = projected.screenX,
                centerY = projected.screenY,
            )
        }.toMutableList()

        visibleBoats.forEach { projected ->
            val boat = projected.boat
            val isStale = boat.updatedAtEpochMillis?.let { now - it > STALE_AFTER_MS } ?: true
            val fill = if (isStale) staleFill else freshFill
            if (boat.id == selectedBoatId) {
                canvas.drawCircle(projected.x, projected.y, markerSize + 6f * density, selectedStroke)
            }
            canvas.save()
            canvas.rotate(
                (boat.headingDegrees - mapView.mapOrientation).toFloat(),
                projected.x,
                projected.y,
            )
            val arrow = Path().apply {
                moveTo(projected.x, projected.y - markerSize)
                lineTo(projected.x + markerSize * 0.72f, projected.y + markerSize * 0.75f)
                lineTo(projected.x, projected.y + markerSize * 0.42f)
                lineTo(projected.x - markerSize * 0.72f, projected.y + markerSize * 0.75f)
                close()
            }
            canvas.drawPath(arrow, fill)
            canvas.drawPath(arrow, stroke)
            canvas.restore()
        }

        if (mapView.zoomLevelDouble >= LABEL_MIN_ZOOM) {
            val horizontalPadding = 7f * density
            val verticalPadding = 4f * density
            val collisionPadding = 4f * density
            val markerClearance = markerSize + collisionPadding
            val occupied = visibleBoats.mapTo(mutableListOf()) { projected ->
                RectF(
                    projected.screenX - markerClearance,
                    projected.screenY - markerClearance,
                    projected.screenX + markerClearance,
                    projected.screenY + markerClearance,
                )
            }
            val mapBounds = RectF(
                collisionPadding,
                140f * density,
                mapView.width - 76f * density,
                mapView.height - 205f * density,
            )

            visibleBoats
                .sortedBy { projected ->
                    val dx = projected.x - mapView.width / 2f
                    val dy = projected.y - mapView.height / 2f
                    dx * dx + dy * dy
                }
                .forEach { projected ->
                val boat = projected.boat
                val speed = if (boat.speedKnots > 0.4) {
                    " · ${String.format(java.util.Locale.US, "%.0f", boat.speedKnots)} kt"
                } else ""
                val text = boat.name + speed
                val textWidth = label.measureText(text) + horizontalPadding * 2
                val metrics = label.fontMetrics
                val textHeight = metrics.descent - metrics.ascent + verticalPadding * 2
                val gap = markerSize + 5f * density
                val candidates = listOf(
                    RectF(
                        projected.screenX + gap,
                        projected.screenY - textHeight / 2f,
                        projected.screenX + gap + textWidth,
                        projected.screenY + textHeight / 2f,
                    ),
                    RectF(
                        projected.screenX - gap - textWidth,
                        projected.screenY - textHeight / 2f,
                        projected.screenX - gap,
                        projected.screenY + textHeight / 2f,
                    ),
                    RectF(
                        projected.screenX - textWidth / 2f,
                        projected.screenY + gap,
                        projected.screenX + textWidth / 2f,
                        projected.screenY + gap + textHeight,
                    ),
                    RectF(
                        projected.screenX - textWidth / 2f,
                        projected.screenY - gap - textHeight,
                        projected.screenX + textWidth / 2f,
                        projected.screenY - gap,
                    ),
                )
                val bounds = candidates.firstOrNull { candidate ->
                    mapBounds.contains(candidate) && occupied.none { RectF.intersects(it, candidate) }
                } ?: return@forEach

                val labelTapPadding = 6f * density
                newHitTargets.firstOrNull { it.boat.id == boat.id }?.labelBounds =
                    RectF(bounds).apply { inset(-labelTapPadding, -labelTapPadding) }

                val drawBounds = RectF(bounds).apply {
                    offset(
                        projected.x - projected.screenX,
                        projected.y - projected.screenY,
                    )
                }
                val saveCount = canvas.save()
                canvas.rotate(
                    counterRotationForMap(mapView.projection.orientation),
                    projected.x,
                    projected.y,
                )
                try {
                    canvas.drawRoundRect(
                        drawBounds,
                        6f * density,
                        6f * density,
                        if (boat.id == selectedBoatId) selectedLabelBackground else labelBackground,
                    )
                    canvas.drawText(
                        text,
                        drawBounds.left + horizontalPadding,
                        drawBounds.top + verticalPadding - metrics.ascent,
                        label,
                    )
                } finally {
                    canvas.restoreToCount(saveCount)
                }
                occupied += RectF(bounds).apply { inset(-collisionPadding, -collisionPadding) }
            }
        }
        hitTargets = newHitTargets
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        // A visible name pill is unambiguous even when several vessel arrows overlap.
        hitTargets.firstOrNull { it.labelBounds?.contains(event.x, event.y) == true }?.let { target ->
            selectedBoatId = target.boat.id
            onBoatsTap(listOf(target.boat))
            mapView.invalidate()
            return true
        }

        val targets = hitTargets
            .filter { it.markerBounds.contains(event.x, event.y) }
            .sortedBy { target ->
                val dx = event.x - target.centerX
                val dy = event.y - target.centerY
                dx * dx + dy * dy
            }
        if (targets.isEmpty()) return false

        selectedBoatId = targets.singleOrNull()?.boat?.id
        onBoatsTap(targets.map(HitTarget::boat))
        mapView.invalidate()
        return true
    }

    private data class ProjectedBoat(
        val boat: FollowMePublicBoat,
        val x: Float,
        val y: Float,
        val screenX: Float,
        val screenY: Float,
    )

    private data class HitTarget(
        val boat: FollowMePublicBoat,
        val markerBounds: RectF,
        val centerX: Float,
        val centerY: Float,
        var labelBounds: RectF? = null,
    )

    companion object {
        const val STALE_AFTER_MS = 5 * 60_000L
        private const val LABEL_MIN_ZOOM = 14.0
    }
}
