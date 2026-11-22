package com.example.data.memory

import com.example.data.ai.JsonCodec
import com.example.data.local.JarvisDao
import com.example.data.local.MemoryEntryEntity
import com.example.data.security.PromptSecurityGuard
import java.util.Locale

enum class MemoryCategory(val label: String) {
    IDENTITY("Identity"),
    PREFERENCE("Preference"),
    DIRECTIVE("Directive"),
    CONTEXT("Context");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory {
            val clean = raw?.trim()?.uppercase(Locale.ROOT)
            return entries.firstOrNull { it.name == clean } ?: PREFERENCE
        }
    }
}

sealed interface MemoryWriteResult {
    data class Saved(val id: Long, val normalizedKey: String) : MemoryWriteResult
    data class Rejected(val reason: String) : MemoryWriteResult
}

data class MemoryImportResult(
    val importedCount: Int,
    val skippedDuplicatesOrInvalid: Int,
    val blockedSecretCount: Int,
    val quarantinedInjectionCount: Int,
    val summaryMessage: String
)

interface MemoryStore {
    suspend fun getAllMemories(): List<MemoryEntryEntity>
    suspend fun upsertDeduplicated(
        category: String,
        normalizedKey: String,
        displayKey: String,
        content: String,
        source: String,
        isPinned: Boolean,
        nowMs: Long
    ): Long
    suspend fun updateMemory(entry: MemoryEntryEntity)
    suspend fun deleteMemory(id: Long)
    suspend fun clearAllMemories()
}

class RoomMemoryStore(private val dao: JarvisDao) : MemoryStore {
    override suspend fun getAllMemories(): List<MemoryEntryEntity> = dao.getAllMemoriesOnce()

    override suspend fun upsertDeduplicated(
        category: String,
        normalizedKey: String,
        displayKey: String,
        content: String,
        source: String,
        isPinned: Boolean,
        nowMs: Long
    ): Long = dao.upsertDeduplicatedMemory(
        category = category,
        normalizedKey = normalizedKey,
        displayKey = displayKey,
        content = content,
        source = source,
        isPinned = isPinned,
        nowMs = nowMs
    )

    override suspend fun updateMemory(entry: MemoryEntryEntity) {
        dao.insertOrReplaceMemory(entry)
    }

    override suspend fun deleteMemory(id: Long) = dao.deleteMemory(id)

    override suspend fun clearAllMemories() = dao.clearMemories()
}

