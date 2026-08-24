package com.captainavi.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.captainavi.app.safety.FuelMarginCalculator
import com.captainavi.app.safety.StormAlertEvaluator
import com.captainavi.app.safety.TripCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("captain_avi_prefs", Context.MODE_PRIVATE)

    private val _captainName = MutableStateFlow(
        prefs.getString(KEY_CAPTAIN_NAME, "Adam's Father") ?: "Adam's Father"
    )
    val captainName: StateFlow<String> = _captainName.asStateFlow()

    private val _relayUrl = MutableStateFlow(
        prefs.getString(KEY_RELAY_URL, "https://captain-avi-relay.workers.dev")
            ?: "https://captain-avi-relay.workers.dev"
    )
    val relayUrl: StateFlow<String> = _relayUrl.asStateFlow()

    private val _apiSecretKey = MutableStateFlow(
        prefs.getString(KEY_API_SECRET_KEY, "") ?: ""
    )
    val apiSecretKey: StateFlow<String> = _apiSecretKey.asStateFlow()

    private val _telegramBotToken = MutableStateFlow(
        prefs.getString(KEY_BOT_TOKEN, "") ?: ""
    )
    val telegramBotToken: StateFlow<String> = _telegramBotToken.asStateFlow()

    private val _telegramChatId = MutableStateFlow(
        prefs.getString(KEY_CHAT_ID, "") ?: ""
    )
    val telegramChatId: StateFlow<String> = _telegramChatId.asStateFlow()

    private val _trustedSmsNumber = MutableStateFlow(
        prefs.getString(KEY_TRUSTED_SMS_NUMBER, "") ?: ""
    )
    val trustedSmsNumber: StateFlow<String> = _trustedSmsNumber.asStateFlow()

    private val _smsRequestPhrase = MutableStateFlow(
        prefs.getString(KEY_SMS_REQUEST_PHRASE, DEFAULT_SMS_REQUEST_PHRASE)
            ?: DEFAULT_SMS_REQUEST_PHRASE
    )
    val smsRequestPhrase: StateFlow<String> = _smsRequestPhrase.asStateFlow()

    private val _smsAutoReplyEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SMS_AUTO_REPLY_ENABLED, false)
    )
    val smsAutoReplyEnabled: StateFlow<Boolean> = _smsAutoReplyEnabled.asStateFlow()

    private val _breadcrumbIntervalMinutes = MutableStateFlow(
        prefs.getInt(KEY_BREADCRUMB_INTERVAL, 1)
    )
    val breadcrumbIntervalMinutes: StateFlow<Int> = _breadcrumbIntervalMinutes.asStateFlow()

    private val _telegramUpdateIntervalMinutes = MutableStateFlow(
        prefs.getInt(KEY_TELEGRAM_INTERVAL, DEFAULT_TELEGRAM_UPDATE_MINUTES)
            .coerceIn(MIN_TELEGRAM_UPDATE_MINUTES, MAX_TELEGRAM_UPDATE_MINUTES)
    )
    val telegramUpdateIntervalMinutes: StateFlow<Int> = _telegramUpdateIntervalMinutes.asStateFlow()

    private val _maxDistanceHomeNm = MutableStateFlow(
        prefs.getFloat(KEY_MAX_DISTANCE_HOME, 15.0f).toDouble()
    )
    val maxDistanceHomeNm: StateFlow<Double> = _maxDistanceHomeNm.asStateFlow()

    private val _stationaryThresholdMinutes = MutableStateFlow(
        prefs.getInt(KEY_STATIONARY_MINUTES, 30)
    )
    val stationaryThresholdMinutes: StateFlow<Int> = _stationaryThresholdMinutes.asStateFlow()

    private val _tripReferenceDistanceNm = MutableStateFlow(
        prefs.getFloat(
            KEY_TRIP_REFERENCE_DISTANCE_NM,
            TripCalculator.DEFAULT_REFERENCE_DISTANCE_NM.toFloat(),
        ).toDouble()
    )
    val tripReferenceDistanceNm: StateFlow<Double> = _tripReferenceDistanceNm.asStateFlow()

    private val _tripReferenceCostMvr = MutableStateFlow(
        prefs.getFloat(
            KEY_TRIP_REFERENCE_COST_MVR,
            TripCalculator.DEFAULT_REFERENCE_COST_MVR.toFloat(),
        ).toDouble()
    )
    val tripReferenceCostMvr: StateFlow<Double> = _tripReferenceCostMvr.asStateFlow()

    private val _tripReferenceFuelLiters = MutableStateFlow(
        prefs.getFloat(
            KEY_TRIP_REFERENCE_FUEL_LITERS,
            TripCalculator.DEFAULT_REFERENCE_FUEL_LITERS.toFloat(),
        ).toDouble()
    )
    val tripReferenceFuelLiters: StateFlow<Double> = _tripReferenceFuelLiters.asStateFlow()

    private val _fuelTankLiters = MutableStateFlow(
        prefs.getFloat(
            KEY_FUEL_TANK_LITERS,
            FuelMarginCalculator.DEFAULT_TANK_LITERS.toFloat(),
        ).toDouble()
    )
    val fuelTankLiters: StateFlow<Double> = _fuelTankLiters.asStateFlow()

    private val _reefWarningsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_REEF_WARNINGS_ENABLED, true)
    )
    val reefWarningsEnabled: StateFlow<Boolean> = _reefWarningsEnabled.asStateFlow()

    private val _reefWarningBufferMeters = MutableStateFlow(
        prefs.getInt(KEY_REEF_WARNING_BUFFER_METERS, ReefBoundaryRepository.DEFAULT_WARNING_BUFFER_METERS.toInt())
    )
    val reefWarningBufferMeters: StateFlow<Int> = _reefWarningBufferMeters.asStateFlow()

    private val _stormAlertsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_STORM_ALERTS_ENABLED, true)
    )
    val stormAlertsEnabled: StateFlow<Boolean> = _stormAlertsEnabled.asStateFlow()

    private val _stormWaveHeightThresholdMeters = MutableStateFlow(
        prefs.getFloat(
            KEY_STORM_WAVE_THRESHOLD_METERS,
            StormAlertEvaluator.DEFAULT_WAVE_HEIGHT_THRESHOLD_METERS.toFloat(),
        ).toDouble()
    )
    val stormWaveHeightThresholdMeters: StateFlow<Double> = _stormWaveHeightThresholdMeters.asStateFlow()

    private val _stormWindGustThresholdKnots = MutableStateFlow(
        prefs.getFloat(
            KEY_STORM_WIND_GUST_THRESHOLD_KNOTS,
            StormAlertEvaluator.DEFAULT_WIND_GUST_THRESHOLD_KNOTS.toFloat(),
        ).toDouble()
    )
    val stormWindGustThresholdKnots: StateFlow<Double> = _stormWindGustThresholdKnots.asStateFlow()

    private val _reefOverlayEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_REEF_OVERLAY_ENABLED, true)
    )
    val reefOverlayEnabled: StateFlow<Boolean> = _reefOverlayEnabled.asStateFlow()

    private val _fishingPointsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_FISHING_POINTS_ENABLED, true)
    )
    val fishingPointsEnabled: StateFlow<Boolean> = _fishingPointsEnabled.asStateFlow()

    private val _divePointsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_DIVE_POINTS_ENABLED, true)
    )
    val divePointsEnabled: StateFlow<Boolean> = _divePointsEnabled.asStateFlow()

    private val _rtlMarineRoutesEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_RTL_MARINE_ROUTES_ENABLED, true)
    )
    val rtlMarineRoutesEnabled: StateFlow<Boolean> = _rtlMarineRoutesEnabled.asStateFlow()

    private val _simulationMode = MutableStateFlow(
        prefs.getBoolean(KEY_SIMULATION_MODE, false)
    )
    val simulationMode: StateFlow<Boolean> = _simulationMode.asStateFlow()

    private val _nightMode = MutableStateFlow(
        prefs.getBoolean(KEY_NIGHT_MODE, false)
    )
    val nightMode: StateFlow<Boolean> = _nightMode.asStateFlow()

    private val _languageCode = MutableStateFlow(
        prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    )
    val languageCode: StateFlow<String> = _languageCode.asStateFlow()

    fun setNightMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_MODE, enabled).apply()
        _nightMode.value = enabled
    }

    fun setLanguageCode(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply()
        _languageCode.value = code
    }

    fun setSimulationMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIMULATION_MODE, enabled).apply()
        _simulationMode.value = enabled
    }

    fun setReefOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REEF_OVERLAY_ENABLED, enabled).apply()
        _reefOverlayEnabled.value = enabled
    }

    fun setFishingPointsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FISHING_POINTS_ENABLED, enabled).apply()
        _fishingPointsEnabled.value = enabled
    }

    fun setDivePointsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIVE_POINTS_ENABLED, enabled).apply()
        _divePointsEnabled.value = enabled
    }

    fun setRtlMarineRoutesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RTL_MARINE_ROUTES_ENABLED, enabled).apply()
        _rtlMarineRoutesEnabled.value = enabled
    }

    fun updateSettings(
        name: String,
        url: String,
        secretKey: String,
        botToken: String,
        chatId: String,
        trustedSmsNumber: String,
        smsRequestPhrase: String,
        smsAutoReplyEnabled: Boolean,
        breadcrumbInterval: Int,
        telegramInterval: Int,
        maxDistHome: Double,
        stationaryMinutes: Int,
        tripReferenceDistanceNm: Double,
        tripReferenceCostMvr: Double,
        tripReferenceFuelLiters: Double,
        fuelTankLiters: Double,
        reefWarningsEnabled: Boolean,
        reefWarningBufferMeters: Int,
        stormAlertsEnabled: Boolean,
        stormWaveHeightThresholdMeters: Double,
        stormWindGustThresholdKnots: Double,
        simMode: Boolean
    ) {
        val safeReefBuffer = reefWarningBufferMeters.coerceIn(
            ReefBoundaryRepository.MIN_WARNING_BUFFER_METERS.toInt(),
            ReefBoundaryRepository.MAX_WARNING_BUFFER_METERS.toInt(),
        )
        val safeStormWaveThreshold = stormWaveHeightThresholdMeters
            .takeIf { it.isFinite() && it > 0.0 }
            ?: StormAlertEvaluator.DEFAULT_WAVE_HEIGHT_THRESHOLD_METERS
        val safeStormWindGustThreshold = stormWindGustThresholdKnots
            .takeIf { it.isFinite() && it > 0.0 }
            ?: StormAlertEvaluator.DEFAULT_WIND_GUST_THRESHOLD_KNOTS
        val safeTripReferenceDistance = tripReferenceDistanceNm
            .takeIf { it.isFinite() && it > 0.0 }
            ?: _tripReferenceDistanceNm.value
        val safeTripReferenceCost = tripReferenceCostMvr
            .takeIf { it.isFinite() && it >= 0.0 }
            ?: _tripReferenceCostMvr.value
        val safeTripReferenceFuel = tripReferenceFuelLiters
            .takeIf { it.isFinite() && it >= 0.0 }
            ?: _tripReferenceFuelLiters.value
        val safeFuelTankLiters = fuelTankLiters
            .takeIf { it.isFinite() && it > 0.0 }
            ?: _fuelTankLiters.value
        val safeTelegramInterval = telegramInterval.coerceIn(
            MIN_TELEGRAM_UPDATE_MINUTES,
            MAX_TELEGRAM_UPDATE_MINUTES,
        )
        val safeTrustedSmsNumber = trustedSmsNumber.trim()
        val safeSmsRequestPhrase = smsRequestPhrase.trim().ifBlank { DEFAULT_SMS_REQUEST_PHRASE }
        prefs.edit()
            .putString(KEY_CAPTAIN_NAME, name)
            .putString(KEY_RELAY_URL, url)
            .putString(KEY_API_SECRET_KEY, secretKey)
            .putString(KEY_BOT_TOKEN, botToken)
            .putString(KEY_CHAT_ID, chatId)
            .putString(KEY_TRUSTED_SMS_NUMBER, safeTrustedSmsNumber)
            .putString(KEY_SMS_REQUEST_PHRASE, safeSmsRequestPhrase)
            .putBoolean(KEY_SMS_AUTO_REPLY_ENABLED, smsAutoReplyEnabled)
            .putInt(KEY_BREADCRUMB_INTERVAL, breadcrumbInterval)
            .putInt(KEY_TELEGRAM_INTERVAL, safeTelegramInterval)
            .putFloat(KEY_MAX_DISTANCE_HOME, maxDistHome.toFloat())
            .putInt(KEY_STATIONARY_MINUTES, stationaryMinutes)
            .putFloat(KEY_TRIP_REFERENCE_DISTANCE_NM, safeTripReferenceDistance.toFloat())
            .putFloat(KEY_TRIP_REFERENCE_COST_MVR, safeTripReferenceCost.toFloat())
            .putFloat(KEY_TRIP_REFERENCE_FUEL_LITERS, safeTripReferenceFuel.toFloat())
            .putFloat(KEY_FUEL_TANK_LITERS, safeFuelTankLiters.toFloat())
            .putBoolean(KEY_REEF_WARNINGS_ENABLED, reefWarningsEnabled)
            .putInt(KEY_REEF_WARNING_BUFFER_METERS, safeReefBuffer)
            .putBoolean(KEY_STORM_ALERTS_ENABLED, stormAlertsEnabled)
            .putFloat(KEY_STORM_WAVE_THRESHOLD_METERS, safeStormWaveThreshold.toFloat())
            .putFloat(KEY_STORM_WIND_GUST_THRESHOLD_KNOTS, safeStormWindGustThreshold.toFloat())
            .putBoolean(KEY_SIMULATION_MODE, simMode)
            .apply()

        _captainName.value = name
        _relayUrl.value = url
        _apiSecretKey.value = secretKey
        _telegramBotToken.value = botToken
        _telegramChatId.value = chatId
        _trustedSmsNumber.value = safeTrustedSmsNumber
        _smsRequestPhrase.value = safeSmsRequestPhrase
        _smsAutoReplyEnabled.value = smsAutoReplyEnabled
        _breadcrumbIntervalMinutes.value = breadcrumbInterval
        _telegramUpdateIntervalMinutes.value = safeTelegramInterval
        _maxDistanceHomeNm.value = maxDistHome
        _stationaryThresholdMinutes.value = stationaryMinutes
        _tripReferenceDistanceNm.value = safeTripReferenceDistance
        _tripReferenceCostMvr.value = safeTripReferenceCost
        _tripReferenceFuelLiters.value = safeTripReferenceFuel
        _fuelTankLiters.value = safeFuelTankLiters
        _reefWarningsEnabled.value = reefWarningsEnabled
        _reefWarningBufferMeters.value = safeReefBuffer
        _stormAlertsEnabled.value = stormAlertsEnabled
        _stormWaveHeightThresholdMeters.value = safeStormWaveThreshold
        _stormWindGustThresholdKnots.value = safeStormWindGustThreshold
        _simulationMode.value = simMode
    }

    companion object {
        const val MIN_TELEGRAM_UPDATE_MINUTES = 5
        const val MAX_TELEGRAM_UPDATE_MINUTES = 120
        const val DEFAULT_TELEGRAM_UPDATE_MINUTES = 10
        const val DEFAULT_SMS_REQUEST_PHRASE = "CAPTAIN AVI LOCATION"

        private const val KEY_CAPTAIN_NAME = "captain_name"
        private const val KEY_RELAY_URL = "relay_url"
        private const val KEY_API_SECRET_KEY = "api_secret_key"
        private const val KEY_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_CHAT_ID = "telegram_chat_id"
        private const val KEY_TRUSTED_SMS_NUMBER = "trusted_sms_number"
        private const val KEY_SMS_REQUEST_PHRASE = "sms_request_phrase"
        private const val KEY_SMS_AUTO_REPLY_ENABLED = "sms_auto_reply_enabled"
        private const val KEY_BREADCRUMB_INTERVAL = "breadcrumb_interval_minutes"
        private const val KEY_TELEGRAM_INTERVAL = "telegram_interval_minutes"
        private const val KEY_MAX_DISTANCE_HOME = "max_dist_home_nm"
        private const val KEY_STATIONARY_MINUTES = "stationary_minutes"
        private const val KEY_TRIP_REFERENCE_DISTANCE_NM = "trip_reference_distance_nm"
        private const val KEY_TRIP_REFERENCE_COST_MVR = "trip_reference_cost_mvr"
        private const val KEY_TRIP_REFERENCE_FUEL_LITERS = "trip_reference_fuel_liters"
        private const val KEY_FUEL_TANK_LITERS = "fuel_tank_liters"
        private const val KEY_REEF_WARNINGS_ENABLED = "reef_warnings_enabled"
        private const val KEY_REEF_WARNING_BUFFER_METERS = "reef_warning_buffer_meters"
        private const val KEY_STORM_ALERTS_ENABLED = "storm_alerts_enabled"
        private const val KEY_STORM_WAVE_THRESHOLD_METERS = "storm_wave_threshold_meters"
        private const val KEY_STORM_WIND_GUST_THRESHOLD_KNOTS = "storm_wind_gust_threshold_knots"
        private const val KEY_REEF_OVERLAY_ENABLED = "reef_overlay_enabled"
        private const val KEY_FISHING_POINTS_ENABLED = "fishing_points_enabled"
        private const val KEY_DIVE_POINTS_ENABLED = "dive_points_enabled"
        private const val KEY_RTL_MARINE_ROUTES_ENABLED = "rtl_marine_routes_enabled"
        private const val KEY_SIMULATION_MODE = "simulation_mode"
        private const val KEY_NIGHT_MODE = "night_mode"
        private const val KEY_LANGUAGE = "language_code"
    }
}
