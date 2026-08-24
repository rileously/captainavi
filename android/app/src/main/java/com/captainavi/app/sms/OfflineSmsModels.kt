package com.captainavi.app.sms

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class OfflineSmsPosition(
    val latitude: Double,
    val longitude: Double,
    val speedKnots: Double,
    val bearingDegrees: Float,
    val headingCardinal: String,
    val accuracyMeters: Float,
    val batteryPct: Int,
    val recordedAtMillis: Long,
)

object OfflineSmsRules {
    const val AUTO_REPLY_RATE_LIMIT_MILLIS = 5 * 60 * 1_000L
    const val MIN_REQUEST_PHRASE_LENGTH = 6
    const val MAX_SINGLE_SMS_CHARACTERS = 160

    fun matchesRequest(receivedBody: String, configuredPhrase: String): Boolean =
        configuredPhrase.trim().length >= MIN_REQUEST_PHRASE_LENGTH &&
            receivedBody.trim().equals(configuredPhrase.trim(), ignoreCase = true)

    fun normalizeDialableNumber(number: String): String {
        val trimmed = number.trim()
        val hasInternationalPrefix = trimmed.startsWith('+')
        val digits = trimmed.filter(Char::isDigit)
        return if (hasInternationalPrefix && digits.isNotEmpty()) "+$digits" else digits
    }

    fun isValidPhoneNumber(number: String): Boolean {
        val digits = normalizeDialableNumber(number).filter(Char::isDigit)
        return digits.length in 7..15
    }

    fun isRateLimited(lastReplyAtMillis: Long, nowMillis: Long): Boolean =
        lastReplyAtMillis > 0L && nowMillis - lastReplyAtMillis < AUTO_REPLY_RATE_LIMIT_MILLIS

    fun buildLocationMessage(
        captainName: String,
        position: OfflineSmsPosition,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val latitude = String.format(Locale.US, "%.6f", position.latitude)
        val longitude = String.format(Locale.US, "%.6f", position.longitude)
        val speed = String.format(Locale.US, "%.1f", position.speedKnots)
        val bearing = position.bearingDegrees.roundToInt().floorMod360()
        val gpsStatus = ageLabel(position.recordedAtMillis, nowMillis)
        val recordedTime = SimpleDateFormat("dd MMM HH:mm", Locale.US)
            .format(Date(position.recordedAtMillis))
        val safeCaptainName = captainName
            .ifBlank { "Unknown" }
            .map { character -> if (character.code in 32..126) character else '?' }
            .joinToString(separator = "")
            .take(16)
        return buildString {
            append("Captain Avi: ").append(safeCaptainName).append('\n')
            append("Position: ").append(latitude).append(", ").append(longitude).append('\n')
            append("https://maps.google.com/?q=").append(latitude).append(',').append(longitude).append('\n')
            append(speed).append("kt ").append(bearing).append("deg ")
                .append(position.headingCardinal).append(" Bat")
                .append(position.batteryPct.coerceIn(0, 100)).append("%\n")
            append(gpsStatus).append(' ').append(recordedTime)
        }.take(MAX_SINGLE_SMS_CHARACTERS)
    }

    fun buildLocationUnavailableMessage(captainName: String): String =
        "Captain Avi offline reply: GPS location is unavailable for ${captainName.ifBlank { "the captain" }}. " +
            "Ask the captain to open Captain Avi and enable GPS."

    private fun ageLabel(recordedAtMillis: Long, nowMillis: Long): String {
        val ageMinutes = ((nowMillis - recordedAtMillis).coerceAtLeast(0L) / 60_000L)
        return when {
            ageMinutes <= 2L -> "GPS CURRENT"
            ageMinutes < 60L -> "LAST KNOWN ${ageMinutes}m ago"
            ageMinutes < 24L * 60L -> "LAST KNOWN ${ageMinutes / 60L}h ago"
            else -> "LAST KNOWN ${ageMinutes / (24L * 60L)}d ago"
        }
    }

    private fun Int.floorMod360(): Int = ((this % 360) + 360) % 360
}