class HermesMemoryManager(
    private val store: MemoryStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        const val MEMORY_EXPORT_SCHEMA_VERSION = 1
        const val MAX_MEMORY_ENTRIES = 200
        const val MAX_KEY_CHARS = 80
        const val MAX_CONTENT_CHARS = 500
    }

    suspend fun saveUserMemory(
        category: MemoryCategory,
        key: String,
        content: String,
        isPinned: Boolean = true
    ): MemoryWriteResult {
        val cleanKey = PromptSecurityGuard.sanitizeUntrustedText(key, MAX_KEY_CHARS)
        val cleanContent = PromptSecurityGuard.sanitizeUntrustedText(content, MAX_CONTENT_CHARS)

        if (cleanKey.isBlank() || cleanContent.isBlank()) {
            return MemoryWriteResult.Rejected("Both memory label and value are required.")
        }
        if (PromptSecurityGuard.containsSensitiveSecret(cleanKey) ||
            PromptSecurityGuard.containsSensitiveSecret(cleanContent)
        ) {
            return MemoryWriteResult.Rejected(
                "Refused to store sensitive credential, API key, password, or OTP in Hermes Memory."
            )
        }
        if (PromptSecurityGuard.isSuspiciousForMemory(cleanContent) ||
            PromptSecurityGuard.isSuspiciousForMemory(cleanKey)
        ) {
            return MemoryWriteResult.Rejected(
                "Memory entry blocked due to suspicious prompt-injection or action-tag syntax."
            )
        }

        val normalizedKey = normalizeKey(cleanKey)
        val existing = store.getAllMemories()
        val exactSame = existing.firstOrNull {
            it.category == category.name &&
                it.normalizedKey == normalizedKey &&
                it.content.trim().equals(cleanContent, ignoreCase = true)
        }
        if (exactSame != null) {
            return MemoryWriteResult.Saved(exactSame.id, normalizedKey)
        }

        val duplicateContentOtherKey = existing.firstOrNull {
            it.category == category.name &&
                it.content.trim().equals(cleanContent, ignoreCase = true)
        }
        if (duplicateContentOtherKey != null) {
            return MemoryWriteResult.Rejected(
                "Duplicate memory already exists under '${duplicateContentOtherKey.displayKey}'."
            )
        }

        if (existing.size >= MAX_MEMORY_ENTRIES &&
            existing.none { it.category == category.name && it.normalizedKey == normalizedKey }
        ) {
            return MemoryWriteResult.Rejected(
                "Hermes Memory quota ($MAX_MEMORY_ENTRIES entries) reached. Delete unused items first."
            )
        }

        val id = store.upsertDeduplicated(
            category = category.name,
            normalizedKey = normalizedKey,
            displayKey = cleanKey,
            content = cleanContent,
            source = "USER_MANUAL",
            isPinned = isPinned,
            nowMs = clock()
        )
        return MemoryWriteResult.Saved(id, normalizedKey)
    }

    suspend fun editMemory(
        existing: MemoryEntryEntity,
        newCategory: MemoryCategory,
        newKey: String,
        newContent: String,
        isPinned: Boolean
    ): MemoryWriteResult {
        val cleanKey = PromptSecurityGuard.sanitizeUntrustedText(newKey, MAX_KEY_CHARS)
        val cleanContent = PromptSecurityGuard.sanitizeUntrustedText(newContent, MAX_CONTENT_CHARS)
        if (cleanKey.isBlank() || cleanContent.isBlank()) {
            return MemoryWriteResult.Rejected("Memory label and content cannot be empty.")
        }
        if (PromptSecurityGuard.containsSensitiveSecret(cleanKey) ||
            PromptSecurityGuard.containsSensitiveSecret(cleanContent)
        ) {
            return MemoryWriteResult.Rejected(
                "Refused to save API key, password, or OTP inside Hermes Memory."
            )
        }
        if (PromptSecurityGuard.isSuspiciousForMemory(cleanKey) ||
            PromptSecurityGuard.isSuspiciousForMemory(cleanContent)
        ) {
            return MemoryWriteResult.Rejected(
                "Memory update rejected due to suspicious instruction-override syntax."
            )
        }
        val normalizedKey = normalizeKey(cleanKey)
        val updated = existing.copy(
            category = newCategory.name,
            normalizedKey = normalizedKey,
            displayKey = cleanKey,
            content = cleanContent,
            isPinned = isPinned,
            updatedAt = clock()
        )
        store.updateMemory(updated)
        return MemoryWriteResult.Saved(updated.id, normalizedKey)
    }

    suspend fun deleteMemory(id: Long) = store.deleteMemory(id)

    suspend fun clearAllMemories() = store.clearAllMemories()

    suspend fun buildPromptMemoryBlock(limit: Int = 12): String {
        val entries = store.getAllMemories()
            .filter {
                !PromptSecurityGuard.containsSensitiveSecret(it.content) &&
                    !PromptSecurityGuard.isSuspiciousForMemory(it.content)
            }
            .take(limit)
        if (entries.isEmpty()) return ""
        val formatted = entries.joinToString("\n") { e ->
            "- [${e.category}] ${e.displayKey}: ${e.content}"
        }
        return PromptSecurityGuard.wrapUntrustedData("hermes_user_memory_passive_facts", formatted, 2200)
    }

    suspend fun exportMemoriesJson(): String {
        val entries = store.getAllMemories().map { e ->
            mapOf(
                "category" to e.category,
                "key" to e.displayKey,
                "content" to e.content,
                "source" to e.source,
                "pinned" to e.isPinned,
                "updatedAt" to e.updatedAt
            )
        }
        return JsonCodec.toJson(
            mapOf(
                "schemaVersion" to MEMORY_EXPORT_SCHEMA_VERSION,
                "kind" to "jarvis_hermes_memory",
                "exportedAt" to clock(),
                "entries" to entries
            )
        )
    }

    suspend fun importMemoriesJson(rawJson: String): MemoryImportResult {
        if (rawJson.length > 512_000) {
            return MemoryImportResult(0, 1, 0, 0, "Import rejected: payload exceeds 512 KB limit.")
        }
        val root = JsonCodec.parseObjectOrNull(rawJson)
            ?: return MemoryImportResult(0, 1, 0, 0, "Import rejected: malformed JSON.")
        val schemaVersion = (root["schemaVersion"] as? Number)?.toInt()
        if (schemaVersion != null && schemaVersion > MEMORY_EXPORT_SCHEMA_VERSION) {
            return MemoryImportResult(
                0,
                1,
                0,
                0,
                "Import rejected: unsupported future schemaVersion=$schemaVersion."
            )
        }
        val rawEntries = root["entries"] as? List<*>
            ?: return MemoryImportResult(0, 1, 0, 0, "Import rejected: missing 'entries' array.")

        var imported = 0
        var skipped = 0
        var blockedSecrets = 0
        var quarantinedInjection = 0

        for (item in rawEntries.take(MAX_MEMORY_ENTRIES)) {
            val map = item as? Map<*, *>
            if (map == null) {
                skipped++
                continue
            }
            val category = MemoryCategory.fromRaw(map["category"] as? String)
            val rawKey = (map["key"] as? String).orEmpty()
            val rawContent = (map["content"] as? String).orEmpty()
            val pinned = (map["pinned"] as? Boolean) ?: true

            if (rawKey.isBlank() || rawContent.isBlank()) {
                skipped++
                continue
            }
            if (PromptSecurityGuard.containsSensitiveSecret(rawKey) ||
                PromptSecurityGuard.containsSensitiveSecret(rawContent)
            ) {
                blockedSecrets++
                continue
            }
            if (PromptSecurityGuard.isSuspiciousForMemory(rawKey) ||
                PromptSecurityGuard.isSuspiciousForMemory(rawContent)
            ) {
                quarantinedInjection++
                continue
            }

            val cleanKey = PromptSecurityGuard.sanitizeUntrustedText(rawKey, MAX_KEY_CHARS)
            val cleanContent = PromptSecurityGuard.sanitizeUntrustedText(rawContent, MAX_CONTENT_CHARS)
            if (cleanKey.isBlank() || cleanContent.isBlank()) {
                skipped++
                continue
            }

            store.upsertDeduplicated(
                category = category.name,
                normalizedKey = normalizeKey(cleanKey),
                displayKey = cleanKey,
                content = cleanContent,
                source = "IMPORTED",
                isPinned = pinned,
                nowMs = clock()
            )
            imported++
        }

        val summary = buildString {
            append("Imported $imported memory entry(ies)")
            if (quarantinedInjection > 0) append(", blocked $quarantinedInjection injection attempt(s)")
            if (blockedSecrets > 0) append(", blocked $blockedSecrets secret(s)")
            if (skipped > 0) append(", skipped $skipped invalid item(s)")
            append(".")
        }
        return MemoryImportResult(
            importedCount = imported,
            skippedDuplicatesOrInvalid = skipped,
            blockedSecretCount = blockedSecrets,
            quarantinedInjectionCount = quarantinedInjection,
            summaryMessage = summary
        )
    }

    internal fun normalizeKey(key: String): String {
        return key.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "entry" }
    }
}
