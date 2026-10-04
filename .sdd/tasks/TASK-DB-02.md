# Задача TASK-DB-02: реализовать миграцию MIGRATION_5_6 и чистый AppDatabase (v6)

**Файл:** `app/src/main/java/com/eventengine/app/storage/AppDatabase.kt` (обновить)
**Спека:** [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) (v1), [architecture.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture.md) (§5)

**Версия БД:** 6
**Сущности БД v6:**
- `MusicTrackEntity::class`
- `MusicListeningSessionEntity::class`
- `LyricsCacheEntity::class`
- `AchievementEntity::class`

**Удаляемые сущности из аннотации @Database:**
- `EventEntity`
- `FinancialTransactionEntity`
- `StudyItemEntity`
- `RuleEntity`

**Миграция `val MIGRATION_5_6 = object : Migration(5, 6)`:**
1. Создать новую таблицу `lyrics_cache`:
   ```sql
   CREATE TABLE IF NOT EXISTS `lyrics_cache` (
       `trackKey` TEXT NOT NULL,
       `plainLyrics` TEXT,
       `syncedLyricsLrc` TEXT,
       `chordsAmDm` TEXT,
       `userNotes` TEXT,
       `provider` TEXT NOT NULL,
       `updatedAt` INTEGER NOT NULL,
       PRIMARY KEY(`trackKey`)
   )
   ```
2. Создать новую таблицу `achievements`:
   ```sql
   CREATE TABLE IF NOT EXISTS `achievements` (
       `id` TEXT NOT NULL,
       `title` TEXT NOT NULL,
       `description` TEXT NOT NULL,
       `iconRes` TEXT NOT NULL,
       `isUnlocked` INTEGER NOT NULL DEFAULT 0,
       `unlockedAt` INTEGER,
       `currentProgress` INTEGER NOT NULL DEFAULT 0,
       `maxProgress` INTEGER NOT NULL DEFAULT 1,
       PRIMARY KEY(`id`)
   )
   ```
3. Выполнить сидирование 7 базовых достижений в `achievements` через `INSERT OR IGNORE`.
4. Удалить устаревшие таблицы:
   ```sql
   DROP TABLE IF EXISTS `financial_transactions`;
   DROP TABLE IF EXISTS `study_items`;
   DROP TABLE IF EXISTS `rule_entities`;
   DROP TABLE IF EXISTS `events`;
   ```

**Критерий приёмки:**
- Тест миграции `AppDatabaseMigrationTest::test_migration_5_to_6` проходит успешно без исключений и потери треков.
