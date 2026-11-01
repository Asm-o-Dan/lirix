# TASK-ROMAN-001: Data models and Romanizer contracts

**Спецификация:** `SPEC-ROMAN-01` §2  
**Зона:** lyrics-engine  
**Файл:** `shared/src/commonMain/kotlin/com/lirix/app/feature/lyrics/romanization/RomanizationModels.kt`  
**Зависимости:** нет

## Что сделать
1. Создать пакет `com.lirix.app.feature.lyrics.romanization` в `:shared` (commonMain).
2. Определить `enum class RomanizationLanguage { JAPANESE, CHINESE, AUTO, NONE }`.
3. Определить `enum class RomanizationDisplayMode { ORIGINAL_ONLY, READING_ABOVE, ROMANIZED_ONLY }`.
4. Определить `data class RomanizedLyricLine(val originalText: String, val romanizedText: String, val timestampMs: Long)`.
5. Определить интерфейс `LyricsRomanizer`.

## DoD
- Компилируется в `:shared:compileKotlinDesktop` и `:shared:compileKotlinAndroid`.
- Unit-тест с проверкой дефолтных значений контрактов.
