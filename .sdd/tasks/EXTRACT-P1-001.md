## Задача EXTRACT-P1-001: Реализовать нормализатор RegionalTextSanitizer

**Модуль:** `:extract:finance`  
**Целевой файл:** `extract/finance/src/main/kotlin/com/example/npc/extract/finance/RegionalTextSanitizer.kt`  
**Спецификация:** `.sdd/specs/extract-finance/overview.md#41-региональная-санитизация-текста-regionaltextsanitizer`  
**Архитектура:** `.sdd/architecture_phase1.md#34`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.extract.finance

object RegionalTextSanitizer {
    /**
     * Выполняет ReDoS-безопасную предобработку банковского текста:
     * - Замена неразрывных пробелов NBSP (\u00A0) и Narrow NBSP (\u202F) на стандартные пробелы.
     * - Нормализация румынских диакритик (ș, ț, ă, î, â).
     * - Жесткое усечение длины до 1024 символов (ReDoS guard).
     */
    fun sanitize(text: String): String
}
```

### Инварианты и алгоритм:
1. Если длина строки превышает 1024 символа, она безопасно обрезается: `take(1024)`.
2. Замена пробелов: `\u00A0`, `\u202F`, `\u2007`, `\uFEFF` $\to$ стандартный пробел `' '`.
3. Нормализация диакритических знаков Unicode (NFC / NFD соответствие для румынского языка).
4. Линейная обработка за один проход $O(n)$ без бэктрекинга.

### Критерии приемки (DoD):
- [ ] Стресс-тест строкой 5000+ символов обрезает до 1024 символов за < 1 мс.
- [ ] NBSP заменяются на обычные пробелы для корректной работы AmountParser.
- [ ] 100% покрытие unit-тестами.
