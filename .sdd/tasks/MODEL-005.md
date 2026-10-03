## Задача MODEL-005: реализовать функцию EventNormalizer.computeDeduplicationKey

**Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/normalize/EventNormalizer.kt` (изменить)  
**Место в файле:** объект `EventNormalizer`  
**Спека:** `.sdd/specs/core-model/overview.md#функция-3-computededuplicationkey` (v1)  
**Зависит от:** `MODEL-001` (`SourceId`, `DeduplicationKey`)  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
fun computeDeduplicationKey(
    source: SourceId,
    packageName: String,
    payloadJson: String
): DeduplicationKey
```

**Поведение:**
1. Валидация аргументов:
   - Если `source.value.isBlank()` -> `throw IllegalArgumentException("source value must not be blank")`
   - Если `packageName.isBlank()` -> `throw IllegalArgumentException("packageName must not be blank")`
   - Если `payloadJson.isBlank()` -> `throw IllegalArgumentException("payloadJson must not be blank")`
2. Распарсить `payloadJson` как JSON Object (проверить, что начинается на `{` и заканчивается на `}`).
3. Канонизация JSON:
   - Исключить поля `"postTime"` и `"when"`.
   - Отсортировать ключи в лексикографическом порядке по ASCII.
   - Сформировать канонический компактный JSON без пробелов.
4. Вычислить SHA-256 от канонического JSON в UTF-8 -> `payloadHashHex` (64 символа hex в нижнем регистре).
5. Сформировать композитную строку: `"${source.value}|${packageName}|${payloadHashHex}"`.
6. Вычислить SHA-256 от композитной строки в UTF-8 -> 64-символьный hex в нижнем регистре.
7. Вернуть `DeduplicationKey(finalHashHex)`.

**Ошибки:**
- Невалидный JSON или пустые аргументы -> `IllegalArgumentException`.

**Граничные случаи:**
- Разный порядок ключей в JSON -> строго одинаковый ключ.
- Разные значения `postTime` при одинаковом содержимом -> строго одинаковый ключ.
- JSON содержит только исключаемые поля -> канонический `"{}"`, корректный хеш без ошибок.

**Критерий приёмки:**
- Проходят юнит-тесты `core/model/src/test/kotlin/com/example/npc/core/model/normalize/EventNormalizerDeduplicationKeyTest.kt`.
