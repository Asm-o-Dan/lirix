## Задача MODEL-P1-001: Реализовать перечисление Category

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/classify/Category.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#31-category`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.classify

enum class Category {
    FINANCE,
    COMMUNICATION,
    MUSIC,
    SERVICES,
    ADVERTISEMENT,
    OTHER,
    UNCLASSIFIED;

    companion object {
        fun fromStringOrUnclassified(raw: String?): Category
        val UNKNOWN: Category get() = UNCLASSIFIED
    }
}
```

### Инварианты и алгоритм:
1. Регистронезависимый парсинг через `fromStringOrUnclassified`:
   - Если `raw` равен `null` или пуст/состоит из пробелов, возвращается `Category.UNCLASSIFIED`.
   - Если переданная строка совпадает с именем любого элемента enum (без учета регистра), возвращается этот элемент.
   - В противном случае возвращается `Category.UNCLASSIFIED`.
2. Псевдоним `UNKNOWN` указывает на `UNCLASSIFIED` для обратной совместимости со спецификациями.

### Критерии приемки (DoD):
- [ ] Файл скомпилирован в чистом Kotlin JVM (`kotlin-jvm`).
- [ ] 100% покрытие unit-тестами парсинга всех элементов, пустых строк, мусорных строк и регистра.
