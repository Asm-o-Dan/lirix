# Спецификация Зоны Media Core & Storage: Движок Черного Списка (SPEC-BLK-01)

**TASK_ID:** SPEC-BLK-01  
**Зона ответственности:** `media-core` & `storage`  
**Зависимости:** ARCH-BLK-01 (.sdd/architecture_blacklist_filters.md), ADR-016 (.sdd/decisions.md)  
**Статус:** FROZEN v1  

---

## 1. Назначение и Цели
Спецификация определяет модель хранения, миграцию базы данных Room до версии 11, DAO-методы, in-memory кэш правил для zero-latency рантайм-фильтрации и атомарный движок каскадной очистки медиатеки (`Purge Engine`).

---

## 2. Модель Данных (Room Schema v11)

### 2.1 Перечисления (Enums)
Пакет: `com.lirix.app.storage.blacklist`

```kotlin
package com.lirix.app.storage.blacklist

enum class BlacklistRuleType {
    TITLE,         // Сопоставление по названию трека
    ARTIST,        // Сопоставление по исполнителю
    PACKAGE_NAME   // Сопоставление по имени Android-пакета источника
}

enum class BlacklistMatchMode {
    CONTAINS_IGNORE_CASE, // Вхождение подстроки без учета регистра (дефолт)
    EXACT,                // Полное точное совпадение
    REGEX                 // Регулярное выражение (Case-Insensitive)
}
```

