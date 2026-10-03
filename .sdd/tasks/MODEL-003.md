## Задача MODEL-003: реализовать функцию EventNormalizer.cleanText

**Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/normalize/EventNormalizer.kt` (создать)  
**Место в файле:** объект `EventNormalizer`  
**Спека:** `.sdd/specs/core-model/overview.md#функция-1-cleantext` (v1)  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
package com.example.npc.core.model.normalize

object EventNormalizer {
    fun cleanText(rawText: String?): String
}
```

**Поведение:**
1. Если `rawText == null` или `rawText.isEmpty()`, вернуть `""`.
2. Удалить все невидимые символы Unicode диапазона `[\u200B-\u200D\uFEFF\u00AD]`.
3. Заменить `\r\n` и одиночные `\r` на `\n`.
4. Разбить на строки по `\n`.
5. В каждой строке заменить последовательности горизонтальных пробельных символов (`\t`, `\u0020`, `\u00A0`, `\u2000`–`\u200A`) на одиночный пробел `" "`.
6. Выполнить `.trim()` для каждой отдельной строки.
7. Склеить обратно через `\n`.
8. Заменить 3 и более подряд идущих `\n` (`\n{3,}`) на ровно `\n\n`.
9. Выполнить финальный `.trim()` всей строки и вернуть результат.

**Ошибки:**
- Не выбрасывает исключений. Чистая функция полного охвата.

**Граничные случаи:**
- `rawText = null` -> `""`
- `rawText = ""` -> `""`
- `rawText = "   \t  \u00A0  \r\n  "` -> `""`
- `rawText = "Строка 1\r\n\r\n\r\n\r\nСтрока 2"` -> `"Строка 1\n\nСтрока 2"`

**Запрещено:**
- Использовать внешние тяжелые NLP-библиотеки.
- Менять поведение переносов абзацев (`\n\n` должно сохраняться).

**Критерий приёмки:**
- Проходят юнит-тесты `core/model/src/test/kotlin/com/example/npc/core/model/normalize/EventNormalizerCleanTextTest.kt`.
