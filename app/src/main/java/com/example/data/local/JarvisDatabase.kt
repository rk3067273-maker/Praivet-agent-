package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object JarvisDatabaseMigrationSpec {
    val MIGRATION_1_2_STATEMENTS: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `memory_entries` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `category` TEXT NOT NULL,
            `normalizedKey` TEXT NOT NULL,
            `displayKey` TEXT NOT NULL,
            `content` TEXT NOT NULL,
            `source` TEXT NOT NULL,
            `isPinned` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_entries_category_normalizedKey` ON `memory_entries` (`category`, `normalizedKey`)",
        """
        CREATE TABLE IF NOT EXISTS `ai_usage_logs` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `providerId` TEXT NOT NULL,
            `modelId` TEXT NOT NULL,
            `promptTokens` INTEGER NOT NULL,
            `completionTokens` INTEGER NOT NULL,
            `totalTokens` INTEGER NOT NULL,
            `tokenCountsExact` INTEGER NOT NULL,
            `latencyMs` INTEGER NOT NULL,
            `succeeded` INTEGER NOT NULL,
            `httpStatusCode` INTEGER,
            `rateLimitInfo` TEXT,
            `errorSummary` TEXT,
            `timestamp` INTEGER NOT NULL
        )
        """.trimIndent()
    )
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        JarvisDatabaseMigrationSpec.MIGRATION_1_2_STATEMENTS.forEach { sql ->
            db.execSQL(sql)
        }
    }
}

@Database(
    entities = [
        ChatMessageEntity::class,
        WorkflowEntity::class,
        SentryEventEntity::class,
        MemoryEntryEntity::class,
        AiUsageLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao

    fun verifyQuickIntegrity(): Boolean {
        return try {
            openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0).equals("ok", ignoreCase = true)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("JarvisDatabase", "Database integrity quick_check failed", e)
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_private_agent.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
