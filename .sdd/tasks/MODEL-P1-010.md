## Задача MODEL-P1-010: Реализовать модель ClassificationResult

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/classify/ClassificationResult.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#41-engine-classificationresult`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.classify

data class ClassificationResult(
    val category: Category,
    val confidence: Double,
    val engine: Engine,
    val contentFingerprint: String
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in 0.0..1.0 (got $confidence)" }
        require(contentFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "contentFingerprint must be 64-char hex SHA-256 (got '$contentFingerprint')"
        }
    }

    val typedConfidence: Confidence get() = Confidence(confidence)

    companion object {
        fun unclassified(fingerprint: String): ClassificationResult
        fun fromPrototype(prototype: UserPrototype): ClassificationResult
    }
}
```

### Инварианты и алгоритм:
1. `confidence` строго в `0.0..1.0`.
2. `contentFingerprint` валидируется регулярным выражением 64-char lowercase hex SHA-256.
3. Фабрика `unclassified`: категория `UNCLASSIFIED`, `confidence = 0.0`, `engine = Engine.NONE`.
4. Фабрика `fromPrototype`: категория `prototype.category`, `confidence = 1.0`, `engine = Engine.PROTOTYPE`.

### Критерии приемки (DoD):
- [ ] 100% покрытие фабричных методов и валидации.
