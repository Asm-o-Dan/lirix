package com.example.npc.core.storage.migration

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class Migration3To4Test {

    @Test
    fun `migration versions are exactly 3 to 4`() {
        MIGRATION_3_4.startVersion shouldBe 3
        MIGRATION_3_4.endVersion shouldBe 4
    }

    @Test
    fun `migrationTest_3_to_4`() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val cursor: Cursor = mockk(relaxed = true)
        val sqlStatements = mutableListOf<String>()

        every { db.execSQL(capture(sqlStatements)) } returns Unit
        every { db.query("PRAGMA foreign_key_check") } returns cursor
        every { cursor.count } returns 0

        MIGRATION_3_4.migrate(db)

        val allSql = sqlStatements.joinToString("\n")

        // 1. Verify dynamic_template table and indexes
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `dynamic_template`")) {
            "dynamic_template table DDL is missing"
        }
        assert(allSql.contains("`id` TEXT NOT NULL")) { "id column is missing" }
        assert(allSql.contains("`sourceKey` TEXT NOT NULL")) { "sourceKey column is missing" }
        assert(allSql.contains("`tier` TEXT NOT NULL")) { "tier column is missing" }
        assert(allSql.contains("`origin` TEXT NOT NULL")) { "origin column is missing" }
        assert(allSql.contains("`state` TEXT NOT NULL")) { "state column is missing" }
        assert(allSql.contains("`priority` INTEGER NOT NULL")) { "priority column is missing" }
        assert(allSql.contains("`pattern` TEXT NOT NULL")) { "pattern column is missing" }
        assert(allSql.contains("`canonicalHash` TEXT NOT NULL")) { "canonicalHash column is missing" }

        // Required indexes for dynamic_template
        assert(allSql.contains("CREATE INDEX IF NOT EXISTS `idx_dyn_tmpl_src_state` ON `dynamic_template` (`sourceKey`, `state`)")) {
            "idx_dyn_tmpl_src_state index DDL is missing"
        }
        assert(allSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS `idx_dyn_tmpl_hash` ON `dynamic_template` (`canonicalHash`)")) {
            "idx_dyn_tmpl_hash unique index DDL is missing"
        }

        // 2. Verify template_stats table with CASCADE foreign key
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `template_stats`")) {
            "template_stats table DDL is missing"
        }
        assert(allSql.contains("FOREIGN KEY(`templateId`) REFERENCES `dynamic_template`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE")) {
            "template_stats foreign key with ON DELETE CASCADE is missing"
        }

        // 3. Verify template_bank_version and template_bank_membership tables
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `template_bank_version`")) {
            "template_bank_version table DDL is missing"
        }
        assert(allSql.contains("CREATE TABLE IF NOT EXISTS `template_bank_membership`")) {
            "template_bank_membership table DDL is missing"
        }

        // 4. Verify additive financial_transaction column additions (zero data loss)
        assert(allSql.contains("ALTER TABLE `financial_transaction` ADD COLUMN `extractorKind` TEXT NOT NULL DEFAULT 'STATIC'")) {
            "extractorKind column addition is missing"
        }
        assert(allSql.contains("ALTER TABLE `financial_transaction` ADD COLUMN `templateId` TEXT DEFAULT NULL")) {
            "templateId column addition is missing"
        }
        assert(allSql.contains("ALTER TABLE `financial_transaction` ADD COLUMN `bankVersion` INTEGER NOT NULL DEFAULT 0")) {
            "bankVersion column addition is missing"
        }
        assert(allSql.contains("ALTER TABLE `financial_transaction` ADD COLUMN `isRefund` INTEGER NOT NULL DEFAULT 0")) {
            "isRefund column addition is missing"
        }

        // 5. Verify existing tables are never dropped (100% data preservation)
        assert(!allSql.contains("DROP TABLE")) {
            "Migration must be purely additive: no DROP TABLE statements allowed"
        }
        assert(!allSql.contains("DELETE FROM")) {
            "Migration must not delete existing events or transactions"
        }

        // 6. Verify foreign_key_check was executed
        verify(exactly = 1) { db.query("PRAGMA foreign_key_check") }
        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun `migrate preserves existing event and financial transaction records`() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val cursor: Cursor = mockk(relaxed = true)
        val executedStatements = mutableListOf<String>()

        every { db.execSQL(capture(executedStatements)) } returns Unit
        every { db.query("PRAGMA foreign_key_check") } returns cursor
        every { cursor.count } returns 0

        MIGRATION_3_4.migrate(db)

        // Ensure no data modifications on existing event or financial_transaction tables
        val dangerousKeywords = listOf("DROP TABLE", "TRUNCATE", "DELETE FROM `event`", "DELETE FROM `financial_transaction`")
        executedStatements.forEach { statement ->
            dangerousKeywords.forEach { dangerous ->
                assert(!statement.uppercase().contains(dangerous)) {
                    "Found destructive operation '$dangerous' in statement: $statement"
                }
            }
        }
    }

    @Test
    fun `migrate throws IllegalStateException when foreign_key_check fails`() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val cursor: Cursor = mockk(relaxed = true)

        every { db.query("PRAGMA foreign_key_check") } returns cursor
        every { cursor.count } returns 4 // 4 violations

        val ex = assertThrows<IllegalStateException> {
            MIGRATION_3_4.migrate(db)
        }

        assert(ex.message?.contains("Foreign key integrity check failed after Migration 3->4") == true)
        assert(ex.message?.contains("Violations count: 4") == true)
    }
}