### 2.2 Сущность `BlacklistRuleEntity`
```kotlin
package com.lirix.app.storage.blacklist

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blacklist_rules",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["ruleType"])
    ]
)
data class BlacklistRuleEntity(
    @PrimaryKey
    val id: String,
    val ruleType: BlacklistRuleType,
    val pattern: String,
    val matchMode: BlacklistMatchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 2.3 TypeConverters
В `AppDatabase.kt` (или отдельном конвертере):
- `BlacklistRuleType` <-> `String` (`name`)
- `BlacklistMatchMode` <-> `String` (`name`)

---

## 3. Room DAO: `BlacklistDao`

```kotlin
package com.lirix.app.storage.blacklist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {
    @Query("SELECT * FROM blacklist_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<BlacklistRuleEntity>>

    @Query("SELECT * FROM blacklist_rules ORDER BY createdAt DESC")
    suspend fun getAllRules(): List<BlacklistRuleEntity>

    @Query("SELECT * FROM blacklist_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getActiveRules(): List<BlacklistRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlacklistRuleEntity)

    @Update
    suspend fun updateRule(rule: BlacklistRuleEntity)

    @Query("DELETE FROM blacklist_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)

    @Query("SELECT * FROM blacklist_rules WHERE id = :ruleId LIMIT 1")
    suspend fun getRuleById(ruleId: String): BlacklistRuleEntity?

    @Query("SELECT COUNT(*) FROM blacklist_rules")
    suspend fun countRules(): Int
}
```

---

## 4. Миграция Room: `MIGRATION_10_11`

В `AppDatabase.kt`:
1. Увеличить версию базы данных с 10 до 11:
   ```kotlin
   @Database(
       entities = [
           TrackEntity::class,
           ListeningSessionEntity::class,
           LyricsCacheEntity::class,
           AchievementEntity::class,
           RejectedSourceEntity::class,
           CustomLyricsRuleEntity::class,
           BlacklistRuleEntity::class // НОВАЯ СУЩНОСТЬ
       ],
       version = 11,
       exportSchema = false
   )
   ```
2. Определить миграцию:
   ```kotlin
   val MIGRATION_10_11 = object : Migration(10, 11) {
       override fun migrate(database: SupportSQLiteDatabase) {
           database.execSQL(
               """
               CREATE TABLE IF NOT EXISTS `blacklist_rules` (
                   `id` TEXT NOT NULL PRIMARY KEY,
                   `ruleType` TEXT NOT NULL,
                   `pattern` TEXT NOT NULL,
                   `matchMode` TEXT NOT NULL,
                   `isEnabled` INTEGER NOT NULL,
                   `createdAt` INTEGER NOT NULL
               )
               """.trimIndent()
           )
           database.execSQL(
               "CREATE INDEX IF NOT EXISTS `index_blacklist_rules_isEnabled` ON `blacklist_rules` (`isEnabled`)"
           )
           database.execSQL(
               "CREATE INDEX IF NOT EXISTS `index_blacklist_rules_ruleType` ON `blacklist_rules` (`ruleType`)"
           )
       }
   }
   ```
3. Зарегистрировать `MIGRATION_10_11` в `addMigrations` билдера базы данных.
4. Добавить абстрактный метод `abstract fun blacklistDao(): BlacklistDao`.

---

## 5. Движок сопоставления и кэш правил (`BlacklistMatcher`)

Пакет: `com.lirix.app.feature.blacklist`

```kotlin
package com.lirix.app.feature.blacklist

import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType

object BlacklistMatcher {

    fun matches(
        title: String,
        artist: String,
        packageName: String,
        rule: BlacklistRuleEntity
    ): Boolean {
        if (!rule.isEnabled) return false
        val candidate = when (rule.ruleType) {
            BlacklistRuleType.TITLE -> title.trim()
            BlacklistRuleType.ARTIST -> artist.trim()
            BlacklistRuleType.PACKAGE_NAME -> packageName.trim()
        }

        return when (rule.matchMode) {
            BlacklistMatchMode.CONTAINS_IGNORE_CASE -> {
                candidate.contains(rule.pattern.trim(), ignoreCase = true)
            }
            BlacklistMatchMode.EXACT -> {
                candidate.equals(rule.pattern.trim(), ignoreCase = true)
            }
            BlacklistMatchMode.REGEX -> {
                try {
                    val regex = Regex(rule.pattern.trim(), RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(candidate)
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    fun isBlacklisted(
        title: String,
        artist: String,
        packageName: String,
        activeRules: List<BlacklistRuleEntity>
    ): Boolean {
        if (activeRules.isEmpty()) return false
        return activeRules.any { matches(title, artist, packageName, it) }
    }
}
```

---

## 6. Движок каскадной очистки медиатеки (`PurgeEngine`)

Пакет: `com.lirix.app.feature.blacklist`

```kotlin
data class PurgeReport(
    val deletedTracksCount: Int,
    val deletedSessionsCount: Int
)
```

В интерфейс `MusicDao` (или расширение):
```kotlin
@Query("DELETE FROM music_tracks WHERE trackKey IN (:trackKeys)")
suspend fun deleteTracksByKeys(trackKeys: List<String>): Int

@Query("DELETE FROM music_listening_sessions WHERE trackKey IN (:trackKeys)")
suspend fun deleteSessionsByTrackKeys(trackKeys: List<String>): Int
```

Логика `purgeMatchingTracks(rule: BlacklistRuleEntity, db: AppDatabase): PurgeReport`:
1. Получить все треки: `val allTracks = db.musicDao().getAllTracks()`.
2. Отфильтровать совпадения:
   ```kotlin
   val matchingKeys = allTracks
       .filter { track ->
           BlacklistMatcher.matches(
               title = track.title,
               artist = track.artist,
               packageName = track.sourceApp ?: "",
               rule = rule
           )
       }
       .map { it.trackKey }
   ```
3. Если `matchingKeys.isEmpty()`, вернуть `PurgeReport(0, 0)`.
4. Выполнить атомарное удаление пакетами (по 500 ключей, чтобы не превысить лимит SQLite аргументов):
   ```kotlin
   var deletedTracks = 0
   var deletedSessions = 0
   matchingKeys.chunked(500).forEach { chunk ->
       deletedSessions += db.musicDao().deleteSessionsByTrackKeys(chunk)
       deletedTracks += db.musicDao().deleteTracksByKeys(chunk)
   }
   PurgeReport(deletedTracks, deletedSessions)
   ```

---

## 7. Интеграция в `MusicFeatureEngine` (Рантайм-защита)

В `MusicFeatureEngine.kt`:
1. Подписка на правила:
   ```kotlin
   private val activeBlacklistRules = MutableStateFlow<List<BlacklistRuleEntity>>(emptyList())
   
   fun startBlacklistSync(scope: CoroutineScope) {
       scope.launch {
           db.blacklistDao().getAllRulesFlow().collect { allRules ->
               activeBlacklistRules.value = allRules.filter { it.isEnabled }
           }
       }
   }
   ```
2. В методе сохранения трека / сессии (`processMusicEvent` / `recordPlaybackSignal`):
   ```kotlin
   val isBlocked = BlacklistMatcher.isBlacklisted(
       title = currentTitle,
       artist = currentArtist,
       packageName = currentPackage,
       activeRules = activeBlacklistRules.value
   )
   if (isBlocked) {
       Timber.d("MusicFeatureEngine: Track is blacklisted, skipping DB persistence.")
       return // НЕ сохранять в music_tracks, НЕ писать сессию
   }
   ```
3. Для UI (`LivePlaybackSnapshot`): добавить поле `isBlacklisted: Boolean = false`, чтобы плеер мог отобразить бейдж «Заблокировано правилом».

---

## 8. Definition of Ready (DoR) и Критерии Приемки (DoD)
- **DoR**: Спецификация утверждена координатором; контракты типов согласованы.
- **DoD**:
  1. Room схема 11 компилируется, миграция `MIGRATION_10_11` протестирована в unit-тесте с валидацией схемы.
  2. `BlacklistDao` поддерживает полный CRUD и реактивный `Flow`.
  3. `BlacklistMatcher` покрыт тестами на все 3 типа правил и 3 режима сопоставления.
  4. `purgeMatchingTracks` протестирован на удаление связанных треков и сессий без остаточных сирот.
  5. 100% существующих тестов (`testDebugUnitTest`) остаются зелеными.
