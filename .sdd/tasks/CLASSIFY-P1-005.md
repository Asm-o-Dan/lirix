## Задача CLASSIFY-P1-005: Реализовать фасад SemanticClassifierImpl

**Модуль:** `:classify:rules`  
**Целевой файл:** `classify/rules/src/main/kotlin/com/example/npc/classify/rules/SemanticClassifierImpl.kt`  
**Спецификация:** `.sdd/specs/classify-rules/overview.md#7-координатор-семантической-классификации-semanticclassifierimpl`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.classify.rules

import com.example.npc.core.model.Event
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.PackageGatedRouter
import com.example.npc.core.model.classify.SemanticClassifier
import com.example.npc.core.model.classify.UserPrototype

class SemanticClassifierImpl(
    private val router: PackageGatedRouter,
    private val ruleClassifier: RuleBasedCategoryClassifier
) : SemanticClassifier {
    override fun classify(event: Event, prototype: UserPrototype?): ClassificationResult
}
```

### Инварианты и алгоритм:
1. Вычисление отпечатка контента через `Fingerprinter.computeFingerprint(packageName, event.text, event.title)`.
2. Фаза 1 — Проверка прототипа:
   - Вызов `PrototypeStage.match(prototype)`.
   - Если вернулся не null результат $\to$ немедленный возврат (Prototype-First Resolution).
3. Фаза 2 — Эвристические правила:
   - Вызов `ruleClassifier.classifyByRules(event, packageName, fingerprint)`.
   - Возврат полученного `ClassificationResult`.

### Критерии приемки (DoD):
- [ ] При наличии подтвержденного прототипа эвристический классификатор не вызывается.
- [ ] Результат классификации содержит корректный 64-символьный fingerprint.
- [ ] 100% покрытие unit-тестами взаимодействия всех компонентов классификации.
