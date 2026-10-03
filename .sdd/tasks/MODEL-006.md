## Задача MODEL-006: реализовать фабричную функцию EventNormalizer.normalize

**Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/normalize/EventNormalizer.kt` (изменить)  
**Место в файле:** объект `EventNormalizer`  
**Спека:** `.sdd/specs/core-model/overview.md#функция-4-normalize` (v1)  
**Зависит от:** `MODEL-002` (`RawEvent`, `Event`), `MODEL-003` (`cleanText`), `MODEL-004` (`detectLang`)  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
fun normalize(
    rawEvent: RawEvent,
    title: String,
    text: String,
    threadKey: ThreadKey? = null,
    isUpdateOf: Long? = null
): Event
```

**Поведение:**
1. Вызвать `cleanText(text)` для получения `normalizedText`.
2. Вызвать `detectLang(normalizedText)` для вычисления `lang: Lang`.
3. Создать и вернуть экземпляр `Event` с полями:
   - `id = 0L`
   - `rawId = rawEvent.id`
   - `ts = rawEvent.receivedAt`
   - `title = title`
   - `text = text`
   - `normalizedText = normalizedText`
   - `lang = lang`
   - `threadKey = threadKey`
   - `isUpdateOf = isUpdateOf`

**Ошибки:**
- Не выбрасывает исключений при валидных входных аргументах.

**Критерий приёмки:**
- Проходят юнит-тесты `core/model/src/test/kotlin/com/example/npc/core/model/normalize/EventNormalizerNormalizeTest.kt`.
