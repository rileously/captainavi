package com.captainavi.app.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.captainavi.app.CaptainAviApp

class SmsLocationRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val app = context.applicationContext as? CaptainAviApp ?: return
        val settings = app.settingsRepository
        if (!settings.smsAutoReplyEnabled.value) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.firstNotNullOfOrNull { it.originatingAddress } ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val trustedNumber = settings.trustedSmsNumber.value
        if (!OfflineSmsPhoneNumbers.areSame(context, sender, trustedNumber)) return
        if (!OfflineSmsRules.matchesRequest(body, settings.smsRequestPhrase.value)) return

        val now = System.currentTimeMillis()
        if (!app.offlineSmsLocationStore.reserveAutomaticReply(now)) {
            Log.i(TAG, "Trusted location request ignored by five-minute rate limit")
            return
        }

        val result = app.offlineSmsMessenger.sendAutomaticReply(trustedNumber)
        if (result.success) {
            Log.i(TAG, "Queued offline location reply to trusted number")
        } else {
            app.offlineSmsLocationStore.releaseAutomaticReply(now)
            Log.w(TAG, "Could not send offline location reply: ${result.message}")
        }
    }

    companion object {
        private const val TAG = "CaptainAviSms"
    }
}
