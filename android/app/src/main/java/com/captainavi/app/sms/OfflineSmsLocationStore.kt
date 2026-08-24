package com.captainavi.app.sms

import android.content.Context

class OfflineSmsLocationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(position: OfflineSmsPosition) {
        prefs.edit()
            .putLong(KEY_LATITUDE, position.latitude.toRawBits())
            .putLong(KEY_LONGITUDE, position.longitude.toRawBits())
            .putLong(KEY_SPEED_KNOTS, position.speedKnots.toRawBits())
            .putFloat(KEY_BEARING_DEGREES, position.bearingDegrees)
            .putString(KEY_HEADING_CARDINAL, position.headingCardinal)
            .putFloat(KEY_ACCURACY_METERS, position.accuracyMeters)
            .putInt(KEY_BATTERY_PCT, position.batteryPct)
            .putLong(KEY_RECORDED_AT, position.recordedAtMillis)
            .apply()
    }

    fun load(): OfflineSmsPosition? {
        val recordedAt = prefs.getLong(KEY_RECORDED_AT, 0L)
        if (recordedAt <= 0L || !prefs.contains(KEY_LATITUDE) || !prefs.contains(KEY_LONGITUDE)) {
            return null
        }
        return OfflineSmsPosition(
            latitude = Double.fromBits(prefs.getLong(KEY_LATITUDE, 0L)),
            longitude = Double.fromBits(prefs.getLong(KEY_LONGITUDE, 0L)),
            speedKnots = Double.fromBits(prefs.getLong(KEY_SPEED_KNOTS, 0L)),
            bearingDegrees = prefs.getFloat(KEY_BEARING_DEGREES, 0f),
            headingCardinal = prefs.getString(KEY_HEADING_CARDINAL, "N") ?: "N",
            accuracyMeters = prefs.getFloat(KEY_ACCURACY_METERS, 0f),
            batteryPct = prefs.getInt(KEY_BATTERY_PCT, 100),
            recordedAtMillis = recordedAt,
        )
    }

    @Synchronized
    fun reserveAutomaticReply(nowMillis: Long): Boolean {
        val lastReply = prefs.getLong(KEY_LAST_AUTO_REPLY_AT, 0L)
        if (OfflineSmsRules.isRateLimited(lastReply, nowMillis)) return false
        return prefs.edit().putLong(KEY_LAST_AUTO_REPLY_AT, nowMillis).commit()
    }

    @Synchronized
    fun releaseAutomaticReply(reservedAtMillis: Long) {
        if (prefs.getLong(KEY_LAST_AUTO_REPLY_AT, 0L) == reservedAtMillis) {
            prefs.edit().remove(KEY_LAST_AUTO_REPLY_AT).commit()
        }
    }

    companion object {
        private const val PREFS_NAME = "captain_avi_offline_sms_state"
        private const val KEY_LATITUDE = "latitude_bits"
        private const val KEY_LONGITUDE = "longitude_bits"
        private const val KEY_SPEED_KNOTS = "speed_knots_bits"
        private const val KEY_BEARING_DEGREES = "bearing_degrees"
        private const val KEY_HEADING_CARDINAL = "heading_cardinal"
        private const val KEY_ACCURACY_METERS = "accuracy_meters"
        private const val KEY_BATTERY_PCT = "battery_pct"
        private const val KEY_RECORDED_AT = "recorded_at"
        private const val KEY_LAST_AUTO_REPLY_AT = "last_auto_reply_at"
    }
}
