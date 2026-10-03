package com.example.npc.core.storage.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

const val LEGACY_PRESET_CANONICAL_JSON =
    """{"description":"Эталонная конфигурация Фазы 1.1 для непрерывности догфудинга","enabled":true,"id":"legacy-1.1-preset","metadata":{"builtIn":"true","origin":"preset-legacy-1.1","parityBaseline":"1.1"},"name":"Legacy 1.1 Baseline Pipeline","packageWhitelist":[],"priority":100,"revision":1,"schemaVersion":1,"stages":[{"actions":[],"condition":null,"enabled":true,"id":"stage-fingerprint","name":"Вычисление отпечатка контента","terminateOnMatch":false,"transforms":[{"algorithm":"SHA-256-TEMPLATED","targetVar":"contentFingerprint","type":"FingerprintCompute"}]},{"actions":[{"category":"UNCLASSIFIED","confidence":1.0,"engine":"PROTOTYPE","type":"SetCategory"}],"condition":{"minSupportCount":2,"type":"PrototypeSupportCount"},"enabled":true,"id":"stage-prototype-feedback","name":"Пользовательский прототип (Feedback Loop)","terminateOnMatch":false,"transforms":[]},{"actions":[],"condition":{"conditions":[{"conditions":[{"matchMode":"EXACT","packages":["com.apb.mobile","com.prisbank.app","md.maib.maibank"],"type":"PackageMatch"},{"conditions":[{"matchMode":"EXACT","packages":["com.google.android.apps.messaging","com.android.mms",""],"type":"PackageMatch"},{"caseSensitive":false,"senders":["APB","AGROPROMBANK","PRISBANK","SBERBANK","MAIB","900"],"type":"SenderMatch"}],"type":"LogicalAnd"}],"type":"LogicalOr"},{"condition":{"matchMode":"EXACT","packages":["org.telegram.messenger","org.telegram.plus","org.thunderdog.challegram","com.radolyn.ayugram","nekox.messenger","com.whatsapp","com.whatsapp.w4b","com.viber.voip","com.facebook.orca","com.facebook.mlite","com.discord","com.vkontakte.android","com.vk.im"],"type":"PackageMatch"},"type":"LogicalNot"}],"type":"LogicalAnd"},"enabled":true,"id":"stage-bank-finance","name":"Финансовая обработка доверенных банков","terminateOnMatch":false,"transforms":[{"maxChars":1024,"normalizeNbsp":true,"stripDiacritics":false,"targetVar":"sanitizedText","type":"RegionalTextSanitize"},{"extractorId":"auto","targetVar":"transaction","type":"FinanceExtract"}]}],"triggers":[{"ignoreSelf":true,"subTypes":[],"type":"NOTIFICATION"},{"allowDirectReceiver":true,"allowMessagingApps":true,"type":"SMS"},{"captureArtwork":false,"type":"MEDIA"}]}"""

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // =================================================================
        // 1. Создание таблицы pipeline_definition
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pipeline_definition` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `schema_version` INTEGER NOT NULL DEFAULT 1,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `priority` INTEGER NOT NULL DEFAULT 100,
                `package_whitelist` TEXT NOT NULL DEFAULT '[]',
                `active_revision_id` INTEGER DEFAULT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_definition_enabled_priority` ON `pipeline_definition` (`enabled`, `priority`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_definition_active_revision_id` ON `pipeline_definition` (`active_revision_id`)")

        // =================================================================
        // 2. Создание таблицы pipeline_revision
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pipeline_revision` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pipeline_id` TEXT NOT NULL,
                `revision_number` INTEGER NOT NULL,
                `definition_json` TEXT NOT NULL,
                `canonical_sha256` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `commit_message` TEXT,
                FOREIGN KEY(`pipeline_id`) REFERENCES `pipeline_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pipeline_revision_pipeline_id_revision_number` ON `pipeline_revision` (`pipeline_id`, `revision_number`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_revision_pipeline_id_created_at` ON `pipeline_revision` (`pipeline_id`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_revision_canonical_sha256` ON `pipeline_revision` (`canonical_sha256`)")

        // =================================================================
        // 3. Создание таблицы runtime_alert
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `runtime_alert` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pipeline_id` TEXT NOT NULL,
                `node_id` TEXT,
                `level` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `payload_json` TEXT,
                `timestamp` INTEGER NOT NULL,
                `is_dismissed` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_runtime_alert_pipeline_id_timestamp` ON `runtime_alert` (`pipeline_id`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_runtime_alert_level` ON `runtime_alert` (`level`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_runtime_alert_is_dismissed_timestamp` ON `runtime_alert` (`is_dismissed`, `timestamp`)")

        // =================================================================
        // 4. Модификация существующей таблицы event
        // =================================================================
        db.execSQL("ALTER TABLE `event` ADD COLUMN `pipeline_revision_id` INTEGER DEFAULT NULL REFERENCES `pipeline_revision`(`id`) ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_pipeline_revision_id` ON `event` (`pipeline_revision_id`)")

        // =================================================================
        // 5. Сидинг системного пресета: preset-legacy-1.1
        // =================================================================
        seedLegacyPreset(db)

        // =================================================================
        // 6. Валидация внешних ключей (Integrity Gate)
        // =================================================================
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) {
                "Foreign key integrity check failed after Migration 2->3. Violations count: ${cursor.count}"
            }
        }
    }

    private fun seedLegacyPreset(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val pipelineId = "legacy-1.1-preset"
        val canonicalJson = LEGACY_PRESET_CANONICAL_JSON
        val sha256 = calculateSha256(canonicalJson)

        // 5.1 Вставляем запись в pipeline_definition
        val defValues = ContentValues().apply {
            put("id", pipelineId)
            put("name", "Legacy 1.1 Baseline Pipeline")
            put("description", "Эталонная конфигурация Фазы 1.1 для непрерывности догфудинга")
            put("schema_version", 1)
            put("enabled", 1)
            put("priority", 100)
            put("package_whitelist", "[]")
            putNull("active_revision_id")
            put("created_at", now)
            put("updated_at", now)
        }
        db.insert("pipeline_definition", SQLiteDatabase.CONFLICT_REPLACE, defValues)

        // 5.2 Вставляем ревизию #1
        val revValues = ContentValues().apply {
            put("pipeline_id", pipelineId)
            put("revision_number", 1L)
            put("definition_json", canonicalJson)
            put("canonical_sha256", sha256)
            put("created_at", now)
            put("commit_message", "Initial seed for Phase 1.1 parity continuity")
        }
        val revisionId = db.insert("pipeline_revision", SQLiteDatabase.CONFLICT_REPLACE, revValues)

        // 5.3 Связываем active_revision_id в pipeline_definition
        val updateValues = ContentValues().apply {
            put("active_revision_id", revisionId)
            put("updated_at", now)
        }
        db.update("pipeline_definition", SQLiteDatabase.CONFLICT_REPLACE, updateValues, "id = ?", arrayOf(pipelineId))
    }

    private fun calculateSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
