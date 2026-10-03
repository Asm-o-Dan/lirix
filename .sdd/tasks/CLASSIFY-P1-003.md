## Задача CLASSIFY-P1-003: Реализовать стадию классификации по прототипам PrototypeStage

**Модуль:** `:classify:rules`  
**Целевой файл:** `classify/rules/src/main/kotlin/com/example/npc/classify/rules/PrototypeStage.kt`  
**Спецификация:** `.sdd/specs/classify-rules/overview.md#5-приоритетный-резолвер-прототипов-prototypestage`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.classify.rules

import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.UserPrototype

object PrototypeStage {
    /**
     * Проверяет пользовательский прототип на соответствие порогу уверенности.
     * @return ClassificationResult при supportCount >= 2, иначе null.
     */
    fun match(prototype: UserPrototype?): ClassificationResult?
}
```

### Инварианты и алгоритм:
1. Если `prototype == null` $\to$ возвращает `null`.
2. Если `prototype.supportCount >= UserPrototype.CONFIDENCE_THRESHOLD` (порог = 2):
   - Возвращает `ClassificationResult(category = prototype.category, confidence = 1.0, engine = Engine.PROTOTYPE, contentFingerprint = prototype.fingerprint)`.
3. Если `prototype.supportCount < 2` $\to$ возвращает `null` (недостаточно подтверждений пользователя для безусловного переопределения).

### Критерии приемки (DoD):
- [ ] При `supportCount >= 2` выдается `confidence = 1.0` и `engine = Engine.PROTOTYPE`.
- [ ] При `supportCount == 1` возвращается `null` для продолжения в эвристический классификатор.
- [ ] 100% покрытие unit-тестами.
