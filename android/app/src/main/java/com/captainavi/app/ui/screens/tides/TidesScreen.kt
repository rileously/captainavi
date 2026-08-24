package com.captainavi.app.ui.screens.tides

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.captainavi.app.tides.TidePredictor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Hydrographic Tide Chart with live real-time red cursor & interactive blue inspection crosshair:
 * - Red vertical line & top pill: Anchored to real-time "NOW" on today's chart
 * - Blue Crosshair Target Reticle: Appears upon touch/scrub with white/blue tooltip (e.g. 0.8 m / 15:46)
 * - Daylight (Yellow) & Nighttime (Blue) Shading
 * - Datum Switcher: Toggle between LAT (Lowest Astronomical Tide / Chart Datum) and MSL (Mean Sea Level)
 * - Horizontal swipe to switch days with slide animation
 */
@Composable
fun TidesScreen(
    modifier: Modifier = Modifier
) {
    val maldivesTz = remember { TimeZone.getTimeZone("Indian/Maldives") }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dayOffset by remember { mutableIntStateOf(0) } // 0 = Today, -1 = Yesterday, +1 = Tomorrow, etc.
    var inspectHourFraction by remember { mutableStateOf<Float?>(null) }
    var isMslMode by remember { mutableStateOf(false) } // false = LAT (Chart), true = MSL (Mean Sea Level)

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(5_000)
        }
    }

    var totalBannerDragX by remember { mutableFloatStateOf(0f) }
    var totalFooterDragX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF274D77))
    ) {
        // 1. Compact station header. The title stays optically centered while
        // the datum control remains easy to reach on the right.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF173451), Color(0xFF234F78))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Hanimaadhoo B",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = "UHSLC 117 · Maldives",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = Color(0xFFBBD3E8)
                    )
                )
            }
            // Datum Badge (LAT vs MSL)
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF0F2A43),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { isMslMode = !isMslMode }
            ) {
                Text(
                    text = if (isMslMode) "MSL" else "LAT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F)
                    ),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }

        // Animated Day Content with Horizontal Slide Transition
        AnimatedContent(
            targetState = dayOffset,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
                } else {
                    slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { targetDayOffset ->

            // Calculate selected day boundaries in Maldives Time
            val cal = remember(nowMillis, targetDayOffset) {
                Calendar.getInstance(maldivesTz).apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, targetDayOffset)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
            val dayStartEpoch = cal.timeInMillis
            val dayEndEpoch = dayStartEpoch + 24 * 3_600_000L

            // Moon phase & date formatters
            val moonInfo = remember(dayStartEpoch) { TidePredictor.getMoonPhase(dayStartEpoch + 12 * 3_600_000L) }
            val dateDisplayFmt = remember { SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { timeZone = maldivesTz } }
            val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = maldivesTz } }

            val dateStr = dateDisplayFmt.format(Date(dayStartEpoch))
            val daySubtitle = when (targetDayOffset) {
                0 -> "(Today)"
                -1 -> "(Yesterday)"
                1 -> "(Tomorrow)"
                else -> {
                    val nameFmt = SimpleDateFormat("(EEEE)", Locale.US).apply { timeZone = maldivesTz }
                    nameFmt.format(Date(dayStartEpoch))
                }
            }

            // Extremes for the selected 24h day
            val dayExtremes = remember(dayStartEpoch) {
                TidePredictor.extremaBetween(dayStartEpoch, dayEndEpoch)
                    .filter { it.epochMillis in dayStartEpoch until dayEndEpoch }
                    .sortedBy { it.epochMillis }
            }
            val lowTides = dayExtremes.filter { !it.isHigh }
            val highTides = dayExtremes.filter { it.isHigh }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 2. Overview Banner (Moon Phase, Date Navigator, Weather) - Swipe Enabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF3872AB), Color(0xFF2B5F95))
                            )
                        )
                        .pointerInput(targetDayOffset) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalBannerDragX += dragAmount.x
                                },
                                onDragEnd = {
                                    if (totalBannerDragX < -45f) {
                                        dayOffset++
                                        inspectHourFraction = null
                                    } else if (totalBannerDragX > 45f) {
                                        dayOffset--
                                        inspectHourFraction = null
                                    }
                                    totalBannerDragX = 0f
                                },
                                onDragCancel = { totalBannerDragX = 0f }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Moon Phase Left
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        MoonIcon(
                            illuminationPct = moonInfo.illuminationPct,
                            isWaxing = moonInfo.isWaxing,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = moonInfo.shortLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = Color(0xFFE2EDF8)
                            )
                        )
                    }

                    // Date Navigator Center
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1.8f)
                    ) {
                        IconButton(
                            onClick = {
                                dayOffset--
                                inspectHourFraction = null
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Previous Day",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                dayOffset = 0
                                inspectHourFraction = null
                            }
                        ) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = daySubtitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = Color(0xFFE2EDF8)
                                )
                            )
                        }

                        IconButton(
                            onClick = {
                                dayOffset++
                                inspectHourFraction = null
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next Day",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Weather & Temp Right
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Sunny Weather",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "29° | 28°",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        )
                    }
                }

                // 3. Central Harmonic Tide Graph
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    TideHarmonicCanvas(
                        dayStartEpoch = dayStartEpoch,
                        nowEpoch = nowMillis,
                        isToday = (targetDayOffset == 0),
                        isMslMode = isMslMode,
                        inspectHourFraction = inspectHourFraction,
                        onInspectHour = { fraction ->
                            inspectHourFraction = fraction
                        },
                        onSwipeDay = { delta ->
                            dayOffset += delta
                            inspectHourFraction = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 4. Bottom High / Low Tide Footer - Swipe Enabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF284F7C), Color(0xFF1D3B5F))
                            )
                        )
                        .pointerInput(targetDayOffset) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalFooterDragX += dragAmount.x
                                },
                                onDragEnd = {
                                    if (totalFooterDragX < -45f) {
                                        dayOffset++
                                        inspectHourFraction = null
                                    } else if (totalFooterDragX > 45f) {
                                        dayOffset--
                                        inspectHourFraction = null
                                    }
                                    totalFooterDragX = 0f
                                },
                                onDragCancel = { totalFooterDragX = 0f }
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Low Tides Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Low Tides:",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            )
                        )
                        if (lowTides.isEmpty()) {
                            Text(
                                text = "",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB0C8E0))
                            )
                        } else {
                            lowTides.forEach { ext ->
                                val hVal = if (isMslMode) ext.heightMslMeters else ext.heightMslMeters + TidePredictor.LAT_OFFSET_METERS
                                val prefix = if (isMslMode && hVal > 0) "+" else ""
                                Text(
                                    text = String.format(Locale.US, "%s%.1f m @ %s", prefix, hVal, timeFmt.format(Date(ext.epochMillis))),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // High Tides Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "High Tides:",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            )
                        )
                        if (highTides.isEmpty()) {
                            Text(
                                text = "",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB0C8E0))
                            )
                        } else {
                            highTides.forEach { ext ->
                                val hVal = if (isMslMode) ext.heightMslMeters else ext.heightMslMeters + TidePredictor.LAT_OFFSET_METERS
                                val prefix = if (isMslMode && hVal > 0) "+" else ""
                                Text(
                                    text = String.format(Locale.US, "%s%.1f m @ %s", prefix, hVal, timeFmt.format(Date(ext.epochMillis))),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-fidelity Canvas renderer matching the exact hydrographic tide chart style:
 * - Night twilight background (00:00-06:00, 18:15-24:00)
 * - Daylight golden background band (06:00-18:15)
 * - Datum Switcher: LAT (Chart) vs MSL (Mean Sea Level)
 * - Y-Axis grid lines & labels
 * - X-Axis hour markers (02, 04, 06, 08, 10, 12, 14, 16, 18, 20, 22)
 * - Red vertical cursor line with top rounded callout pill anchored to live "NOW"
 * - Blue Crosshair Target Reticle with floating white inspection bubble (e.g. 0.8 m / 15:46)
 */
@Composable
private fun TideHarmonicCanvas(
    dayStartEpoch: Long,
    nowEpoch: Long,
    isToday: Boolean,
    isMslMode: Boolean,
    inspectHourFraction: Float?,
    onInspectHour: (Float?) -> Unit,
    onSwipeDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleCount = 97
    val samples = remember(dayStartEpoch, isMslMode) {
        List(sampleCount) { i ->
            val fraction = i / (sampleCount - 1f) * 24f
            val t = dayStartEpoch + (fraction * 3_600_000L).toLong()
            val height = if (isMslMode) TidePredictor.heightMslMeters(t) else TidePredictor.heightLatMeters(t)
            fraction to height
        }
    }

    val minH = if (isMslMode) -0.8 else 0.0
    val maxH = if (isMslMode) 0.8 else 1.45

    val maldivesTz = remember { TimeZone.getTimeZone("Indian/Maldives") }
    val todayCal = remember(nowEpoch) {
        Calendar.getInstance(maldivesTz).apply { timeInMillis = nowEpoch }
    }
    val nowHourFraction = todayCal.get(Calendar.HOUR_OF_DAY) +
            todayCal.get(Calendar.MINUTE) / 60f +
            todayCal.get(Calendar.SECOND) / 3600f

    val nowHeight = remember(nowEpoch, isMslMode) {
        if (isMslMode) TidePredictor.heightMslMeters(nowEpoch) else TidePredictor.heightLatMeters(nowEpoch)
    }
    val nextNowHeight = remember(nowEpoch, isMslMode) {
        if (isMslMode) TidePredictor.heightMslMeters(nowEpoch + 15 * 60_000L) else TidePredictor.heightLatMeters(nowEpoch + 15 * 60_000L)
    }
    val isNowRising = nextNowHeight >= nowHeight

    var totalCanvasDragX by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val frac = (offset.x / size.width * 24f).coerceIn(0f, 24f)
                        onInspectHour(frac)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        totalCanvasDragX = 0f
                        val frac = (offset.x / size.width * 24f).coerceIn(0f, 24f)
                        onInspectHour(frac)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalCanvasDragX += dragAmount.x
                        val frac = (change.position.x / size.width * 24f).coerceIn(0f, 24f)
                        onInspectHour(frac)
                    },
                    onDragEnd = {
                        if (totalCanvasDragX < -45f) {
                            onSwipeDay(1)
                        } else if (totalCanvasDragX > 45f) {
                            onSwipeDay(-1)
                        }
                        totalCanvasDragX = 0f
                    },
                    onDragCancel = {
                        totalCanvasDragX = 0f
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val bottomMargin = 26.dp.toPx()
        val topMargin = 34.dp.toPx()

        val graphH = h - topMargin - bottomMargin

        // Use the entire width so 24:00 on one page and 00:00 on the next
        // occupy the same physical edge during the slide transition.
        fun xOf(hour: Float): Float = tideChartX(hour, w)
        fun yOf(height: Double): Float = topMargin + graphH - (((height - minH) / (maxH - minH)).toFloat() * graphH)

        // 1. Shaded Background: Night Blue (00:00-06:00, 18:15-24:00) vs Day Yellow (06:00-18:15)
        val nightBlueColor = Color(0xFFBED2E8)
        val dayYellowColor = Color(0xFFE9E5BE)

        val sunriseHour = 6.0f   // 06:00 AM Sunrise
        val sunsetHour = 18.25f  // 06:15 PM Sunset

        // Night time (00:00 to Sunrise 06:00) - Blue
        drawRect(
            color = nightBlueColor,
            topLeft = Offset(0f, 0f),
            size = Size(xOf(sunriseHour), h)
        )
        // Day time (Sunrise 06:00 to Sunset 18:15) - Yellow
        drawRect(
            color = dayYellowColor,
            topLeft = Offset(xOf(sunriseHour), 0f),
            size = Size(xOf(sunsetHour) - xOf(sunriseHour), h)
        )
        // Night time (Sunset 18:15 to Midnight 24:00) - Blue
        drawRect(
            color = nightBlueColor,
            topLeft = Offset(xOf(sunsetHour), 0f),
            size = Size(w - xOf(sunsetHour), h)
        )

        // 2. Y-Axis Horizontal Gridlines & Text Labels
        val gridHeights = if (isMslMode) {
            listOf(-0.4, -0.2, 0.0, 0.2, 0.4, 0.6)
        } else {
            listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0, 1.2)
        }
        val gridLineColor = Color.White.copy(alpha = 0.85f)
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(30, 45, 65)
            textSize = 13.dp.toPx()
            typeface = android.graphics.Typeface.DEFAULT
            isAntiAlias = true
            setShadowLayer(2.dp.toPx(), 0f, 0f, android.graphics.Color.argb(220, 255, 255, 255))
        }

        gridHeights.forEach { gh ->
            val y = yOf(gh)
            drawLine(
                color = gridLineColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.2.dp.toPx()
            )
            val label = if (isMslMode) {
                if (gh == 0.0) "0.0 m" else String.format(Locale.US, "%+.1f m", gh)
            } else {
                when (gh) {
                    0.0 -> "0 m"
                    1.0 -> "1 m"
                    else -> String.format(Locale.US, "%.1f m", gh)
                }
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                4.dp.toPx(),
                y - 4.dp.toPx(),
                textPaint
            )
        }

        // 3. X-Axis Bottom Hour Markers (02, 04, 06, 08, 10, 12, 14, 16, 18, 20, 22)
        val hourPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(30, 45, 65)
            textSize = 13.dp.toPx()
            typeface = android.graphics.Typeface.DEFAULT
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val hourTicks = listOf(2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22)
        hourTicks.forEach { hr ->
            val x = xOf(hr.toFloat())
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%02d", hr),
                x,
                h - 6.dp.toPx(),
                hourPaint
            )
        }

        // 4. Harmonic Sinusoidal Tide Curve
        val curvePath = Path()
        samples.forEachIndexed { i, (hr, height) ->
            val x = xOf(hr)
            val y = yOf(height)
            if (i == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
        }
        drawPath(
            path = curvePath,
            color = Color.White.copy(alpha = 0.35f),
            style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = curvePath,
            color = Color(0xFF315A85),
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // 5. Red Interactive Vertical Cursor Line (Anchored to Real-Time "NOW")
        if (isToday) {
            val cursorX = xOf(nowHourFraction)

            drawLine(
                color = Color(0xFFD32F2F),
                start = Offset(cursorX, topMargin - 8.dp.toPx()),
                end = Offset(cursorX, h),
                strokeWidth = 2.dp.toPx()
            )

            // Top Callout Pill (e.g. "0.84 m ↓" or "+0.04 m ↓")
            val arrow = if (isNowRising) "↑" else "↓"
            val prefix = if (isMslMode && nowHeight > 0) "+" else ""
            val pillText = String.format(Locale.US, "%s%.2f m %s", prefix, nowHeight, arrow)
            val pillPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(200, 20, 20)
                textSize = 13.dp.toPx()
                typeface = android.graphics.Typeface.DEFAULT
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val pillW = 76.dp.toPx()
            val pillH = 22.dp.toPx()
            val pillX = (cursorX - pillW / 2f).coerceIn(4.dp.toPx(), w - pillW - 4.dp.toPx())
            val pillY = 4.dp.toPx()

            // White callout bubble with subtle border
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(pillX, pillY),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFD32F2F),
                topLeft = Offset(pillX, pillY),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Little downward pointer triangle connecting pill to vertical red line
            val triPath = Path().apply {
                moveTo(cursorX - 4.dp.toPx(), pillY + pillH)
                lineTo(cursorX + 4.dp.toPx(), pillY + pillH)
                lineTo(cursorX, pillY + pillH + 4.dp.toPx())
                close()
            }
            drawPath(triPath, color = Color.White, style = Fill)
            drawPath(triPath, color = Color(0xFFD32F2F), style = Stroke(width = 1.dp.toPx()))

            drawContext.canvas.nativeCanvas.drawText(
                pillText,
                pillX + pillW / 2f,
                pillY + pillH - 6.dp.toPx(),
                pillPaint
            )
        }

        // 6. Blue Target Reticle & Floating Inspection Bubble upon Touch / Scrub
        if (inspectHourFraction != null) {
            val insEpoch = dayStartEpoch + (inspectHourFraction * 3_600_000L).toLong()
            val insHeight = if (isMslMode) TidePredictor.heightMslMeters(insEpoch) else TidePredictor.heightLatMeters(insEpoch)
            val insX = xOf(inspectHourFraction)
            val insY = yOf(insHeight)

            val reticleRadius = 20.dp.toPx()

            // Translucent grey/blue shaded disc
            drawCircle(
                color = Color(0x667B8FA8),
                radius = reticleRadius,
                center = Offset(insX, insY)
            )

            // Blue outer target ring
            val reticleBlue = Color(0xFF1E88E5)
            drawCircle(
                color = reticleBlue,
                radius = reticleRadius,
                center = Offset(insX, insY),
                style = Stroke(width = 2.8.dp.toPx())
            )

            // 4 Crosshair Tick Notches
            val tickLen = 7.dp.toPx()
            drawLine(
                color = reticleBlue,
                start = Offset(insX, insY - reticleRadius),
                end = Offset(insX, insY - reticleRadius + tickLen),
                strokeWidth = 2.5.dp.toPx()
            )
            drawLine(
                color = reticleBlue,
                start = Offset(insX, insY + reticleRadius - tickLen),
                end = Offset(insX, insY + reticleRadius),
                strokeWidth = 2.5.dp.toPx()
            )
            drawLine(
                color = reticleBlue,
                start = Offset(insX - reticleRadius, insY),
                end = Offset(insX - reticleRadius + tickLen, insY),
                strokeWidth = 2.5.dp.toPx()
            )
            drawLine(
                color = reticleBlue,
                start = Offset(insX + reticleRadius - tickLen, insY),
                end = Offset(insX + reticleRadius, insY),
                strokeWidth = 2.5.dp.toPx()
            )

            // Floating White Tooltip Bubble Above Reticle
            val bubbleW = 68.dp.toPx()
            val bubbleH = 40.dp.toPx()
            val bubbleX = (insX - bubbleW / 2f).coerceIn(4.dp.toPx(), w - bubbleW - 4.dp.toPx())
            val bubbleY = (insY - reticleRadius - bubbleH - 6.dp.toPx()).coerceAtLeast(4.dp.toPx())

            // White rounded rect with subtle border
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(bubbleX, bubbleY),
                size = Size(bubbleW, bubbleH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFB0BEC5),
                topLeft = Offset(bubbleX, bubbleY),
                size = Size(bubbleW, bubbleH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Downward pointer
            val bubbleTri = Path().apply {
                moveTo(insX - 4.dp.toPx(), bubbleY + bubbleH)
                lineTo(insX + 4.dp.toPx(), bubbleY + bubbleH)
                lineTo(insX, bubbleY + bubbleH + 4.dp.toPx())
                close()
            }
            drawPath(bubbleTri, color = Color.White, style = Fill)
            drawPath(bubbleTri, color = Color(0xFFB0BEC5), style = Stroke(width = 1.dp.toPx()))

            // Line 1: Height (e.g. 0.8 m or +0.0 m)
            val bubbleTextPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(25, 118, 210) // Blue
                textSize = 13.dp.toPx()
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val prefixIns = if (isMslMode && insHeight > 0) "+" else ""
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%s%.1f m", prefixIns, insHeight),
                bubbleX + bubbleW / 2f,
                bubbleY + 16.dp.toPx(),
                bubbleTextPaint
            )

            // Line 2: Time (e.g. 15:46)
            val totalMins = (inspectHourFraction * 60f).toInt()
            val insHr = (totalMins / 60).coerceIn(0, 23)
            val insMin = (totalMins % 60).coerceIn(0, 59)
            val timeText = String.format(Locale.US, "%02d:%02d", insHr, insMin)

            bubbleTextPaint.typeface = android.graphics.Typeface.DEFAULT
            drawContext.canvas.nativeCanvas.drawText(
                timeText,
                bubbleX + bubbleW / 2f,
                bubbleY + 32.dp.toPx(),
                bubbleTextPaint
            )
        }
    }
}

