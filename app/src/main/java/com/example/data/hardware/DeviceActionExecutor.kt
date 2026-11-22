package com.example.data.hardware

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.net.toUri

private const val TAG = "DeviceActionExec"

data class ParsedAiReply(
    val cleanText: String,
    val actionToken: String?
)

class DeviceActionExecutor(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    var isTorchOn: Boolean = false
        private set

    fun hasTorchHardware(): Boolean {
        return cameraManager != null &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    fun parseActionTag(rawText: String): ParsedAiReply {
        val regex = Regex("""\[ACTION:([A-Z0-9_]+(?::[^\]]+)?)\]""")
        val match = regex.find(rawText)
        val token = match?.groupValues?.getOrNull(1)
        val cleaned = rawText.replace(regex, "").trim()
        return ParsedAiReply(cleanText = cleaned, actionToken = token)
    }

    fun toggleTorch(enable: Boolean? = null): Pair<Boolean, String> {
        val targetState = enable ?: !isTorchOn
        val cm = cameraManager
            ?: return false to "Camera service unavailable on this device."
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return false to "Camera flash hardware unavailable on this device."
        }
        return try {
            val backCameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false to "No flash-capable camera found."
            cm.setTorchMode(backCameraId, targetState)
            isTorchOn = targetState
            true to if (targetState) "Tactical Torch: ON" else "Tactical Torch: OFF"
        } catch (e: Exception) {
            Log.e(TAG, "Torch toggle failed", e)
            false to "Torch control unavailable: ${e.localizedMessage ?: "hardware busy"}"
        }
    }

    fun dispatchMediaKey(keyCode: Int): String {
        val am = audioManager ?: return "Audio service unavailable on this device."
        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        am.dispatchMediaKeyEvent(downEvent)
        am.dispatchMediaKeyEvent(upEvent)
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Media: Next Track Dispatched"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Media: Play/Pause Toggled"
            else -> "Media Key Dispatched"
        }
    }

    fun pulseHaptic(durationMs: Long = 180L) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic pulse skipped", e)
        }
    }

    fun executeActionToken(
        token: String,
        onArmSentry: () -> Unit,
        onEnableGestures: () -> Unit
    ): String {
        return when {
            token == "TORCH_ON" -> toggleTorch(true).second
            token == "TORCH_OFF" -> toggleTorch(false).second
            token == "MEDIA_NEXT" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            token == "MEDIA_PLAY_PAUSE" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            token == "ARM_SENTRY" -> {
                onArmSentry()
                "Sentry Mode: Countdown Initiated"
            }
            token == "ENABLE_GESTURES" -> {
                onEnableGestures()
                "Zero-Touch Air Gestures: Enabled"
            }
            token == "OPEN_WIFI" -> launchSettings(Settings.ACTION_WIFI_SETTINGS, "Opened Wi-Fi Settings")
            token == "OPEN_BLUETOOTH" -> launchSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Opened Bluetooth Settings")
            token == "OPEN_SETTINGS" -> launchSettings(Settings.ACTION_SETTINGS, "Opened System Settings")
            token.startsWith("WEB_SEARCH:") -> {
                val query = token.removePrefix("WEB_SEARCH:").trim()
                launchWebSearch(query)
            }
            else -> "Ignored unsupported action: $token"
        }
    }

    private fun launchSettings(action: String, successLabel: String): String {
        return try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            successLabel
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open $action", e)
            "Unable to open requested system screen."
        }
    }

    private fun launchWebSearch(query: String): String {
        val clean = query.take(120).trim()
        if (clean.isBlank()) return "Empty search query ignored."
        return try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, clean)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
            "Web Search: $clean"
        } catch (_: Exception) {
            try {
                val encoded = java.net.URLEncoder.encode(clean, "UTF-8")
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://www.google.com/search?q=$encoded".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
                "Browser Search: $clean"
            } catch (e: Exception) {
                Log.e(TAG, "Web search failed", e)
                "No browser available for search."
            }
        }
    }
}
