package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ai.AiHttpException
import com.example.data.ai.AiProviderId
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.JarvisAiRepository
import com.example.data.ai.JsonCodec
import com.example.data.ai.ModelCapability
import com.example.data.ai.ModelCapabilityRegistry
import com.example.data.ai.ToolExecutionHandler
import com.example.data.ai.WorkflowStepItem
import com.example.data.hardware.AirGestureType
import com.example.data.hardware.DeviceActionExecutor
import com.example.data.hardware.DeviceTelemetryManager
import com.example.data.hardware.SensorTelemetryState
import com.example.data.hardware.SentryCameraHelper
import com.example.data.hardware.VoiceController
import com.example.data.hardware.VoiceState
import com.example.data.local.AiUsageLogEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConfigSaveResult
import com.example.data.local.ExportBundleInput
import com.example.data.local.JarvisDataPortability
import com.example.data.local.JarvisDatabase
import com.example.data.local.JarvisPreferences
import com.example.data.local.JarvisSettings
import com.example.data.local.MemoryEntryEntity
import com.example.data.local.SentryEventEntity
import com.example.data.local.UsageSummaryState
import com.example.data.local.WorkflowEntity
import com.example.data.memory.HermesMemoryManager
import com.example.data.memory.MemoryCategory
import com.example.data.memory.MemoryWriteResult
import com.example.data.memory.RoomMemoryStore
import com.example.data.security.AndroidKeyStoreApiKeyStore
import com.example.data.security.KeyStorageResult
import com.example.data.security.SecretRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "JarvisViewModel"
private const val KEY_DRAFT_COMMAND = "hud_draft_command"

sealed interface AiRunState {
    data object Idle : AiRunState
    data object Thinking : AiRunState
    data class Streaming(val partialText: String) : AiRunState
    data class Error(val message: String) : AiRunState
}

sealed interface SentryState {
    data object Disarmed : SentryState
    data class Arming(val secondsLeft: Int) : SentryState
    data object Armed : SentryState
    data class Triggered(val peakDelta: Float, val timestamp: Long) : SentryState
}

data class WorkflowExecutionState(
    val workflowId: Long,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val currentStepLabel: String
)

data class PendingDeviceAction(
    val token: String,
    val title: String,
    val description: String
)

class JarvisViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = JarvisDatabase.getInstance(appContext)
    private val dao = database.jarvisDao()
    private val preferences = JarvisPreferences(appContext)
    private val keyStore = AndroidKeyStoreApiKeyStore(appContext)
    private val aiRepository = JarvisAiRepository()
    private val memoryManager = HermesMemoryManager(RoomMemoryStore(dao))
    private val telemetryManager = DeviceTelemetryManager(appContext)
    private val actionExecutor = DeviceActionExecutor(appContext)
    private val voiceController = VoiceController(appContext)
    private val sentryCameraHelper = SentryCameraHelper(appContext)

    val draftCommand: StateFlow<String> = savedStateHandle.getStateFlow(KEY_DRAFT_COMMAND, "")

    val settings: StateFlow<JarvisSettings> = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JarvisSettings()
    )

    val messages: StateFlow<List<ChatMessageEntity>> = dao.observeMessages().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val workflows: StateFlow<List<WorkflowEntity>> = dao.observeWorkflows().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val sentryEvents: StateFlow<List<SentryEventEntity>> = dao.observeSentryEvents().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val memories: StateFlow<List<MemoryEntryEntity>> = dao.observeMemories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val usageLogs: StateFlow<List<AiUsageLogEntity>> = dao.observeUsageLogs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val usageSummary: StateFlow<UsageSummaryState> = combine(usageLogs, settings) { logs, s ->
        UsageSummaryState.fromLogs(logs, s.usageWarningTokenLimit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageSummaryState())

    val telemetry: StateFlow<SensorTelemetryState> = telemetryManager.telemetry
    val voiceState: StateFlow<VoiceState> = voiceController.voiceState
    val partialTranscript: StateFlow<String> = voiceController.partialTranscript

    private val _keyRevision = MutableStateFlow(0)
    val maskedApiKeyForSelectedProvider: StateFlow<String> = combine(settings, _keyRevision) { s, _ ->
        SecretRedactor.maskForDisplay(keyStore.getApiKey(s.selectedProvider.name))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Not configured")

    val hasApiKeyForSelectedProvider: StateFlow<Boolean> = combine(settings, _keyRevision) { s, _ ->
        keyStore.hasApiKey(s.selectedProvider.name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _aiRunState = MutableStateFlow<AiRunState>(AiRunState.Idle)
    val aiRunState: StateFlow<AiRunState> = _aiRunState.asStateFlow()

    private val _sentryState = MutableStateFlow<SentryState>(SentryState.Disarmed)
    val sentryState: StateFlow<SentryState> = _sentryState.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _statusBanner = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val statusBanner: SharedFlow<String> = _statusBanner.asSharedFlow()

    private val _workflowGenerating = MutableStateFlow(false)
    val workflowGenerating: StateFlow<Boolean> = _workflowGenerating.asStateFlow()

    private val _workflowExecution = MutableStateFlow<WorkflowExecutionState?>(null)
    val workflowExecution: StateFlow<WorkflowExecutionState?> = _workflowExecution.asStateFlow()

    private val _connectionTesting = MutableStateFlow(false)
    val connectionTesting: StateFlow<Boolean> = _connectionTesting.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    private val _pendingActionConfirmation = MutableStateFlow<PendingDeviceAction?>(null)
    val pendingActionConfirmation: StateFlow<PendingDeviceAction?> = _pendingActionConfirmation.asStateFlow()

    private val _sentryCaptureSignal = MutableSharedFlow<Float>(extraBufferCapacity = 4)
    val sentryCaptureSignal: SharedFlow<Float> = _sentryCaptureSignal.asSharedFlow()

    private val _databaseIntegrityHealthy = MutableStateFlow<Boolean?>(null)
    val databaseIntegrityHealthy: StateFlow<Boolean?> = _databaseIntegrityHealthy.asStateFlow()

    private var activeAiJob: Job? = null
    private var sentryArmJob: Job? = null
    private var workflowExecJob: Job? = null
    private var lastSentryTriggerMs: Long = 0L

    init {
        telemetryManager.startListening()
        verifyDatabaseAndRecoverInterruption()
        migrateLegacyKeysIfPresent()
        seedInitialWorkflowsIfNeeded()
        observeSettingsSync()
        observeAirGestures()
        observeSentryMotion()
    }

    fun onAppForeground() {
        telemetryManager.startListening()
    }

    fun onAppBackground() {
        val keepMotion = _sentryState.value is SentryState.Armed || _sentryState.value is SentryState.Arming
        telemetryManager.pauseForBackground(keepMotionForSentry = keepMotion)
        if (voiceController.voiceState.value == VoiceState.LISTENING_COMMAND) {
            voiceController.stopListening()
        }
    }

    fun updateDraftCommand(text: String) {
        savedStateHandle[KEY_DRAFT_COMMAND] = text
    }

    private fun verifyDatabaseAndRecoverInterruption() {
        viewModelScope.launch(Dispatchers.IO) {
            val healthy = database.verifyQuickIntegrity()
            _databaseIntegrityHealthy.value = healthy
            if (!healthy) {
                _statusBanner.tryEmit("Database integrity warning detected; export backup recommended.")
            }
            val current = preferences.settings.first()
            if (current.wasAiInFlightOnLastExit) {
                preferences.setAiInFlight(false)
                _statusBanner.tryEmit(
                    "Recovered cleanly after previous interrupted AI request; pending actions were not re-run."
                )
            }
        }
    }

    private fun migrateLegacyKeysIfPresent() {
        viewModelScope.launch(Dispatchers.IO) {
            AiProviderId.entries.forEach { provider ->
                keyStore.migrateLegacyPlaintextKey(
                    providerKey = provider.name,
                    legacyPrefsName = "jarvis_legacy_keys",
                    legacyKeyName = "api_key_${provider.name}"
                )
            }
            _keyRevision.value++
        }
    }

    private fun seedInitialWorkflowsIfNeeded() {
        viewModelScope.launch {
            if (dao.countWorkflows() == 0) {
                val defaults = listOf(
                    WorkflowEntity(
                        title = "Morning HUD Briefing",
                        description = "Scans device telemetry, prepares voice briefing, and activates ambient gesture control.",
                        stepsJson = serializeSteps(
                            listOf(
                                WorkflowStepItem("Run System Diagnostics", "DIAGNOSTIC_SCAN"),
                                WorkflowStepItem("Enable Zero-Touch Air Gestures", "ENABLE_GESTURES"),
                                WorkflowStepItem("Deliver Vocal Status Briefing", "SPEAK_BRIEFING")
                            )
                        ),
                        triggerLabel = "VOICE",
                        isPinned = true
                    ),
                    WorkflowEntity(
                        title = "Tactical Torch & Focus",
                        description = "Engages tactical flashlight beam and verifies power/network readiness.",
                        stepsJson = serializeSteps(
                            listOf(
                                WorkflowStepItem("Engage Tactical Torch", "TORCH_ON"),
                                WorkflowStepItem("Run Power & Network Check", "DIAGNOSTIC_SCAN")
                            )
                        ),
                        triggerLabel = "MANUAL",
                        isPinned = false
                    ),
                    WorkflowEntity(
                        title = "Night Sentry Lockdown",
                        description = "Secures flashlight off and arms the accelerometer intruder watchdog.",
                        stepsJson = serializeSteps(
                            listOf(
                                WorkflowStepItem("Extinguish Tactical Torch", "TORCH_OFF"),
                                WorkflowStepItem("Arm Tactical Sentry Watchdog", "ARM_SENTRY")
                            )
                        ),
                        triggerLabel = "SENTRY",
                        isPinned = true
                    )
                )
                defaults.forEach { dao.insertWorkflow(it) }
            }
        }
    }

    private fun observeSettingsSync() {
        viewModelScope.launch {
            settings.collect { current ->
                telemetryManager.setAirGesturesEnabled(current.airGesturesEnabled)
            }
        }
    }

    private fun observeAirGestures() {
        viewModelScope.launch {
            telemetryManager.airGestures.collect { event ->
                val feedback = when (event.type) {
                    AirGestureType.PALM_WAVE_NEXT ->
                        actionExecutor.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    AirGestureType.DOUBLE_WAVE_PLAY_PAUSE ->
                        actionExecutor.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    AirGestureType.PALM_HOVER_TORCH -> {
                        val (_, msg) = actionExecutor.toggleTorch()
                        _isTorchOn.value = actionExecutor.isTorchOn
                        msg
                    }
                }
                actionExecutor.pulseHaptic(75L)
                _statusBanner.tryEmit("Air Gesture: $feedback")
            }
        }
    }

    private fun observeSentryMotion() {
        viewModelScope.launch {
            telemetryManager.motionDeltaEvents.collect { delta ->
                if (_sentryState.value is SentryState.Armed) {
                    val threshold = settings.value.sentrySensitivity
                    val now = System.currentTimeMillis()
                    if (delta >= threshold && now - lastSentryTriggerMs > 4_000L) {
                        lastSentryTriggerMs = now
                        _sentryState.value = SentryState.Triggered(delta, now)
                        actionExecutor.pulseHaptic(650L)
                        voiceController.speak(appContext.getString(R.string.sentry_armed_warning))
                        _sentryCaptureSignal.tryEmit(delta)
                    }
                }
            }
        }
    }

    fun onSentryCaptureReady(lifecycleOwner: LifecycleOwner, peakDelta: Float) {
        viewModelScope.launch {
            val photoPath = sentryCameraHelper.captureIntruderPhoto(lifecycleOwner)
            val summary = "Motion spike ${"%.2f".format(peakDelta)} m/s² detected"
            dao.insertSentryEvent(
                SentryEventEntity(
                    peakDeltaMs2 = peakDelta,
                    photoPath = photoPath,
                    summary = summary
                )
            )
            val webhook = settings.value.webhookUrl
            if (webhook.isNotBlank()) {
                aiRepository.postWebhookAlert(webhook, "JARVIS Sentry Alert: $summary")
            }
            _statusBanner.tryEmit(
                if (photoPath != null) "Sentry Alert: Intruder snapshot logged."
                else "Sentry Alert: Movement logged."
            )
        }
    }

    fun sendCommand(rawPrompt: String, imageUri: Uri? = null) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() && imageUri == null) {
            _statusBanner.tryEmit(appContext.getString(R.string.error_empty_command))
            return
        }

        val effectivePrompt = prompt.ifEmpty { "Analyze this image and report key tactical details." }
        savedStateHandle[KEY_DRAFT_COMMAND] = ""
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            val currentSettings = settings.first()
            val provider = currentSettings.selectedProvider
            val modelId = currentSettings.activeConfig.modelId
            val apiKey = keyStore.getApiKey(provider.name)
            val historySnapshot = messages.value.takeLast(12).map { it.role to it.content }
            val backendBadge = "${provider.displayName} • $modelId"

            dao.insertMessage(
                ChatMessageEntity(
                    role = "user",
                    content = effectivePrompt,
                    backendLabel = backendBadge,
                    imageUri = imageUri?.toString()
                )
            )

            preferences.setAiInFlight(true)
            _aiRunState.value = AiRunState.Thinking
            val accumulated = StringBuilder()

            val toolHandler = ToolExecutionHandler { toolName, args ->
                executeSafeToolCall(toolName, args)
            }

            try {
                val bitmap = imageUri?.let { decodeBitmap(it) }
                val telemetrySummary = telemetryManager.buildTelemetrySummary()
                val memoryBlock = memoryManager.buildPromptMemoryBlock(limit = 12)

                val outcome = aiRepository.executeChatTurn(
                    prompt = effectivePrompt,
                    history = historySnapshot,
                    bitmap = bitmap,
                    telemetrySummary = telemetrySummary,
                    hermesMemoryBlock = memoryBlock,
                    settings = currentSettings,
                    apiKey = apiKey,
                    toolHandler = toolHandler,
                    onStreamDelta = { delta ->
                        accumulated.append(delta)
                        _aiRunState.value = AiRunState.Streaming(accumulated.toString())
                    }
                )

                val finalRawText = outcome.text.ifBlank { accumulated.toString() }.trim()
                val parsed = actionExecutor.parseActionTag(finalRawText)
                val actionTags = mutableListOf<String>()
                if (outcome.executedToolLabels.isNotEmpty()) {
                    actionTags.addAll(outcome.executedToolLabels)
                }

                if (outcome.pendingConfirmationTool != null) {
                    val pending = outcome.pendingConfirmationTool
                    _pendingActionConfirmation.value = PendingDeviceAction(
                        token = pending.legacyActionToken,
                        title = "Authorize AI Tool Action",
                        description = describeAction(pending.legacyActionToken)
                    )
                    actionTags.add("Awaiting confirmation: ${pending.toolName}")
                } else if (parsed.actionToken != null) {
                    val token = parsed.actionToken
                    if (requiresUserConfirmation(token)) {
                        _pendingActionConfirmation.value = PendingDeviceAction(
                            token = token,
                            title = "Confirm Device Action",
                            description = describeAction(token)
                        )
                        actionTags.add("Awaiting confirmation: $token")
                    } else {
                        val res = actionExecutor.executeActionToken(
                            token = token,
                            onArmSentry = { armSentry() },
                            onEnableGestures = { setAirGesturesEnabled(true) }
                        )
                        _isTorchOn.value = actionExecutor.isTorchOn
                        actionTags.add(res)
                    }
                }

                val finalContent = buildString {
                    append(parsed.cleanText.ifBlank { actionTags.firstOrNull() ?: "Command acknowledged." })
                    if (outcome.finishReason == "length") {
                        append("\n\n[Truncated: Max completion token budget reached. Increase Max Tokens in Agent Settings.]")
                    }
                }

                dao.insertMessage(
                    ChatMessageEntity(
                        role = "jarvis",
                        content = finalContent,
                        backendLabel = backendBadge,
                        actionExecuted = actionTags.joinToString(" • ").ifBlank { null }
                    )
                )

                dao.insertUsageLog(
                    AiUsageLogEntity(
                        providerId = provider.name,
                        modelId = modelId,
                        promptTokens = outcome.usage?.promptTokens ?: 0,
                        completionTokens = outcome.usage?.completionTokens ?: 0,
                        totalTokens = outcome.usage?.totalTokens ?: 0,
                        tokenCountsExact = outcome.usage?.isExact == true,
                        latencyMs = System.currentTimeMillis() - startMs,
                        succeeded = true,
                        httpStatusCode = 200,
                        rateLimitInfo = outcome.rateLimitSummary
                    )
                )

                _aiRunState.value = AiRunState.Idle
                if (currentSettings.ttsEnabled && parsed.cleanText.isNotBlank()) {
                    voiceController.speak(parsed.cleanText)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI inference timed out", e)
                recordFailedUsage(provider.name, modelId, startMs, 408, "Timed out after 60s")
                _aiRunState.value = AiRunState.Error(
                    "Core timed out after 60 seconds. Check network or provider endpoint."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiHttpException) {
                Log.e(TAG, "AI HTTP error status=${e.statusCode} category=${e.category}")
                recordFailedUsage(provider.name, modelId, startMs, e.statusCode, e.category.name)
                _aiRunState.value = AiRunState.Error(e.userMessage)
            } catch (e: Exception) {
                val secrets = listOfNotNull(keyStore.getApiKey(provider.name))
                val safeMsg = SecretRedactor.redact(
                    e.localizedMessage ?: "AI core error occurred.",
                    secrets
                )
                Log.e(TAG, "AI inference failed: $safeMsg")
                recordFailedUsage(provider.name, modelId, startMs, null, safeMsg.take(120))
                _aiRunState.value = AiRunState.Error(safeMsg)
            } finally {
                preferences.setAiInFlight(false)
            }
        }
    }

    private suspend fun recordFailedUsage(
        providerId: String,
        modelId: String,
        startMs: Long,
        statusCode: Int?,
        errSummary: String
    ) {
        runCatching {
            dao.insertUsageLog(
                AiUsageLogEntity(
                    providerId = providerId,
                    modelId = modelId,
                    promptTokens = 0,
                    completionTokens = 0,
                    totalTokens = 0,
                    tokenCountsExact = false,
                    latencyMs = System.currentTimeMillis() - startMs,
                    succeeded = false,
                    httpStatusCode = statusCode,
                    errorSummary = errSummary
                )
            )
        }
    }

    private fun executeSafeToolCall(toolName: String, args: Map<String, Any?>): Result<String> {
        return when (toolName) {
            "get_device_telemetry" -> Result.success(telemetryManager.buildTelemetrySummary())
            "set_flashlight" -> {
                val enabled = args["enabled"] as? Boolean ?: return Result.failure(IllegalArgumentException("Missing enabled"))
                val (ok, msg) = actionExecutor.toggleTorch(enabled)
                _isTorchOn.value = actionExecutor.isTorchOn
                if (ok) Result.success(msg) else Result.failure(IllegalStateException(msg))
            }
            "media_control" -> {
                val cmd = args["command"] as? String
                val code = if (cmd == "next") KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                Result.success(actionExecutor.dispatchMediaKey(code))
            }
            "enable_air_gestures" -> {
                setAirGesturesEnabled(true)
                Result.success("Zero-Touch Air Gestures enabled")
            }
            else -> Result.failure(IllegalArgumentException("Tool '$toolName' cannot run without confirmation."))
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingActionConfirmation.value ?: return
        _pendingActionConfirmation.value = null
        val result = actionExecutor.executeActionToken(
            token = pending.token,
            onArmSentry = { armSentry() },
            onEnableGestures = { setAirGesturesEnabled(true) }
        )
        _isTorchOn.value = actionExecutor.isTorchOn
        _statusBanner.tryEmit(result)
    }

    fun dismissPendingAction() {
        _pendingActionConfirmation.value = null
        _statusBanner.tryEmit("Action cancelled by operator.")
    }

    private fun requiresUserConfirmation(token: String): Boolean {
        return token == "ARM_SENTRY" ||
            token == "OPEN_SETTINGS" ||
            token == "OPEN_WIFI" ||
            token == "OPEN_BLUETOOTH" ||
            token.startsWith("WEB_SEARCH:")
    }

    private fun describeAction(token: String): String {
        return when {
            token == "ARM_SENTRY" -> "Arm Sentry Mode accelerometer watchdog and camera deterrent?"
            token == "OPEN_WIFI" -> "Open Android Wi-Fi system settings?"
            token == "OPEN_BLUETOOTH" -> "Open Android Bluetooth system settings?"
            token == "OPEN_SETTINGS" -> "Open Android system settings?"
            token.startsWith("WEB_SEARCH:") ->
                "Launch external web search for \"${token.removePrefix("WEB_SEARCH:")}\"?"
            else -> "Execute device action: $token?"
        }
    }

    fun cancelAiGeneration() {
        activeAiJob?.cancel()
        activeAiJob = null
        viewModelScope.launch { preferences.setAiInFlight(false) }
        _aiRunState.value = AiRunState.Idle
        _statusBanner.tryEmit("AI dispatch cancelled.")
    }

    fun dismissAiError() {
        if (_aiRunState.value is AiRunState.Error) {
            _aiRunState.value = AiRunState.Idle
        }
    }

    fun startSingleVoiceCommand() {
        voiceController.stopSpeaking()
        voiceController.startCommandListening(
            onResult = { spoken -> sendCommand(spoken) },
            onError = { msg -> _statusBanner.tryEmit(msg) }
        )
    }

    fun stopVoiceListening() {
        voiceController.stopListening()
        viewModelScope.launch { preferences.setWakeWordEnabled(false) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setWakeWordEnabled(enabled)
            voiceController.setWakeWordMode(
                enabled = enabled,
                onWakeCommand = { command -> sendCommand(command) },
                onError = { msg ->
                    _statusBanner.tryEmit(msg)
                    viewModelScope.launch { preferences.setWakeWordEnabled(false) }
                }
            )
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setTtsEnabled(enabled)
            if (!enabled) voiceController.stopSpeaking()
        }
    }

    fun setAirGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAirGesturesEnabled(enabled)
            _statusBanner.tryEmit(
                if (enabled) "Zero-Touch Air Gestures Armed" else "Zero-Touch Air Gestures Disarmed"
            )
        }
    }

    fun toggleTorch() {
        val (_, msg) = actionExecutor.toggleTorch()
        _isTorchOn.value = actionExecutor.isTorchOn
        _statusBanner.tryEmit(msg)
    }

    fun armSentry() {
        if (_sentryState.value is SentryState.Arming || _sentryState.value is SentryState.Armed) return
        sentryArmJob?.cancel()
        sentryArmJob = viewModelScope.launch {
            for (sec in 5 downTo 1) {
                _sentryState.value = SentryState.Arming(sec)
                delay(1_000L)
            }
            _sentryState.value = SentryState.Armed
            actionExecutor.pulseHaptic(120L)
            if (settings.value.ttsEnabled) {
                voiceController.speak("Sentry protocol armed.")
            }
        }
    }

    fun disarmSentry() {
        sentryArmJob?.cancel()
        voiceController.stopSpeaking()
        _sentryState.value = SentryState.Disarmed
        _statusBanner.tryEmit("Sentry Mode disarmed.")
    }

    fun setSentrySensitivity(value: Float) {
        viewModelScope.launch { preferences.setSentrySensitivity(value) }
    }

    fun selectProvider(provider: AiProviderId) {
        _connectionTestResult.value = null
        viewModelScope.launch { preferences.setProvider(provider) }
    }

    fun saveActiveProviderConfig(
        provider: AiProviderId,
        baseUrl: String,
        modelId: String,
        maxTokens: Int,
        temperature: Float
    ) {
        viewModelScope.launch {
            when (val res = preferences.saveProviderConfig(provider, baseUrl, modelId, maxTokens, temperature)) {
                ConfigSaveResult.Saved ->
                    _statusBanner.tryEmit("${provider.displayName} configuration saved.")
                is ConfigSaveResult.ValidationError ->
                    _statusBanner.tryEmit(res.reason)
            }
        }
    }

    fun saveApiKeyForProvider(provider: AiProviderId, rawKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = keyStore.saveApiKey(provider.name, rawKey)) {
                KeyStorageResult.Success -> {
                    _keyRevision.value++
                    _statusBanner.tryEmit("${provider.displayName} API key encrypted in Android Keystore.")
                }
                KeyStorageResult.IgnoredEmptyInput ->
                    _statusBanner.tryEmit("Blank input ignored; existing key preserved.")
                is KeyStorageResult.Failure ->
                    _statusBanner.tryEmit(res.reason)
            }
        }
    }

    fun clearApiKeyForProvider(provider: AiProviderId) {
        viewModelScope.launch(Dispatchers.IO) {
            keyStore.clearApiKey(provider.name)
            _keyRevision.value++
            _statusBanner.tryEmit("${provider.displayName} API key cleared.")
        }
    }

    fun testProviderConnection(
        provider: AiProviderId,
        draftBaseUrl: String,
        draftModelId: String,
        draftMaxTokens: Int,
        draftTemperature: Float
    ) {
        viewModelScope.launch {
            _connectionTesting.value = true
            _connectionTestResult.value = null
            val saveRes = preferences.saveProviderConfig(
                provider = provider,
                baseUrl = draftBaseUrl,
                modelId = draftModelId,
                maxTokens = draftMaxTokens,
                temperature = draftTemperature
            )
            if (saveRes is ConfigSaveResult.ValidationError) {
                _connectionTesting.value = false
                _connectionTestResult.value = ConnectionTestResult(
                    success = false,
                    httpStatusCode = null,
                    latencyMs = 0L,
                    normalizedChatUrl = draftBaseUrl,
                    discoveredModels = emptyList(),
                    message = saveRes.reason
                )
                return@launch
            }
            val current = settings.first()
            val apiKey = keyStore.getApiKey(provider.name)
            val result = aiRepository.testProviderConnection(current, apiKey)
            _connectionTestResult.value = result
            _connectionTesting.value = false
        }
    }

    fun setMaxAgentSteps(steps: Int) {
        viewModelScope.launch { preferences.setMaxAgentSteps(steps) }
    }

    fun setUsageWarningTokenLimit(limit: Int) {
        viewModelScope.launch { preferences.setUsageWarningTokenLimit(limit) }
    }

    fun updateWebhookUrl(url: String) {
        viewModelScope.launch {
            when (val res = preferences.setWebhookUrl(url)) {
                ConfigSaveResult.Saved -> _statusBanner.tryEmit("Alert webhook updated.")
                is ConfigSaveResult.ValidationError -> _statusBanner.tryEmit(res.reason)
            }
        }
    }

    // Hermes Memory Management
    fun saveMemory(category: MemoryCategory, key: String, content: String, isPinned: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = memoryManager.saveUserMemory(category, key, content, isPinned)) {
                is MemoryWriteResult.Saved -> _statusBanner.tryEmit("Saved to Hermes Memory (${res.normalizedKey}).")
                is MemoryWriteResult.Rejected -> _statusBanner.tryEmit(res.reason)
            }
        }
    }

    fun updateMemoryEntry(
        entry: MemoryEntryEntity,
        category: MemoryCategory,
        key: String,
        content: String,
        isPinned: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = memoryManager.editMemory(entry, category, key, content, isPinned)) {
                is MemoryWriteResult.Saved -> _statusBanner.tryEmit("Updated Hermes Memory (${res.normalizedKey}).")
                is MemoryWriteResult.Rejected -> _statusBanner.tryEmit(res.reason)
            }
        }
    }

    fun deleteMemoryEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryManager.deleteMemory(id)
            _statusBanner.tryEmit("Memory entry deleted.")
        }
    }

    fun importMemoriesJson(rawJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = memoryManager.importMemoriesJson(rawJson)
            _statusBanner.tryEmit(res.summaryMessage)
        }
    }

    fun exportAllDataToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bundleJson = buildSanitizedExportJson()
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bundleJson.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Unable to open destination file.")
                _statusBanner.tryEmit("Sanitized data export saved (API keys excluded).")
            } catch (e: Exception) {
                Log.e(TAG, "Data export failed", e)
                _statusBanner.tryEmit("Export failed: ${e.localizedMessage ?: "I/O error"}")
            }
        }
    }

    suspend fun buildSanitizedExportJson(): String = withContext(Dispatchers.IO) {
        val currentSettings = settings.first()
        val secrets = AiProviderId.entries.mapNotNull { keyStore.getApiKey(it.name) }
        JarvisDataPortability.buildSanitizedExportJson(
            input = ExportBundleInput(
                settings = currentSettings,
                messages = dao.getAllMessagesOnce(),
                memories = dao.getAllMemoriesOnce(),
                workflows = dao.getAllWorkflowsOnce(),
                usageLogs = dao.getAllUsageLogsOnce()
            ),
            knownSecretsToRedact = secrets
        )
    }

    fun clearTemporaryCache() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { appContext.cacheDir.deleteRecursively() }
            _aiRunState.value = AiRunState.Idle
            _statusBanner.tryEmit("Temporary caches cleared.")
        }
    }

    fun revokeAllCredentialsAndWebhook() {
        viewModelScope.launch(Dispatchers.IO) {
            AiProviderId.entries.forEach { keyStore.clearApiKey(it.name) }
            preferences.setWebhookUrl("")
            _keyRevision.value++
            _connectionTestResult.value = null
            _statusBanner.tryEmit("All stored API keys and remote webhook cleared.")
        }
    }

    fun wipeAllLocalData() {
        viewModelScope.launch(Dispatchers.IO) {
            activeAiJob?.cancel()
            workflowExecJob?.cancel()
            sentryArmJob?.cancel()
            _sentryState.value = SentryState.Disarmed
            _aiRunState.value = AiRunState.Idle
            savedStateHandle[KEY_DRAFT_COMMAND] = ""

            dao.wipeAllUserTables()
            preferences.clearAllPreferences()
            AiProviderId.entries.forEach { keyStore.clearApiKey(it.name) }
            File(appContext.filesDir, "sentry_captures").deleteRecursively()
            runCatching { appContext.cacheDir.deleteRecursively() }
            _keyRevision.value++
            _connectionTestResult.value = null
            _statusBanner.tryEmit(
                "All local chats, memories, keys, usage logs, and photos wiped."
            )
        }
    }

    fun generateWorkflow(goal: String) {
        val cleanGoal = goal.trim()
        if (cleanGoal.isBlank()) {
            _statusBanner.tryEmit("Describe a workflow goal first.")
            return
        }
        viewModelScope.launch {
            _workflowGenerating.value = true
            try {
                val planned = aiRepository.generateStructuredWorkflow(cleanGoal)
                dao.insertWorkflow(
                    WorkflowEntity(
                        title = planned.title,
                        description = planned.description,
                        stepsJson = serializeSteps(planned.steps),
                        triggerLabel = planned.triggerLabel,
                        isPinned = false
                    )
                )
                _statusBanner.tryEmit("Workflow '${planned.title}' synthesized.")
            } catch (e: TimeoutCancellationException) {
                _statusBanner.tryEmit("Workflow synthesis timed out. Try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Workflow synthesis failed", e)
                _statusBanner.tryEmit(e.localizedMessage ?: "Workflow synthesis failed.")
            } finally {
                _workflowGenerating.value = false
            }
        }
    }

    fun executeWorkflow(workflow: WorkflowEntity) {
        if (_workflowExecution.value != null) return
        val maxSteps = settings.value.maxAgentSteps.coerceIn(1, 10)
        val steps = parseSteps(workflow.stepsJson).take(maxSteps)
        if (steps.isEmpty()) return

        workflowExecJob?.cancel()
        workflowExecJob = viewModelScope.launch {
            try {
                steps.forEachIndexed { idx, step ->
                    _workflowExecution.value = WorkflowExecutionState(
                        workflowId = workflow.id,
                        currentStepIndex = idx,
                        totalSteps = steps.size,
                        currentStepLabel = step.label
                    )
                    val verified = executeStepAction(step.actionCode)
                    if (!verified) {
                        _statusBanner.tryEmit("Step halted: '${step.label}' could not be verified.")
                        _workflowExecution.value = null
                        return@launch
                    }
                    delay(750L)
                }
                dao.updateWorkflowLastRun(workflow.id, System.currentTimeMillis())
                _workflowExecution.value = null
                _statusBanner.tryEmit("Workflow '${workflow.title}' completed (${steps.size} verified steps).")
            } catch (e: CancellationException) {
                _workflowExecution.value = null
                throw e
            }
        }
    }

    fun cancelWorkflowExecution() {
        workflowExecJob?.cancel()
        workflowExecJob = null
        _workflowExecution.value = null
        _statusBanner.tryEmit("Workflow execution cancelled.")
    }

    private fun executeStepAction(actionCode: String): Boolean {
        return when (actionCode) {
            "DIAGNOSTIC_SCAN" -> {
                telemetryManager.refreshPowerAndNetwork()
                true
            }
            "TORCH_ON" -> {
                val (ok, msg) = actionExecutor.toggleTorch(true)
                _isTorchOn.value = actionExecutor.isTorchOn
                if (!ok) _statusBanner.tryEmit(msg)
                ok
            }
            "TORCH_OFF" -> {
                val (ok, _) = actionExecutor.toggleTorch(false)
                _isTorchOn.value = actionExecutor.isTorchOn
                ok || !actionExecutor.isTorchOn
            }
            "ENABLE_GESTURES" -> {
                setAirGesturesEnabled(true)
                true
            }
            "ARM_SENTRY" -> {
                armSentry()
                true
            }
            "MEDIA_PLAY_PAUSE" -> {
                actionExecutor.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                true
            }
            "SPEAK_BRIEFING" -> {
                val summary = telemetryManager.buildTelemetrySummary()
                voiceController.speak("J.A.R.V.I.S. tactical briefing. $summary")
                true
            }
            else -> false
        }
    }

    fun deleteWorkflow(id: Long) {
        viewModelScope.launch { dao.deleteWorkflow(id) }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            dao.clearMessages()
            _statusBanner.tryEmit("HUD conversation cleared.")
        }
    }

    fun deleteSentryEvent(event: SentryEventEntity) {
        viewModelScope.launch {
            event.photoPath?.let { path -> runCatching { File(path).delete() } }
            dao.deleteSentryEvent(event.id)
        }
    }

    fun refreshPowerAndNetwork() {
        telemetryManager.refreshPowerAndNetwork()
    }

    fun emitBanner(message: String) {
        _statusBanner.tryEmit(message)
    }

    private suspend fun decodeBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(appContext.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(appContext.contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode attached image", e)
            null
        }
    }

    fun parseSteps(stepsJson: String): List<WorkflowStepItem> {
        val list = JsonCodec.parseArrayOrNull(stepsJson) ?: return emptyList()
        return list.mapIndexedNotNull { idx, item ->
            val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
            WorkflowStepItem(
                label = (map["label"] as? String)?.ifBlank { "Step ${idx + 1}" } ?: "Step ${idx + 1}",
                actionCode = (map["actionCode"] as? String)?.ifBlank { "DIAGNOSTIC_SCAN" } ?: "DIAGNOSTIC_SCAN"
            )
        }
    }

    private fun serializeSteps(steps: List<WorkflowStepItem>): String {
        return JsonCodec.toJson(
            steps.map { mapOf("label" to it.label, "actionCode" to it.actionCode) }
        )
    }

    fun resolveCapabilityForSelected(): ModelCapability {
        val s = settings.value
        return ModelCapabilityRegistry.resolveCapability(s.selectedProvider, s.activeConfig.modelId)
    }

    override fun onCleared() {
        super.onCleared()
        telemetryManager.stopListening()
        voiceController.release()
    }
}
