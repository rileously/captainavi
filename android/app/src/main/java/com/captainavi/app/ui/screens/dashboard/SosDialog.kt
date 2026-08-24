package com.captainavi.app.ui.screens.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.captainavi.app.ui.theme.MarineTheme
import kotlinx.coroutines.delay

private const val SOS_HOLD_DURATION_MS = 2000

@Composable
fun SosDialog(
    isSosActive: Boolean,
    isOnline: Boolean,
    onConfirmSos: () -> Unit,
    onCancelSos: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MarineTheme.colors

    // A slow, urgent pulse on the icon ring and dialog border — this dialog can arm a
    // real emergency beacon, so it should never look like just another confirm sheet.
    val pulseTransition = rememberInfiniteTransition(label = "sosPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sosPulseAlpha",
    )
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sosPulseScale",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .background(colors.background, RoundedCornerShape(16.dp))
                .border(1.dp, colors.emergency.copy(alpha = pulseAlpha), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(pulseScale)
                            .background(colors.emergency.copy(alpha = 0.2f), CircleShape)
                            .border(2.dp, colors.emergency.copy(alpha = pulseAlpha), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = colors.emergency,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isSosActive) "Emergency beacon active" else "Activate emergency SOS",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.emergency
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            isSosActive && isOnline ->
                                "The siren is sounding and the GPS distress alert is queued for immediate delivery."
                            isSosActive ->
                                "The siren is sounding. The GPS distress alert is saved and will transmit when a connection returns."
                            isOnline ->
                                "Hold the button below for 2 seconds to sound the alarm and queue a high-priority GPS distress alert for your family group."
                            else ->
                                "You are offline. Hold the button below for 2 seconds to sound the alarm and save the GPS distress alert for automatic delivery after reconnection."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.card, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Safety protocol",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.emergency
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "If you are outside mobile range, switch to VHF Channel 16 or activate a PLB/EPIRB. Keep a life jacket on. Captain Avi will transmit coordinates when a signal returns.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isSosActive) {
                        HoldToConfirmSosButton(
                            onConfirmed = {
                                onConfirmSos()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        )
                    } else {
                        Button(
                            onClick = {
                                onCancelSos()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.emergencyDark,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Silence and cancel SOS", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
                    ) {
                        Text("Close", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * Arming a real emergency beacon shouldn't fire off a single accidental tap. Requires
 * a continuous 2-second press; releasing early resets the progress to zero. A growing
 * fill communicates progress, and a long-press haptic confirms the moment it fires.
 */
@Composable
private fun HoldToConfirmSosButton(
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var progress by remember { mutableFloatStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed && !confirmed) {
            val stepMs = 16L
            while (isPressed && progress < 1f) {
                delay(stepMs)
                progress = (progress + stepMs / SOS_HOLD_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            }
            if (progress >= 1f && !confirmed) {
                confirmed = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirmed()
            }
        } else if (!isPressed && !confirmed) {
            progress = 0f
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.emergency.copy(alpha = 0.35f))
            .border(1.dp, colors.emergency, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(colors.emergency),
        )
        Text(
            text = if (progress > 0.02f) "Keep holding… ${(progress * 100).toInt()}%" else "Hold to confirm distress SOS",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
    }
}
