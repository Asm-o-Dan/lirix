package com.example.npc.core.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // =================================================================
        // 1. Создание таблицы dynamic_template и индексов
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dynamic_template` (
                `id` TEXT NOT NULL,
                `sourceKey` TEXT NOT NULL,
                `tier` TEXT NOT NULL,
                `origin` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `priority` INTEGER NOT NULL,
                `pattern` TEXT NOT NULL,
                `bindingsJson` TEXT NOT NULL,
                `constantsJson` TEXT NOT NULL,
                `amountFormatJson` TEXT NOT NULL,
                `specVersion` INTEGER NOT NULL,
                `compilerVersion` INTEGER NOT NULL,
                `canonicalHash` TEXT NOT NULL,
                `specificity` REAL NOT NULL,
                `parentTemplateId` TEXT,
                `sampleEventId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `stateReason` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_dyn_tmpl_src_state` ON `dynamic_template` (`sourceKey`, `state`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_dyn_tmpl_hash` ON `dynamic_template` (`canonicalHash`)")

        // =================================================================
        // 2. Создание таблицы template_stats
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `template_stats` (
                `templateId` TEXT NOT NULL,
                `hits` INTEGER NOT NULL DEFAULT 0,
                `parseFailures` INTEGER NOT NULL DEFAULT 0,
                `userCorrections` INTEGER NOT NULL DEFAULT 0,
                `shadowAgreements` INTEGER NOT NULL DEFAULT 0,
                `shadowDisagreements` INTEGER NOT NULL DEFAULT 0,
                `lastHitAt` INTEGER DEFAULT NULL,
                PRIMARY KEY(`templateId`),
                FOREIGN KEY(`templateId`) REFERENCES `dynamic_template`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // =================================================================
        // 3. Создание таблицы template_bank_version
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `template_bank_version` (
                `version` INTEGER NOT NULL,
                `parentVersion` INTEGER DEFAULT NULL,
                `membershipHash` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `cause` TEXT NOT NULL,
                PRIMARY KEY(`version`)
            )
            """.trimIndent()
        )

        // =================================================================
        // 4. Создание таблицы template_bank_membership
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `template_bank_membership` (
                `version` INTEGER NOT NULL,
                `templateId` TEXT NOT NULL,
                PRIMARY KEY(`version`, `templateId`)
            )
            """.trimIndent()
        )

        // =================================================================
        // 5. Модификация существующей таблицы financial_transaction
        // =================================================================
        db.execSQL("ALTER TABLE `financial_transaction` ADD COLUMN `extractorKind` TEXT NOT NULL DEFAULT 'STATIC'")
        db.execSQL("ALTER TABLE `financial_transaction` ADD COLUMN `templateId` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `financial_transaction` ADD COLUMN `bankVersion` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `financial_transaction` ADD COLUMN `isRefund` INTEGER NOT NULL DEFAULT 0")

        // =================================================================
        // 6. Валидация внешних ключей (Integrity Gate)
        // =================================================================
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) {
                "Foreign key integrity check failed after Migration 3->4. Violations count: ${cursor.count}"
            }
        }
    }
}
