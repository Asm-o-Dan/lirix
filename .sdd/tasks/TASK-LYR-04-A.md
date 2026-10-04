# Задача TASK-LYR-04-A: Модель пользовательских правил CustomLyricsRuleEntity и Room миграция MIGRATION_9_10

- **ID задачи:** `TASK-LYR-04-A`
- **Роль исполнителя:** Кодер
- **Зона:** `media-core`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/storage/MusicEntities.kt` (сущность `CustomLyricsRuleEntity`)
  2. `app/src/main/java/com/eventengine/app/storage/LyricsDao.kt` (методы сохранения, выборки и удаления правил)
  3. `app/src/main/java/com/eventengine/app/storage/AppDatabase.kt` (версия базы 10, миграция `MIGRATION_9_10`)
  4. `app/src/test/java/com/eventengine/app/DatabaseMigration9to10Test.kt` (unit-тесты миграции и операций с правилами)
- **Спека:** [.sdd/architecture_lyr_community.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_lyr_community.md) (Разделы 2.1, 2.2, 2.3)
- **Приоритет:** HIGH (Хранилище декларативных правил для FR-3/FR-4)

---

## 1. Назначение и контекст

Для реализации пользовательских источников текстов (FR-3) и экрана управления правилами (FR-4) необходимо сохранять в Room декларативные правила-парсеры. Каждое правило описывает домен, селекторы контента, шаблоны поиска и параметры очистки в виде JSON. Правила должны поддерживать приоритеты, включение/выключение, реактивное наблюдение через Flow и безопасную миграцию базы данных с версии 9 на 10 без потери пользовательских данных.

---

## 2. Спецификация изменений

### 2.1 Новая сущность в `storage/MusicEntities.kt`

Добавить Room-сущность `CustomLyricsRuleEntity`:

```kotlin
package com.eventengine.app.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a declarative scraper rule for a specific lyrics domain.
 * Spec: TASK-LYR-04-A / .sdd/architecture_lyr_community.md
 *
 * id         — Unique slug or UUID (e.g. "rule_amalgama_v1")
 * domain     — Root web domain (e.g. "amalgama-lab.com")
 * name       — Human-readable source name (e.g. "Амальгама (Русский перевод)")
 * ruleJson   — Declarative JSON string describing selectors and search patterns
 * isEnabled  — Active toggle for cascade execution
 * priority   — Ordering priority (lower value = higher priority, default 100)
 * isBuiltIn  — Protection flag against accidental deletion
 * author     — Author identifier ("local" or community handle)
 * version    — Schema version of the rule JSON
 * createdAt  — Epoch timestamp of creation
 * updatedAt  — Epoch timestamp of last update
 */
@Entity(
    tableName = "custom_lyrics_rules",
    indices = [
        Index(value = ["domain"]),
        Index(value = ["isEnabled"]),
        Index(value = ["priority"])
    ]
)
data class CustomLyricsRuleEntity(
    @PrimaryKey
    val id: String,
    val domain: String,
    val name: String,
    val ruleJson: String,
    val isEnabled: Boolean = true,
    val priority: Int = 100,
    val isBuiltIn: Boolean = false,
    val author: String = "local",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

### 2.2 Дополнения в DAO `storage/LyricsDao.kt`

Добавить в интерфейс `LyricsDao` следующие методы:

```kotlin
// Сохранение и обновление правила
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun saveRule(rule: CustomLyricsRuleEntity)

// Получение всех активных правил, отсортированных по приоритету
@Query("SELECT * FROM custom_lyrics_rules WHERE isEnabled = 1 ORDER BY priority ASC, createdAt DESC")
suspend fun getActiveRules(): List<CustomLyricsRuleEntity>

// Получение правила по ID
@Query("SELECT * FROM custom_lyrics_rules WHERE id = :id LIMIT 1")
suspend fun getRuleById(id: String): CustomLyricsRuleEntity?

// Получение правил для конкретного домена
@Query("SELECT * FROM custom_lyrics_rules WHERE domain = :domain")
suspend fun getRulesForDomain(domain: String): List<CustomLyricsRuleEntity>

// Реактивное наблюдение всех правил для экрана управления (FR-4)
@Query("SELECT * FROM custom_lyrics_rules ORDER BY priority ASC, createdAt DESC")
fun observeAllRules(): kotlinx.coroutines.flow.Flow<List<CustomLyricsRuleEntity>>

// Включение / выключение правила
@Query("UPDATE custom_lyrics_rules SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
suspend fun setRuleEnabled(id: String, isEnabled: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

// Удаление пользовательского правила (встроенные правила удалять запрещено)
@Query("DELETE FROM custom_lyrics_rules WHERE id = :id AND isBuiltIn = 0")
suspend fun deleteRule(id: String): Int
```

### 2.3 Обновление базы данных в `storage/AppDatabase.kt`

1. Добавить `CustomLyricsRuleEntity::class` в аннотацию `@Database`:
   ```kotlin
   @Database(
       entities = [
           TrackEntity::class,
           ListeningSessionEntity::class,
           LyricsCacheEntity::class,
           AchievementEntity::class,
           LyricsRejectionEntity::class,
           CustomLyricsRuleEntity::class
       ],
       version = 10,
       exportSchema = false
   )
   ```
2. Реализовать объект `MIGRATION_9_10`:
   ```kotlin
   @JvmField
   val MIGRATION_9_10: Migration = object : Migration(9, 10) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL(
               """
               CREATE TABLE IF NOT EXISTS `custom_lyrics_rules` (
                   `id` TEXT NOT NULL,
                   `domain` TEXT NOT NULL,
                   `name` TEXT NOT NULL,
                   `ruleJson` TEXT NOT NULL,
                   `isEnabled` INTEGER NOT NULL DEFAULT 1,
                   `priority` INTEGER NOT NULL DEFAULT 100,
                   `isBuiltIn` INTEGER NOT NULL DEFAULT 0,
                   `author` TEXT NOT NULL DEFAULT 'local',
                   `version` INTEGER NOT NULL DEFAULT 1,
                   `createdAt` INTEGER NOT NULL,
                   `updatedAt` INTEGER NOT NULL,
                   PRIMARY KEY(`id`)
               )
               """.trimIndent()
           )
           db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_lyrics_rules_domain` ON `custom_lyrics_rules` (`domain`)")
           db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_lyrics_rules_isEnabled` ON `custom_lyrics_rules` (`isEnabled`)")
           db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_lyrics_rules_priority` ON `custom_lyrics_rules` (`priority`)")
       }
   }
   ```
3. Зарегистрировать `MIGRATION_9_10` в методе `buildDatabase`:
   ```kotlin
   .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
   ```

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `DatabaseMigration9to10Test.kt`:**
   - `test_migration_9_to_10_preserves_existing_data`: миграция v9 $\to$ v10 создает таблицу `custom_lyrics_rules` с 3 индексами и сохраняет все записи в `music_tracks`, `lyrics_cache`, `achievements`, `lyrics_rejections`.
   - `test_save_and_query_active_rules`: сохранение двух правил (одно `isEnabled = true, priority = 50`, второе `isEnabled = false`) возвращает через `getActiveRules()` только активное правило с корректным приоритетом.
   - `test_delete_rule_prevents_builtin_deletion`: вызов `deleteRule` удаляет локальное правило (`isBuiltIn = false`), но возвращает 0 удаленных строк для правила с `isBuiltIn = true`.
   - `test_set_rule_enabled_toggles_state`: обновление статуса активности корректно обновляет поле `isEnabled` и `updatedAt`.
2. Все тесты (`.\gradlew.bat test`) завершаются со статусом SUCCESS.
