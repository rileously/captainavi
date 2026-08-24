package com.captainavi.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.captainavi.app.ui.theme.MarineTheme

@Composable
fun SosDialog(
    isSosActive: Boolean,
    isOnline: Boolean,
    onConfirmSos: () -> Unit,
    onCancelSos: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MarineTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .background(colors.background, RoundedCornerShape(16.dp))
                .border(1.dp, if (isSosActive) colors.emergency else colors.border, RoundedCornerShape(16.dp))
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
                            .background(colors.emergency.copy(alpha = 0.2f), CircleShape)
                            .border(2.dp, colors.emergency, CircleShape),
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
                                "Confirm to sound the alarm and queue a high-priority GPS distress alert for your family group."
                            else ->
                                "You are offline. Confirm to sound the alarm and save the GPS distress alert for automatic delivery after reconnection."
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
                        Button(
                            onClick = {
                                onConfirmSos()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.emergency,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Confirm distress SOS", style = MaterialTheme.typography.titleMedium)
                        }
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
