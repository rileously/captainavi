package com.captainavi.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.remote.MarineConditionsClient
import com.captainavi.app.safety.StormAlertEvaluator
import com.captainavi.app.safety.StormAlertNotifier
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Periodically checks the marine forecast near the last known GPS fix and posts a local
 * notification if it crosses the rough-sea thresholds in Settings. Runs only while online;
 * skips quietly (retrying next cycle) if location or a permission isn't available yet.
 */
class StormAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CaptainAviApp
        val settings = app.settingsRepository
        if (!settings.stormAlertsEnabled.value) return Result.success()
        if (!app.networkMonitor.isCurrentlyOnline()) return Result.retry()

        val location = lastKnownLocation() ?: return Result.retry()

        return try {
            val conditions = MarineConditionsClient().fetch(location.first, location.second)
            val alert = StormAlertEvaluator.evaluate(
                conditions = conditions,
                waveHeightThresholdMeters = settings.stormWaveHeightThresholdMeters.value,
                windGustThresholdKnots = settings.stormWindGustThresholdKnots.value,
            )
            if (alert != null) {
                StormAlertNotifier.maybeNotify(applicationContext, alert)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun lastKnownLocation(): Pair<Double, Double>? {
        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val client = LocationServices.getFusedLocationProviderClient(applicationContext)
        return try {
            suspendCancellableCoroutine { continuation ->
                client.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { it.latitude to it.longitude })
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        } catch (e: SecurityException) {
            null
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK_NAME = "CaptainAviStormAlertWorker"
        private const val CHECK_INTERVAL_HOURS = 3L

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<StormAlertWorker>(
                CHECK_INTERVAL_HOURS, TimeUnit.HOURS,
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
