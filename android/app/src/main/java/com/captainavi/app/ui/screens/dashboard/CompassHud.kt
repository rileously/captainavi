package com.captainavi.app.ui.screens.dashboard

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.captainavi.app.localization.LanguageManager
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.service.NavigationDestination
import com.captainavi.app.ui.theme.MarineTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassHud(
    currentHeadingDegrees: Float,
    headingCardinal: String,
    bearingToHomeDegrees: Double,
    distanceToHomeNm: Double,
    speedKnots: Double,
    activeDestination: NavigationDestination? = null,
    bearingToDestDegrees: Double = 0.0,
    distToDestNm: Double = 0.0,
    etaMinutes: Double = -1.0,
    vmgKnots: Double = 0.0,
    crossTrackErrorMeters: Double = 0.0,
    cogDegrees: Float = currentHeadingDegrees,
    headingSource: String = "GPS",
    onClearDestination: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MarineTheme.colors
    val hasDestination = activeDestination != null
    val isAtHome = distanceToHomeNm < 0.15 || distanceToHomeNm == 0.0

    val destColor = when {
        activeDestination?.isMob == true -> colors.mob
        else -> colors.destination
    }
    val homeColor = colors.home

    var smoothHeading by remember { mutableFloatStateOf(currentHeadingDegrees) }
    LaunchedEffect(currentHeadingDegrees) {
        val delta = ((currentHeadingDegrees - smoothHeading + 540f) % 360f) - 180f
        smoothHeading += delta
    }
    val animatedHeading by animateFloatAsState(
        targetValue = smoothHeading,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "heading_anim"
    )

    // Destination relative pointer
    val relBearingDest = ((bearingToDestDegrees - currentHeadingDegrees + 360) % 360).toFloat()
    var smoothBearingDest by remember { mutableFloatStateOf(relBearingDest) }
    LaunchedEffect(relBearingDest) {
        val delta = ((relBearingDest - smoothBearingDest + 540f) % 360f) - 180f
        smoothBearingDest += delta
    }
    val animatedPointerDest by animateFloatAsState(
        targetValue = smoothBearingDest,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "bearing_dest_anim"
    )

    // Home relative pointer
    val relBearingHome = ((bearingToHomeDegrees - currentHeadingDegrees + 360) % 360).toFloat()
    var smoothBearingHome by remember { mutableFloatStateOf(relBearingHome) }
    LaunchedEffect(relBearingHome) {
        val delta = ((relBearingHome - smoothBearingHome + 540f) % 360f) - 180f
        smoothBearingHome += delta
    }
    val animatedPointerHome by animateFloatAsState(
        targetValue = smoothBearingHome,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "bearing_home_anim"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val headerTint = when {
            hasDestination -> destColor
            isAtHome -> colors.accent
            else -> homeColor
        }

        // Top Navigation Header Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerTint.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .border(1.dp, headerTint.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(headerTint.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            hasDestination -> Icons.Default.Place
                            isAtHome -> Icons.Default.Anchor
                            else -> Icons.Default.Home
                        },
                        contentDescription = null,
                        tint = headerTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when {
                            activeDestination?.isMob == true -> "🚨 MOB RESCUE"
                            hasDestination -> "TARGET DESTINATION"
                            isAtHome -> "HARBOUR STATION"
                            else -> LanguageManager.returnToHome
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = headerTint
                    )
                    Text(
                        text = when {
                            hasDestination -> activeDestination?.name ?: ""
                            isAtHome -> LanguageManager.inHomeHarbour
                            else -> "${String.format(java.util.Locale.US, "%.1f", distanceToHomeNm)} NM to Harbour"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    if (hasDestination) {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", distToDestNm)} NM · XTE ${formatCrossTrackError(crossTrackErrorMeters)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    } else if (isAtHome) {
                        Text(
                            text = "Ready for voyage · Vessel at dock",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            hasDestination -> "ETA"
                            isAtHome -> "STATUS"
                            else -> LanguageManager.steerHeading
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.caution
                    )
                    Text(
                        text = when {
                            hasDestination -> NauticalMath.formatEta(etaMinutes)
                            isAtHome -> "PORT"
                            else -> "${bearingToHomeDegrees.toInt()}°"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.caution
                    )
                }
                if (hasDestination && onClearDestination != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onClearDestination,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Mark", tint = colors.textSecondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val ringColor = colors.border
        val dialFill = colors.background
        val accentArgb = colors.accent.toArgb()
        val secondaryArgb = colors.textSecondary.toArgb()
        val lubberColor = colors.accent

        Box(modifier = Modifier.size(208.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(208.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2 - 10.dp.toPx()

                drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
                drawCircle(color = dialFill, radius = radius - 6.dp.toPx())

                val native = drawContext.canvas.nativeCanvas
                val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 13.dp.toPx()
                }

                // Dial ticks and cardinals
                for (angle in 0 until 360 step 30) {
                    val rad = Math.toRadians((angle - animatedHeading).toDouble())
                    val isCardinal = angle % 90 == 0
                    val tickLen = if (isCardinal) 16.dp.toPx() else 8.dp.toPx()
                    val tickColor = when {
                        angle == 0 -> colors.accent
                        isCardinal -> colors.textSecondary
                        else -> colors.textMuted
                    }
                    drawLine(
                        color = tickColor,
                        start = Offset(
                            center.x + (radius - tickLen) * sin(rad).toFloat(),
                            center.y - (radius - tickLen) * cos(rad).toFloat()
                        ),
                        end = Offset(
                            center.x + radius * sin(rad).toFloat(),
                            center.y - radius * cos(rad).toFloat()
                        ),
                        strokeWidth = if (isCardinal) 3.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val labelR = radius - 28.dp.toPx()
                    val lx = center.x + labelR * sin(rad).toFloat()
                    val ly = center.y - labelR * cos(rad).toFloat()
                    when (angle) {
                        0 -> {
                            cardinalPaint.color = accentArgb
                            native.drawText("N", lx, ly + 4.dp.toPx(), cardinalPaint)
                        }
                        90 -> {
                            cardinalPaint.color = secondaryArgb
                            native.drawText("E", lx, ly + 4.dp.toPx(), cardinalPaint)
                        }
                        180 -> {
                            cardinalPaint.color = secondaryArgb
                            native.drawText("S", lx, ly + 4.dp.toPx(), cardinalPaint)
                        }
                        270 -> {
                            cardinalPaint.color = secondaryArgb
                            native.drawText("W", lx, ly + 4.dp.toPx(), cardinalPaint)
                        }
                    }
                }

                // Outer Bezel Bug for Home Harbour (Green dot) — only when NOT at home
                if (!isAtHome) {
                    val homeRad = Math.toRadians(animatedPointerHome.toDouble())
                    val homeBugX = center.x + (radius - 2.dp.toPx()) * sin(homeRad).toFloat()
                    val homeBugY = center.y - (radius - 2.dp.toPx()) * cos(homeRad).toFloat()
                    drawCircle(color = homeColor, radius = 5.dp.toPx(), center = Offset(homeBugX, homeBugY))
                    drawCircle(color = colors.background, radius = 2.5.dp.toPx(), center = Offset(homeBugX, homeBugY))
                }

                // If destination is active: draw Destination Outer Bug and Needle
                if (hasDestination) {
                    val destRad = Math.toRadians(animatedPointerDest.toDouble())
                    val destBugX = center.x + (radius - 2.dp.toPx()) * sin(destRad).toFloat()
                    val destBugY = center.y - (radius - 2.dp.toPx()) * cos(destRad).toFloat()
                    drawCircle(color = destColor, radius = 6.5.dp.toPx(), center = Offset(destBugX, destBugY))
                    drawCircle(color = colors.background, radius = 3.dp.toPx(), center = Offset(destBugX, destBugY))

                    // Secondary Home pointer line (subtle green chevron) — only if out at sea
                    if (!isAtHome) {
                        rotate(animatedPointerHome, pivot = center) {
                            val homeArrow = Path().apply {
                                moveTo(center.x, center.y - radius + 14.dp.toPx())
                                lineTo(center.x + 6.dp.toPx(), center.y - radius + 28.dp.toPx())
                                lineTo(center.x, center.y - radius + 22.dp.toPx())
                                lineTo(center.x - 6.dp.toPx(), center.y - radius + 28.dp.toPx())
                                close()
                            }
                            drawPath(homeArrow, homeColor)
                        }
                    }

                    // Primary Destination Needle
                    rotate(animatedPointerDest, pivot = center) {
                        val needle = Path().apply {
                            moveTo(center.x, center.y - radius + 22.dp.toPx())
                            lineTo(center.x + 10.dp.toPx(), center.y + 8.dp.toPx())
                            lineTo(center.x, center.y - 6.dp.toPx())
                            lineTo(center.x - 10.dp.toPx(), center.y + 8.dp.toPx())
                            close()
                        }
                        drawPath(needle, destColor)
                    }
                } else if (!isAtHome) {
                    // Primary Home Needle — only shown when at sea
                    rotate(animatedPointerHome, pivot = center) {
                        val needle = Path().apply {
                            moveTo(center.x, center.y - radius + 22.dp.toPx())
                            lineTo(center.x + 10.dp.toPx(), center.y + 8.dp.toPx())
                            lineTo(center.x, center.y - 6.dp.toPx())
                            lineTo(center.x - 10.dp.toPx(), center.y + 8.dp.toPx())
                            close()
                        }
                        drawPath(needle, homeColor)
                    }
                }

                // Fixed lubber line at 12 o'clock (Boat's Bow)
                val lubber = Path().apply {
                    moveTo(center.x, 4.dp.toPx())
                    lineTo(center.x - 7.dp.toPx(), 18.dp.toPx())
                    lineTo(center.x + 7.dp.toPx(), 18.dp.toPx())
                    close()
                }
                drawPath(lubber, lubberColor)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", speedKnots),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.accent
                )
                Text(
                    text = LanguageManager.knots,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, RoundedCornerShape(8.dp))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavDataCell("SOG", "${String.format(java.util.Locale.US, "%.1f", speedKnots)} kt")
            if (headingSource == "COMPASS") {
                NavDataCell("HDG", "${currentHeadingDegrees.toInt()}° $headingCardinal")
                NavDataCell("COG", "${cogDegrees.toInt()}°")
            } else {
                NavDataCell("COG", "${currentHeadingDegrees.toInt()}° $headingCardinal")
            }
            if (hasDestination) {
                NavDataCell("DTW", "${String.format(java.util.Locale.US, "%.1f", distToDestNm)} NM")
                NavDataCell("BTW", "${bearingToDestDegrees.toInt()}°")
                NavDataCell("VMG", "${String.format(java.util.Locale.US, "%.1f", vmgKnots)} kt")
                NavDataCell("ETA", NauticalMath.formatEta(etaMinutes))
            } else if (isAtHome) {
                NavDataCell("PORT", "DOCKED")
                NavDataCell("SOURCE", headingSource)
            } else {
                NavDataCell("DTW", "${String.format(java.util.Locale.US, "%.1f", distanceToHomeNm)} NM")
                NavDataCell("BTW", "${bearingToHomeDegrees.toInt()}°")
                NavDataCell("SOURCE", headingSource)
            }
        }
    }
}

@Composable
private fun NavDataCell(label: String, value: String) {
    val colors = MarineTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        Text(value, style = MaterialTheme.typography.labelLarge, color = colors.accent)
    }
}

private fun formatCrossTrackError(errorMeters: Double): String {
    val side = when {
        errorMeters > 1.0 -> "R"
        errorMeters < -1.0 -> "L"
        else -> ""
    }
    return "${kotlin.math.abs(errorMeters).toInt()}m$side"
}
