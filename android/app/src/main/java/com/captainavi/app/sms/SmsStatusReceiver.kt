package com.captainavi.app.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SMS_SENT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "Carrier accepted offline location SMS")
                } else {
                    Log.w(TAG, "Carrier rejected offline location SMS: ${sentResultLabel(resultCode)}")
                }
            }
            ACTION_SMS_DELIVERED -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "Offline location SMS delivered")
                } else {
                    Log.w(TAG, "Offline location SMS delivery was not confirmed: $resultCode")
                }
            }
        }
    }

    private fun sentResultLabel(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no mobile service"
        SmsManager.RESULT_ERROR_NULL_PDU -> "invalid message"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "mobile radio is off"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS sending limit exceeded"
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "short code blocked"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "short code not allowed"
        else -> "result $code"
    }

    companion object {
        const val ACTION_SMS_SENT = "com.captainavi.app.action.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.captainavi.app.action.SMS_DELIVERED"
        private const val TAG = "CaptainAviSms"
    }
}
