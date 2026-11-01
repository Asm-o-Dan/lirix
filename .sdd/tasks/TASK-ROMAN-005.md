# TASK-ROMAN-005: Romanized Lyrics UI Display

**Спецификация:** `SPEC-ROMAN-01` §2  
**Зона:** media-ui  
**Файл:** `app/src/main/java/com/lirix/app/ui/components/RomanizedKaraokeLine.kt`  
**Зависимости:** `TASK-ROMAN-001`, `TASK-ROMAN-004`

## Что сделать
1. Создать Composable `RomanizedKaraokeLine`:
   - При `RomanizationDisplayMode.ORIGINAL_ONLY`: обычная строка караоке.
   - При `RomanizationDisplayMode.READING_ABOVE`: основной текст крупно, над ним руби-аннотация (роммадзи/пиньинь) мелким шрифтом (11-12sp, полупрозрачный акцентный цвет).
   - При `RomanizationDisplayMode.ROMANIZED_ONLY`: отображение только латинской транскрипции.
2. Добавить кнопку переключения режима чтения в меню ⋮ (Overflow) экрана `NowPlayingScreen`.

## DoD
- UI компилируется в `:app`.
- Руби-аннотация не ломает автоскролл и тайминги караоке.
