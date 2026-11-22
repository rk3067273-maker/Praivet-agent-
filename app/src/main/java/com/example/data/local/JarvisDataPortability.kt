package com.example.data.local

import com.example.data.ai.JsonCodec
import com.example.data.security.SecretRedactor
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

data class ExportBundleInput(
    val settings: JarvisSettings,
    val messages: List<ChatMessageEntity>,
    val memories: List<MemoryEntryEntity>,
    val workflows: List<WorkflowEntity>,
    val usageLogs: List<AiUsageLogEntity>,
    val exportedAtMs: Long = System.currentTimeMillis()
)

object JarvisDataPortability {

    const val EXPORT_SCHEMA_VERSION = 1
    const val EXTERNAL_TRANSMISSION_NOTICE =
        "Data sent to remote AI providers (prompts, attached images, optional webhook alerts) is processed by those external services under their privacy policies. Deleting local device data does not erase records already transmitted to external providers."

    fun buildSanitizedExportJson(
        input: ExportBundleInput,
        knownSecretsToRedact: List<String> = emptyList()
    ): String {
        val sanitizedProviders = input.settings.providerConfigs.mapKeys { it.key.name }.mapValues { (_, cfg) ->
            mapOf(
                "baseUrl" to cfg.baseUrl,
                "modelId" to cfg.modelId,
                "maxTokens" to cfg.maxTokens,
                "temperature" to cfg.temperature.toDouble()
            )
        }

        val safeSettings = mapOf(
            "selectedProvider" to input.settings.selectedProvider.name,
            "providerConfigs" to sanitizedProviders,
            "ttsEnabled" to input.settings.ttsEnabled,
            "wakeWordEnabled" to input.settings.wakeWordEnabled,
            "airGesturesEnabled" to input.settings.airGesturesEnabled,
            "sentrySensitivity" to input.settings.sentrySensitivity.toDouble(),
            "maxAgentSteps" to input.settings.maxAgentSteps,
            "usageWarningTokenLimit" to input.settings.usageWarningTokenLimit,
            "webhookConfigured" to input.settings.webhookUrl.isNotBlank(),
            "apiKeysIncluded" to false
        )

        val safeMessages = input.messages.map { m ->
            mapOf(
                "role" to m.role,
                "content" to SecretRedactor.redact(m.content, knownSecretsToRedact),
                "backendLabel" to m.backendLabel,
                "actionExecuted" to m.actionExecuted,
                "timestamp" to m.timestamp
            )
        }

        val safeMemories = input.memories.map { mem ->
            mapOf(
                "category" to mem.category,
                "key" to mem.displayKey,
                "content" to SecretRedactor.redact(mem.content, knownSecretsToRedact),
                "source" to mem.source,
                "pinned" to mem.isPinned,
                "updatedAt" to mem.updatedAt
            )
        }

        val safeWorkflows = input.workflows.map { w ->
            mapOf(
                "title" to w.title,
                "description" to w.description,
                "triggerLabel" to w.triggerLabel,
                "stepsJson" to w.stepsJson,
                "pinned" to w.isPinned
            )
        }

        val totalPromptTokens = input.usageLogs.sumOf { it.promptTokens }
        val totalCompletionTokens = input.usageLogs.sumOf { it.completionTokens }

        val usageSummary = mapOf(
            "totalRequests" to input.usageLogs.size,
            "successfulRequests" to input.usageLogs.count { it.succeeded },
            "failedRequests" to input.usageLogs.count { !it.succeeded },
            "loggedPromptTokens" to totalPromptTokens,
            "loggedCompletionTokens" to totalCompletionTokens,
            "loggedTotalTokens" to (totalPromptTokens + totalCompletionTokens)
        )

        val bundle = linkedMapOf<String, Any?>(
            "schemaVersion" to EXPORT_SCHEMA_VERSION,
            "app" to "JARVIS PrivateAgent",
            "exportedAtMs" to input.exportedAtMs,
            "privacyNotice" to EXTERNAL_TRANSMISSION_NOTICE,
            "sanitizedSettings" to safeSettings,
            "memories" to safeMemories,
            "conversations" to safeMessages,
            "workflows" to safeWorkflows,
            "usageSummary" to usageSummary
        )
        return JsonCodec.toJson(bundle)
    }

    fun writeAtomicUtf8File(targetFile: File, content: String) {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tmpFile = File(parent ?: targetFile.absoluteFile.parentFile, "${targetFile.name}.tmp")
        try {
            tmpFile.writeText(content, StandardCharsets.UTF_8)
            if (targetFile.exists() && !targetFile.delete()) {
                throw IOException("Unable to replace existing file: ${targetFile.name}")
            }
            if (!tmpFile.renameTo(targetFile)) {
                throw IOException("Atomic rename failed for ${targetFile.name}")
            }
        } catch (e: Exception) {
            runCatching { tmpFile.delete() }
            throw e
        }
    }
}
