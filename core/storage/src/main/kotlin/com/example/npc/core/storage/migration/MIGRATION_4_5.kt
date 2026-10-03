package com.example.npc.core.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `extraction_feedback` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `event_id` INTEGER NOT NULL,
                `features_json` TEXT NOT NULL,
                `verdict` TEXT NOT NULL,
                `extractor_kind` TEXT NOT NULL,
                `user_action` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`event_id`) REFERENCES `event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_extraction_feedback_event_id` ON `extraction_feedback` (`event_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_extraction_feedback_created_at` ON `extraction_feedback` (`created_at`)")
    }
}
