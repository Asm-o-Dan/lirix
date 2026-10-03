## Задача STORE-P2-004: Реализовать MIGRATION_2_3 и автосидинг пресета

**Файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/migration/MIGRATION_2_3.kt` (создать)  
**Модуль:** `:core:storage`  
**Спека:** `.sdd/specs/pipeline-store/overview.md#4-реализация-миграции-room-migration_2_3kt`  
**Контракт:** `.sdd/contracts/pipeline-store__dsl.md#3-миграция-v2--v3-и-premigrationbackup-30`  

---

### Сигнатуры (НЕ МЕНЯТЬ):

```kotlin
package com.example.npc.core.storage.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase)
}
```

---

### Поведение:

1. **Создание таблицы `pipeline_definition` и индексов:**
   ```sql
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
   );
   CREATE INDEX IF NOT EXISTS `index_pipeline_definition_enabled_priority` ON `pipeline_definition` (`enabled`, `priority`);
   CREATE INDEX IF NOT EXISTS `index_pipeline_definition_active_revision_id` ON `pipeline_definition` (`active_revision_id`);
   ```

2. **Создание таблицы `pipeline_revision` и индексов:**
   ```sql
   CREATE TABLE IF NOT EXISTS `pipeline_revision` (
       `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
       `pipeline_id` TEXT NOT NULL,
       `revision_number` INTEGER NOT NULL,
       `definition_json` TEXT NOT NULL,
       `canonical_sha256` TEXT NOT NULL,
       `created_at` INTEGER NOT NULL,
       `commit_message` TEXT,
       FOREIGN KEY(`pipeline_id`) REFERENCES `pipeline_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
   );
   CREATE UNIQUE INDEX IF NOT EXISTS `index_pipeline_revision_pipeline_id_revision_number` ON `pipeline_revision` (`pipeline_id`, `revision_number`);
   CREATE INDEX IF NOT EXISTS `index_pipeline_revision_pipeline_id_created_at` ON `pipeline_revision` (`pipeline_id`, `created_at`);
   CREATE INDEX IF NOT EXISTS `index_pipeline_revision_canonical_sha256` ON `pipeline_revision` (`canonical_sha256`);
   ```

3. **Создание таблицы `runtime_alert` и индексов:**
   ```sql
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
   );
   CREATE INDEX IF NOT EXISTS `index_runtime_alert_is_dismissed_timestamp` ON `runtime_alert` (`is_dismissed`, `timestamp`);
   CREATE INDEX IF NOT EXISTS `index_runtime_alert_pipeline_id` ON `runtime_alert` (`pipeline_id`);
   ```

4. **Модификация существующей таблицы `event`:**
   ```sql
   ALTER TABLE `event` ADD COLUMN `pipeline_revision_id` INTEGER DEFAULT NULL;
   CREATE INDEX IF NOT EXISTS `index_event_pipeline_revision_id` ON `event` (`pipeline_revision_id`);
   ```

5. **Автоматический сидинг эталонного пресета (`preset-legacy-1.1`):**
   - Используется каноническая строка JSON `LEGACY_PRESET_CANONICAL_JSON` (соответствующая схеме DSL v1).
   - Вычисляется SHA-256 хеш строки через `MessageDigest.getInstance("SHA-256")`.
   - Вставляется запись в `pipeline_definition`:
     - `id = "legacy-1.1-preset"`
     - `name = "Legacy 1.1 Baseline Pipeline"`
     - `description = "Эталонная конфигурация Фазы 1.1 для непрерывности догфудинга"`
     - `schema_version = 1`, `enabled = 1`, `priority = 100`, `package_whitelist = "[]"`
   - Вставляется запись в `pipeline_revision`:
     - `pipeline_id = "legacy-1.1-preset"`, `revision_number = 1L`
     - `definition_json = LEGACY_PRESET_CANONICAL_JSON`
     - `canonical_sha256 = sha256`
     - `commit_message = "Initial seed for Phase 1.1 parity continuity"`
   - Обновляется `pipeline_definition`: `active_revision_id = revisionId`.

6. **Валидация внешних ключей (Integrity Gate):**
   ```kotlin
   db.query("PRAGMA foreign_key_check").use { cursor ->
       check(cursor.count == 0) {
           "Foreign key integrity check failed after Migration 2->3. Violations count: ${cursor.count}"
       }
   }
   ```

---

### Ошибки:

- При наличии нарушений целостности внешних ключей выбрасывать `IllegalStateException`.
- Ошибки выполнения SQL-запросов приводят к автоматическому откату транзакции на уровне SQLite.

---

### Граничные случаи:

- Повторный запуск миграции (все операторы используют `IF NOT EXISTS`, а сидинг выполняется с `CONFLICT_REPLACE`).
- Миграция базы с 50 000+ событий: `ALTER TABLE ADD COLUMN` выполняется мгновенно за счет SQLite column metadata append.
- Системный пресет `preset-legacy-1.1` обеспечивает мгновенную готовность рантайма без необходимости сетевых запросов.

---

### Запрещено:

- Использовать операторы `DROP TABLE` или `DELETE FROM` для любых существующих таблиц догфудинга (`raw_event`, `event`, `financial_transaction`, `user_prototype`, `source_health`).
- Подключать внешние сериализаторы JSON на этапе миграции (канонический JSON пресета обязан быть зашит как строковая константа для детерминизма).
- Пропускать вызов `PRAGMA foreign_key_check`.

---

### Критерий приёмки:

- Объект `MIGRATION_2_3` скомпилирован в модуле `:core:storage`.
- Инструментальный/Robolectric тест `Migration2to3Test`:
  1. Выполняет миграцию с версии 2 на версию 3 на тестовой БД с фикстурами догфудинга.
  2. Проверяет неизменность количества строк во всех существующих таблицах (Zero Data Loss).
  3. Проверяет наличие пресета `legacy-1.1-preset` с ревизией 1 и `active_revision_id`.
  4. Проверяет успешность `PRAGMA foreign_key_check`.
