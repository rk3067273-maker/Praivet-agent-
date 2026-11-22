package com.example.data.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

enum class AirGestureType(val label: String) {
    PALM_WAVE_NEXT("Palm Wave -> Next Track"),
    DOUBLE_WAVE_PLAY_PAUSE("Double Wave -> Play / Pause"),
    PALM_HOVER_TORCH("Sustained Hover -> Tactical Torch")
}

data class AirGestureEvent(
    val type: AirGestureType,
    val timestamp: Long = System.currentTimeMillis()
)

data class SensorTelemetryState(
    val hasProximitySensor: Boolean = false,
    val proximitySensorName: String = "Unavailable",
    val proximityMaxRangeCm: Float = 0f,
    val proximityDistanceCm: Float = 0f,
    val proximityAccuracyLabel: String = "Unknown",
    val isProximityNear: Boolean = false,
    val hasAccelerometer: Boolean = false,
    val accelAccuracyLabel: String = "Unknown",
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val accelMagnitude: Float = 9.81f,
    val accelDelta: Float = 0f,
    val hasGyroscope: Boolean = false,
    val hasLightSensor: Boolean = false,
    val ambientLux: Float = 0f,
    val hasCamera: Boolean = false,
    val hasCameraFlash: Boolean = false,
    val hasMicrophone: Boolean = false,
    val hasVibrator: Boolean = false,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val networkTransport: String = "Offline",
    val isLowPowerSensorPolling: Boolean = false,
    val gestureCount: Int = 0,
    val lastGestureLabel: String = "None"
)

class DeviceTelemetryManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val hasVibratorHardware: Boolean by lazy {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vib?.hasVibrator() == true
    }

    private val _telemetry = MutableStateFlow(
        SensorTelemetryState(
            hasProximitySensor = proximitySensor != null,
            proximitySensorName = proximitySensor?.name ?: "Not present",
            proximityMaxRangeCm = proximitySensor?.maximumRange ?: 0f,
            proximityDistanceCm = proximitySensor?.maximumRange ?: 0f,
            hasAccelerometer = accelSensor != null,
            hasGyroscope = gyroSensor != null,
            hasLightSensor = lightSensor != null,
            hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            hasCameraFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
            hasMicrophone = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            hasVibrator = hasVibratorHardware
        )
    )
    val telemetry: StateFlow<SensorTelemetryState> = _telemetry.asStateFlow()

    private val _airGestures = MutableSharedFlow<AirGestureEvent>(extraBufferCapacity = 8)
    val airGestures: SharedFlow<AirGestureEvent> = _airGestures.asSharedFlow()

    private val _motionDeltaEvents = MutableSharedFlow<Float>(extraBufferCapacity = 16)
    val motionDeltaEvents: SharedFlow<Float> = _motionDeltaEvents.asSharedFlow()

    @Volatile
    private var airGesturesEnabled = false

    private var nearStartTimeMs: Long = 0L
    private var wasNear = false
    private var lastWaveTimestampMs: Long = 0L
    private var hoverTriggeredThisHold = false
    private var lastAccelMagnitude = 9.81f
    private var isRegistered = false
    private var lastAccelUiEmitMs: Long = 0L
    private var lastLightUiEmitMs: Long = 0L

    fun startListening() {
        val sm = sensorManager ?: return
        if (isRegistered) return
        proximitySensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        isRegistered = true
        _telemetry.update { it.copy(isLowPowerSensorPolling = false) }
        refreshPowerAndNetwork()
    }

    fun pauseForBackground(keepMotionForSentry: Boolean) {
        val sm = sensorManager ?: return
        sm.unregisterListener(this)
        isRegistered = false
        if (keepMotionForSentry && accelSensor != null) {
            sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
            isRegistered = true
        }
        _telemetry.update { it.copy(isLowPowerSensorPolling = true) }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        isRegistered = false
    }

    fun setAirGesturesEnabled(enabled: Boolean) {
        airGesturesEnabled = enabled
        wasNear = false
        nearStartTimeMs = 0L
        hoverTriggeredThisHold = false
    }

    fun refreshPowerAndNetwork() {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }

        _telemetry.update {
            it.copy(
                batteryPercent = pct,
                isCharging = charging,
                networkTransport = transport
            )
        }
    }

    fun buildTelemetrySummary(): String {
        refreshPowerAndNetwork()
        val t = _telemetry.value
        return "Battery ${t.batteryPercent}% (${if (t.isCharging) "Charging" else "Discharging"}), " +
            "Network: ${t.networkTransport}, " +
            "Accel: ${"%.2f".format(t.accelMagnitude)} m/s², " +
            "Light: ${if (t.hasLightSensor) "${"%.0f".format(t.ambientLux)} lx" else "N/A"}, " +
            "Proximity: ${if (t.hasProximitySensor) "${"%.1f".format(t.proximityDistanceCm)} cm" else "N/A"}"
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.isEmpty()) return
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> handleProximity(event.values[0])
            Sensor.TYPE_ACCELEROMETER -> if (event.values.size >= 3) handleAccelerometer(event.values)
            Sensor.TYPE_LIGHT -> {
                val now = System.currentTimeMillis()
                if (now - lastLightUiEmitMs >= 300L) {
                    lastLightUiEmitMs = now
                    _telemetry.update { it.copy(ambientLux = event.values[0]) }
                }
            }
        }
    }

    private fun handleProximity(distanceCm: Float) {
        val maxRange = proximitySensor?.maximumRange ?: 5f
        val isNear = distanceCm < maxRange && distanceCm < 5f
        val now = System.currentTimeMillis()

        if (isNear && !wasNear) {
            nearStartTimeMs = now
            hoverTriggeredThisHold = false
        } else if (isNear && wasNear && airGesturesEnabled && !hoverTriggeredThisHold) {
            if (now - nearStartTimeMs >= 1000L) {
                hoverTriggeredThisHold = true
                emitGesture(AirGestureType.PALM_HOVER_TORCH)
            }
        } else if (!isNear && wasNear && airGesturesEnabled && !hoverTriggeredThisHold) {
            val holdDuration = now - nearStartTimeMs
            if (holdDuration in 40L..750L) {
                if (now - lastWaveTimestampMs in 120L..900L) {
                    lastWaveTimestampMs = 0L
                    emitGesture(AirGestureType.DOUBLE_WAVE_PLAY_PAUSE)
                } else {
                    lastWaveTimestampMs = now
                    emitGesture(AirGestureType.PALM_WAVE_NEXT)
                }
            }
        }
        wasNear = isNear

        _telemetry.update {
            it.copy(
                proximityDistanceCm = distanceCm,
                isProximityNear = isNear
            )
        }
    }

    private fun handleAccelerometer(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val delta = kotlin.math.abs(magnitude - lastAccelMagnitude)
        lastAccelMagnitude = magnitude

        // Sentry motion detection always runs unthrottled for immediate tamper alerting
        _motionDeltaEvents.tryEmit(delta)

        // UI telemetry updates are throttled (~150ms) to avoid 60Hz Compose recompositions
        val now = System.currentTimeMillis()
        if (now - lastAccelUiEmitMs >= 150L) {
            lastAccelUiEmitMs = now
            _telemetry.update {
                it.copy(
                    accelX = x,
                    accelY = y,
                    accelZ = z,
                    accelMagnitude = magnitude,
                    accelDelta = delta
                )
            }
        }
    }

    private fun emitGesture(type: AirGestureType) {
        _telemetry.update {
            it.copy(
                gestureCount = it.gestureCount + 1,
                lastGestureLabel = type.label
            )
        }
        _airGestures.tryEmit(AirGestureEvent(type))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val label = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
            else -> "No Contact"
        }
        when (sensor?.type) {
            Sensor.TYPE_PROXIMITY -> _telemetry.update { it.copy(proximityAccuracyLabel = label) }
            Sensor.TYPE_ACCELEROMETER -> _telemetry.update { it.copy(accelAccuracyLabel = label) }
        }
    }
}
