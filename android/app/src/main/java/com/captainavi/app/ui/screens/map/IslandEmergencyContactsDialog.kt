package com.captainavi.app.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.captainavi.app.data.repository.EmergencyPhoneContact
import com.captainavi.app.data.repository.IslandEmergencyContacts
import com.captainavi.app.data.repository.IslandPlace
import com.captainavi.app.ui.theme.MarineTheme

@Composable
internal fun IslandEmergencyContactsDialog(
    island: IslandPlace,
    contacts: IslandEmergencyContacts?,
    snapshotDate: String,
    onDial: (String) -> Unit,
    onNavigate: ((IslandPlace) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = MarineTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(island.englishName, color = colors.accent)
                if (island.dhivehiName.isNotBlank()) {
                    Text(
                        island.dhivehiName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${island.atoll} · Emergency contacts",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    "Call opens your phone app with the number ready. The app never starts a call automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )

                EmergencyContactCard(
                    contact = contacts?.council,
                    missingLabel = "No council number is listed in the official directory.",
                    icon = Icons.Default.AccountBalance,
                    accent = colors.accent,
                    filledCallButton = false,
                    onDial = onDial,
                )
                EmergencyContactCard(
                    contact = contacts?.health,
                    missingLabel = "No island health-centre number is listed in the official directory.",
                    icon = Icons.Default.LocalHospital,
                    accent = colors.emergency,
                    filledCallButton = true,
                    onDial = onDial,
                )

                Text(
                    buildString {
                        append("Official directory snapshot")
                        if (snapshotDate.isNotBlank()) append(" · $snapshotDate")
                        append(" · available offline")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                Text(
                    "Numbers may change. Confirm when possible.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.caution,
                )
            }
        },
        confirmButton = {
            if (onNavigate != null) {
                Button(
                    onClick = {
                        onNavigate(island)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate", color = colors.onAccent)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = colors.accent)
                }
            }
        },
        dismissButton = if (onNavigate != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = colors.accent)
                }
            }
        } else null,
        containerColor = colors.surface,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun EmergencyContactCard(
    contact: EmergencyPhoneContact?,
    missingLabel: String,
    icon: ImageVector,
    accent: Color,
    filledCallButton: Boolean,
    onDial: (String) -> Unit,
) {
    val colors = MarineTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (contact == null) {
            Text(missingLabel, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            return@Column
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(contact.serviceLabel, style = MaterialTheme.typography.titleSmall, color = accent)
                Text(contact.organization, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                Text("Source: ${contact.sourceLabel}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }

        contact.phones.forEach { phone ->
            val modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Call ${contact.serviceLabel} at $phone" }
            if (filledCallButton) {
                Button(
                    onClick = { onDial(phone) },
                    modifier = modifier,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Text("  Call $phone", color = colors.onAccent)
                }
            } else {
                OutlinedButton(onClick = { onDial(phone) }, modifier = modifier) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = accent)
                    Text("  Call $phone", color = accent)
                }
            }
        }
    }
}
