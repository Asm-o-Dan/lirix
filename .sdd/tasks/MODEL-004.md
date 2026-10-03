## Задача MODEL-004: реализовать функцию EventNormalizer.detectLang

**Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/normalize/EventNormalizer.kt` (изменить)  
**Место в файле:** объект `EventNormalizer`  
**Спека:** `.sdd/specs/core-model/overview.md#функция-2-detectlang` (v1)  
**Зависит от:** `MODEL-001` (enum `Lang`)  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
fun detectLang(text: String): Lang
```

**Поведение:**
1. Инициализировать счётчики: `cyrillicCount = 0`, `latinCount = 0`.
2. Проитерировать по символам строки `text`:
   - Если `c in '\u0400'..'\u04FF'` -> `cyrillicCount++`
   - Иначе если `c in 'a'..'z' || c in 'A'..'Z'` -> `latinCount++`
3. Суммарно букв: `totalLetters = cyrillicCount + latinCount`.
4. Если `totalLetters < 3` -> вернуть `Lang.UNK`.
5. Доля кириллицы: `cyrillicRatio = cyrillicCount.toDouble() / totalLetters.toDouble()`.
6. Доля латиницы: `latinRatio = latinCount.toDouble() / totalLetters.toDouble()`.
7. Если `cyrillicRatio >= 0.70` -> вернуть `Lang.RU`.
8. Если `latinRatio >= 0.70` -> вернуть `Lang.EN`.
9. Иначе -> вернуть `Lang.UNK`.

**Ошибки:**
- Не выбрасывает исключений.

**Граничные случаи:**
- `text = ""` -> `Lang.UNK`
- `text = "12345 !? #$%"` -> `Lang.UNK`
- `text = "Hi"` (< 3 букв) -> `Lang.UNK`
- `text = "Yes"` -> `Lang.EN`
- `text = "Код"` -> `Lang.RU`
- Смешанный `"Код: 1234 ABCD"` -> `Lang.UNK`

**Критерий приёмки:**
- Проходят юнит-тесты `core/model/src/test/kotlin/com/example/npc/core/model/normalize/EventNormalizerDetectLangTest.kt`.
