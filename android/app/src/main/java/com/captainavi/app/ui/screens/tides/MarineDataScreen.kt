package com.captainavi.app.ui.screens.tides

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.remote.MarineConditions
import com.captainavi.app.data.remote.MarineForecastDay
import com.captainavi.app.data.remote.MarineForecastHour
import com.captainavi.app.marine.weatherCodeLabel
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.safety.StormAlertEvaluator
import com.captainavi.app.service.MarineLocationService
import com.captainavi.app.tides.TidePredictor
import com.captainavi.app.ui.components.StormAlertBanner
import com.captainavi.app.ui.theme.MarineTheme
import com.captainavi.app.ui.screens.map.StreetTileSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private const val DEFAULT_FORECAST_LATITUDE = 6.704
private const val DEFAULT_FORECAST_LONGITUDE = 73.123
private val MALDIVES_ZONE: ZoneId = ZoneId.of("Indian/Maldives")
private val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH", Locale.US)
private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE\ndd", Locale.US)
private val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
private val CELL_HEIGHT = 42.dp
private val HOUR_COLUMN_WIDTH = 72.dp

/** Forecast-first marine screen. The original harmonic tide chart remains one tap away. */
@Composable
fun MarineDataScreen(
    modifier: Modifier = Modifier,
    onOpenMap: () -> Unit = {},
) {
    val colors = MarineTheme.colors
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarineModeButton(
                label = "FORECAST",
                selected = selectedMode == 0,
                onClick = { selectedMode = 0 },
                modifier = Modifier.weight(1f),
            )
            MarineModeButton(
                label = "TIDES",
                selected = selectedMode == 1,
                onClick = { selectedMode = 1 },
                modifier = Modifier.weight(1f),
            )
        }

        if (selectedMode == 0) {
            MarineForecastPanel(
                modifier = Modifier.weight(1f),
                onOpenMap = onOpenMap,
            )
        } else {
            TidesScreen(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MarineModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.accent else colors.card,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            color = if (selected) colors.onAccent else colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MarineForecastPanel(
    modifier: Modifier = Modifier,
    onOpenMap: () -> Unit,
) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val telemetry by MarineLocationService.telemetry.collectAsState()
    val state by app.marineConditionsRepository.state.collectAsState()
    val islandState by app.islandGazetteerRepository.state.collectAsState()
    val isOnline by app.networkMonitor.isOnline.collectAsState()
    val scope = rememberCoroutineScope()
    val stormAlertsEnabled by app.settingsRepository.stormAlertsEnabled.collectAsState()
    val stormWaveHeightThresholdMeters by app.settingsRepository.stormWaveHeightThresholdMeters.collectAsState()
    val stormWindGustThresholdKnots by app.settingsRepository.stormWindGustThresholdKnots.collectAsState()
    val stormAlert = remember(
        state.conditions,
        stormAlertsEnabled,
        stormWaveHeightThresholdMeters,
        stormWindGustThresholdKnots,
    ) {
        state.conditions
            ?.takeIf { stormAlertsEnabled }
            ?.let { StormAlertEvaluator.evaluate(it, stormWaveHeightThresholdMeters, stormWindGustThresholdKnots) }
    }

    val requestedLatitude = when {
        telemetry.hasGpsFix -> telemetry.latitude
        state.conditions != null -> state.conditions!!.latitude
        else -> DEFAULT_FORECAST_LATITUDE
    }
    val requestedLongitude = when {
        telemetry.hasGpsFix -> telemetry.longitude
        state.conditions != null -> state.conditions!!.longitude
        else -> DEFAULT_FORECAST_LONGITUDE
    }
    val forecastGridKey = (requestedLatitude * 10).roundToInt() to (requestedLongitude * 10).roundToInt()

    LaunchedEffect(forecastGridKey, state.conditions?.hourlyForecast?.isEmpty()) {
        app.marineConditionsRepository.refresh(
            latitude = requestedLatitude,
            longitude = requestedLongitude,
            force = state.conditions?.hourlyForecast.isNullOrEmpty(),
        )
    }

    val conditions = state.conditions
    // Keep the map tied to the payload currently on screen. During refresh this
    // deliberately continues to show the cached forecast's original point.
    val forecastLatitude = conditions?.latitude ?: requestedLatitude
    val forecastLongitude = conditions?.longitude ?: requestedLongitude
    val nearestIsland = remember(islandState.islands, forecastLatitude, forecastLongitude) {
        islandState.islands.minByOrNull { island ->
            NauticalMath.distanceNauticalMiles(
                forecastLatitude,
                forecastLongitude,
                island.latitude,
                island.longitude,
            )
        }
    }
    val nearestIslandDistanceNm = remember(nearestIsland, forecastLatitude, forecastLongitude) {
        nearestIsland?.let { island ->
            NauticalMath.distanceNauticalMiles(
                forecastLatitude,
                forecastLongitude,
                island.latitude,
                island.longitude,
            )
        }
    }
    val forecastLocationName = remember(nearestIsland, forecastLatitude, forecastLongitude) {
        nearestIsland?.let { island ->
            val distance = NauticalMath.distanceNauticalMiles(
                forecastLatitude,
                forecastLongitude,
                island.latitude,
                island.longitude,
            )
            if (distance <= 1.5) {
                island.englishName
            } else {
                val bearingFromIsland = NauticalMath.bearingDegrees(
                    island.latitude,
                    island.longitude,
                    forecastLatitude,
                    forecastLongitude,
                )
                String.format(
                    Locale.US,
                    "%.1f NM %s of %s",
                    distance,
                    windDirectionCardinal(bearingFromIsland),
                    island.englishName,
                )
            }
        } ?: "Selected marine point"
    }
    val days = remember(conditions?.dailyForecast, conditions?.hourlyForecast) {
        conditions?.dailyForecast?.takeIf { it.isNotEmpty() }
            ?: conditions?.hourlyForecast.orEmpty()
                .map { it.time.substringBefore('T') }
                .distinct()
                .map { MarineForecastDay(date = it) }
    }
    var selectedDate by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(days) {
        if (days.none { it.date == selectedDate }) {
            selectedDate = days.firstOrNull()?.date.orEmpty()
        }
    }
    val selectedHours = remember(conditions?.hourlyForecast, selectedDate) {
        conditions?.hourlyForecast.orEmpty()
            .filter { it.time.startsWith(selectedDate) }
            .filterIndexed { index, _ -> index % 3 == 0 }
    }
    var selectedHourTime by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(selectedHours) {
        if (selectedHours.none { it.time == selectedHourTime }) {
            val now = LocalDateTime.now(MALDIVES_ZONE)
            selectedHourTime = if (selectedDate == now.toLocalDate().toString()) {
                selectedHours.firstOrNull { hour ->
                    runCatching { LocalDateTime.parse(hour.time) >= now }.getOrDefault(false)
                }?.time ?: selectedHours.lastOrNull()?.time.orEmpty()
            } else {
                selectedHours.firstOrNull()?.time.orEmpty()
            }
        }
    }
    val selectedHour = selectedHours.firstOrNull { it.time == selectedHourTime }
        ?: selectedHours.firstOrNull()
        ?: conditions?.hourlyForecast?.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        CurrentMarineOverview(
            conditions = conditions,
            selectedHour = selectedHour,
            latitude = forecastLatitude,
            longitude = forecastLongitude,
            locationName = forecastLocationName,
            nearestLandDistanceNm = nearestIslandDistanceNm,
            usingGps = telemetry.hasGpsFix,
            isOnline = isOnline,
            onOpenMap = onOpenMap,
        )

        stormAlert?.let { alert ->
            StormAlertBanner(alert, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "7-DAY MARINE FORECAST",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        state.errorMessage != null && conditions != null -> "Saved forecast · ${state.errorMessage}"
                        state.errorMessage != null -> state.errorMessage.orEmpty()
                        else -> "Open-Meteo best match · 3-hour steps"
                    },
                    color = if (state.errorMessage == null) colors.textMuted else colors.caution,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                )
            }
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = colors.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(
                    onClick = {
                        scope.launch {
                            app.marineConditionsRepository.refresh(
                                requestedLatitude,
                                requestedLongitude,
                                force = true,
                            )
                        }
                    },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh forecast", tint = colors.accent)
                }
            }
        }

        if (days.isNotEmpty()) {
            DaySelector(
                days = days,
                selectedDate = selectedDate,
                onSelect = { selectedDate = it },
            )
        }

        when {
            conditions == null && state.isLoading -> ForecastMessage("Loading wind, waves and weather…")
            conditions == null -> ForecastMessage("Forecast unavailable. Connect to the internet and refresh.", offline = true)
            selectedHours.isEmpty() -> ForecastMessage("No hourly forecast is available for this day.", offline = true)
            else -> {
                ForecastGrid(
                    hours = selectedHours,
                    selectedHourTime = selectedHour?.time.orEmpty(),
                    onSelectedHour = { selectedHourTime = it.time },
                )
                TideTrendCard(
                    hours = selectedHours,
                    selectedHourTime = selectedHour?.time.orEmpty(),
                )
            }
        }

        Text(
            text = "Forecast guidance only. Confirm local weather, tide tables, warnings and safe depth before departure.",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CurrentMarineOverview(
    conditions: MarineConditions?,
    selectedHour: MarineForecastHour?,
    latitude: Double,
    longitude: Double,
    locationName: String,
    nearestLandDistanceNm: Double?,
    usingGps: Boolean,
    isOnline: Boolean,
    onOpenMap: () -> Unit,
) {
    val windSpeed = selectedHour?.windSpeedKnots ?: conditions?.windSpeedKnots
    val windDirection = selectedHour?.windDirectionDegrees ?: conditions?.windDirectionDegrees
    val gust = selectedHour?.windGustKnots ?: conditions?.windGustKnots
    val waveHeight = selectedHour?.waveHeightMeters ?: conditions?.waveHeightMeters
    val wavePeriod = selectedHour?.wavePeriodSeconds ?: conditions?.wavePeriodSeconds
    val selectedTime = remember(selectedHour?.time) {
        selectedHour?.time?.let { time ->
            runCatching {
                LocalDateTime.parse(time).format(DateTimeFormatter.ofPattern("EEE dd · HH:mm", Locale.US))
            }.getOrNull()
        }
    }

    ForecastLocationMap(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        nearestLandDistanceNm = nearestLandDistanceNm,
        selectedTime = selectedTime,
        usingGps = usingGps,
        isOnline = isOnline,
        windDirection = windDirection,
        windSpeed = windSpeed,
        gust = gust,
        waveHeight = waveHeight,
        wavePeriod = wavePeriod,
        onOpenMap = onOpenMap,
    )
}

@Composable
private fun ForecastLocationMap(
    latitude: Double,
    longitude: Double,
    locationName: String,
    nearestLandDistanceNm: Double?,
    selectedTime: String?,
    usingGps: Boolean,
    isOnline: Boolean,
    windDirection: Double?,
    windSpeed: Double?,
    gust: Double?,
    waveHeight: Double?,
    wavePeriod: Double?,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val mapZoom = remember(nearestLandDistanceNm) {
        when {
            nearestLandDistanceNm == null -> 12.0
            nearestLandDistanceNm <= 1.5 -> 15.5
            nearestLandDistanceNm <= 4.0 -> 13.5
            nearestLandDistanceNm <= 10.0 -> 11.8
            else -> 10.5
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val activeMap = mapView
        if (activeMap == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> activeMap.onResume()
                    Lifecycle.Event.ON_PAUSE -> activeMap.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                activeMap.onResume()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                activeMap.onPause()
            }
        }
    }

    Box(
        modifier = modifier
            .height(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setMultiTouchControls(false)
                    setUseDataConnection(isOnline)
                    setBackgroundColor(AndroidColor.rgb(3, 31, 48))
                    isTilesScaledToDpi = true
                    setTileSource(StreetTileSource)
                    overlayManager.tilesOverlay.loadingBackgroundColor = AndroidColor.rgb(3, 31, 48)
                    overlayManager.tilesOverlay.loadingLineColor = AndroidColor.rgb(8, 52, 74)
                    minZoomLevel = 3.0
                    maxZoomLevel = StreetTileSource.maximumZoomLevel.toDouble()
                    controller.setZoom(mapZoom)
                    controller.setCenter(GeoPoint(latitude, longitude))
                    contentDescription = String.format(
                        Locale.US,
                        "Forecast map centered at %s, %.4f, %.4f",
                        locationName,
                        latitude,
                        longitude,
                    )
                    setOnTouchListener { _, _ -> true }
                    mapView = this
                }
            },
            update = { map ->
                map.setUseDataConnection(isOnline)
                map.controller.setCenter(GeoPoint(latitude, longitude))
                if (kotlin.math.abs(map.zoomLevelDouble - mapZoom) > 0.1) {
                    map.controller.setZoom(mapZoom)
                }
                map.contentDescription = String.format(
                    Locale.US,
                    "Forecast map centered at %s, %.4f, %.4f",
                    locationName,
                    latitude,
                    longitude,
                )
                map.invalidate()
            },
            onRelease = { map ->
                if (mapView === map) mapView = null
                map.onPause()
                map.onDetach()
            },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            color = colors.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text(
                    text = locationName,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = selectedTime?.let { "FORECAST · $it" }
                        ?: if (usingGps) "VESSEL FORECAST POINT" else "SAVED FORECAST POINT",
                    color = colors.accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clickable(onClick = onOpenMap),
            color = colors.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = "Open forecast point on full chart",
                tint = colors.textPrimary,
                modifier = Modifier.padding(10.dp).size(24.dp),
            )
        }

        WindCompass(
            directionDegrees = windDirection,
            speedKnots = windSpeed,
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            color = colors.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(9.dp),
        ) {
            Text(
                text = String.format(Locale.US, "%.4f°, %.4f°", latitude, longitude),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                color = colors.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            color = colors.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(9.dp),
        ) {
            Row(
                modifier = Modifier.padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                OverviewPill("GUST", "${formatDecimal(gust)} kn", colors.success)
                OverviewPill(
                    "WAVE",
                    "${formatDecimal(waveHeight)} m · ${formatDecimal(wavePeriod)} s",
                    colors.destination,
                )
            }
        }

        Text(
            text = "MAP · OpenStreetMap",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .background(colors.surface.copy(alpha = 0.78f), RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp),
            color = colors.textMuted,
            fontSize = 8.sp,
        )
    }
}

