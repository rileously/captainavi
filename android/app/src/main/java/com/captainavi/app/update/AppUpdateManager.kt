package com.captainavi.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class AppUpdateUiState {
    data object Idle : AppUpdateUiState()
    data object Checking : AppUpdateUiState()
    data class UpToDate(val currentVersion: String) : AppUpdateUiState()
    data class Available(val update: AvailableAppUpdate, val currentVersion: String) : AppUpdateUiState()
    data class Downloading(val update: AvailableAppUpdate, val progress: Float) : AppUpdateUiState()
    data class ReadyToInstall(val update: AvailableAppUpdate, val apkFile: File) : AppUpdateUiState()
    data class Error(val message: String) : AppUpdateUiState()
}

class AppUpdateManager(
    private val context: Context,
    private val client: GitHubReleaseUpdateClient = GitHubReleaseUpdateClient(),
) {
    private val _state = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private var lastCheckAtMs: Long = 0L

    fun currentVersionName(): String = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "0"
    } catch (_: Exception) {
        "0"
    }

    fun currentVersionCode(): Long = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (_: Exception) {
        0L
    }

    /**
     * @param force when false, skips network if a check ran recently (saves radio on deck).
     */
    suspend fun checkForUpdate(force: Boolean = true): AppUpdateUiState {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckAtMs < MIN_AUTO_CHECK_INTERVAL_MS) {
            return _state.value
        }
        _state.value = AppUpdateUiState.Checking
        val local = currentVersionName()
        val result = client.fetchLatestRelease()
        lastCheckAtMs = now
        val next = result.fold(
            onSuccess = { remote ->
                if (AppVersionCompare.isNewer(remote.versionName, local)) {
                    AppUpdateUiState.Available(remote, local)
                } else {
                    AppUpdateUiState.UpToDate(local)
                }
            },
            onFailure = { error ->
                AppUpdateUiState.Error(error.message ?: "Could not check for updates")
            },
        )
        _state.value = next
        return next
    }

    suspend fun downloadUpdate(update: AvailableAppUpdate): AppUpdateUiState {
        _state.value = AppUpdateUiState.Downloading(update, 0f)
        val dest = File(File(context.cacheDir, UPDATE_CACHE_DIR), update.apkFileName)
        val result = client.downloadApk(update.apkDownloadUrl, dest) { progress ->
            _state.value = AppUpdateUiState.Downloading(update, progress)
        }
        val next = result.fold(
            onSuccess = { file -> AppUpdateUiState.ReadyToInstall(update, file) },
            onFailure = { error -> AppUpdateUiState.Error(error.message ?: "Download failed") },
        )
        _state.value = next
        return next
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installPermissionSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun installApk(apkFile: File): Result<Unit> = try {
        if (!apkFile.exists()) {
            Result.failure(IllegalStateException("APK file missing"))
        } else if (!canInstallPackages()) {
            Result.failure(SecurityException("Install permission required"))
        } else {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        }
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        private const val UPDATE_CACHE_DIR = "updates"
        private const val MIN_AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }
}
