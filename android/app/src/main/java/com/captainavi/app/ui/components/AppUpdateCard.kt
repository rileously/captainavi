package com.captainavi.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.ui.theme.MarineTheme
import com.captainavi.app.update.AppUpdateUiState
import kotlinx.coroutines.launch

@Composable
fun AppUpdateCard(modifier: Modifier = Modifier) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()
    val state by app.appUpdateManager.state.collectAsState()
    val currentVersion = rememberCurrentVersion(app)

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val ready = state as? AppUpdateUiState.ReadyToInstall
        if (ready != null && app.appUpdateManager.canInstallPackages()) {
            app.appUpdateManager.installApk(ready.apkFile)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("App updates", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
            Text(
                text = "Installed $currentVersion · Checks GitHub Releases when online",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            when (val s = state) {
                is AppUpdateUiState.Idle -> {
                    Text("Tap check to look for a newer signed build.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                is AppUpdateUiState.Checking -> {
                    Text("Checking GitHub…", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.accent)
                }
                is AppUpdateUiState.UpToDate -> {
                    Text("You're on the latest release (${s.currentVersion}).", style = MaterialTheme.typography.bodyMedium, color = colors.success)
                }
                is AppUpdateUiState.Available -> {
                    Text(
                        text = "Update available: ${s.update.versionName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.caution,
                    )
                    if (s.update.releaseNotes.isNotBlank()) {
                        Text(
                            text = s.update.releaseNotes.lines().take(6).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                is AppUpdateUiState.Downloading -> {
                    Text(
                        text = "Downloading ${s.update.versionName}… ${(s.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    LinearProgressIndicator(
                        progress = s.progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accent,
                    )
                }
                is AppUpdateUiState.ReadyToInstall -> {
                    Text(
                        text = "${s.update.versionName} downloaded — install to update.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.success,
                    )
                }
                is AppUpdateUiState.Error -> {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = colors.emergency)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch { app.appUpdateManager.checkForUpdate(force = true) }
                    },
                    enabled = state !is AppUpdateUiState.Checking && state !is AppUpdateUiState.Downloading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Check")
                }

                when (val s = state) {
                    is AppUpdateUiState.Available -> {
                        Button(
                            onClick = {
                                scope.launch { app.appUpdateManager.downloadUpdate(s.update) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                    is AppUpdateUiState.ReadyToInstall -> {
                        Button(
                            onClick = {
                                if (!app.appUpdateManager.canInstallPackages()) {
                                    installPermissionLauncher.launch(app.appUpdateManager.installPermissionSettingsIntent())
                                } else {
                                    val result = app.appUpdateManager.installApk(s.apkFile)
                                    if (result.isFailure) {
                                        // Re-surface as error state
                                        scope.launch {
                                            app.appUpdateManager.checkForUpdate(force = false)
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (app.appUpdateManager.canInstallPackages()) "Install" else "Allow install",
                            )
                        }
                    }
                    else -> {
                        Spacer(modifier = Modifier.weight(1f).height(1.dp))
                    }
                }
            }

            Text(
                text = "After you publish a new GitHub Release with an APK, boats can update from here over Wi‑Fi/mobile data.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun rememberCurrentVersion(app: CaptainAviApp): String {
    return androidx.compose.runtime.remember { app.appUpdateManager.currentVersionName() }
}