@Composable
private fun WindCompass(
    directionDegrees: Double?,
    speedKnots: Double?,
    modifier: Modifier = Modifier,
) {
    val colors = MarineTheme.colors
    Box(
        modifier = modifier
            .border(1.dp, colors.border, CircleShape)
            .background(colors.surface.copy(alpha = 0.82f), CircleShape),
    ) {
        Canvas(Modifier.fillMaxSize().padding(13.dp)) {
            drawLine(
                color = colors.border,
                start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = colors.border,
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
            )
        }
        Text("N", Modifier.align(Alignment.TopCenter).padding(top = 5.dp), color = colors.emergency, fontSize = 11.sp)
        Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp), color = colors.textMuted, fontSize = 11.sp)
        Text("W", Modifier.align(Alignment.CenterStart).padding(start = 7.dp), color = colors.textMuted, fontSize = 11.sp)
        Text("E", Modifier.align(Alignment.CenterEnd).padding(end = 7.dp), color = colors.textMuted, fontSize = 11.sp)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(13.dp)
                .background(colors.accent, CircleShape)
                .border(2.dp, colors.textPrimary, CircleShape),
        )
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = "Wind from ${windDirectionCardinal(directionDegrees)}",
            tint = colors.caution,
            modifier = Modifier
                .align(Alignment.Center)
                .size(38.dp)
                .rotate(((directionDegrees ?: 0.0) + 180.0).toFloat()),
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 17.dp),
            color = colors.accent,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = "${formatDecimal(speedKnots)} kn",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                color = colors.onAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OverviewPill(label: String, value: String, color: Color) {
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = "$label  $value",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DaySelector(
    days: List<MarineForecastDay>,
    selectedDate: String,
    onSelect: (String) -> Unit,
) {
    val colors = MarineTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        days.forEach { day ->
            val selected = day.date == selectedDate
            val parsedDate = runCatching { LocalDate.parse(day.date) }.getOrNull()
            Surface(
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .clickable { onSelect(day.date) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) colors.accent else colors.card,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = parsedDate?.format(DAY_FORMATTER) ?: day.date,
                        color = if (selected) colors.onAccent else colors.textPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (parsedDate != null) {
                        Text(
                            text = parsedDate.format(MONTH_FORMATTER),
                            color = if (selected) colors.onAccent.copy(alpha = 0.72f) else colors.textMuted,
                            fontSize = 9.sp,
                        )
                    }
                    val sun = listOfNotNull(day.sunrise?.substringAfter('T'), day.sunset?.substringAfter('T'))
                    if (sun.isNotEmpty()) {
                        Text(
                            text = "☀ ${sun.joinToString(" · ")}",
                            color = if (selected) colors.onAccent else colors.caution,
                            fontSize = 8.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastGrid(
    hours: List<MarineForecastHour>,
    selectedHourTime: String,
    onSelectedHour: (MarineForecastHour) -> Unit,
) {
    val colors = MarineTheme.colors
    val horizontalState = rememberScrollState()
    val columnWidthPx = with(LocalDensity.current) { HOUR_COLUMN_WIDTH.toPx() }
    var scrollInitialized by remember(hours.firstOrNull()?.time) { mutableStateOf(false) }

    LaunchedEffect(hours.firstOrNull()?.time, selectedHourTime, columnWidthPx) {
        if (!scrollInitialized && selectedHourTime.isNotBlank()) {
            val selectedIndex = hours.indexOfFirst { it.time == selectedHourTime }.coerceAtLeast(0)
            horizontalState.scrollTo((selectedIndex * columnWidthPx).roundToInt())
            scrollInitialized = true
        }
    }
    LaunchedEffect(horizontalState, hours, columnWidthPx, scrollInitialized) {
        if (!scrollInitialized) return@LaunchedEffect
        snapshotFlow {
            (horizontalState.value / columnWidthPx).roundToInt().coerceIn(hours.indices)
        }
            .distinctUntilChanged()
            .collect { index -> onSelectedHour(hours[index]) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        color = colors.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(vertical = 6.dp)) {
            ForecastLabels()
            Row(modifier = Modifier.horizontalScroll(horizontalState)) {
                hours.forEachIndexed { index, hour ->
                    ForecastHourColumn(
                        hour = hour,
                        alternate = index % 2 == 1,
                        selected = hour.time == selectedHourTime,
                        onClick = { onSelectedHour(hour) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastLabels() {
    val colors = MarineTheme.colors
    Column(modifier = Modifier.width(76.dp)) {
        Box(modifier = Modifier.height(58.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text("LOCAL\nTIME", modifier = Modifier.padding(start = 9.dp), color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        ForecastLabelCell("WEATHER", 54.dp)
        ForecastLabelCell("WIND", CELL_HEIGHT, "kn")
        ForecastLabelCell("GUST", CELL_HEIGHT, "kn")
        ForecastLabelCell("PRESSURE", CELL_HEIGHT, "hPa")
        ForecastLabelCell("AIR", CELL_HEIGHT, "°C")
        ForecastLabelCell("SEA", CELL_HEIGHT, "°C")
        ForecastLabelCell("WAVES", CELL_HEIGHT, "m")
        ForecastLabelCell("PERIOD", CELL_HEIGHT, "s")
        ForecastLabelCell("SWELL", CELL_HEIGHT, "m")
        ForecastLabelCell("CURRENT", CELL_HEIGHT, "kn")
        ForecastLabelCell("VISIBILITY", CELL_HEIGHT, "km")
        ForecastLabelCell("RAIN", CELL_HEIGHT, "%")
        ForecastLabelCell("TIDE", CELL_HEIGHT, "LAT m")
    }
}

@Composable
private fun ForecastLabelCell(label: String, height: Dp, unit: String? = null) {
    val colors = MarineTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .border(width = 0.5.dp, color = colors.border.copy(alpha = 0.6f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit != null) Text(unit, color = colors.textMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun ForecastHourColumn(
    hour: MarineForecastHour,
    alternate: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MarineTheme.colors
    val localDateTime = remember(hour.time) { runCatching { LocalDateTime.parse(hour.time) }.getOrNull() }
    val tideHeight = remember(hour.time) {
        localDateTime?.atZone(MALDIVES_ZONE)?.toInstant()?.toEpochMilli()?.let(TidePredictor::heightLatMeters)
    }
    val base = if (alternate) colors.card.copy(alpha = 0.72f) else colors.surface

    Column(
        modifier = Modifier
            .width(HOUR_COLUMN_WIDTH)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) colors.accent else Color.Transparent,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(58.dp).background(base),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = localDateTime?.format(HOUR_FORMATTER) ?: "--",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("hour", color = colors.textMuted, fontSize = 8.sp)
            }
        }
        ForecastWeatherCell(hour, base)
        ForecastValueCell(formatDecimal(hour.windSpeedKnots), base.metricTint(colors.success, hour.windSpeedKnots, 25.0), hour.windDirectionDegrees)
        ForecastValueCell(formatDecimal(hour.windGustKnots), base.metricTint(colors.success, hour.windGustKnots, 35.0))
        ForecastValueCell(formatInteger(hour.pressureMslHpa), base.metricTint(colors.caution, hour.pressureMslHpa?.minus(995.0), 35.0))
        ForecastValueCell(formatDecimal(hour.airTemperatureCelsius, "°"), base.metricTint(Color(0xFFFF824D), hour.airTemperatureCelsius?.minus(20.0), 15.0))
        ForecastValueCell(formatDecimal(hour.seaSurfaceTemperatureCelsius, "°"), base.metricTint(colors.destination, hour.seaSurfaceTemperatureCelsius?.minus(20.0), 15.0))
        ForecastValueCell(formatDecimal(hour.waveHeightMeters), base.metricTint(Color(0xFF168CFF), hour.waveHeightMeters, 3.0), hour.waveDirectionDegrees)
        ForecastValueCell(formatDecimal(hour.wavePeriodSeconds), base)
        ForecastValueCell(formatDecimal(hour.swellHeightMeters), base.metricTint(Color(0xFF615CFF), hour.swellHeightMeters, 3.0), hour.swellDirectionDegrees)
        ForecastValueCell(formatDecimal(hour.oceanCurrentKnots), base.metricTint(colors.accent, hour.oceanCurrentKnots, 2.0), hour.oceanCurrentDirectionDegrees)
        ForecastValueCell(formatDecimal(hour.visibilityMeters?.div(1_000.0)), base)
        ForecastValueCell(hour.precipitationProbabilityPercent?.let { "$it" } ?: "—", base.metricTint(Color(0xFF4AA8FF), hour.precipitationProbabilityPercent?.toDouble(), 100.0))
        ForecastValueCell(formatDecimal(tideHeight), base.metricTint(colors.caution, tideHeight, 1.5))
    }
}

@Composable
private fun TideTrendCard(
    hours: List<MarineForecastHour>,
    selectedHourTime: String,
) {
    val colors = MarineTheme.colors
    val samples = remember(hours) {
        hours.mapNotNull { hour ->
            runCatching {
                val dateTime = LocalDateTime.parse(hour.time)
                val epoch = dateTime.atZone(MALDIVES_ZONE).toInstant().toEpochMilli()
                Triple(hour.time, dateTime.format(HOUR_FORMATTER), TidePredictor.heightLatMeters(epoch))
            }.getOrNull()
        }
    }
    if (samples.size < 2) return

    val low = samples.minBy { it.third }
    val high = samples.maxBy { it.third }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        color = colors.card,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TIDE TREND · LAT",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Low ${formatDecimal(low.third)} m @ ${low.second}  ·  High ${formatDecimal(high.third)} m @ ${high.second}",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(top = 10.dp),
            ) {
                val minHeight = samples.minOf { it.third } - 0.08
                val maxHeight = samples.maxOf { it.third } + 0.08
                val range = (maxHeight - minHeight).coerceAtLeast(0.1)
                fun x(index: Int): Float = index / (samples.size - 1f) * size.width
                fun y(value: Double): Float = size.height - (((value - minHeight) / range).toFloat() * size.height)

                val path = Path().apply {
                    samples.forEachIndexed { index, sample ->
                        val pointX = x(index)
                        val pointY = y(sample.third)
                        if (index == 0) moveTo(pointX, pointY) else lineTo(pointX, pointY)
                    }
                }
                drawLine(
                    color = colors.border,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawPath(path = path, color = colors.accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                samples.forEachIndexed { index, sample ->
                    val selected = sample.first == selectedHourTime
                    drawCircle(
                        color = if (selected) colors.caution else colors.accent,
                        radius = if (selected) 6.dp.toPx() else 3.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x(index), y(sample.third)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastWeatherCell(hour: MarineForecastHour, background: Color) {
    val colors = MarineTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(background)
            .border(0.5.dp, colors.border.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weatherSymbol(hour.weatherCode), fontSize = 20.sp)
            Text(
                text = weatherCodeLabel(hour.weatherCode),
                color = colors.textSecondary,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ForecastValueCell(
    value: String,
    background: Color,
    directionDegrees: Double? = null,
) {
    val colors = MarineTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CELL_HEIGHT)
            .background(background)
            .border(0.5.dp, colors.border.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (directionDegrees != null) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(12.dp).rotate(directionDegrees.toFloat()),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(value, color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ForecastMessage(message: String, offline: Boolean = false) {
    val colors = MarineTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (offline) Icons.Default.CloudOff else Icons.Default.Waves,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(36.dp),
        )
        Text(message, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

private fun Color.metricTint(accent: Color, value: Double?, range: Double): Color {
    val strength = ((value ?: 0.0) / range).coerceIn(0.0, 1.0).toFloat()
    return Color(
        red = red * (1f - strength * 0.44f) + accent.red * strength * 0.44f,
        green = green * (1f - strength * 0.44f) + accent.green * strength * 0.44f,
        blue = blue * (1f - strength * 0.44f) + accent.blue * strength * 0.44f,
        alpha = 1f,
    )
}

private fun formatDecimal(value: Double?, suffix: String = ""): String =
    value?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.1f%s", it, suffix) } ?: "—"

private fun formatInteger(value: Double?): String =
    value?.takeIf(Double::isFinite)?.roundToInt()?.toString() ?: "—"

private fun windDirectionCardinal(direction: Double?): String {
    if (direction == null || !direction.isFinite()) return "—"
    val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return labels[((direction + 22.5) / 45.0).toInt().mod(labels.size)]
}

private fun weatherSymbol(code: Int?): String = when (code) {
    0 -> "☀"
    1, 2 -> "🌤"
    3 -> "☁"
    45, 48 -> "≋"
    in 51..67 -> "🌧"
    in 71..77 -> "❄"
    in 80..82 -> "🌦"
    in 85..86 -> "🌨"
    in 95..99 -> "⛈"
    else -> "·"
}
