package com.example.npc.core.storage.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class Migration4To5Test {

    @Test
    fun `migration versions are exactly 4 to 5`() {
        MIGRATION_4_5.startVersion shouldBe 4
        MIGRATION_4_5.endVersion shouldBe 5
    }

    @Test
    fun `migrationTest_4_to_5`() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val sqlStatements = mutableListOf<String>()

        every { db.execSQL(capture(sqlStatements)) } returns Unit

        MIGRATION_4_5.migrate(db)

        val allSql = sqlStatements.joinToString("\n")

        // 1. Verify extraction_feedback table DDL and columns
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `extraction_feedback`")) {
            "extraction_feedback table DDL is missing"
        }
        assert(allSql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL")) { "id column is missing" }
        assert(allSql.contains("`event_id` INTEGER NOT NULL")) { "event_id column is missing" }
        assert(allSql.contains("`features_json` TEXT NOT NULL")) { "features_json column is missing" }
        assert(allSql.contains("`verdict` TEXT NOT NULL")) { "verdict column is missing" }
        assert(allSql.contains("`extractor_kind` TEXT NOT NULL")) { "extractor_kind column is missing" }
        assert(allSql.contains("`user_action` TEXT")) { "user_action column is missing" }
        assert(allSql.contains("`created_at` INTEGER NOT NULL")) { "created_at column is missing" }
        assert(allSql.contains("FOREIGN KEY(`event_id`) REFERENCES `event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE")) {
            "Foreign key on event_id with ON DELETE CASCADE is missing"
        }

        // 2. Verify indexes
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_extraction_feedback_event_id` ON `extraction_feedback` (`event_id`)")) {
            "index_extraction_feedback_event_id index is missing"
        }
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_extraction_feedback_created_at` ON `extraction_feedback` (`created_at`)")) {
            "index_extraction_feedback_created_at index is missing"
        }
    }
}
