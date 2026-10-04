# Задача TASK-DB-01: реализовать сущности и DAO `LyricsCacheEntity` и `AchievementEntity`

**Файл:** `app/src/main/java/com/eventengine/app/storage/MusicEntities.kt` (создать / обновить)
**Спека:** [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) (v1), контракт `contracts/core__lyrics.md`, `contracts/core__wrapped.md`

## 1. Сущность `LyricsCacheEntity`
```kotlin
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey
    val trackKey: String, // SHA-256("lowercase_artist||lowercase_title")
    val plainLyrics: String?,
    val syncedLyricsLrc: String?,
    val chordsAmDm: String?,
    val userNotes: String?,
    val provider: String, // "LRCLIB", "AMDM", "USER", "NONE"
    val updatedAt: Long = System.currentTimeMillis()
)
```

## 2. Сущность `AchievementEntity`
```kotlin
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String, // e.g. "FIRST_TRACK", "NIGHT_OWL", "CENTURY_CLUB"
    val title: String,
    val description: String,
    val iconRes: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val currentProgress: Int = 0,
    val maxProgress: Int = 1
)
```

## 3. DAO интерфейсы:
- `LyricsDao`:
  - `@Query("SELECT * FROM lyrics_cache WHERE trackKey = :trackKey LIMIT 1") suspend fun getLyrics(trackKey: String): LyricsCacheEntity?`
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLyrics(entity: LyricsCacheEntity)`
- `AchievementDao`:
  - `@Query("SELECT * FROM achievements ORDER BY isUnlocked DESC, id ASC") fun observeAchievements(): Flow<List<AchievementEntity>>`
  - `@Query("SELECT * FROM achievements WHERE id = :id LIMIT 1") suspend fun getAchievement(id: String): AchievementEntity?`
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOrUpdate(achievement: AchievementEntity)`
  - `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(achievements: List<AchievementEntity>)`

**Критерий приёмки:**
- Сущности и DAO компилируются в Room.
- Тесты `MusicDatabaseTest` проверяют CRUD-операции.
