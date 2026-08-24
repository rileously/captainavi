package com.captainavi.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale

@Serializable
data class TelegramInlineKeyboardButton(
    val text: String,
    val url: String,
)

@Serializable
data class TelegramInlineKeyboardMarkup(
    val inline_keyboard: List<List<TelegramInlineKeyboardButton>>,
)

@Serializable
data class TelegramLinkPreviewOptions(
    val is_disabled: Boolean = true,
)

@Serializable
data class TelegramSendMessageRequest(
    val chat_id: String,
    val text: String,
    val parse_mode: String = "HTML",
    val link_preview_options: TelegramLinkPreviewOptions = TelegramLinkPreviewOptions(),
    val reply_markup: TelegramInlineKeyboardMarkup? = null,
)

@Serializable
private data class TelegramEditMessageRequest(
    val chat_id: String,
    val message_id: Long,
    val text: String,
    val parse_mode: String = "HTML",
    val link_preview_options: TelegramLinkPreviewOptions = TelegramLinkPreviewOptions(),
    val reply_markup: TelegramInlineKeyboardMarkup? = null,
)

@Serializable
private data class TelegramMessageResult(
    val message_id: Long,
)

@Serializable
private data class TelegramApiResponse(
    val ok: Boolean = false,
    val result: TelegramMessageResult? = null,
    val description: String? = null,
)

