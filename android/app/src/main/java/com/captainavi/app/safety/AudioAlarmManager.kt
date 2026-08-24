package com.captainavi.app.safety

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioAlarmManager(private val context: Context) {
    private var toneGenerator: ToneGenerator? = null
    private var alarmJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            // Fallback to music stream if alarm stream is restricted
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            } catch (_: Exception) {}
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Triggers high-priority loud siren sound and rhythmic vibration for SOS
     */
    fun startEmergencySiren() {
        stopAlarm()
        alarmJob = scope.launch {
            val vibrator = getVibrator()
            while (isActive) {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 700)
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500),
                        -1
                    )
                )
                delay(1200)
            }
        }
    }

    /**
     * Plays a loud warning double-beep and vibration for alerts (low battery, reef proximity, etc.)
     */
    fun playWarningBeep(repeatCount: Int = 3) {
        scope.launch {
            val vibrator = getVibrator()
            for (i in 0 until repeatCount) {
                toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 350)
                vibrator?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
                delay(500)
            }
        }
    }

    /**
     * Short confirmation click / pip
     */
    fun playConfirmTone() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    fun stopAlarm() {
        alarmJob?.cancel()
        alarmJob = null
        getVibrator()?.cancel()
    }
}