/**
 * Custom Moon Icon rendering accurate moon phase waxing/waning crescent and gibbous.
 */
@Composable
private fun MoonIcon(
    illuminationPct: Int,
    isWaxing: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(radius, radius)

        // Dark moon background
        drawCircle(
            color = Color(0xFF1E324A),
            radius = radius,
            center = center
        )

        // Illuminated moon disc
        val moonGlow = Color(0xFFE8ECEF)
        drawCircle(
            color = moonGlow,
            radius = radius - 1.dp.toPx(),
            center = center
        )

        // Shadow clip overlay to simulate crescent / gibbous
        val fraction = illuminationPct / 100f
        if (fraction < 0.95f) {
            val shadowPath = Path().apply {
                val shadowW = (1f - fraction) * radius * 2f
                if (isWaxing) {
                    moveTo(center.x, 0f)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - radius, 0f,
                            center.x + radius, radius * 2f
                        ),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = -180f,
                        forceMoveTo = false
                    )
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - radius + shadowW, 0f,
                            center.x + radius, radius * 2f
                        ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                } else {
                    moveTo(center.x, 0f)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - radius, 0f,
                            center.x + radius, radius * 2f
                        ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                }
                close()
            }
            drawPath(shadowPath, color = Color(0xFF1E324A))
        }

        // Outer rim ring
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
