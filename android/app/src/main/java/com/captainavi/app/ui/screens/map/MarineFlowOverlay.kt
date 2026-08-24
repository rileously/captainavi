package com.captainavi.app.ui.screens.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class FlowLayerMode {
    OFF,
    WIND,
    CURRENT,
    BOTH;

    fun next(): FlowLayerMode = when (this) {
        OFF -> WIND
        WIND -> CURRENT
        CURRENT -> BOTH
        BOTH -> OFF
    }

    val title: String
        get() = when (this) {
            OFF -> "Flow Off"
            WIND -> "Wind Flow"
            CURRENT -> "Current Flow"
            BOTH -> "Wind & Current"
        }
}

private data class FlowParticle(
    var x: Float,
    var y: Float,
    var age: Int,
    var maxAge: Int,
    var speedMultiplier: Float,
    var lengthPx: Float,
    var thickness: Float,
    var wavePhase: Float,
    var layer: Int, // 0 = Ambient soft drift, 1 = Standard stream, 2 = Fast glowing tracer
    var isCurrent: Boolean = false
)

/**
 * Premium, high-performance animated marine flow engine.
 * Renders living organic wind streamlines and ocean current drift vectors
 * with head glow, curved wavelets, multi-layer depth, and true geographical alignment.
 */
class MarineFlowOverlay(
    context: Context,
    var mapView: MapView? = null
) : Overlay() {

    var flowMode: FlowLayerMode = FlowLayerMode.OFF
        set(value) {
            field = value
            isEnabled = value != FlowLayerMode.OFF
        }

    var windSpeedKnots: Double = 12.0
    var windDirectionDegrees: Double = 65.0 // Meteorological (from)
    var oceanCurrentKnots: Double = 0.8
    var oceanCurrentDirectionDegrees: Double = 240.0 // Oceanographic (to)

    private val density = context.resources.displayMetrics.density
    private val particles = ArrayList<FlowParticle>()
    private val particleCount = 220
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastTimeMs = 0L

    // Reusable path and paints to eliminate runtime GC allocations
    private val streamPath = Path()

    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val headGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val headCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private fun initParticles(cx: Float, cy: Float, radius: Float) {
        particles.clear()
        val rng = Random(1337)
        for (i in 0 until particleCount) {
            val isCurrentParticle = i % 3 == 0
            val layerType = when {
                i % 5 == 0 -> 2 // 20% Fast tracer comet
                i % 4 == 0 -> 0 // 25% Ambient soft background drift
                else -> 1       // 55% Standard crisp stream
            }
            val baseLen = when (layerType) {
                0 -> (18f + rng.nextFloat() * 22f) * density
                2 -> (12f + rng.nextFloat() * 16f) * density
                else -> (14f + rng.nextFloat() * 18f) * density
            }
            val speedMul = when (layerType) {
                0 -> 0.65f + rng.nextFloat() * 0.25f
                2 -> 1.25f + rng.nextFloat() * 0.45f
                else -> 0.85f + rng.nextFloat() * 0.35f
            }
            val thick = when (layerType) {
                0 -> 1.4f * density
                2 -> 2.4f * density
                else -> 1.9f * density
            }
            particles.add(
                FlowParticle(
                    x = cx + (rng.nextFloat() * 2f - 1f) * radius,
                    y = cy + (rng.nextFloat() * 2f - 1f) * radius,
                    age = rng.nextInt(70),
                    maxAge = 45 + rng.nextInt(55),
                    speedMultiplier = speedMul,
                    lengthPx = baseLen,
                    thickness = thick,
                    wavePhase = rng.nextFloat() * 6.283f,
                    layer = layerType,
                    isCurrent = isCurrentParticle
                )
            )
        }
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        if (!isEnabled || flowMode == FlowLayerMode.OFF) return

        val w = canvas.width
        val h = canvas.height
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val boundRadius = hypot(w.toFloat(), h.toFloat()) * 0.78f

        if (w != lastWidth || h != lastHeight || particles.isEmpty()) {
            initParticles(cx, cy, boundRadius)
            lastWidth = w
            lastHeight = h
        }

        val now = System.currentTimeMillis()
        val dt = if (lastTimeMs > 0L) ((now - lastTimeMs).toFloat() / 1000f).coerceIn(0.005f, 0.05f) else 0.016f
        lastTimeMs = now

        // True geographical angles (Chart coordinates: -Y is North, +X is East):
        // 1. Wind: from windDirectionDegrees -> moves to (windDirectionDegrees + 180)
        val windTargetAngleDeg = (windDirectionDegrees + 180.0).mod(360.0)
        val windRad = Math.toRadians(windTargetAngleDeg)
        val baseWindPxPerSec = ((windSpeedKnots.coerceAtLeast(1.5) * 8.5) * density).toFloat()
        val windDx = (sin(windRad) * baseWindPxPerSec * dt).toFloat()
        val windDy = (-cos(windRad) * baseWindPxPerSec * dt).toFloat()
        val windPerpX = (-cos(windRad)).toFloat()
        val windPerpY = (-sin(windRad)).toFloat()

        // 2. Ocean current: flows to oceanCurrentDirectionDegrees
        val currentTargetAngleDeg = oceanCurrentDirectionDegrees.mod(360.0)
        val currentRad = Math.toRadians(currentTargetAngleDeg)
        val baseCurrentPxPerSec = ((oceanCurrentKnots.coerceAtLeast(0.15) * 38.0) * density).toFloat()
        val currentDx = (sin(currentRad) * baseCurrentPxPerSec * dt).toFloat()
        val currentDy = (-cos(currentRad) * baseCurrentPxPerSec * dt).toFloat()
        val currentPerpX = (-cos(currentRad)).toFloat()
        val currentPerpY = (-sin(currentRad)).toFloat()

        // Dynamic multi-tone marine colors
        val (windR, windG, windB) = when {
            windSpeedKnots > 28.0 -> Triple(255, 61, 95)    // Gale Storm (Vibrant Coral/Crimson)
            windSpeedKnots > 20.0 -> Triple(255, 171, 0)    // Fresh Breeze (Luminous Amber)
            windSpeedKnots > 13.0 -> Triple(0, 230, 200)    // Moderate Trade Wind (Tropical Mint/Turquoise)
            windSpeedKnots > 6.0  -> Triple(0, 229, 255)    // Gentle Breeze (Electric Cyan)
            else                  -> Triple(165, 243, 252)  // Light Air (Soft Ice Blue)
        }

        // Ocean Current: Deep Emerald Seafoam
        val currentR = 0
        val currentG = 230
        val currentB = 118

        val minX = cx - boundRadius
        val maxX = cx + boundRadius
        val minY = cy - boundRadius
        val maxY = cy + boundRadius

        for (p in particles) {
            val isCurrent = p.isCurrent
            if (isCurrent && flowMode == FlowLayerMode.WIND) continue
            if (!isCurrent && flowMode == FlowLayerMode.CURRENT) continue

            val stepDx = if (isCurrent) currentDx * p.speedMultiplier else windDx * p.speedMultiplier
            val stepDy = if (isCurrent) currentDy * p.speedMultiplier else windDy * p.speedMultiplier
            val angleRad = if (isCurrent) currentRad else windRad
            val perpX = if (isCurrent) currentPerpX else windPerpX
            val perpY = if (isCurrent) currentPerpY else windPerpY

            p.x += stepDx
            p.y += stepDy
            p.age++

            // Respawn when life expires or moves outside the radial field
            if (p.age >= p.maxAge || p.x < minX || p.x > maxX || p.y < minY || p.y > maxY) {
                p.x = cx + (Random.nextFloat() * 2f - 1f) * boundRadius
                p.y = cy + (Random.nextFloat() * 2f - 1f) * boundRadius
                p.age = 0
                p.maxAge = 45 + Random.nextInt(55)
            }

            // Smooth cubic ease in & out for silky fade
            val t = p.age.toFloat() / p.maxAge.toFloat()
            val alphaNorm = sin(t * Math.PI).toFloat().coerceIn(0f, 1f)
            val layerBaseAlpha = when (p.layer) {
                0 -> 110
                2 -> 245
                else -> 190
            }
            val alphaInt = (alphaNorm * layerBaseAlpha).toInt()
            if (alphaInt <= 5) continue

            val (r, g, b) = if (isCurrent) Triple(currentR, currentG, currentB) else Triple(windR, windG, windB)

            // Organic serpentine wave undulation along the streamline
            val waveFreq = 0.08f
            val waveAmp = (if (isCurrent) 1.8f else 2.2f) * density
            val waveOffset = sin(p.wavePhase + p.age * waveFreq) * waveAmp

            val tailLen = p.lengthPx * (0.75f + alphaNorm * 0.35f)
            val headX = p.x
            val headY = p.y

            // Midpoint of curve with subtle perpendicular displacement
            val midDist = tailLen * 0.5f
            val midX = headX - (sin(angleRad) * midDist).toFloat() + (perpX * waveOffset)
            val midY = headY - (-cos(angleRad) * midDist).toFloat() + (perpY * waveOffset)

            // Tail end
            val tailX = headX - (sin(angleRad) * tailLen).toFloat()
            val tailY = headY - (-cos(angleRad) * tailLen).toFloat()

            // Draw organic curved streamline
            streamPath.reset()
            streamPath.moveTo(tailX, tailY)
            streamPath.quadTo(midX, midY, headX, headY)

            streakPaint.color = Color.argb(alphaInt, r, g, b)
            streakPaint.strokeWidth = p.thickness * (0.8f + alphaNorm * 0.3f)
            canvas.drawPath(streamPath, streakPaint)

            // Leading Edge Glow & Head Dot for Fast Tracers and Ocean Current
            if (p.layer == 2 || isCurrent) {
                // Outer glow halo
                headGlowPaint.color = Color.argb((alphaInt * 0.45f).toInt(), r, g, b)
                val glowR = (if (isCurrent) 3.5f else 2.8f) * density
                canvas.drawCircle(headX, headY, glowR, headGlowPaint)

                // Crisp luminous white/color core
                if (p.layer == 2) {
                    headCorePaint.alpha = alphaInt
                    canvas.drawCircle(headX, headY, 1.3f * density, headCorePaint)
                } else if (isCurrent) {
                    headGlowPaint.color = Color.argb(alphaInt, currentR, currentG, currentB)
                    canvas.drawCircle(headX, headY, 1.8f * density, headGlowPaint)
                }
            }
        }

        // Request next frame for buttery smooth 60fps animation
        mapView?.postInvalidateOnAnimation()
    }
}
