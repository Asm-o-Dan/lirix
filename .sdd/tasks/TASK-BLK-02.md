# Задача TASK-BLK-02: Движок сопоставления правил BlacklistMatcher и каскадная очистка PurgeEngine

- **ID задачи:** `TASK-BLK-02`
- **Роль исполнителя:** Кодер (Core Engine)
- **Зона:** `media-core`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/feature/blacklist/BlacklistMatcher.kt` (создать)
  2. `app/src/main/java/com/lirix/app/feature/blacklist/BlacklistPurgeEngine.kt` (создать)
- **Спека:** [.sdd/specs/media-core/blacklist_storage_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/blacklist_storage_spec.md)
- **Зависит от:** `TASK-BLK-01`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Реализовать независимую логику сопоставления метаданных треков с правилами черного списка (`BlacklistMatcher`) и механизм немедленной каскадной очистки медиатеки (`BlacklistPurgeEngine`), включающий расчет превью-количества удаляемых треков.

---

## 2. Реализация `BlacklistMatcher.kt` (пакет `feature/blacklist`)

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
        val pattern = rule.pattern.trim()
        if (pattern.isEmpty()) return false

        val candidate = when (rule.ruleType) {
            BlacklistRuleType.TITLE -> title.trim()
            BlacklistRuleType.ARTIST -> artist.trim()
            BlacklistRuleType.PACKAGE_NAME -> packageName.trim()
        }

        return when (rule.matchMode) {
            BlacklistMatchMode.CONTAINS_IGNORE_CASE -> {
                candidate.contains(pattern, ignoreCase = true)
            }
            BlacklistMatchMode.EXACT -> {
                candidate.equals(pattern, ignoreCase = true)
            }
            BlacklistMatchMode.REGEX -> {
                try {
                    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
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

## 3. Реализация `BlacklistPurgeEngine.kt` (пакет `feature/blacklist`)

```kotlin
package com.lirix.app.feature.blacklist

import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PurgeReport(
    val deletedTracksCount: Int,
    val deletedSessionsCount: Int
)

class BlacklistPurgeEngine(private val db: AppDatabase) {

    /**
     * Возвращает список ключей треков, попадающих под указанное правило.
     */
    suspend fun findMatchingTrackKeys(rule: BlacklistRuleEntity): List<String> = withContext(Dispatchers.IO) {
        val allTracks = db.musicDao().getAllTracks()
        allTracks.filter { track ->
            BlacklistMatcher.matches(
                title = track.title,
                artist = track.artist,
                packageName = track.sourcePackage,
                rule = rule
            )
        }.map { it.trackKey }
    }

    /**
     * Предварительный подсчет количества удаляемых треков (для превью в UI конструкторе).
     */
    suspend fun previewPurgeCount(rule: BlacklistRuleEntity): Int = withContext(Dispatchers.IO) {
        findMatchingTrackKeys(rule).size
    }

    /**
     * Атомарная каскадная очистка медиатеки от треков и сессий, подпадающих под правило.
     */
    suspend fun purgeMatchingTracks(rule: BlacklistRuleEntity): PurgeReport = withContext(Dispatchers.IO) {
        val matchingKeys = findMatchingTrackKeys(rule)
        if (matchingKeys.isEmpty()) {
            return@withContext PurgeReport(0, 0)
        }

        var deletedTracks = 0
        var deletedSessions = 0

        // Удаление порциями по 500 элементов во избежание переполнения аргументов SQLite
        matchingKeys.chunked(500).forEach { chunk ->
            deletedSessions += db.musicDao().deleteSessionsByTrackKeys(chunk)
            deletedTracks += db.musicDao().deleteTracksByKeys(chunk)
        }

        PurgeReport(
            deletedTracksCount = deletedTracks,
            deletedSessionsCount = deletedSessions
        )
    }
}
```

---

## 4. Критерии приемки (DoD)
- Классы скомпилированы и соответствуют межзонному контракту `core__blacklist.md`.
- `matches()` корректно обрабатывает пустые строки и невалидные регулярные выражения без исключений.
- Пакетное удаление `chunked(500)` не вызывает `SQLiteException`.
