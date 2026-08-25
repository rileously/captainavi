package com.captainavi.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.repository.SettingsRepository
import com.captainavi.app.localization.Language
import com.captainavi.app.localization.LanguageManager
import com.captainavi.app.safety.FuelMarginCalculator
import com.captainavi.app.safety.StormAlertEvaluator
import com.captainavi.app.sms.OfflineSmsRules
import com.captainavi.app.ui.components.AppUpdateCard
import com.captainavi.app.ui.components.ScreenHeader
import com.captainavi.app.ui.components.marineTextFieldColors
import com.captainavi.app.ui.theme.MarineTheme
import com.captainavi.app.ui.theme.NightModeState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()
    val settings = app.settingsRepository

    val captainName by settings.captainName.collectAsState()
    val relayUrl by settings.relayUrl.collectAsState()
    val apiSecretKey by settings.apiSecretKey.collectAsState()
    val botToken by settings.telegramBotToken.collectAsState()
    val chatId by settings.telegramChatId.collectAsState()
    val trustedSmsNumber by settings.trustedSmsNumber.collectAsState()
    val smsRequestPhrase by settings.smsRequestPhrase.collectAsState()
    val smsAutoReplyEnabled by settings.smsAutoReplyEnabled.collectAsState()
    val breadcrumbInterval by settings.breadcrumbIntervalMinutes.collectAsState()
    val telegramInterval by settings.telegramUpdateIntervalMinutes.collectAsState()
    val maxDistHome by settings.maxDistanceHomeNm.collectAsState()
    val stationaryMinutes by settings.stationaryThresholdMinutes.collectAsState()
    val tripReferenceDistanceNm by settings.tripReferenceDistanceNm.collectAsState()
    val tripReferenceCostMvr by settings.tripReferenceCostMvr.collectAsState()
    val tripReferenceFuelLiters by settings.tripReferenceFuelLiters.collectAsState()
    val fuelTankLiters by settings.fuelTankLiters.collectAsState()
    val reefWarningsEnabled by settings.reefWarningsEnabled.collectAsState()
    val reefWarningBufferMeters by settings.reefWarningBufferMeters.collectAsState()
    val stormAlertsEnabled by settings.stormAlertsEnabled.collectAsState()
    val stormWaveHeightThresholdMeters by settings.stormWaveHeightThresholdMeters.collectAsState()
    val stormWindGustThresholdKnots by settings.stormWindGustThresholdKnots.collectAsState()
    val simMode by settings.simulationMode.collectAsState()
    val isOnline by app.networkMonitor.isOnline.collectAsState()

    var nameInput by remember(captainName) { mutableStateOf(captainName) }
    var urlInput by remember(relayUrl) { mutableStateOf(relayUrl) }
    var secretKeyInput by remember(apiSecretKey) { mutableStateOf(apiSecretKey) }
    var botTokenInput by remember(botToken) { mutableStateOf(botToken) }
    var chatIdInput by remember(chatId) { mutableStateOf(chatId) }
    var trustedSmsNumberInput by remember(trustedSmsNumber) { mutableStateOf(trustedSmsNumber) }
    var smsRequestPhraseInput by remember(smsRequestPhrase) { mutableStateOf(smsRequestPhrase) }
    var smsAutoReplyInput by remember(smsAutoReplyEnabled) { mutableStateOf(smsAutoReplyEnabled) }
    var telegramInput by remember(telegramInterval) { mutableStateOf(telegramInterval.toString()) }
    var maxDistInput by remember(maxDistHome) { mutableStateOf(maxDistHome.toString()) }
    var stationaryInput by remember(stationaryMinutes) { mutableStateOf(stationaryMinutes.toString()) }
    var tripDistanceInput by remember(tripReferenceDistanceNm) { mutableStateOf(tripReferenceDistanceNm.toString()) }
    var tripCostInput by remember(tripReferenceCostMvr) { mutableStateOf(tripReferenceCostMvr.toString()) }
    var tripFuelInput by remember(tripReferenceFuelLiters) { mutableStateOf(tripReferenceFuelLiters.toString()) }
    var fuelTankInput by remember(fuelTankLiters) { mutableStateOf(fuelTankLiters.toString()) }
    var reefWarningsInput by remember(reefWarningsEnabled) { mutableStateOf(reefWarningsEnabled) }
    var reefBufferInput by remember(reefWarningBufferMeters) { mutableStateOf(reefWarningBufferMeters.toString()) }
    var stormAlertsInput by remember(stormAlertsEnabled) { mutableStateOf(stormAlertsEnabled) }
    var stormWaveThresholdInput by remember(stormWaveHeightThresholdMeters) {
        mutableStateOf(stormWaveHeightThresholdMeters.toString())
    }
    var stormWindGustThresholdInput by remember(stormWindGustThresholdKnots) {
        mutableStateOf(stormWindGustThresholdKnots.toString())
    }
    var simModeInput by remember(simMode) { mutableStateOf(simMode) }

    var saveToast by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var telegramTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var smsPermissionMessage by remember { mutableStateOf<String?>(null) }
    var smsPermissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        smsPermissionsGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        smsPermissionMessage = if (smsPermissionsGranted) {
            "Offline SMS access enabled"
        } else {
            "SMS access was not fully enabled; automatic replies will stay unavailable"
        }
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsPermissionsGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val fieldColors = marineTextFieldColors()
    val cardColors = CardDefaults.cardColors(containerColor = colors.card)
    val cardBorder = androidx.compose.foundation.BorderStroke(1.dp, colors.border)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(title = "Configuration")

        AppUpdateCard()

        saveToast?.let { (success, msg) ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color = if (success) colors.success else colors.emergency,
            )
        }

        telegramTestResult?.let { (success, msg) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (success) colors.success.copy(alpha = 0.2f) else colors.emergency.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (success) colors.success else colors.emergency,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (success) colors.success else colors.emergency
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = RoundedCornerShape(10.dp), border = cardBorder) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance", style = MaterialTheme.typography.labelLarge, color = colors.caution)
                SettingSwitchRow(
                    title = "Night mode",
                    subtitle = "Red-on-black to preserve night vision.",
                    checked = NightModeState.isNightMode,
                    onCheckedChange = {
                        NightModeState.isNightMode = it
                        settings.setNightMode(it)
                    }
                )
                SettingSwitchRow(
                    title = "Dhivehi",
                    subtitle = "Use ދިވެހި labels on the Helm and Chart.",
                    checked = LanguageManager.isDhivehi,
                    onCheckedChange = { enabled ->
                        val language = if (enabled) Language.DHIVEHI else Language.ENGLISH
                        LanguageManager.setLanguage(language)
                        settings.setLanguageCode(language.code)
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = RoundedCornerShape(10.dp), border = cardBorder) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Captain", style = MaterialTheme.typography.labelLarge, color = colors.caution)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Captain name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = RoundedCornerShape(10.dp), border = cardBorder) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offline SMS safety", style = MaterialTheme.typography.labelLarge, color = colors.success)
                Text(
                    text = "Send the captain's latest GPS position without internet. A trusted phone can request it by sending the exact private phrase below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                OutlinedTextField(
                    value = trustedSmsNumberInput,
                    onValueChange = { trustedSmsNumberInput = it.filter { character -> character.isDigit() || character == '+' || character == ' ' || character == '-' } },
                    label = { Text("Trusted requester's number") },
                    supportingText = { Text("Enter the other phone, not this captain's number. Example: +960 777 1234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = smsRequestPhraseInput,
                    onValueChange = { smsRequestPhraseInput = it },
                    label = { Text("Exact location request phrase") },
                    supportingText = { Text("Only this number and phrase can trigger a reply; matching ignores letter case") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    singleLine = true,
                )
                SettingSwitchRow(
                    title = "Automatic trusted reply",
                    subtitle = "Reply from the phone's SIM with the latest position. Repeated requests are limited to one reply every 5 minutes.",
                    checked = smsAutoReplyInput,
                    onCheckedChange = { smsAutoReplyInput = it },
                )
                Button(
                    enabled = !smsPermissionsGranted,
                    onClick = {
                        smsPermissionLauncher.launch(
                            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.success,
                        contentColor = colors.onAccent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = if (smsPermissionsGranted) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (smsPermissionsGranted) "SMS access enabled" else "Enable offline SMS access")
                }
                smsPermissionMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (smsPermissionsGranted) colors.success else colors.emergency,
                    )
                }
                Text(
                    text = "One compact carrier SMS is sent per reply; charges may apply. No incoming message history is stored or uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.caution,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = RoundedCornerShape(10.dp), border = cardBorder) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Family alerts", style = MaterialTheme.typography.labelLarge, color = colors.accent)
                Text(
                    text = "Bot token and family chat ID for live location and SOS messages in Telegram.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                OutlinedTextField(
                    value = botTokenInput,
                    onValueChange = { botTokenInput = it },
                    label = { Text("Bot token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = chatIdInput,
                    onValueChange = { chatIdInput = it },
                    label = { Text("Telegram chat ID") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Button(
                    enabled = isOnline,
                    onClick = {
                        scope.launch {
                            app.relayApiClient.updateConfig(urlInput, secretKeyInput, botTokenInput, chatIdInput)
                            val res = app.relayApiClient.sendDirectTelegram(
                                "<b>Captain Avi connection test</b>\n\nBot is connected. Family will receive trip locations and emergency alerts here."
                            )
                            telegramTestResult = if (res.isSuccess) {
                                true to "Test message sent to your Telegram group."
                            } else {
                                false to (res.exceptionOrNull()?.message ?: "Failed to connect to Telegram")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.caution, contentColor = colors.onAccent),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isOnline) "Send test message" else "Offline — test unavailable",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = RoundedCornerShape(10.dp), border = cardBorder) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Relay (optional)", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Relay worker URL") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = RoundedCornerShape(10.dp), border = cardBorder) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trip calculator", style = MaterialTheme.typography.labelLarge, color = colors.accent)
                Text(
                    text = "Calibrate from one known one-way trip. Selecting a destination on the chart will scale cost and petrol use by its live straight-line distance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                OutlinedTextField(
                    value = tripDistanceInput,
                    onValueChange = { tripDistanceInput = it.decimalCharactersOnly() },
                    label = { Text("Reference distance (NM)") },
                    supportingText = { Text("Example: Naivaadhoo to Kulhudhufushi — 11 NM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tripCostInput,
                        onValueChange = { tripCostInput = it.decimalCharactersOnly() },
                        label = { Text("Trip cost (MVR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = tripFuelInput,
                        onValueChange = { tripFuelInput = it.decimalCharactersOnly() },
                        label = { Text("Petrol used (L)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                    )
                }
                OutlinedTextField(
                    value = fuelTankInput,
                    onValueChange = { fuelTankInput = it.decimalCharactersOnly() },
                    label = { Text("Fuel tank capacity (L)") },
                    supportingText = { Text("Used for live return-margin warnings during a trip") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                Text(
                    text = "Default calibration: 11 NM = 1,000 MVR = 25 L; tank ${FuelMarginCalculator.DEFAULT_TANK_LITERS.toInt()} L. Estimates do not account for weather, load, currents, detours, or fuel reserve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.caution,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = RoundedCornerShape(10.dp), border = cardBorder) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Safety and tracking", style = MaterialTheme.typography.labelLarge, color = colors.caution)
                OutlinedTextField(
                    value = telegramInput,
                    onValueChange = { telegramInput = it.filter(Char::isDigit) },
                    label = { Text("Telegram status refresh (min)") },
                    supportingText = {
                        Text("5–120 min. Each refresh edits the same live trip card; only alerts and trip completion create new messages.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxDistInput,
                        onValueChange = { maxDistInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Safe radius (NM)") },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = stationaryInput,
                        onValueChange = { stationaryInput = it.filter(Char::isDigit) },
                        label = { Text("Drift alert (min)") },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors
                    )
                }
                SettingSwitchRow(
                    title = "Official reef warnings",
                    subtitle = "Warn near or inside bundled OneMap reef boundaries.",
                    checked = reefWarningsInput,
                    onCheckedChange = { reefWarningsInput = it }
                )
                OutlinedTextField(
                    value = reefBufferInput,
                    onValueChange = { reefBufferInput = it.filter(Char::isDigit) },
                    label = { Text("Reef approach buffer (m)") },
                    supportingText = { Text("100–2000 m; default 300 m") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    enabled = reefWarningsInput,
                )
                Text(
                    text = "Reef boundaries provide awareness only. They have no surveyed depths, safety contours, chart datum, or navigation corrections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.emergency,
                )
                SettingSwitchRow(
                    title = "Storm & high-wave alerts",
                    subtitle = "Notify if the marine forecast crosses your rough-sea thresholds nearby.",
                    checked = stormAlertsInput,
                    onCheckedChange = { stormAlertsInput = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stormWaveThresholdInput,
                        onValueChange = { stormWaveThresholdInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Wave/swell alert (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                        enabled = stormAlertsInput,
                    )
                    OutlinedTextField(
                        value = stormWindGustThresholdInput,
                        onValueChange = { stormWindGustThresholdInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Wind gust alert (kt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                        enabled = stormAlertsInput,
                    )
                }
                Text(
                    text = "Checks the forecast near your last known position every few hours while online. A forecast, not an observation — always verify before departure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                SettingSwitchRow(
                    title = "Simulation mode",
                    subtitle = "Test a voyage around home harbour without real GPS movement.",
                    checked = simModeInput,
                    onCheckedChange = { simModeInput = it }
                )
            }
        }

        Button(
            onClick = {
                val referenceDistance = tripDistanceInput.toDoubleOrNull()
                val referenceCost = tripCostInput.toDoubleOrNull()
                val referenceFuel = tripFuelInput.toDoubleOrNull()
                val tankLiters = fuelTankInput.toDoubleOrNull()
                if (
                    referenceDistance == null || referenceDistance <= 0.0 ||
                    referenceCost == null || referenceCost < 0.0 ||
                    referenceFuel == null || referenceFuel < 0.0
                ) {
                    saveToast = false to "Trip distance must be above zero; cost and petrol cannot be negative"
                    return@Button
                }
                if (tankLiters == null || tankLiters <= 0.0) {
                    saveToast = false to "Fuel tank capacity must be above zero"
                    return@Button
                }
                val smsConfigured = trustedSmsNumberInput.isNotBlank() || smsAutoReplyInput
                if (smsConfigured && !OfflineSmsRules.isValidPhoneNumber(trustedSmsNumberInput)) {
                    saveToast = false to "Enter a valid trusted SMS number (7–15 digits)"
                    return@Button
                }
                if (
                    smsConfigured &&
                    smsRequestPhraseInput.trim().length < OfflineSmsRules.MIN_REQUEST_PHRASE_LENGTH
                ) {
                    saveToast = false to "The SMS request phrase must be at least 6 characters"
                    return@Button
                }
                settings.updateSettings(
                    name = nameInput,
                    url = urlInput,
                    secretKey = secretKeyInput,
                    botToken = botTokenInput,
                    chatId = chatIdInput,
                    trustedSmsNumber = trustedSmsNumberInput,
                    smsRequestPhrase = smsRequestPhraseInput,
                    smsAutoReplyEnabled = smsAutoReplyInput,
                    breadcrumbInterval = breadcrumbInterval,
                    telegramInterval = telegramInput.toIntOrNull()
                        ?: SettingsRepository.DEFAULT_TELEGRAM_UPDATE_MINUTES,
                    maxDistHome = maxDistInput.toDoubleOrNull() ?: 15.0,
                    stationaryMinutes = stationaryInput.toIntOrNull() ?: 30,
                    tripReferenceDistanceNm = referenceDistance,
                    tripReferenceCostMvr = referenceCost,
                    tripReferenceFuelLiters = referenceFuel,
                    fuelTankLiters = tankLiters,
                    reefWarningsEnabled = reefWarningsInput,
                    reefWarningBufferMeters = reefBufferInput.toIntOrNull() ?: 300,
                    stormAlertsEnabled = stormAlertsInput,
                    stormWaveHeightThresholdMeters = stormWaveThresholdInput.toDoubleOrNull()
                        ?: StormAlertEvaluator.DEFAULT_WAVE_HEIGHT_THRESHOLD_METERS,
                    stormWindGustThresholdKnots = stormWindGustThresholdInput.toDoubleOrNull()
                        ?: StormAlertEvaluator.DEFAULT_WIND_GUST_THRESHOLD_KNOTS,
                    simMode = simModeInput
                )
                app.relayApiClient.updateConfig(urlInput, secretKeyInput, botTokenInput, chatIdInput)
                saveToast = true to if (smsAutoReplyInput && !smsPermissionsGranted) {
                    "Settings saved — enable SMS access to receive and answer requests"
                } else {
                    "Settings saved"
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save configuration", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun String.decimalCharactersOnly(): String {
    var decimalSeen = false
    return filter { character ->
        when {
            character.isDigit() -> true
            character == '.' && !decimalSeen -> {
                decimalSeen = true
                true
            }
            else -> false
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MarineTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.success,
                uncheckedTrackColor = colors.surface
            )
        )
    }
}
