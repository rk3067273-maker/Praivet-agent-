package com.example.data.local

import com.example.data.ai.AiProviderId
import com.example.data.ai.HttpErrorCategory
import com.example.data.ai.HttpErrorHandler
import com.example.data.ai.JsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DataPortabilityUsageAndMigrationTest {

    @Test
    fun `database migration spec v1 to v2 is non-destructive and defines unique memory index`() {
        val stmts = JarvisDatabaseMigrationSpec.MIGRATION_1_2_STATEMENTS.joinToString("\n")
        assertTrue(stmts.contains("CREATE TABLE IF NOT EXISTS `memory_entries`"))
        assertTrue(stmts.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_entries_category_normalizedKey`"))
        assertTrue(stmts.contains("CREATE TABLE IF NOT EXISTS `ai_usage_logs`"))
        assertFalse(stmts.contains("DROP TABLE"))
    }

    @Test
    fun `sanitized export excludes secrets and writes atomically without leaving temp files`() {
        val secretKey = "sk-super-secret-export-key-9999"
        val bundle = ExportBundleInput(
            settings = JarvisSettings(selectedProvider = AiProviderId.OPENROUTER, webhookUrl = "https://hooks.example.com/123"),
            messages = listOf(
                ChatMessageEntity(id = 1, role = "user", content = "Check key $secretKey", backendLabel = "OpenRouter")
            ),
            memories = listOf(
                MemoryEntryEntity(
                    id = 1,
                    category = "PREFERENCE",
                    normalizedKey = "units",
                    displayKey = "Units",
                    content = "Metric",
                    source = "USER_MANUAL"
                )
            ),
            workflows = emptyList(),
            usageLogs = listOf(
                AiUsageLogEntity(
                    id = 1,
                    providerId = "OPENROUTER",
                    modelId = "openai/gpt-4o-mini",
                    promptTokens = 100,
                    completionTokens = 50,
                    totalTokens = 150,
                    tokenCountsExact = true,
                    latencyMs = 420,
                    succeeded = true
                )
            ),
            exportedAtMs = 99999L
        )

        val json = JarvisDataPortability.buildSanitizedExportJson(bundle, listOf(secretKey))
        assertFalse(json.contains(secretKey))
        assertFalse(json.contains("hooks.example.com"))
        assertTrue(json.contains("Metric"))
        assertTrue(json.contains("Deleting local device data does not erase records already transmitted"))

        val parsed = JsonCodec.parseObjectOrNull(json)!!
        val safeSettings = parsed["sanitizedSettings"] as Map<*, *>
        assertEquals(false, safeSettings["apiKeysIncluded"])
        assertEquals(true, safeSettings["webhookConfigured"])

        val tmpDir = createTempDirectory("jarvis_export_test").toFile()
        try {
            val outFile = File(tmpDir, "backup.json")
            JarvisDataPortability.writeAtomicUtf8File(outFile, json)
            assertTrue(outFile.exists())
            assertFalse(File(tmpDir, "backup.json.tmp").exists())
            assertEquals(json, outFile.readText())
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `usage summary aggregates exact tokens and HTTP 402 enforces billing safety`() {
        val logs = listOf(
            AiUsageLogEntity(providerId = "OPENAI", modelId = "gpt-4o-mini", promptTokens = 300, completionTokens = 200, totalTokens = 500, tokenCountsExact = true, latencyMs = 300, succeeded = true, rateLimitInfo = "Req left: 10"),
            AiUsageLogEntity(providerId = "OPENAI", modelId = "gpt-4o-mini", promptTokens = 0, completionTokens = 0, totalTokens = 0, tokenCountsExact = false, latencyMs = 150, succeeded = false, httpStatusCode = 402, errorSummary = "QUOTA_EXHAUSTED")
        )
        val summary = UsageSummaryState.fromLogs(logs, warningLimit = 400)
        assertEquals(2, summary.totalRequests)
        assertEquals(1, summary.successfulRequests)
        assertEquals(1, summary.failedRequests)
        assertEquals(500, summary.totalLoggedTokens)
        assertEquals(1, summary.exactTokenLogCount)
        assertTrue(summary.thresholdWarningActive)
        assertEquals("Req left: 10", summary.lastRateLimitInfo)

        val err402 = HttpErrorHandler.mapHttpError(402, """{"error":{"message":"Insufficient credits"}}""", AiProviderId.OPENROUTER, null)
        assertEquals(HttpErrorCategory.QUOTA_EXHAUSTED, err402.category)
        assertTrue(err402.userMessage.contains("automatic recharge"))
    }
}