class RelayApiClient(
    context: Context,
    private var baseUrl: String = "",
    private var apiSecretKey: String = "",
    private var directBotToken: String = "",
    private var directChatId: String = "",
) {
    private val statusPrefs: SharedPreferences =
        context.getSharedPreferences("captain_avi_telegram_status", Context.MODE_PRIVATE)
    private val wireJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(wireJson) }
        engine {
            config {
                connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    fun updateConfig(url: String, secretKey: String, botToken: String, chatId: String) {
        baseUrl = url.trimEnd('/')
        apiSecretKey = secretKey
        directBotToken = botToken.trim()
        directChatId = chatId.trim()
    }

    private val directConfigured: Boolean
        get() = directBotToken.isNotBlank() && directChatId.isNotBlank()

    private val relayConfigured: Boolean
        get() = baseUrl.startsWith("http")

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))

    private fun mapsUrl(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            "${String.format(Locale.US, "%.5f", latitude)},${String.format(Locale.US, "%.5f", longitude)}"

    private fun directionsUrl(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/dir/?api=1&destination=" +
            "${String.format(Locale.US, "%.5f", latitude)},${String.format(Locale.US, "%.5f", longitude)}"

    private fun sharePositionUrl(latitude: Double, longitude: Double): String {
        val encodedMapUrl = URLEncoder.encode(
            mapsUrl(latitude, longitude),
            StandardCharsets.UTF_8.name(),
        )
        val encodedText = URLEncoder.encode(
            "Current boat position",
            StandardCharsets.UTF_8.name(),
        )
        return "https://t.me/share/url?url=$encodedMapUrl&text=$encodedText"
    }

    private fun locationKeyboard(latitude: Double, longitude: Double) = TelegramInlineKeyboardMarkup(
        inline_keyboard = listOf(
            listOf(
                TelegramInlineKeyboardButton("🗺 Open map", mapsUrl(latitude, longitude)),
                TelegramInlineKeyboardButton("🧭 Directions", directionsUrl(latitude, longitude)),
            ),
            listOf(
                TelegramInlineKeyboardButton("📤 Share position", sharePositionUrl(latitude, longitude)),
            ),
        )
    )

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun captainName(value: String?): String = escapeHtml(value ?: "Father's Boat")

    private fun statusPreferenceKey(tripId: String): String {
        val channelKey = if (directConfigured) {
            "direct_${directChatId.hashCode()}"
        } else {
            "relay_${baseUrl.hashCode()}"
        }
        return "status_${channelKey}_$tripId"
    }

    private fun completionPreferenceKey(tripId: String): String =
        "completion_${statusPreferenceKey(tripId)}"

    private fun storedStatusMessageId(tripId: String): Long? {
        val key = statusPreferenceKey(tripId)
        return if (statusPrefs.contains(key)) statusPrefs.getLong(key, 0L).takeIf { it > 0L } else null
    }

    private fun storeStatusMessageId(tripId: String, messageId: Long) {
        statusPrefs.edit().putLong(statusPreferenceKey(tripId), messageId).apply()
    }

    suspend fun sendDirectTelegram(text: String): Result<String> {
        if (!directConfigured) {
            return Result.failure(Exception("Telegram Bot Token or Chat ID is empty in Settings."))
        }
        return sendNewDirectMessage(text, replyMarkup = null).map {
            "Message delivered to Telegram group!"
        }
    }

    suspend fun sendTripStart(request: StartTripRequest): Result<Unit> {
        val text = """
            🟢 <b>TRIP ACTIVE</b>
            👤 <b>Captain:</b> ${captainName(request.captainName)}
            📍 <b>Departure:</b> <code>${String.format(Locale.US, "%.5f, %.5f", request.latitude, request.longitude)}</code>
            🔋 <b>Battery:</b> ${request.batteryPct}%
            🕒 <b>Started:</b> ${formatTime(request.timestamp)}
            ℹ️ This card will refresh in place. Safety alerts remain separate.
        """.trimIndent()
        val keyboard = locationKeyboard(request.latitude, request.longitude)

        return when {
            directConfigured -> upsertDirectStatus(request.tripId, text, keyboard)
            relayConfigured -> safeStatusPost(
                "$baseUrl/api/trip/start",
                request.copy(statusMessageId = storedStatusMessageId(request.tripId)),
            ).storeMessageIdFor(request.tripId)
            else -> missingDeliveryConfiguration()
        }
    }

    suspend fun sendLocationUpdate(request: LocationUpdateRequest): Result<Unit> {
        val distanceText = request.distanceFromHomeNm?.let {
            "\n🏠 <b>From home:</b> ${String.format(Locale.US, "%.1f", it)} NM"
        }.orEmpty()
        val text = """
            🟢 <b>LIVE TRIP STATUS</b>
            👤 <b>${captainName(request.captainName)}</b>
            📍 <b>Position:</b> <code>${String.format(Locale.US, "%.5f, %.5f", request.latitude, request.longitude)}</code>
            ⚡ <b>Speed:</b> ${String.format(Locale.US, "%.1f", request.speedKnots)} kt
            🧭 <b>Heading:</b> ${escapeHtml(request.headingCardinal)} (${request.headingDegrees.toInt()}°)
            🔋 <b>Battery:</b> ${request.batteryPct}%$distanceText
            🕒 <b>Updated:</b> ${formatTime(request.timestamp)}
        """.trimIndent()
        val keyboard = locationKeyboard(request.latitude, request.longitude)

        return when {
            directConfigured -> upsertDirectStatus(request.tripId, text, keyboard)
            relayConfigured -> safeStatusPost(
                "$baseUrl/api/trip/update",
                request.copy(statusMessageId = storedStatusMessageId(request.tripId)),
            ).storeMessageIdFor(request.tripId)
            else -> missingDeliveryConfiguration()
        }
    }

    suspend fun sendAlert(request: AlertRequest): Result<Unit> {
        val icon = if (request.alertType == "SOS") "🚨🆘🚨" else "⚠️"
        val text = """
            $icon <b>MARINE ALERT: ${escapeHtml(request.alertType)}</b>
            👤 <b>Captain:</b> ${captainName(request.captainName)}
            ℹ️ <b>Detail:</b> ${escapeHtml(request.message ?: "Emergency beacon")}
            📍 <b>Position:</b> <code>${String.format(Locale.US, "%.5f, %.5f", request.latitude, request.longitude)}</code>
            🔋 <b>Battery:</b> ${request.batteryPct}%
            🕒 <b>Time:</b> ${formatTime(request.timestamp)}
        """.trimIndent()

        return when {
            directConfigured -> sendNewDirectMessage(
                text,
                locationKeyboard(request.latitude, request.longitude),
            ).map { Unit }
            relayConfigured -> safePost("$baseUrl/api/trip/alert", request)
            else -> missingDeliveryConfiguration()
        }
    }

    suspend fun sendBatchSync(request: BatchSyncRequest): Result<Unit> {
        if (directConfigured) {
            val latest = request.locations.lastOrNull()
            if (latest != null) {
                val syncLine = if (request.queuedCount > 1) {
                    "\n📶 <b>Synced:</b> ${request.queuedCount} stored positions"
                } else {
                    ""
                }
                val text = """
                    🟢 <b>LIVE TRIP STATUS</b>
                    👤 <b>${captainName(request.captainName)}</b>
                    📍 <b>Position:</b> <code>${String.format(Locale.US, "%.5f, %.5f", latest.latitude, latest.longitude)}</code>
                    ⚡ <b>Speed:</b> ${String.format(Locale.US, "%.1f", latest.speedKnots)} kt
                    🔋 <b>Battery:</b> ${latest.batteryPct}%$syncLine
                    🕒 <b>Updated:</b> ${formatTime(latest.timestamp)}
                """.trimIndent()
                val updateResult = upsertDirectStatus(
                    request.tripId,
                    text,
                    locationKeyboard(latest.latitude, latest.longitude),
                )
                if (updateResult.isFailure) return updateResult
            }

            if (request.alerts.isNotEmpty()) {
                val latestAlert = request.alerts.maxByOrNull { it.timestamp }!!
                val alertText = """
                    ⚠️ <b>OFFLINE SAFETY EVENTS SYNCED</b>
                    👤 <b>${captainName(request.captainName)}</b>
                    📋 <b>Events:</b> ${request.alerts.size}
                    🚨 <b>Latest:</b> ${escapeHtml(latestAlert.alertType)}
                    ℹ️ ${escapeHtml(latestAlert.message ?: "Safety event")}
                    🕒 <b>Recorded:</b> ${formatTime(latestAlert.timestamp)}
                """.trimIndent()
                return sendNewDirectMessage(
                    alertText,
                    locationKeyboard(latestAlert.latitude, latestAlert.longitude),
                ).map { Unit }
            }
            return Result.success(Unit)
        }

        if (relayConfigured) {
            return safeStatusPost(
                "$baseUrl/api/trip/batch-sync",
                request.copy(statusMessageId = storedStatusMessageId(request.tripId)),
            ).storeMessageIdFor(request.tripId)
        }
        return missingDeliveryConfiguration()
    }

    suspend fun sendTripEnd(request: EndTripRequest): Result<Unit> {
        val durationMinutes = ((request.endTime - request.startTime) / 60_000L).coerceAtLeast(1L)
        val completedText = """
            ✅ <b>TRIP COMPLETED</b>
            👤 <b>Captain:</b> ${captainName(request.captainName)}
            ⏱️ <b>Duration:</b> $durationMinutes min
            📏 <b>Travelled:</b> ${String.format(Locale.US, "%.2f", request.totalDistanceNm)} NM
            🚀 <b>Speed:</b> max ${String.format(Locale.US, "%.1f", request.maxSpeedKnots)} kt · avg ${String.format(Locale.US, "%.1f", request.avgSpeedKnots)} kt
            📍 <b>Final position:</b> <code>${String.format(Locale.US, "%.5f, %.5f", request.finalLatitude, request.finalLongitude)}</code>
            🕒 <b>Finished:</b> ${formatTime(request.endTime)}
        """.trimIndent()
        val keyboard = locationKeyboard(request.finalLatitude, request.finalLongitude)

        if (directConfigured) {
            val statusResult = upsertDirectStatus(request.tripId, completedText, keyboard)
            if (statusResult.isFailure) return statusResult

            val completionKey = completionPreferenceKey(request.tripId)
            if (!statusPrefs.getBoolean(completionKey, false)) {
                val notification = """
                    🏁 <b>ARRIVED SAFELY</b>
                    ${captainName(request.captainName)} completed the trip.
                    📏 ${String.format(Locale.US, "%.2f", request.totalDistanceNm)} NM · ⏱️ $durationMinutes min
                """.trimIndent()
                val notificationResult = sendNewDirectMessage(notification, keyboard)
                if (notificationResult.isFailure) return notificationResult.map { Unit }
                statusPrefs.edit().putBoolean(completionKey, true).apply()
            }
            return Result.success(Unit)
        }

        if (relayConfigured) {
            return safePost(
                "$baseUrl/api/trip/end",
                request.copy(statusMessageId = storedStatusMessageId(request.tripId)),
            )
        }
        return missingDeliveryConfiguration()
    }

    private suspend fun upsertDirectStatus(
        tripId: String,
        text: String,
        replyMarkup: TelegramInlineKeyboardMarkup,
    ): Result<Unit> {
        val existingMessageId = storedStatusMessageId(tripId)
        if (existingMessageId != null) {
            val editResult = editDirectMessage(existingMessageId, text, replyMarkup)
            if (editResult.isSuccess) return Result.success(Unit)
            if (!editResult.exceptionOrNull().isMissingEditableMessage()) return editResult
        }

        return sendNewDirectMessage(text, replyMarkup).map { messageId ->
            storeStatusMessageId(tripId, messageId)
        }
    }

    private suspend fun sendNewDirectMessage(
        text: String,
        replyMarkup: TelegramInlineKeyboardMarkup?,
    ): Result<Long> {
        if (!directConfigured) return Result.failure(Exception("Direct Telegram is not configured"))
        return telegramCall(
            method = "sendMessage",
            body = TelegramSendMessageRequest(
                chat_id = directChatId,
                text = text,
                reply_markup = replyMarkup,
            ),
        ).mapCatching { response ->
            response.result?.message_id
                ?: throw Exception("Telegram did not return a message ID")
        }
    }

    private suspend fun editDirectMessage(
        messageId: Long,
        text: String,
        replyMarkup: TelegramInlineKeyboardMarkup,
    ): Result<Unit> {
        val result = telegramCall(
            method = "editMessageText",
            body = TelegramEditMessageRequest(
                chat_id = directChatId,
                message_id = messageId,
                text = text,
                reply_markup = replyMarkup,
            ),
        )
        return result.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                if (error.message.orEmpty().contains("message is not modified", ignoreCase = true)) {
                    Result.success(Unit)
                } else {
                    Result.failure(error)
                }
            },
        )
    }

    private suspend inline fun <reified T : Any> telegramCall(
        method: String,
        body: T,
    ): Result<TelegramApiResponse> {
        val url = "https://api.telegram.org/bot$directBotToken/$method"
        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val rawBody = response.bodyAsText()
            val telegramResponse = runCatching {
                wireJson.decodeFromString<TelegramApiResponse>(rawBody)
            }.getOrNull()
            if (response.status.isSuccess() && telegramResponse?.ok == true) {
                Result.success(telegramResponse)
            } else {
                Result.failure(
                    Exception(
                        telegramResponse?.description
                            ?: "Telegram API error ${response.status.value}: $rawBody"
                    )
                )
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun Throwable?.isMissingEditableMessage(): Boolean {
        val detail = this?.message.orEmpty()
        return detail.contains("message to edit not found", ignoreCase = true) ||
            detail.contains("message can't be edited", ignoreCase = true) ||
            detail.contains("message can not be edited", ignoreCase = true)
    }

    private fun Result<Long?>.storeMessageIdFor(tripId: String): Result<Unit> = fold(
        onSuccess = { messageId ->
            messageId?.let { storeStatusMessageId(tripId, it) }
            Result.success(Unit)
        },
        onFailure = { Result.failure(it) },
    )

    private fun missingDeliveryConfiguration(): Result<Unit> =
        Result.failure(Exception("No Telegram or relay delivery channel is configured"))

    private suspend inline fun <reified T : Any> safeStatusPost(
        endpoint: String,
        body: T,
    ): Result<Long?> {
        return try {
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (apiSecretKey.isNotBlank()) header("Authorization", "Bearer $apiSecretKey")
                setBody(body)
            }
            val rawBody = response.bodyAsText()
            if (response.status.isSuccess()) {
                val parsed = runCatching {
                    wireJson.decodeFromString<GenericApiResponse>(rawBody)
                }.getOrNull()
                Result.success(parsed?.messageId)
            } else {
                Result.failure(Exception("HTTP error ${response.status.value}: $rawBody"))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend inline fun <reified T : Any> safePost(endpoint: String, body: T): Result<Unit> {
        return try {
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (apiSecretKey.isNotBlank()) header("Authorization", "Bearer $apiSecretKey")
                setBody(body)
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP error ${response.status.value}: ${response.bodyAsText()}"))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
