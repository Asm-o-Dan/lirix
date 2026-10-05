# Задача TASK-LYR-03-A: Модель данных отклонений источников и Room миграция MIGRATION_8_9

- **ID задачи:** `TASK-LYR-03-A`
- **Роль исполнителя:** Кодер
- **Зона:** `media-core`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/storage/MusicEntities.kt` (сущность `LyricsRejectionEntity`)
  2. `app/src/main/java/com/eventengine/app/storage/LyricsDao.kt` (методы работы с отклонениями)
  3. `app/src/main/java/com/eventengine/app/storage/AppDatabase.kt` (миграция v8 -> v9 `MIGRATION_8_9`)
  4. `app/src/test/java/com/eventengine/app/DatabaseMigration8to9Test.kt` (тесты миграции)
- **Спека:** [.sdd/architecture_lyr_community.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_lyr_community.md) (Раздел 2), [.sdd/intake/LYR-COMMUNITY.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/intake/LYR-COMMUNITY.md)
- **Приоритет:** HIGH (Фундамент персистентности для FR-1)

---

## 1. Назначение и контекст

Для реализации требования FR-1 («Не тот текст») необходимо персистентно хранить факты отклонения текста песни пользователем. Если пользователь пометил текст от источника `builtin:lrclib` как неподходящий для трека `X`, приложение обязано сохранить это в базе данных, чтобы ни при перезапуске, ни при фоновой предзагрузке (prefetch) данный источник повторно не предлагался для трека `X`.

---

## 2. Спецификация изменений

### 2.1 Новая сущность в `storage/MusicEntities.kt`

Добавить Room-сущность `LyricsRejectionEntity`:

```kotlin
package com.lirix.app.storage

import androidx.room.Entity
import androidx.room.Index

/**
 * Room entity representing an explicit user rejection of a lyrics source for a specific track.
 * Spec: TASK-LYR-03-A / .sdd/architecture_lyr_community.md
 *
 * trackKey   — SHA-256 fingerprint of normalized track (title + artist + album)
 * sourceId   — Canonical identifier of rejected provider (e.g. "builtin:lrclib", "builtin:amdm")
 * rejectedAt — Epoch timestamp in milliseconds when user clicked "Not this lyrics"
 */
@Entity(
    tableName = "lyrics_rejections",
    primaryKeys = ["trackKey", "sourceId"],
    indices = [
        Index(value = ["trackKey"]),
        Index(value = ["sourceId"])
    ]
)
data class LyricsRejectionEntity(
    val trackKey: String,
    val sourceId: String,
    val rejectedAt: Long = System.currentTimeMillis()
)
```

### 2.2 Дополнения в DAO `storage/LyricsDao.kt`

Добавить в интерфейс `LyricsDao` следующие методы:

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertRejection(rejection: LyricsRejectionEntity)

@Query("SELECT sourceId FROM lyrics_rejections WHERE trackKey = :trackKey")
suspend fun getRejectedSourceIds(trackKey: String): List<String>

@Query("DELETE FROM lyrics_rejections WHERE trackKey = :trackKey AND sourceId = :sourceId")
suspend fun deleteRejection(trackKey: String, sourceId: String): Int

@Query("DELETE FROM lyrics_rejections WHERE trackKey = :trackKey")
suspend fun clearRejectionsForTrack(trackKey: String): Int

@Query("SELECT COUNT(*) FROM lyrics_rejections WHERE trackKey = :trackKey")
suspend fun getRejectionsCountForTrack(trackKey: String): Int
```

### 2.3 Обновление базы данных в `storage/AppDatabase.kt`

1. Добавить `LyricsRejectionEntity::class` в аннотацию `@Database`:
   ```kotlin
   @Database(
       entities = [
           TrackEntity::class,
           ListeningSessionEntity::class,
           LyricsCacheEntity::class,
           AchievementEntity::class,
           LyricsRejectionEntity::class
       ],
       version = 9,
       exportSchema = false
   )
   ```
2. Реализовать объект `MIGRATION_8_9`:
   ```kotlin
   @JvmField
   val MIGRATION_8_9: Migration = object : Migration(8, 9) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL(
               """
               CREATE TABLE IF NOT EXISTS `lyrics_rejections` (
                   `trackKey` TEXT NOT NULL,
                   `sourceId` TEXT NOT NULL,
                   `rejectedAt` INTEGER NOT NULL,
                   PRIMARY KEY(`trackKey`, `sourceId`)
               )
               """.trimIndent()
           )
           db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_rejections_trackKey` ON `lyrics_rejections` (`trackKey`)")
           db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_rejections_sourceId` ON `lyrics_rejections` (`sourceId`)")
       }
   }
   ```
3. Зарегистрировать `MIGRATION_8_9` в `buildDatabase`:
   ```kotlin
   .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
   ```

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `DatabaseMigration8to9Test.kt`:**
   - `test_rejection_insertion_and_retrieval`: вставка `LyricsRejectionEntity` сохраняется и возвращается через `getRejectedSourceIds`.
   - `test_rejection_deletion_and_clear`: метод `deleteRejection` удаляет конкретную связку `(trackKey, sourceId)`, а `clearRejectionsForTrack` удаляет все отклонения трека.
   - `test_rejections_do_not_affect_existing_lyrics_cache`: наличие записей в `lyrics_rejections` не нарушает выборки и сохранность данных в `lyrics_cache` и `music_tracks`.
2. Команда `.\gradlew.bat test` завершается успешно без падений существующих тестов.
