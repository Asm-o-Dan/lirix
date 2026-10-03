package com.example.npc.core.storage

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.npc.core.storage.migration.MIGRATION_2_3
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class Migration2to3Test {

    @Test
    fun `migration versions are exactly 2 to 3`() {
        MIGRATION_2_3.startVersion shouldBe 2
        MIGRATION_2_3.endVersion shouldBe 3
    }

    @Test
    fun `migrate executes all DDL statements and passes foreign key check`() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val cursor: Cursor = mockk(relaxed = true)
        val sqlStatements = mutableListOf<String>()

        every { db.execSQL(capture(sqlStatements)) } returns Unit
        every { db.query("PRAGMA foreign_key_check") } returns cursor
        every { cursor.count } returns 0
        every { db.insert(any(), any(), any()) } returns 1L
        every { db.update(any(), any(), any(), any(), any()) } returns 1

        MIGRATION_2_3.migrate(db)

        val allSql = sqlStatements.joinToString("\n")

        // 1. Verify pipeline_definition table and indexes
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `pipeline_definition`"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_pipeline_definition_enabled_priority`"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_pipeline_definition_active_revision_id`"))

        // 2. Verify pipeline_revision table and indexes
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `pipeline_revision`"))
        assert(allSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_pipeline_revision_pipeline_id_revision_number`"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_pipeline_revision_pipeline_id_created_at`"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_pipeline_revision_canonical_sha256`"))

        // 3. Verify runtime_alert table and indexes
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `runtime_alert`"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_runtime_alert_is_dismissed_timestamp`"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_runtime_alert_pipeline_id_timestamp`"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_runtime_alert_level`"))

        // 4. Verify event table modification
        assert(allSql.contains("ALTER TABLE `event` ADD COLUMN `pipeline_revision_id` INTEGER DEFAULT NULL"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_event_pipeline_revision_id`"))

        // 5. Verify seeding of legacy-1.1-preset
        verify { db.insert(eq("pipeline_definition"), any(), any()) }
        verify { db.insert(eq("pipeline_revision"), any(), any()) }
        verify { db.update(eq("pipeline_definition"), any(), any(), eq("id = ?"), any()) }

        // 6. Verify foreign_key_check was executed
        verify(exactly = 1) { db.query("PRAGMA foreign_key_check") }
        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun `migrate throws IllegalStateException when foreign_key_check fails`() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val cursor: Cursor = mockk(relaxed = true)

        every { db.insert(any(), any(), any()) } returns 1L
        every { db.update(any(), any(), any(), any(), any()) } returns 1
        every { db.query("PRAGMA foreign_key_check") } returns cursor
        every { cursor.count } returns 3 // 3 violations

        val ex = assertThrows<IllegalStateException> {
            MIGRATION_2_3.migrate(db)
        }

        assert(ex.message?.contains("Foreign key integrity check failed after Migration 2->3") == true)
        assert(ex.message?.contains("Violations count: 3") == true)
    }
}
