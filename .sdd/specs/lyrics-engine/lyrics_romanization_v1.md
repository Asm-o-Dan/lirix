# SPEC-ROMAN-01: Japanese / Chinese Lyrics Romanization Engine

**Версия:** 1.0  
**Статус:** APPROVED  
**Зона:** lyrics-engine / media-ui  
**Целевые модули:** `:shared` (commonMain), `:app` (Compose UI), `:desktop` (Teleprompter UI)

---

## 1. Контекст и бизнес-требования
Пользователи часто слушают японские (J-Pop, аниме-опенинги, Vocaloid) и китайские (C-Pop) треки, но не могут подпевать в караоке из-за сложности чтения иероглифов (Кандзи и Ханьцзы).
Требуется:
1. Независимый опциональный слой чтения (Romanization / Reading layer):
   - **Original** (только оригинальный текст)
   - **Ruby / Above** (романизация над строкой)
   - **Romaji / Pinyin Only** (только латинская транслитерация)
2. Чистая архитектура без изменения исходного текста и таймкодов караоке:
   - Исходный LRC и студийные таймкоды остаются нетронутыми.
   - Романизация рассчитывается построчно и кэшируется в памяти/хранилище.
3. Поддержка:
   - Японский: Хирагана/Катакана $\rightarrow$ Hepburn Romaji (с сохранением знаков препинания и английских вставок) + словарь базовых кандзи.
   - Китайский: Ханьцзы $\rightarrow$ Mandarin Pinyin с тональными диакритическими знаками (ā, á, ǎ, à) и без тонов.

---

## 2. Архитектурные контракты (`:shared`)

```kotlin
package com.lirix.app.feature.lyrics.romanization

enum class RomanizationLanguage {
    JAPANESE,
    CHINESE,
    AUTO
}

enum class RomanizationDisplayMode {
    ORIGINAL_ONLY,
    READING_ABOVE, // Ruby annotation
    ROMANIZED_ONLY
}

data class RomanizedLyricLine(
    val originalText: String,
    val romanizedText: String,
    val timestampMs: Long
)

interface LyricsRomanizer {
    fun detectLanguage(text: String): RomanizationLanguage
    fun romanizeLine(text: String, lang: RomanizationLanguage = RomanizationLanguage.AUTO): String
    fun romanizeLrc(lines: List<Pair<Long, String>>, lang: RomanizationLanguage = RomanizationLanguage.AUTO): List<RomanizedLyricLine>
}
```

---

## 3. Декомпозиция задач (`.sdd/tasks/`)

| ID Задачи | Название | Файл реализации | Модуль |
|---|---|---|---|
| `TASK-ROMAN-001` | Data models and Romanizer contracts | `shared/.../lyrics/romanization/RomanizationModels.kt` | `:shared` |
| `TASK-ROMAN-002` | Japanese Kana $\rightarrow$ Hepburn Romaji engine | `shared/.../lyrics/romanization/JapaneseRomajiEngine.kt` | `:shared` |
| `TASK-ROMAN-003` | Chinese Hanzi $\rightarrow$ Pinyin engine | `shared/.../lyrics/romanization/ChinesePinyinEngine.kt` | `:shared` |
| `TASK-ROMAN-004` | RomanizationCoordinator with cache & auto-detect | `shared/.../lyrics/romanization/RomanizationCoordinator.kt` | `:shared` |
| `TASK-ROMAN-005` | UI integration (Compose Karaoke Ruby layer) | `app/.../ui/components/RomanizedKaraokeLine.kt` | `:app` |
