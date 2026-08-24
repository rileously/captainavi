package com.captainavi.app.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.captainavi.app.data.repository.SettingsRepository
import com.captainavi.app.service.MarineLocationService

data class OfflineSmsSendResult(
    val success: Boolean,
    val message: String,
)

class OfflineSmsMessenger(
    private val context: Context,
    private val settings: SettingsRepository,
    private val locationStore: OfflineSmsLocationStore,
) {
    fun sendConfiguredLocation(): OfflineSmsSendResult {
        val destination = settings.trustedSmsNumber.value.trim()
        if (destination.isBlank()) {
            return OfflineSmsSendResult(false, "Add a trusted SMS number in Configuration first")
        }
        return sendLocationTo(destination)
    }

    fun sendAutomaticReply(destination: String): OfflineSmsSendResult {
        val position = bestAvailablePosition()
        val message = if (position != null) {
            OfflineSmsRules.buildLocationMessage(settings.captainName.value, position)
        } else {
            OfflineSmsRules.buildLocationUnavailableMessage(settings.captainName.value)
        }
        return sendTextTo(destination, message)
    }

    fun sendLocationTo(destination: String): OfflineSmsSendResult {
        val position = bestAvailablePosition()
            ?: return OfflineSmsSendResult(false, "No GPS fix yet — wait for the location marker")
        return sendTextTo(
            destination = destination,
            text = OfflineSmsRules.buildLocationMessage(settings.captainName.value, position),
        )
    }

    fun hasSendPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun bestAvailablePosition(): OfflineSmsPosition? {
        val telemetry = MarineLocationService.telemetry.value
        val live = if (telemetry.hasGpsFix) {
            OfflineSmsPosition(
                latitude = telemetry.latitude,
                longitude = telemetry.longitude,
                speedKnots = telemetry.speedKnots,
                bearingDegrees = telemetry.bearingDegrees,
                headingCardinal = telemetry.headingCardinal,
                accuracyMeters = telemetry.accuracyMeters,
                batteryPct = telemetry.batteryPct,
                recordedAtMillis = telemetry.lastUpdateTime.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
            )
        } else null
        val stored = locationStore.load()
        return listOfNotNull(live, stored).maxByOrNull(OfflineSmsPosition::recordedAtMillis)
    }

    private fun sendTextTo(destination: String, text: String): OfflineSmsSendResult {
        if (!OfflineSmsRules.isValidPhoneNumber(destination)) {
            return OfflineSmsSendResult(false, "Trusted phone number is invalid")
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            return OfflineSmsSendResult(false, "This device cannot send carrier SMS")
        }
        if (!hasSendPermission()) {
            return OfflineSmsSendResult(false, "SMS permission is not enabled")
        }

        return try {
            val defaultSmsManager = context.getSystemService(SmsManager::class.java)
                ?: return OfflineSmsSendResult(false, "SMS service is unavailable")
            val defaultSubscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
            val smsManager = if (defaultSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    defaultSmsManager.createForSubscriptionId(defaultSubscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(defaultSubscriptionId)
                }
            } else {
                defaultSmsManager
            }
            val requestId = nextRequestId()
            val sentIntent = PendingIntent.getBroadcast(
                context,
                requestId,
                Intent(context, SmsStatusReceiver::class.java).setAction(SmsStatusReceiver.ACTION_SMS_SENT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                requestId + 1,
                Intent(context, SmsStatusReceiver::class.java).setAction(SmsStatusReceiver.ACTION_SMS_DELIVERED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            smsManager.sendTextMessage(
                destination,
                null,
                text.take(OfflineSmsRules.MAX_SINGLE_SMS_CHARACTERS),
                sentIntent,
                deliveredIntent,
            )
            OfflineSmsSendResult(
                success = true,
                message = "Location SMS queued",
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Android rejected the carrier SMS request", error)
            OfflineSmsSendResult(
                false,
                error.message?.takeIf(String::isNotBlank) ?: "SMS permission was denied",
            )
        } catch (error: IllegalArgumentException) {
            OfflineSmsSendResult(false, error.message ?: "Could not queue the SMS")
        } catch (error: UnsupportedOperationException) {
            OfflineSmsSendResult(false, "Carrier SMS is not supported on this device")
        } catch (error: Exception) {
            OfflineSmsSendResult(false, error.message ?: "Could not queue the SMS")
        }
    }

    companion object {
        private const val TAG = "CaptainAviSms"
        private var requestId = 4_000

        @Synchronized
        private fun nextRequestId(): Int {
            requestId += 2
            return requestId
        }
    }
}

object OfflineSmsPhoneNumbers {
    @Suppress("DEPRECATION")
    fun areSame(context: Context, first: String, second: String): Boolean {
        if (!OfflineSmsRules.isValidPhoneNumber(first) || !OfflineSmsRules.isValidPhoneNumber(second)) {
            return false
        }
        val countryIso = (context.getSystemService(TelephonyManager::class.java)
            ?.networkCountryIso
            ?.takeIf(String::isNotBlank)
            ?: "mv").lowercase()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PhoneNumberUtils.areSamePhoneNumber(first, second, countryIso)
        } else {
            PhoneNumberUtils.compare(context, first, second)
        }
    }
}
