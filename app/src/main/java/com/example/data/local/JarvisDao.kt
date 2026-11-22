package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    suspend fun getAllMessagesOnce(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    @Query("SELECT * FROM workflows ORDER BY isPinned DESC, id DESC")
    fun observeWorkflows(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows ORDER BY isPinned DESC, id DESC")
    suspend fun getAllWorkflowsOnce(): List<WorkflowEntity>

    @Query("SELECT COUNT(*) FROM workflows")
    suspend fun countWorkflows(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: WorkflowEntity): Long

    @Query("UPDATE workflows SET lastRunTimestamp = :timestamp WHERE id = :id")
    suspend fun updateWorkflowLastRun(id: Long, timestamp: Long)

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteWorkflow(id: Long)

    @Query("DELETE FROM workflows")
    suspend fun clearWorkflows()

    @Query("SELECT * FROM sentry_events ORDER BY timestamp DESC")
    fun observeSentryEvents(): Flow<List<SentryEventEntity>>

    @Query("SELECT * FROM sentry_events ORDER BY timestamp DESC")
    suspend fun getAllSentryEventsOnce(): List<SentryEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentryEvent(event: SentryEventEntity): Long

    @Query("DELETE FROM sentry_events WHERE id = :id")
    suspend fun deleteSentryEvent(id: Long)

    @Query("DELETE FROM sentry_events")
    suspend fun clearSentryEvents()

    // Hermes Memory Engine
    @Query("SELECT * FROM memory_entries ORDER BY isPinned DESC, updatedAt DESC, id DESC")
    fun observeMemories(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries ORDER BY isPinned DESC, updatedAt DESC, id DESC")
    suspend fun getAllMemoriesOnce(): List<MemoryEntryEntity>

    @Query("SELECT * FROM memory_entries WHERE category = :category AND normalizedKey = :normalizedKey LIMIT 1")
    suspend fun findMemoryByKey(category: String, normalizedKey: String): MemoryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceMemory(entry: MemoryEntryEntity): Long

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memory_entries")
    suspend fun clearMemories()

    @Transaction
    suspend fun upsertDeduplicatedMemory(
        category: String,
        normalizedKey: String,
        displayKey: String,
        content: String,
        source: String,
        isPinned: Boolean,
        nowMs: Long
    ): Long {
        val existing = findMemoryByKey(category, normalizedKey)
        return if (existing != null) {
            val updated = existing.copy(
                displayKey = displayKey,
                content = content,
                source = source,
                isPinned = isPinned,
                updatedAt = nowMs
            )
            insertOrReplaceMemory(updated)
        } else {
            insertOrReplaceMemory(
                MemoryEntryEntity(
                    category = category,
                    normalizedKey = normalizedKey,
                    displayKey = displayKey,
                    content = content,
                    source = source,
                    isPinned = isPinned,
                    createdAt = nowMs,
                    updatedAt = nowMs
                )
            )
        }
    }

    // AI Usage Transparency
    @Query("SELECT * FROM ai_usage_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeUsageLogs(): Flow<List<AiUsageLogEntity>>

    @Query("SELECT * FROM ai_usage_logs ORDER BY timestamp DESC")
    suspend fun getAllUsageLogsOnce(): List<AiUsageLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageLog(log: AiUsageLogEntity): Long

    @Query("DELETE FROM ai_usage_logs")
    suspend fun clearUsageLogs()

    @Transaction
    suspend fun wipeAllUserTables() {
        clearMessages()
        clearMemories()
        clearSentryEvents()
        clearUsageLogs()
        clearWorkflows()
    }
}
