package com.example.npc.core.storage.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.npc.core.storage.AppDatabase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class Migration5To6Test {
    @Test
    fun `migration adds only status columns with backward compatible defaults`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()
        every { db.execSQL(capture(statements)) } returns Unit

        MIGRATION_5_6.migrate(db)

        MIGRATION_5_6.startVersion shouldBe 5
        MIGRATION_5_6.endVersion shouldBe 6
        statements shouldBe listOf(
            "ALTER TABLE `financial_transaction` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'COMPLETED'",
            "ALTER TABLE `financial_transaction` ADD COLUMN `tx_status` TEXT NOT NULL DEFAULT 'CONFIRMED_AUTO'"
        )
        AppDatabase.MIGRATIONS.last() shouldBe MIGRATION_5_6
    }
}
