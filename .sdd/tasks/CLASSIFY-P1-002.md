## Задача CLASSIFY-P1-002: Реализовать генератор отпечатков контента Fingerprinter

**Модуль:** `:classify:rules`  
**Целевой файл:** `classify/rules/src/main/kotlin/com/example/npc/classify/rules/Fingerprinter.kt`  
**Спецификация:** `.sdd/specs/classify-rules/overview.md#4-генерация-отпечатков-контента-fingerprinter`  
**Архитектура:** `.sdd/architecture_phase1.md#33`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.classify.rules

object Fingerprinter {
    /**
     * Создает шаблон текста путем замены динамических данных на плейсхолдеры:
     * - Даты/время -> <DATE>
     * - Суммы/числа -> <NUM>
     * - Коды авторизации -> <CODE>
     */
    fun createTemplate(text: String, title: String?): String

    /**
     * Вычисляет 64-символьный SHA-256 хэш шаблона текста.
     */
    fun computeFingerprint(packageName: String, text: String, title: String?): String
}
```

### Инварианты и алгоритм:
1. Защита ReDoS: текст усекается до 1024 символов до обработки.
2. Линейная нормализация $O(n)$:
   - Приведение к нижнему регистру.
   - Замена последовательностей цифр (`\d+`) на токен `<NUM>`.
   - Замена дат и временных меток на `<DATE>`.
   - Схлопывание повторяющихся пробелов и знаков препинания.
3. Хэширование: конкатенация `packageName + "|" + template` $\to$ SHA-256 $\to$ 64-символьная hex-строка в нижнем регистре.

### Критерии приемки (DoD):
- [ ] Детерминированность: для двух сообщений с одинаковым текстом, но разными суммами/датами, получается идентичный fingerprint.
- [ ] Время выполнения < 1 мс на строках до 1024 символов.
- [ ] 100% покрытие unit-тестами.
