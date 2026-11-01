# TASK-ROMAN-004: Romanization Coordinator and Language Detection

**Спецификация:** `SPEC-ROMAN-01` §2, §3  
**Зона:** lyrics-engine  
**Файл:** `shared/src/commonMain/kotlin/com/lirix/app/feature/lyrics/romanization/RomanizationCoordinator.kt`  
**Зависимости:** `TASK-ROMAN-001`, `TASK-ROMAN-002`, `TASK-ROMAN-003`

## Что сделать
1. Реализовать `LyricsRomanizer` в `RomanizationCoordinator`:
   - Автодетект языка:
     - Если содержит символы Хираганы (`\u3040-\u309F`) или Катаканы (`\u30A0-\u30FF`) $\rightarrow$ `JAPANESE`.
     - Если содержит CJK иероглифы (`\u4E00-\u9FFF`), но нет Каны $\rightarrow$ `CHINESE`.
     - Иначе `NONE`.
   - Кэширование результатов построчной романизации в памяти (`LruCache` / `ConcurrentHashMap`).
   - Метод `romanizeLrc` для синхронизированных строк караоке с сохранением исходных таймкодов.

## DoD
- Unit-тесты:
  - Автодетект японского текста с каной $\rightarrow$ JAPANESE.
  - Автодетект китайского текста $\rightarrow$ CHINESE.
  - Игнорирование чисто английских/русских текстов $\rightarrow$ возвращается оригинальный текст без искажений.
  - Повторный вызов берет результат из кэша без пересчёта.
