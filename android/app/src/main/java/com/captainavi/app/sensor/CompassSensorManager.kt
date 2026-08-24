package com.captainavi.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.captainavi.app.safety.NauticalMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompassReading(
    val headingDegrees: Float = 0f,
    val cardinal: String = "N",
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val isAvailable: Boolean = false,
    val timestamp: Long = 0L
)

/**
 * High-speed hardware compass listener combining Rotation Vector,
 * Geomagnetic Rotation Vector, and Accelerometer/Magnetometer fallbacks.
 * Streams heading at 30-50 Hz with low-pass circular filtering.
 */
class CompassSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val filter = CompassFilter(smoothingFactor = 0.35f)

    private val _compassState = MutableStateFlow(CompassReading())
    val compassState: StateFlow<CompassReading> = _compassState.asStateFlow()

    private var activeSensor: Sensor? = null
    private var isListening = false

    // Fallback buffers for accel + mag
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    init {
        // Probe available sensors
        activeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

        val available = activeSensor != null ||
            (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
             sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)

        _compassState.value = _compassState.value.copy(isAvailable = available)
    }

    fun start() {
        if (isListening) return
        isListening = true
        filter.reset()

        val rotSensor = activeSensor
        if (rotSensor != null) {
            // SENSOR_DELAY_GAME provides ~20-30ms updates for instant responsive dial movement
            sensorManager.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel != null && mag != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
                sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stop() {
        if (!isListening) return
        isListening = false
        sensorManager.unregisterListener(this)
        hasAccel = false
        hasMag = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var rawAzimuthDeg: Float? = null

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthRad = orientationAngles[0]
                rawAzimuthDeg = ((Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0).toFloat()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                hasAccel = true
                if (hasAccel && hasMag) {
                    rawAzimuthDeg = computeAzimuthFromAccelMag()
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                hasMag = true
                if (hasAccel && hasMag) {
                    rawAzimuthDeg = computeAzimuthFromAccelMag()
                }
            }
        }

        if (rawAzimuthDeg != null) {
            val smoothedHeading = filter.update(rawAzimuthDeg)
            val cardinal = NauticalMath.degreesToShortCardinal(smoothedHeading.toDouble())

            _compassState.value = CompassReading(
                headingDegrees = smoothedHeading,
                cardinal = cardinal,
                accuracy = event.accuracy,
                isAvailable = true,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun computeAzimuthFromAccelMag(): Float? {
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            return ((Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0).toFloat()
        }
        return null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        _compassState.value = _compassState.value.copy(accuracy = accuracy)
    }
}
