package com.captainavi.app.safety

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.captainavi.app.MainActivity
import com.captainavi.app.R
import java.util.Locale

/**
 * Posts a local notification when [StormAlertEvaluator] finds an upcoming rough-sea
 * forecast — a high-priority alert once a threshold is actually crossed
 * ([StormAlertSeverity.WARNING]), or a lower-key heads-up while it's still just trending
 * that way ([StormAlertSeverity.WATCH]). Dedupes so the same forecast reading doesn't
 * re-notify every worker cycle.
 */
object StormAlertNotifier {
    private const val WARNING_CHANNEL_ID = "storm_alerts"
    private const val WATCH_CHANNEL_ID = "storm_alerts_watch"
    private const val NOTIFICATION_ID = 9001
    private const val PREFS_NAME = "storm_alert_state"
    private const val KEY_LAST_SIGNATURE = "last_alert_signature"
    private const val KEY_LAST_ALERT_MILLIS = "last_alert_millis"

    /** Don't repeat an identical alert inside this window, even if the worker reruns sooner. */
    private const val MIN_REPEAT_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L

    fun maybeNotify(context: Context, alert: StormAlert) {
        if (!hasNotificationPermission(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val signature =
            "${alert.severity}:${alert.kind}:${"%.1f".format(Locale.US, alert.peakValue)}:${alert.peakTimeIso}"
        val now = System.currentTimeMillis()
        val isRepeat = signature == prefs.getString(KEY_LAST_SIGNATURE, null) &&
            now - prefs.getLong(KEY_LAST_ALERT_MILLIS, 0L) < MIN_REPEAT_INTERVAL_MILLIS
        if (isRepeat) return

        ensureChannels(context)
        val (title, body) = describe(alert)
        val channelId = if (alert.severity == StormAlertSeverity.WARNING) WARNING_CHANNEL_ID else WATCH_CHANNEL_ID
        val priority = if (alert.severity == StormAlertSeverity.WARNING) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Same notification ID regardless of severity: a fresh WARNING should replace a
        // stale WATCH for the same situation (and vice versa), not stack alongside it.
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)

        prefs.edit()
            .putString(KEY_LAST_SIGNATURE, signature)
            .putLong(KEY_LAST_ALERT_MILLIS, now)
            .apply()
    }

    private fun describe(alert: StormAlert): Pair<String, String> {
        val valueText = "%.1f".format(Locale.US, alert.peakValue)
        val timeText = alert.peakTimeIso?.substringAfter('T')?.take(5)
        val whenText = if (timeText != null) " around $timeText" else " now"

        if (alert.severity == StormAlertSeverity.WATCH) {
            val startText = alert.startValue?.let { "%.1f".format(Locale.US, it) }
            val trendText = if (startText != null) "rising from $startText to $valueText" else "building toward $valueText"
            return when (alert.kind) {
                StormAlertKind.WAVE -> "👀 Seas building" to
                    "Waves $trendText m$whenText near your last position — worth watching before you commit to a trip."
                StormAlertKind.SWELL -> "👀 Swell building" to
                    "Swell $trendText m$whenText near your last position — worth watching before you commit to a trip."
                StormAlertKind.WIND_GUST -> "👀 Wind picking up" to
                    "Gusts $trendText kt$whenText near your last position — worth watching before you commit to a trip."
            }
        }

        return when (alert.kind) {
            StormAlertKind.WAVE -> "⚠️ Rough seas forecast" to
                "Waves up to $valueText m$whenText near your last position — check conditions before heading out."
            StormAlertKind.SWELL -> "⚠️ Heavy swell forecast" to
                "Swell up to $valueText m$whenText near your last position — check conditions before heading out."
            StormAlertKind.WIND_GUST -> "⚠️ Strong wind gusts forecast" to
                "Gusts up to $valueText kt$whenText near your last position — check conditions before heading out."
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(WARNING_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    WARNING_CHANNEL_ID,
                    "Storm & high-wave alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Warns when the marine forecast crosses your rough-sea thresholds."
                },
            )
        }
        if (manager.getNotificationChannel(WATCH_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    WATCH_CHANNEL_ID,
                    "Seas building (early watch)",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "A lower-key heads-up when the forecast is trending toward your rough-sea thresholds, before it crosses them."
                },
            )
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
