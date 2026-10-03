package com.example.npc.core.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // =================================================================
        // 1. Таблица event: строго аддитивное добавление столбцов
        // =================================================================
        db.execSQL("ALTER TABLE `event` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'UNCLASSIFIED'")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `engine_used` TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `is_user_corrected` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `content_fingerprint` TEXT DEFAULT NULL")

        // Индексы для фильтрации в Timeline 2.0
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_category` ON `event` (`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_content_fingerprint` ON `event` (`content_fingerprint`)")

        // =================================================================
        // 2. Новая таблица: financial_transaction
        // =================================================================
        // Внешний ключ event_id имеет ON DELETE SET NULL, чтобы удаление
        // старых событий (retention) не уничтожало финансовый реестр!
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `financial_transaction` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`event_id` INTEGER, " +
                "`bank` TEXT NOT NULL, " +
                "`direction` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`balance_minor` INTEGER, " +
                "`balance_currency` TEXT, " +
                "`merchant` TEXT, " +
                "`account_mask` TEXT, " +
                "`occurred_at` INTEGER NOT NULL, " +
                "`extractor_id` TEXT NOT NULL, " +
                "`extractor_version` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`event_id`) REFERENCES `event`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                ")"
        )

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_transaction_event_id` ON `financial_transaction` (`event_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transaction_occurred_at` ON `financial_transaction` (`occurred_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transaction_bank` ON `financial_transaction` (`bank`)")

        // =================================================================
        // 3. Новая таблица: user_prototype (Feedback Loop)
        // =================================================================
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_prototype` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`package_name` TEXT NOT NULL, " +
                "`fingerprint` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`support_count` INTEGER NOT NULL DEFAULT 1, " +
                "`created_at` INTEGER NOT NULL, " +
                "`last_seen_at` INTEGER NOT NULL" +
                ")"
        )

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_prototype_package_fingerprint` ON `user_prototype` (`package_name`, `fingerprint`)")

        // =================================================================
        // 4. Проверка целостности внешних ключей (Integrity Gate)
        // =================================================================
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) {
                "Foreign key integrity check failed after Migration 1->2. Violations count: ${cursor.count}"
            }
        }
    }
}
