package com.example.npc.core.storage

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.npc.core.storage.migration.MIGRATION_1_2
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class Migration1to2Test {

    @Test
    fun `migration versions are exactly 1 to 2`() {
        MIGRATION_1_2.startVersion shouldBe 1
        MIGRATION_1_2.endVersion shouldBe 2
    }

    @Test
    fun `migrate executes all DDL statements and passes foreign key check`() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val cursor: Cursor = mockk(relaxed = true)
        val sqlStatements = mutableListOf<String>()

        every { db.execSQL(capture(sqlStatements)) } returns Unit
        every { db.query("PRAGMA foreign_key_check") } returns cursor
        every { cursor.count } returns 0

        MIGRATION_1_2.migrate(db)

        // 1. Verify event table column additions with correct default values
        val allSql = sqlStatements.joinToString("\n")
        assert(allSql.contains("ALTER TABLE `event` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'UNCLASSIFIED'"))
        assert(allSql.contains("ALTER TABLE `event` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 0.0"))
        assert(allSql.contains("ALTER TABLE `event` ADD COLUMN `engine_used` TEXT NOT NULL DEFAULT 'NONE'"))
        assert(allSql.contains("ALTER TABLE `event` ADD COLUMN `is_user_corrected` INTEGER NOT NULL DEFAULT 0"))
        assert(allSql.contains("ALTER TABLE `event` ADD COLUMN `content_fingerprint` TEXT DEFAULT NULL"))

        // 2. Verify indexes on event table
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_event_category` ON `event` (`category`)"))
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `index_event_content_fingerprint` ON `event` (`content_fingerprint`)"))

        // 3. Verify financial_transaction table creation with ON DELETE SET NULL
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `financial_transaction`"))
        assert(allSql.contains("ON DELETE SET NULL"))
        assert(allSql.contains("`amount_minor` INTEGER NOT NULL"))
        assert(allSql.contains("`currency` TEXT NOT NULL"))

        // 4. Verify user_prototype table creation and unique composite index
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `user_prototype`"))
        assert(allSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_prototype_package_fingerprint` ON `user_prototype` (`package_name`, `fingerprint`)"))

        // 5. Verify PRAGMA foreign_key_check was executed
        verify(exactly = 1) { db.query("PRAGMA foreign_key_check") }
        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun `migrate throws IllegalStateException when foreign_key_check fails`() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val cursor: Cursor = mockk(relaxed = true)

        every { db.query("PRAGMA foreign_key_check") } returns cursor
        every { cursor.count } returns 2 // 2 foreign key violations!

        val ex = assertThrows<IllegalStateException> {
            MIGRATION_1_2.migrate(db)
        }

        assert(ex.message?.contains("Foreign key integrity check failed") == true)
        assert(ex.message?.contains("Violations count: 2") == true)
    }
}
