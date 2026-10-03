## Задача STORAGE-P1-002: Реализовать объект миграции схемы MIGRATION_1_2

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/migration/MIGRATION_1_2.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#33-исполняемый-объект-migration_1_2-и-валидация-целостности`  
**Архитектура:** `.sdd/architecture_phase1.md#53`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration
```

### Инварианты и алгоритм:
1. Строго аддитивная миграция v1 $\to$ v2:
   - `ALTER TABLE event ADD COLUMN category TEXT NOT NULL DEFAULT 'UNCLASSIFIED'`
   - `ALTER TABLE event ADD COLUMN confidence REAL NOT NULL DEFAULT 0.0`
   - `ALTER TABLE event ADD COLUMN engine_used TEXT NOT NULL DEFAULT 'NONE'`
   - `ALTER TABLE event ADD COLUMN is_user_corrected INTEGER NOT NULL DEFAULT 0`
   - `ALTER TABLE event ADD COLUMN content_fingerprint TEXT DEFAULT NULL`
   - Индексы: `index_event_category` и `index_event_content_fingerprint`.
2. Создание таблицы `financial_transaction` с `FOREIGN KEY(event_id) REFERENCES event(id) ON DELETE SET NULL` и индексами `event_id`, `occurred_at`, `bank`.
3. Создание таблицы `user_prototype` с составным уникальным индексом `(package_name, fingerprint)`.
4. Валидация целостности внешних ключей:
   ```kotlin
   db.query("PRAGMA foreign_key_check").use { cursor ->
       check(cursor.count == 0) { "Foreign key integrity check failed after Migration 1->2" }
   }
   ```

### Критерии приемки (DoD):
- [ ] Тест Room MigrationTestHelper успешно накатывает миграцию v1 $\to$ v2.
- [ ] Все существующие строки таблицы `event` сохраняются и получают дефолтные значения.
- [ ] `PRAGMA foreign_key_check` не выявляет нарушений.
