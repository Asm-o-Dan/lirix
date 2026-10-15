# Задача TASK-KMP-06: Переход на Room KMP 2.7.1 и sqlite-bundled

- **ID задачи:** `TASK-KMP-06`
- **Роль исполнителя:** DB Engineer
- **Зона:** `media-core` & `storage`
- **Файлы:**
  - `gradle/libs.versions.toml` (`room = "2.7.1"`, `androidx-sqlite-bundled`)
  - `shared/src/commonMain/kotlin/com/lirix/app/storage/`:
    - `MusicEntities.kt`, `BlacklistEntities.kt`, `EventEntity.kt`
    - `MusicDao.kt`, `LyricsDao.kt`, `BlacklistDao.kt`, `AchievementDao.kt`, `EventDao.kt`
    - `AppDatabase.kt` (общий интерфейс базы с `@ConstructedBy`)
  - `shared/src/androidMain/kotlin/.../DatabaseFactory.kt` (Room.databaseBuilder на Android с миграциями 5→11)
  - `shared/src/desktopMain/kotlin/.../DatabaseFactory.kt` (BundledSQLiteDriver, `%APPDATA%\Lirix\lirix.db` v11 с нуля)
- **Зависит от:** `TASK-KMP-05`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** HIGH

---

## 1. Цель задачи
Обеспечить единый слой хранения данных Room как на Android, так и на Windows Desktop через Room Multiplatform 2.7.1:
1. Все Entity и DAO компилируются в `commonMain`.
2. На Android сохраняется 100% обратная совместимость с текущей базой Room v11 (миграции 5..11).
3. На Windows создается чистая локальная база v11 без необходимости выполнения Android-миграций.

---

## 2. Критерии приемки (DoD)
- [ ] Все Entity и DAO перенесены в `shared/src/commonMain`.
- [ ] `MusicDatabaseTest` и `BlacklistEngineTest` проходят на 100%.
- [ ] На Windows Desktop база успешно инициализируется в `%APPDATA%\Lirix\lirix.db`.
