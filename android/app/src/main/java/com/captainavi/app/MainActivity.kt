package com.captainavi.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.captainavi.app.safety.NauticalMath
import com.captainavi.app.sensor.CompassSensorManager
import com.captainavi.app.service.MarineLocationService
import com.captainavi.app.ui.navigation.CaptainAviNavigation
import com.captainavi.app.ui.theme.CaptainAviTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var compassSensorManager: CompassSensorManager
    private var locationCallback: LocationCallback? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startDirectGpsListener()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        compassSensorManager = CompassSensorManager(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                compassSensorManager.compassState.collect { reading ->
                    if (reading.isAvailable) {
                        MarineLocationService.updateCompassHeading(
                            heading = reading.headingDegrees,
                            cardinal = reading.cardinal,
                            isAvailable = true
                        )
                    }
                }
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestRequiredPermissions()

        setContent {
            CaptainAviTheme {
                CaptainAviNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        compassSensorManager.start()
        startDirectGpsListener()
    }

    override fun onPause() {
        super.onPause()
        compassSensorManager.stop()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    private fun readBatteryPct(): Int {
        val batteryIntent: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 100
    }

    @SuppressLint("MissingPermission")
    private fun startDirectGpsListener() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) return

        // While a trip is being tracked, the foreground service already streams GPS
        // into the shared telemetry flow — running a second listener here would waste battery.
        if (MarineLocationService.telemetry.value.isTracking) return

        // 1. Fetch last known location immediately
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                val speed = NauticalMath.metersPerSecondToKnots(it.speed)
                MarineLocationService.updateRealLocation(
                    it.latitude, it.longitude, speed, it.bearing, it.accuracy,
                    application as CaptainAviApp, readBatteryPct()
                )
            }
        }

        // 2. Continuous real-time location stream (throttled interval, high accuracy)
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val normalProfile = MarineLocationService.GPS_NORMAL_PROFILE
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            normalProfile.intervalMillis,
        )
            .setMinUpdateIntervalMillis(normalProfile.intervalMillis)
            .setMinUpdateDistanceMeters(normalProfile.minUpdateDistanceMeters)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val speed = NauticalMath.metersPerSecondToKnots(loc.speed)
                    MarineLocationService.updateRealLocation(
                        loc.latitude, loc.longitude, speed, loc.bearing, loc.accuracy,
                        application as CaptainAviApp, readBatteryPct()
                    )
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startDirectGpsListener()
        }
    }
}
