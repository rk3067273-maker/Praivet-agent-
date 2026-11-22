package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "jarvis"
    val content: String,
    val backendLabel: String,
    val actionExecuted: String? = null,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val stepsJson: String,
    val triggerLabel: String,
    val isPinned: Boolean = false,
    val lastRunTimestamp: Long? = null
)

@Entity(tableName = "sentry_events")
data class SentryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val peakDeltaMs2: Float,
    val photoPath: String?,
    val summary: String
)

@Entity(
    tableName = "memory_entries",
    indices = [Index(value = ["category", "normalizedKey"], unique = true)]
)
data class MemoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // IDENTITY, PREFERENCE, DIRECTIVE, CONTEXT
    val normalizedKey: String,
    val displayKey: String,
    val content: String,
    val source: String, // USER_MANUAL, IMPORTED
    val isPinned: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_usage_logs")
data class AiUsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String,
    val modelId: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val tokenCountsExact: Boolean,
    val latencyMs: Long,
    val succeeded: Boolean,
    val httpStatusCode: Int? = null,
    val rateLimitInfo: String? = null,
    val errorSummary: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
