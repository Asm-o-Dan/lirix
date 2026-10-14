# Задача TASK-BLK-QA: TDD Unit-тесты для миграции Room v11, BlacklistMatcher и PurgeEngine

- **ID задачи:** `TASK-BLK-QA`
- **Роль исполнителя:** QA Engineer
- **Зона:** `qa` & `test`
- **Файлы:**
  1. `app/src/test/java/com/lirix/app/BlacklistEngineTest.kt` (создать)
  2. `app/src/test/java/com/lirix/app/MusicDatabaseTest.kt` (добавить тесты миграции 10->11 и BlacklistDao)
- **Спека:** [.sdd/specs/media-core/blacklist_storage_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/blacklist_storage_spec.md)
- **Зависит от:** `TASK-BLK-01`, `TASK-BLK-02`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Разработать исчерпывающий набор unit-тестов, подтверждающих корректность работы механизма черного списка:
1. Тесты `BlacklistMatcher`:
   - `TITLE` совпадения (`CONTAINS_IGNORE_CASE`, `EXACT`, `REGEX`).
   - `ARTIST` совпадения.
   - `PACKAGE_NAME` совпадения (Telegram, Spotify, etc.).
   - Игнорирование выключенных правил (`isEnabled = false`).
   - Устойчивость к некорректным регулярным выражениям (`[a-z`).
2. Тесты `BlacklistPurgeEngine`:
   - Предварительный расчет `previewPurgeCount`.
   - Фактическое каскадное удаление треков и связанных сессий.
   - Неприкосновенность треков, не подпадающих под правило.
3. Тесты `BlacklistDao`:
   - Вставка, обновление, удаление, выборка активных правил.

---

## 2. Тестовые сценарии (`BlacklistEngineTest.kt`)

```kotlin
package com.lirix.app

import com.lirix.app.feature.blacklist.BlacklistMatcher
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlacklistEngineTest {

    @Test
    fun testTitleContainsIgnoreCase() {
        val rule = BlacklistRuleEntity(
            id = "1",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "реклама",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )

        assertTrue(BlacklistMatcher.matches("Встроенная РЕКЛАМА трека", "Артист", "com.music", rule))
        assertFalse(BlacklistMatcher.matches("Обычный трек", "Артист", "com.music", rule))
    }

    @Test
    fun testArtistExactMatch() {
        val rule = BlacklistRuleEntity(
            id = "2",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "Моргенштерн",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = true
        )

        assertTrue(BlacklistMatcher.matches("Cadillac", "моргенштерн", "com.music", rule))
        assertFalse(BlacklistMatcher.matches("Cadillac", "Моргенштерн & Элджей", "com.music", rule))
    }

    @Test
    fun testPackageNameMatch() {
        val rule = BlacklistRuleEntity(
            id = "3",
            ruleType = BlacklistRuleType.PACKAGE_NAME,
            pattern = "org.telegram.messenger",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = true
        )

        assertTrue(BlacklistMatcher.matches("Voice Note", "Unknown", "org.telegram.messenger", rule))
        assertFalse(BlacklistMatcher.matches("Song", "Singer", "com.spotify.music", rule))
    }

    @Test
    fun testDisabledRuleDoesNotMatch() {
        val rule = BlacklistRuleEntity(
            id = "4",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "Test",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = false
        )

        assertFalse(BlacklistMatcher.matches("Test Song", "Artist", "pkg", rule))
    }

    @Test
    fun testInvalidRegexDoesNotThrow() {
        val rule = BlacklistRuleEntity(
            id = "5",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "[invalid(regex",
            matchMode = BlacklistMatchMode.REGEX,
            isEnabled = true
        )

        // Должно вернуть false и не упасть с PatternSyntaxException
        assertFalse(BlacklistMatcher.matches("Any Title", "Artist", "pkg", rule))
    }
}
```

---

## 3. Критерии приемки (DoD)
- Все тесты проходят успешно (`./gradlew testDebugUnitTest`).
- 100% покрытие граничных условий.
