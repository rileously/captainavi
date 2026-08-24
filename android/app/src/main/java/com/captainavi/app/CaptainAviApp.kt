package com.captainavi.app

import android.app.Application
import com.captainavi.app.connectivity.NetworkMonitor
import com.captainavi.app.data.local.CaptainAviDatabase
import com.captainavi.app.data.remote.RelayApiClient
import com.captainavi.app.data.remote.FollowMePublicClient
import com.captainavi.app.data.remote.RtlMarineRouteClient
import com.captainavi.app.data.repository.FollowMePublicBoatRepository
import com.captainavi.app.data.repository.MarineConditionsRepository
import com.captainavi.app.data.repository.MarineActivityPointRepository
import com.captainavi.app.data.repository.IslandGazetteerRepository
import com.captainavi.app.data.repository.OutboxRepository
import com.captainavi.app.data.repository.ReefBoundaryRepository
import com.captainavi.app.data.repository.RtlMarineRouteRepository
import com.captainavi.app.data.repository.SettingsRepository
import com.captainavi.app.data.repository.TripRepository
import com.captainavi.app.data.repository.WaypointRepository
import com.captainavi.app.localization.Language
import com.captainavi.app.localization.LanguageManager
import com.captainavi.app.service.ConnectivitySyncWorker
import com.captainavi.app.service.StormAlertWorker
import com.captainavi.app.sms.OfflineSmsLocationStore
import com.captainavi.app.sms.OfflineSmsMessenger
import com.captainavi.app.tides.TidePredictionAssets
import com.captainavi.app.ui.theme.NightModeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CaptainAviApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { CaptainAviDatabase.getDatabase(this, applicationScope) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val networkMonitor by lazy { NetworkMonitor(this) }
    val offlineSmsLocationStore by lazy { OfflineSmsLocationStore(this) }
    val offlineSmsMessenger by lazy {
        OfflineSmsMessenger(this, settingsRepository, offlineSmsLocationStore)
    }
    val relayApiClient by lazy {
        RelayApiClient(
            context = this,
            baseUrl = settingsRepository.relayUrl.value,
            apiSecretKey = settingsRepository.apiSecretKey.value,
            directBotToken = settingsRepository.telegramBotToken.value,
            directChatId = settingsRepository.telegramChatId.value
        )
    }

    val tripRepository by lazy { TripRepository(database.tripDao(), database.breadcrumbDao()) }
    val waypointRepository by lazy { WaypointRepository(database.waypointDao()) }
    val outboxRepository by lazy {
        OutboxRepository(
            database.breadcrumbDao(),
            database.alertEventDao(),
            database.outgoingEventDao(),
            relayApiClient,
        )
    }
    val marineConditionsRepository by lazy {
        MarineConditionsRepository(this, isOnline = networkMonitor::isCurrentlyOnline)
    }
    val marineActivityPointRepository by lazy { MarineActivityPointRepository(this) }
    val followMePublicBoatRepository by lazy {
        FollowMePublicBoatRepository(
            client = FollowMePublicClient(),
            isOnline = networkMonitor::isCurrentlyOnline,
        )
    }
    val rtlMarineRouteRepository by lazy {
        RtlMarineRouteRepository(
            context = this,
            client = RtlMarineRouteClient(),
            isOnline = networkMonitor::isCurrentlyOnline,
        )
    }
    val islandGazetteerRepository by lazy {
        IslandGazetteerRepository(this, isOnline = networkMonitor::isCurrentlyOnline)
    }
    val reefBoundaryRepository by lazy { ReefBoundaryRepository(this) }

    override fun onCreate() {
        super.onCreate()
        removeRetiredOpenNauticalCache()
        org.osmdroid.config.Configuration.getInstance().userAgentValue =
            "CaptainAvi/1.0 ($packageName)"
        NightModeState.isNightMode = settingsRepository.nightMode.value
        LanguageManager.setLanguage(
            if (settingsRepository.languageCode.value == Language.DHIVEHI.code) {
                Language.DHIVEHI
            } else {
                Language.ENGLISH
            }
        )
        TidePredictionAssets.install(this)
        ConnectivitySyncWorker.schedulePeriodicSync(this)
        StormAlertWorker.schedulePeriodicCheck(this)
        if (settingsRepository.rtlMarineRoutesEnabled.value) {
            applicationScope.launch {
                delay(2_500)
                rtlMarineRouteRepository.refresh()
            }
        }
    }

    private fun removeRetiredOpenNauticalCache() {
        cacheDir.listFiles()
            ?.filter { file -> file.name.startsWith("open_waters_seamap_native_") }
            ?.forEach { file -> runCatching { file.delete() } }

        listOf("mbgl-offline.db", "mbgl-offline.db-shm", "mbgl-offline.db-wal")
            .forEach { name -> runCatching { java.io.File(filesDir, name).delete() } }

        runCatching { deleteSharedPreferences("MapboxSharedPreferences") }
    }
}
