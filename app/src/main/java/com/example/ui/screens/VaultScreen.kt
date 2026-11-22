package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.JarvisDataPortability
import com.example.data.local.JarvisSettings
import com.example.data.local.MemoryEntryEntity
import com.example.data.local.UsageSummaryState
import com.example.data.memory.MemoryCategory
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
import kotlin.math.roundToInt

@Composable
fun VaultScreen(
    settings: JarvisSettings,
    memories: List<MemoryEntryEntity>,
    usageSummary: UsageSummaryState,
    databaseIntegrityHealthy: Boolean?,
    onSaveMemory: (MemoryCategory, String, String, Boolean) -> Unit,
    onUpdateMemory: (MemoryEntryEntity, MemoryCategory, String, String, Boolean) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onImportMemoriesJson: (String) -> Unit,
    onExportAllData: (Uri) -> Unit,
    onSetUsageWarningLimit: (Int) -> Unit,
    onClearTemporaryCache: () -> Unit,
    onRevokeCredentials: () -> Unit,
    onWipeAllLocalData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(MemoryCategory.PREFERENCE) }
    var keyInput by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<MemoryEntryEntity?>(null) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var limitDraft by remember(settings.usageWarningTokenLimit) {
        mutableIntStateOf(settings.usageWarningTokenLimit)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportAllData(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!raw.isNullOrBlank()) {
                onImportMemoriesJson(raw)
            }
        }
    }

    editingEntry?.let { target ->
        var editCategory by remember(target.id) { mutableStateOf(MemoryCategory.fromRaw(target.category)) }
        var editKey by remember(target.id) { mutableStateOf(target.displayKey) }
        var editContent by remember(target.id) { mutableStateOf(target.content) }
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            containerColor = GlassNavy,
            titleContentColor = CyanArc,
            textContentColor = TextPrimary,
            title = { Text("Edit Hermes Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MemoryCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = editCategory == cat,
                                onClick = { editCategory = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editKey,
                        onValueChange = { editKey = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("Value (no secrets/OTPs)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateMemory(target, editCategory, editKey, editContent, target.isPinned)
                        editingEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanArc, contentColor = VoidObsidian)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            containerColor = GlassNavy,
            titleContentColor = AlertCrimson,
            textContentColor = TextPrimary,
            title = { Text("Wipe All Local Data?") },
            text = {
                Text(
                    "This permanently deletes local chats, Hermes memories, workflows, Sentry snapshots, API keys, and usage logs from this device.\n\nNote: Deleting local data cannot remove prompts or images already sent to external AI providers."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeDialog = false
                        onWipeAllLocalData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCrimson, contentColor = TextPrimary),
                    modifier = Modifier.testTag("confirm_wipe_all_button")
                ) {
                    Text("Wipe Device Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
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
                        text = "HERMES VAULT & PRIVACY",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CyanArc
                    )
                    Text(
                        text = "Permanent memory isolation, token transparency, sanitized exports & local data control",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // AI Usage & Billing Safety Transparency Card
            item {
                val ratio = if (limitDraft > 0) {
                    (usageSummary.totalLoggedTokens.toFloat() / limitDraft.toFloat()).coerceIn(0f, 1f)
                } else 0f

                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (usageSummary.thresholdWarningActive) TacticalAmber else EmeraldOnline
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI USAGE & BILLING TRANSPARENCY",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldOnline
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldOnline.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "NO AUTO-RECHARGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldOnline,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            UsageStatCol("Requests", "${usageSummary.totalRequests}")
                            UsageStatCol("Succeeded", "${usageSummary.successfulRequests}")
                            UsageStatCol("Failed", "${usageSummary.failedRequests}")
                            UsageStatCol("Logged Tokens", "${usageSummary.totalLoggedTokens}")
                        }

                        LinearProgressIndicator(
                            progress = { ratio },
                            color = if (usageSummary.thresholdWarningActive) TacticalAmber else CyanArc,
                            trackColor = CyanArc.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Prompt: ${usageSummary.promptTokens} • Completion: ${usageSummary.completionTokens} • Exact logs: ${usageSummary.exactTokenLogCount}/${usageSummary.totalRequests}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )

                        if (!usageSummary.lastRateLimitInfo.isNullOrBlank()) {
                            Text(
                                text = "Last Provider Rate Limit: ${usageSummary.lastRateLimitInfo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IceBlue
                            )
                        }

                        Text(
                            text = "Token Warning Threshold: $limitDraft tokens",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (usageSummary.thresholdWarningActive) TacticalAmber else TextPrimary
                        )
                        Slider(
                            value = limitDraft.toFloat(),
                            onValueChange = { limitDraft = ((it / 5000f).roundToInt() * 5000).coerceIn(10_000, 500_000) },
                            onValueChangeFinished = { onSetUsageWarningLimit(limitDraft) },
                            valueRange = 10_000f..500_000f,
                            colors = SliderDefaults.colors(thumbColor = CyanArc, activeTrackColor = CyanArc)
                        )
                        Text(
                            text = "Token counts reflect provider-reported usage only. JARVIS never auto-purchases credits or switches to paid fallbacks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            // Hermes Memory Composer
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = NeuralViolet
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = NeuralViolet,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HERMES PERMANENT MEMORY (${memories.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = NeuralViolet
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MemoryCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeuralViolet.copy(alpha = 0.22f),
                                        selectedLabelColor = NeuralViolet
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("Memory Label (e.g. Timezone, Preferred Style)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeuralViolet,
                                unfocusedBorderColor = NeuralViolet.copy(alpha = 0.35f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("memory_key_input")
                        )

                        OutlinedTextField(
                            value = valueInput,
                            onValueChange = { valueInput = it },
                            label = { Text("Memory Fact (secrets, API keys & OTPs blocked)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeuralViolet,
                                unfocusedBorderColor = NeuralViolet.copy(alpha = 0.35f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("memory_value_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onSaveMemory(selectedCategory, keyInput, valueInput, true)
                                    keyInput = ""
                                    valueInput = ""
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeuralViolet,
                                    contentColor = VoidObsidian
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_memory_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Memory")
                            }

                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("import_memory_button")
                            ) {
                                Icon(Icons.Filled.UploadFile, contentDescription = null, tint = CyanArc, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import JSON", color = CyanArc)
                            }
                        }
                    }
                }
            }

            if (memories.isEmpty()) {
                item {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), accentColor = CyanArc) {
                        Text(
                            text = "No permanent Hermes memories stored yet. Memories stay separate from ephemeral chats and are injected strictly as passive data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(memories, key = { it.id }) { entry ->
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), accentColor = CyanArc) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "[${entry.category}] ${entry.displayKey}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanArc
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                            IconButton(
                                onClick = { editingEntry = entry },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit memory", tint = IceBlue)
                            }
                            IconButton(
                                onClick = { onDeleteMemory(entry.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete memory", tint = AlertCrimson)
                            }
                        }
                    }
                }
            }

            // Privacy, Export & Data Control
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = PlasmaTeal
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (databaseIntegrityHealthy == false) {
                                    Icons.Filled.WarningAmber
                                } else {
                                    Icons.Filled.VerifiedUser
                                },
                                contentDescription = null,
                                tint = if (databaseIntegrityHealthy == false) TacticalAmber else PlasmaTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PRIVACY, BACKUP & DATA CONTROL",
                                style = MaterialTheme.typography.labelLarge,
                                color = PlasmaTeal
                            )
                        }

                        Text(
                            text = "Database Integrity: ${
                                when (databaseIntegrityHealthy) {
                                    true -> "OK (SQLite quick_check passed)"
                                    false -> "Warning — export backup immediately"
                                    null -> "Checking…"
                                }
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (databaseIntegrityHealthy == false) TacticalAmber else EmeraldOnline
                        )

                        Text(
                            text = JarvisDataPortability.EXTERNAL_TRANSMISSION_NOTICE,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        OutlinedButton(
                            onClick = { exportLauncher.launch("jarvis_sanitized_backup.json") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("export_backup_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = CyanArc, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Chats, Memory & Settings (No Secrets)", color = CyanArc)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onClearTemporaryCache,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text("Clear Cache", color = IceBlue)
                            }
                            OutlinedButton(
                                onClick = onRevokeCredentials,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Shield, contentDescription = null, tint = TacticalAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Revoke Keys", color = TacticalAmber)
                            }
                        }

                        Button(
                            onClick = { showWipeDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertCrimson.copy(alpha = 0.25f),
                                contentColor = AlertCrimson
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("wipe_all_data_button")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete All Local User Data")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageStatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = CyanArc)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}
