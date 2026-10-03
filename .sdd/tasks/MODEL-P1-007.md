## Задача MODEL-P1-007: Реализовать value class Confidence

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/classify/Confidence.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#41-engine-classificationresult`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.classify

@JvmInline
value class Confidence(val value: Double) : Comparable<Confidence> {
    init {
        require(value in 0.0..1.0) { "Confidence value must be in range [0.0, 1.0], but was: $value" }
    }

    override fun compareTo(other: Confidence): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Confidence(0.0)
        val MAXIMUM = Confidence(1.0)
        val HIGH_THRESHOLD = Confidence(0.85)
        val PROTOTYPE_CONFIDENCE = Confidence(1.0)
    }
}
```

### Инварианты и алгоритм:
1. Валидация в init: значение строго внутри `[0.0, 1.0]`. Значения `< 0.0` или `> 1.0` выбрасывают `IllegalArgumentException`.
2. Реализует `Comparable<Confidence>` для удобства фильтрации и сравнения с порогами.
3. `@JvmInline` гарантирует нулевой runtime overhead.

### Критерии приемки (DoD):
- [ ] Unit-тесты проверяют граничные значения `0.0`, `1.0`, валидные дроби `0.5`, и отказ на `-0.01` и `1.01`.
