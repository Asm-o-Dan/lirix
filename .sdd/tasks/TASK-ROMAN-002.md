# TASK-ROMAN-002: Japanese Kana to Hepburn Romaji Engine

**Спецификация:** `SPEC-ROMAN-01` §2, §3  
**Зона:** lyrics-engine  
**Файл:** `shared/src/commonMain/kotlin/com/lirix/app/feature/lyrics/romanization/JapaneseRomajiEngine.kt`  
**Зависимости:** `TASK-ROMAN-001`

## Что сделать
1. Реализовать чистый Kotlin-конвертер Хираганы и Катаканы в Hepburn Romaji:
   - Полная таблица годзюон (а-ка-са-та-на...), дакуон (га-за-да-ба), хандакуон (па-пи-пу-пе-по).
   - Слитные звуки Ёон (кя, кю, кё, ся, сю, сё, тя, чу, чё...).
   - Маленькая цу (sokuon っ / ッ) — удвоение последующей согласной (matte, zutto).
   - Длинные гласные (ō, ū или oo, ou), частицы (は -> wa, を -> o, へ -> e).
   - Сохранение знаков препинания, пробелов и латинских вставок.
2. Поддержка базовых кандзи через встроенную легковесную таблицу сопоставления частых слов J-Pop (私 -> watashi, 恋 -> koi, 愛 -> ai, 夢 -> yume, 心 -> kokoro, 君 -> kimi, あなた -> anata, etc.).

## DoD
- Unit-тесты:
  - "さくら" $\rightarrow$ "sakura"
  - "ずっと" $\rightarrow$ "zutto"
  - "とうきょう" $\rightarrow$ "toukyou"
  - "Tokyo 東京" $\rightarrow$ корректный ромадзи с сохранением латиницы
- Скорость конвертации: < 5 мс на 100 строк текста.
