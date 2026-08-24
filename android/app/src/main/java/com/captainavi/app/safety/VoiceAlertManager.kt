package com.captainavi.app.safety

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAlertManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastSpokenTime = 0L
    private var lastSpokenPhrase = ""

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(0.95f)
            tts?.setPitch(1.0f)
            isInitialized = true
        }
    }

    fun speak(text: String, minIntervalMs: Long = 15000L, isUrgent: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!isUrgent && text == lastSpokenPhrase && (now - lastSpokenTime) < minIntervalMs) {
            return
        }

        if (isInitialized && tts != null) {
            lastSpokenTime = now
            lastSpokenPhrase = text
            val queueMode = if (isUrgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, queueMode, null, "MarineVoiceAlert_${System.currentTimeMillis()}")
        }
    }

    fun speakTripStarted() {
        speak("Fishing trip started. Tracking GPS and safety monitor active.", isUrgent = true)
    }

    fun speakTripEnded() {
        speak("Fishing voyage completed. Welcome back to harbour.", isUrgent = true)
    }

    fun speakReefWarning(reefName: String, distanceMeters: Int) {
        speak("Caution. Danger reef ahead. $reefName, $distanceMeters meters away.", minIntervalMs = 20000L, isUrgent = true)
    }

    fun speakAnchorDragging() {
        speak("Warning! Warning! Vessel is dragging anchor. Check anchor line immediately.", minIntervalMs = 15000L, isUrgent = true)
    }

    fun speakDestinationApproaching(destName: String, distanceNm: Double) {
        val distFormatted = String.format(Locale.US, "%.1f", distanceNm)
        speak("Approaching destination: $destName. Distance: $distFormatted nautical miles.", minIntervalMs = 30000L)
    }

    fun speakMobActivated() {
        speak("Emergency! Man Overboard beacon activated. Steer to return to marker.", isUrgent = true)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
