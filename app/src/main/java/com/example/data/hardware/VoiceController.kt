package com.example.data.hardware

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "VoiceController"
private const val COMMAND_DEDUP_WINDOW_MS = 1500L

enum class VoiceState {
    IDLE,
    LISTENING_COMMAND,
    WAKE_WORD_STANDBY,
    SPEAKING
}

class VoiceController(private val context: Context) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ttsReady = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private var wakeModeActive = false
    private var onCommandCaptured: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    private var lastCommandText: String = ""
    private var lastCommandTimestampMs: Long = 0L

    val isSpeechRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setPitch(0.95f)
            tts?.setSpeechRate(1.06f)
        } else {
            Log.w(TAG, "TTS initialization failed with status=$status")
        }
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        val clean = text.replace(Regex("""[*_`#]"""), "").take(450)
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun startCommandListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSpeechRecognitionAvailable) {
            onError("Speech recognition service is unavailable on this device.")
            return
        }
        wakeModeActive = false
        onCommandCaptured = onResult
        onErrorCallback = onError
        mainHandler.post { launchRecognizer(VoiceState.LISTENING_COMMAND) }
    }

    fun setWakeWordMode(
        enabled: Boolean,
        onWakeCommand: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        wakeModeActive = enabled
        onCommandCaptured = onWakeCommand
        onErrorCallback = onError
        if (enabled) {
            if (!isSpeechRecognitionAvailable) {
                wakeModeActive = false
                onError("Continuous wake word requires Google Speech Recognition service.")
                return
            }
            mainHandler.post { launchRecognizer(VoiceState.WAKE_WORD_STANDBY) }
        } else {
            stopListening()
        }
    }

    fun stopListening() {
        wakeModeActive = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e)
            }
            _partialTranscript.value = ""
            _voiceState.value = VoiceState.IDLE
        }
    }

    private fun dispatchIfNotDuplicate(spoken: String) {
        val normalized = spoken.trim()
        if (normalized.isEmpty()) return
        val now = System.currentTimeMillis()
        if (normalized.equals(lastCommandText, ignoreCase = true) &&
            now - lastCommandTimestampMs < COMMAND_DEDUP_WINDOW_MS
        ) {
            return
        }
        lastCommandText = normalized
        lastCommandTimestampMs = now
        onCommandCaptured?.invoke(normalized)
    }

    private fun launchRecognizer(targetState: VoiceState) {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = targetState
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        _partialTranscript.value = ""
                        val recoverableWakeError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        if (wakeModeActive && recoverableWakeError) {
                            mainHandler.postDelayed({
                                if (wakeModeActive) launchRecognizer(VoiceState.WAKE_WORD_STANDBY)
                            }, 500L)
                        } else {
                            wakeModeActive = false
                            _voiceState.value = VoiceState.IDLE
                            if (error != SpeechRecognizer.ERROR_CLIENT &&
                                error != SpeechRecognizer.ERROR_NO_MATCH
                            ) {
                                onErrorCallback?.invoke("Voice input interrupted (error $error). Tap mic to retry.")
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            .orEmpty()
                        val spoken = matches.firstOrNull().orEmpty().trim()
                        _partialTranscript.value = ""

                        if (wakeModeActive) {
                            val lower = spoken.lowercase(Locale.ROOT)
                            val wakeTriggers = listOf("hey jarvis", "ok jarvis", "jarvis")
                            val trigger = wakeTriggers.firstOrNull { lower.contains(it) }
                            if (trigger != null) {
                                val stripped = spoken.replace(
                                    Regex("(?i)(hey|ok)?\\s*jarvis[,:]?\\s*"),
                                    ""
                                ).trim()
                                val command = stripped.ifBlank { "Status report, Jarvis." }
                                dispatchIfNotDuplicate(command)
                            }
                            mainHandler.postDelayed({
                                if (wakeModeActive) launchRecognizer(VoiceState.WAKE_WORD_STANDBY)
                            }, 600L)
                        } else {
                            _voiceState.value = VoiceState.IDLE
                            dispatchIfNotDuplicate(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer start failed", e)
            wakeModeActive = false
            _voiceState.value = VoiceState.IDLE
            onErrorCallback?.invoke("Unable to start speech recognizer.")
        }
    }

    fun release() {
        wakeModeActive = false
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
