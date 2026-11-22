package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiProviderId
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.ModelCapabilityRegistry
import com.example.data.ai.WorkflowStepItem
import com.example.data.local.JarvisSettings
import com.example.data.local.WorkflowEntity
import com.example.ui.WorkflowExecutionState
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CyanArc
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassNavy
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.PlasmaTeal
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import kotlin.math.roundToInt

@Composable
fun WorkflowsScreen(
    settings: JarvisSettings,
    maskedApiKey: String,
    hasApiKey: Boolean,
    workflows: List<WorkflowEntity>,
    workflowGenerating: Boolean,
    workflowExecution: WorkflowExecutionState?,
    connectionTesting: Boolean,
    connectionTestResult: ConnectionTestResult?,
    parseSteps: (String) -> List<WorkflowStepItem>,
    onSelectProvider: (AiProviderId) -> Unit,
    onSaveProviderConfig: (AiProviderId, String, String, Int, Float) -> Unit,
    onSaveApiKey: (AiProviderId, String) -> Unit,
    onClearApiKey: (AiProviderId) -> Unit,
    onTestConnection: (AiProviderId, String, String, Int, Float) -> Unit,
    onSetMaxAgentSteps: (Int) -> Unit,
    onSaveWebhook: (String) -> Unit,
    onGenerateWorkflow: (String) -> Unit,
    onExecuteWorkflow: (WorkflowEntity) -> Unit,
    onCancelWorkflow: () -> Unit,
    onDeleteWorkflow: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedProvider = settings.selectedProvider
    val activeConfig = settings.activeConfig

    var baseUrlInput by remember(selectedProvider, activeConfig.baseUrl) {
        mutableStateOf(activeConfig.baseUrl)
    }
    var modelInput by remember(selectedProvider, activeConfig.modelId) {
        mutableStateOf(activeConfig.modelId)
    }
    var maxTokensDraft by remember(selectedProvider, activeConfig.maxTokens) {
        mutableIntStateOf(activeConfig.maxTokens)
    }
    var temperatureDraft by remember(selectedProvider, activeConfig.temperature) {
        mutableFloatStateOf(activeConfig.temperature)
    }
    var apiKeyInput by remember(selectedProvider) { mutableStateOf("") }
    var workflowGoalInput by remember { mutableStateOf("") }
    var webhookInput by remember(settings.webhookUrl) { mutableStateOf(settings.webhookUrl) }

    val capability = remember(selectedProvider, modelInput) {
        ModelCapabilityRegistry.resolveCapability(selectedProvider, modelInput)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "AI PROVIDERS & WORKFLOWS",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CyanArc
                    )
                    Text(
                        text = "Per-provider isolated configuration, Android Keystore keys & AgentRouter diagnostics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Provider Selector & Settings Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = NeuralViolet
                ) {
                    Column {
                        Text(
                            text = "AI PROVIDER ENGINE",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeuralViolet
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AiProviderId.entries.forEach { provider ->
                                FilterChip(
                                    selected = provider == selectedProvider,
                                    onClick = { onSelectProvider(provider) },
                                    label = { Text(provider.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanArc.copy(alpha = 0.22f),
                                        selectedLabelColor = CyanArc
                                    ),
                                    modifier = Modifier.testTag("provider_chip_${provider.name.lowercase()}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = selectedProvider.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedProvider == AiProviderId.AGENTROUTER) TacticalAmber else TextSecondary
                        )

                        if (selectedProvider != AiProviderId.GEMINI) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = baseUrlInput,
                                onValueChange = { baseUrlInput = it },
                                label = { Text("Base URL (HTTPS required for remote)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanArc,
                                    unfocusedBorderColor = CyanArc.copy(alpha = 0.35f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("provider_base_url_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("Model ID") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanArc,
                                unfocusedBorderColor = CyanArc.copy(alpha = 0.35f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_model_input")
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append("Context: ${capability.contextWindowTokens / 1000}K • ")
                                append("Max Out: ${capability.maxOutputTokens} • ")
                                append(if (capability.usesMaxCompletionTokens) "uses max_completion_tokens" else "uses max_tokens")
                                if (capability.supportsVision) append(" • Vision")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )

                        // Keystore API Key Manager
                        if (selectedProvider.requiresApiKey) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = null,
                                        tint = PlasmaTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Keystore: $maskedApiKey",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (hasApiKey) EmeraldOnline else TacticalAmber
                                    )
                                }
                                if (hasApiKey) {
                                    OutlinedButton(
                                        onClick = { onClearApiKey(selectedProvider) },
                                        modifier = Modifier.testTag("clear_api_key_button")
                                    ) {
                                        Text("Clear Key", color = AlertCrimson)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = { Text("Enter new API key") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PlasmaTeal,
                                        unfocusedBorderColor = PlasmaTeal.copy(alpha = 0.35f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("provider_api_key_input")
                                )
                                Button(
                                    onClick = {
                                        onSaveApiKey(selectedProvider, apiKeyInput)
                                        apiKeyInput = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PlasmaTeal,
                                        contentColor = VoidObsidian
                                    ),
                                    modifier = Modifier.testTag("save_api_key_button")
                                ) {
                                    Text("Save Key")
                                }
                            }
                        }

                        // Completion Token Budget & Temperature Controls
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MAX COMPLETION TOKENS",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "$maxTokensDraft / ${capability.maxOutputTokens}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanArc
                            )
                        }
                        Slider(
                            value = maxTokensDraft.toFloat().coerceIn(256f, capability.maxOutputTokens.toFloat()),
                            onValueChange = { maxTokensDraft = (it / 128f).roundToInt() * 128 },
                            valueRange = 256f..capability.maxOutputTokens.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = CyanArc, activeTrackColor = CyanArc),
                            modifier = Modifier.testTag("max_tokens_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TEMPERATURE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = if (capability.supportsTemperature) {
                                    "${"%.2f".format(temperatureDraft)}"
                                } else {
                                    "Disabled for reasoning model"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (capability.supportsTemperature) CyanArc else TacticalAmber
                            )
                        }
                        Slider(
                            value = temperatureDraft,
                            onValueChange = { temperatureDraft = it },
                            enabled = capability.supportsTemperature,
                            valueRange = 0f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = NeuralViolet, activeTrackColor = NeuralViolet)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSaveProviderConfig(
                                        selectedProvider,
                                        baseUrlInput,
                                        modelInput,
                                        maxTokensDraft,
                                        temperatureDraft
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_provider_config_button")
                            ) {
                                Text("Save Config", color = CyanArc)
                            }

                            Button(
                                onClick = {
                                    onTestConnection(
                                        selectedProvider,
                                        baseUrlInput,
                                        modelInput,
                                        maxTokensDraft,
                                        temperatureDraft
                                    )
                                },
                                enabled = !connectionTesting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanArc,
                                    contentColor = VoidObsidian
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("test_connection_button")
                            ) {
                                if (connectionTesting) {
                                    CircularProgressIndicator(
                                        color = VoidObsidian,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing…")
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.NetworkCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test Connection")
                                }
                            }
                        }

                        // Connection Test Result Panel
                        AnimatedVisibility(visible = connectionTestResult != null) {
                            connectionTestResult?.let { result ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (result.success) {
                                        EmeraldOnline.copy(alpha = 0.14f)
                                    } else {
                                        AlertCrimson.copy(alpha = 0.14f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .testTag("connection_test_result_card")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (result.success) {
                                                    Icons.Filled.CheckCircle
                                                } else {
                                                    Icons.Filled.ErrorOutline
                                                },
                                                contentDescription = null,
                                                tint = if (result.success) EmeraldOnline else AlertCrimson,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = result.message,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TextPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Endpoint: ${result.normalizedChatUrl}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                        if (result.discoveredModels.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                result.discoveredModels.take(12).forEach { modelId ->
                                                    FilterChip(
                                                        selected = modelInput == modelId,
                                                        onClick = { modelInput = modelId },
                                                        label = {
                                                            Text(
                                                                text = modelId,
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // AI Workflow Synthesizer + Safety Step Cap
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = CyanArc
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "AUTONOMOUS WORKFLOW PLANNER",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanArc
                            )
                            Text(
                                text = "Max Steps: ${settings.maxAgentSteps}",
                                style = MaterialTheme.typography.labelMedium,
                                color = PlasmaTeal
                            )
                        }
                        Slider(
                            value = settings.maxAgentSteps.toFloat(),
                            onValueChange = { onSetMaxAgentSteps(it.roundToInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(thumbColor = PlasmaTeal, activeTrackColor = PlasmaTeal)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = workflowGoalInput,
                            onValueChange = { workflowGoalInput = it },
                            placeholder = {
                                Text(
                                    text = "e.g., Prepare desk focus mode with flashlight off & gestures on",
                                    color = TextMuted
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanArc,
                                unfocusedBorderColor = CyanArc.copy(alpha = 0.35f),
                                focusedContainerColor = GlassNavy.copy(alpha = 0.5f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("workflow_goal_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                onGenerateWorkflow(workflowGoalInput)
                                workflowGoalInput = ""
                            },
                            enabled = !workflowGenerating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanArc,
                                contentColor = VoidObsidian
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("synthesize_workflow_button")
                        ) {
                            if (workflowGenerating) {
                                CircularProgressIndicator(
                                    color = VoidObsidian,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Synthesizing Plan…")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Workflow with Gemini")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "SAVED WORKFLOW PROTOCOLS (${workflows.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }

            items(workflows, key = { it.id }) { workflow ->
                val steps = remember(workflow.stepsJson) { parseSteps(workflow.stepsJson) }
                val running = workflowExecution?.takeIf { it.workflowId == workflow.id }

                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (running != null) PlasmaTeal else CyanArc
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = workflow.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CyanArc.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = workflow.triggerLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyanArc,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = workflow.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }

                            IconButton(
                                onClick = { onDeleteWorkflow(workflow.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete workflow",
                                    tint = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        steps.forEachIndexed { idx, step ->
                            val isActiveStep = running?.currentStepIndex == idx
                            Text(
                                text = "${idx + 1}. ${step.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isActiveStep) PlasmaTeal else TextSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        if (running != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = {
                                    (running.currentStepIndex + 1).toFloat() / running.totalSteps.coerceAtLeast(1)
                                },
                                color = PlasmaTeal,
                                trackColor = PlasmaTeal.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Executing: ${running.currentStepLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PlasmaTeal
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        if (running != null) {
                            Button(
                                onClick = onCancelWorkflow,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AlertCrimson.copy(alpha = 0.25f),
                                    contentColor = AlertCrimson
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("cancel_workflow_${workflow.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.StopCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel Protocol")
                            }
                        } else {
                            Button(
                                onClick = { onExecuteWorkflow(workflow) },
                                enabled = workflowExecution == null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanArc.copy(alpha = 0.22f),
                                    contentColor = CyanArc
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("execute_workflow_${workflow.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Execute Protocol")
                            }
                        }
                    }
                }
            }

            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = TacticalAmber
                ) {
                    Column {
                        Text(
                            text = "REMOTE SENTRY ALERT WEBHOOK (HTTPS)",
                            style = MaterialTheme.typography.labelLarge,
                            color = TacticalAmber
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Forward Sentry intrusion alerts to your own HTTPS webhook endpoint.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = webhookInput,
                            onValueChange = { webhookInput = it },
                            placeholder = { Text("https://...", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TacticalAmber,
                                unfocusedBorderColor = TacticalAmber.copy(alpha = 0.35f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { onSaveWebhook(webhookInput) }) {
                            Text("Save Webhook URL", color = TacticalAmber)
                        }
                    }
                }
            }
        }
    }
}
