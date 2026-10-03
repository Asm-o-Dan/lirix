package com.example.npc.core.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve bank outcome and user confirmation when transactions are read or reprocessed. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Earlier schemas did not retain either status. Keep their historical defaults;
        // reparsing source events can recover the bank outcome without guessing here.
        db.execSQL("ALTER TABLE `financial_transaction` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'COMPLETED'")
        db.execSQL("ALTER TABLE `financial_transaction` ADD COLUMN `tx_status` TEXT NOT NULL DEFAULT 'CONFIRMED_AUTO'")
    }
}
