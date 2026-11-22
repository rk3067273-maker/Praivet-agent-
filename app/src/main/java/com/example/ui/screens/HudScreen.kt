package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.data.hardware.SensorTelemetryState
import com.example.data.hardware.VoiceState
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisSettings
import com.example.ui.AiRunState
import com.example.ui.PendingDeviceAction
import com.example.ui.SentryState
import com.example.ui.components.CoreVisualMode
import com.example.ui.components.HologramCoreCanvas
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CyanArc
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassNavy
import com.example.ui.theme.IceBlue
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.PlasmaTeal
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian

@Composable
fun HudScreen(
    settings: JarvisSettings,
    draftCommand: String,
    messages: List<ChatMessageEntity>,
    telemetry: SensorTelemetryState,
    aiRunState: AiRunState,
    voiceState: VoiceState,
    partialTranscript: String,
    sentryState: SentryState,
    isTorchOn: Boolean,
    pendingAction: PendingDeviceAction?,
    onDraftCommandChange: (String) -> Unit,
    onSendCommand: (String, Uri?) -> Unit,
    onCancelAi: () -> Unit,
    onConfirmPendingAction: () -> Unit,
    onDismissPendingAction: () -> Unit,
    onTapVoiceMic: () -> Unit,
    onStopVoice: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onToggleTorch: () -> Unit,
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    onPermissionDenied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVoiceAction by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingVoiceAction) {
                "SINGLE" -> onTapVoiceMic()
                "WAKE" -> onToggleWakeWord(true)
            }
        } else {
            onPermissionDenied(context.getString(R.string.error_mic_permission))
        }
        pendingVoiceAction = null
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachedImageUri = uri
    }

    fun requestMicAndRun(mode: String) {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            if (mode == "SINGLE") onTapVoiceMic() else onToggleWakeWord(true)
        } else {
            pendingVoiceAction = mode
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages.size, aiRunState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = onDismissPendingAction,
            containerColor = GlassNavy,
            titleContentColor = TacticalAmber,
            textContentColor = TextPrimary,
            title = { Text(pendingAction.title, style = MaterialTheme.typography.titleLarge) },
            text = { Text(pendingAction.description, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = onConfirmPendingAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanArc,
                        contentColor = VoidObsidian
                    ),
                    modifier = Modifier.testTag("confirm_device_action_button")
                ) {
                    Text("Authorize")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissPendingAction,
                    modifier = Modifier.testTag("cancel_device_action_button")
                ) {
                    Text("Deny", color = TextSecondary)
                }
            }
        )
    }

    val coreMode = when {
        sentryState is SentryState.Triggered -> CoreVisualMode.ALERT
        voiceState == VoiceState.LISTENING_COMMAND || voiceState == VoiceState.WAKE_WORD_STANDBY ->
            CoreVisualMode.LISTENING
        aiRunState is AiRunState.Thinking -> CoreVisualMode.THINKING
        aiRunState is AiRunState.Streaming -> CoreVisualMode.STREAMING
        else -> CoreVisualMode.IDLE
    }

    val activeProviderLabel = "${settings.selectedProvider.displayName.uppercase()} • ${settings.activeConfig.modelId}"
    val statusLabel = when {
        sentryState is SentryState.Triggered -> "SENTRY BREACH ALERT"
        voiceState == VoiceState.LISTENING_COMMAND ->
            if (partialTranscript.isNotBlank()) "LISTENING: \"$partialTranscript\"" else "LISTENING FOR COMMAND…"
        voiceState == VoiceState.WAKE_WORD_STANDBY ->
            "WAKE WORD ARMED • SAY \"JARVIS\""
        aiRunState is AiRunState.Thinking -> "NEURAL DISPATCH • ANALYZING…"
        aiRunState is AiRunState.Streaming -> "STREAMING TELEMETRY…"
        else -> activeProviderLabel
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CyanArc
                    )
                    Text(
                        text = stringResource(R.string.hud_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleTorch,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hud_torch_button")
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = if (isTorchOn) "Turn off flashlight" else "Turn on flashlight",
                            tint = if (isTorchOn) TacticalAmber else TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { onToggleTts(!settings.ttsEnabled) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hud_tts_button")
                    ) {
                        Icon(
                            imageVector = if (settings.ttsEnabled) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.AutoMirrored.Filled.VolumeOff
                            },
                            contentDescription = if (settings.ttsEnabled) "Mute voice reply" else "Enable voice reply",
                            tint = if (settings.ttsEnabled) CyanArc else TextSecondary
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hud_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear conversation",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = when (coreMode) {
                    CoreVisualMode.LISTENING -> PlasmaTeal
                    CoreVisualMode.THINKING -> NeuralViolet
                    CoreVisualMode.ALERT -> TacticalAmber
                    else -> CyanArc
                },
                contentPadding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HologramCoreCanvas(
                        mode = coreMode,
                        onTapCore = {
                            if (voiceState == VoiceState.LISTENING_COMMAND) {
                                onStopVoice()
                            } else {
                                requestMicAndRun("SINGLE")
                            }
                        },
                        modifier = Modifier
                            .size(84.dp)
                            .testTag("hologram_core_button")
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = when (coreMode) {
                                CoreVisualMode.LISTENING -> PlasmaTeal
                                CoreVisualMode.THINKING -> NeuralViolet
                                CoreVisualMode.ALERT -> TacticalAmber
                                else -> CyanArc
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TelemetryPill("${telemetry.batteryPercent}% PWR", EmeraldOnline)
                            TelemetryPill(telemetry.networkTransport.uppercase(), IceBlue)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = settings.wakeWordEnabled,
                                onClick = {
                                    if (settings.wakeWordEnabled) {
                                        onToggleWakeWord(false)
                                    } else {
                                        requestMicAndRun("WAKE")
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (settings.wakeWordEnabled) "Wake Word Active" else "Arm \"Hey Jarvis\"",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PlasmaTeal.copy(alpha = 0.20f),
                                    selectedLabelColor = PlasmaTeal,
                                    selectedLeadingIconColor = PlasmaTeal
                                ),
                                modifier = Modifier.testTag("wake_word_chip")
                            )

                            if (aiRunState is AiRunState.Thinking || aiRunState is AiRunState.Streaming) {
                                IconButton(
                                    onClick = onCancelAi,
                                    modifier = Modifier.testTag("cancel_ai_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.StopCircle,
                                        contentDescription = "Cancel AI response",
                                        tint = AlertCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = aiRunState is AiRunState.Error) {
                val errMsg = (aiRunState as? AiRunState.Error)?.message.orEmpty()
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    accentColor = AlertCrimson,
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = "AI Error",
                            tint = AlertCrimson
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissError) {
                            Text("Dismiss", color = CyanArc)
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && aiRunState is AiRunState.Idle) {
                    item {
                        EmptyHudWelcomeCard(
                            onSelectPrompt = { sample -> onSendCommand(sample, null) }
                        )
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    HudMessageBubble(message = msg)
                }

                if (aiRunState is AiRunState.Streaming) {
                    item(key = "streaming_bubble") {
                        HudMessageBubble(
                            message = ChatMessageEntity(
                                role = "jarvis",
                                content = aiRunState.partialText,
                                backendLabel = "${settings.selectedProvider.displayName} • LIVE"
                            )
                        )
                    }
                } else if (aiRunState is AiRunState.Thinking) {
                    item(key = "thinking_bubble") {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(0.75f),
                            accentColor = NeuralViolet,
                            contentPadding = 12.dp
                        ) {
                            Text(
                                text = "Synthesizing tactical response…",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeuralViolet
                            )
                        }
                    }
                }
            }

            val quickPrompts = remember {
                listOf(
                    "System diagnostics",
                    "Turn on flashlight",
                    "Arm Sentry Mode",
                    "Enable Air Gestures",
                    "Daily tactical briefing"
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEach { prompt ->
                    AssistChip(
                        onClick = { onSendCommand(prompt, null) },
                        label = {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = CyanArc,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = GlassNavy.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            AnimatedVisibility(visible = attachedImageUri != null) {
                attachedImageUri?.let { uri ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = "Attached vision input",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, CyanArc, RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Vision payload attached",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanArc,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { attachedImageUri = null }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove attached photo",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("hud_attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = "Attach image for visual analysis",
                        tint = IceBlue
                    )
                }

                OutlinedTextField(
                    value = draftCommand,
                    onValueChange = onDraftCommandChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.hud_input_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onSendCommand(draftCommand, attachedImageUri)
                            attachedImageUri = null
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanArc,
                        unfocusedBorderColor = CyanArc.copy(alpha = 0.35f),
                        focusedContainerColor = GlassNavy.copy(alpha = 0.78f),
                        unfocusedContainerColor = GlassNavy.copy(alpha = 0.60f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hud_command_input")
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (voiceState == VoiceState.LISTENING_COMMAND) {
                            onStopVoice()
                        } else {
                            requestMicAndRun("SINGLE")
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            if (voiceState == VoiceState.LISTENING_COMMAND) {
                                PlasmaTeal.copy(alpha = 0.25f)
                            } else {
                                GlassNavy
                            }
                        )
                        .testTag("hud_mic_button")
                ) {
                    Icon(
                        imageVector = if (voiceState == VoiceState.LISTENING_COMMAND) {
                            Icons.Filled.MicOff
                        } else {
                            Icons.Filled.Mic
                        },
                        contentDescription = "Voice command",
                        tint = if (voiceState == VoiceState.LISTENING_COMMAND) PlasmaTeal else CyanArc
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        onSendCommand(draftCommand, attachedImageUri)
                        attachedImageUri = null
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(CyanArc.copy(alpha = 0.22f))
                        .testTag("hud_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                        tint = CyanArc
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun HudMessageBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    val accent = if (isUser) NeuralViolet else CyanArc

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(0.88f),
            accentColor = accent,
            cornerRadius = 18.dp,
            contentPadding = 12.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "OPERATOR" else "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                    Text(
                        text = message.backendLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                if (!message.imageUri.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(message.imageUri)),
                        contentDescription = "Command image attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )

                if (!message.actionExecuted.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldOnline.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "ACTION: ${message.actionExecuted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldOnline,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHudWelcomeCard(onSelectPrompt: (String) -> Unit) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = CyanArc,
        contentPadding = 16.dp
    ) {
        Column {
            Text(
                text = "HUD READY FOR DIRECTIVES",
                style = MaterialTheme.typography.titleMedium,
                color = CyanArc
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap the Hologram Core to speak, attach an image for multimodal vision analysis, or configure any OpenAI-compatible provider (OpenAI, DeepSeek, OpenRouter, NVIDIA NIM, AgentRouter, Ollama) in Agent.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            listOf(
                "Run a full system diagnostic report",
                "Open Wi-Fi settings for me",
                "Create a 3-step productivity plan"
            ).forEach { sample ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyanArc.copy(alpha = 0.10f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSelectPrompt(sample) }
                ) {
                    Text(
                        text = "-> $sample",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}
