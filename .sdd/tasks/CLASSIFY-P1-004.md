## Задача CLASSIFY-P1-004: Реализовать эвристический классификатор RuleBasedCategoryClassifier

**Модуль:** `:classify:rules`  
**Целевой файл:** `classify/rules/src/main/kotlin/com/example/npc/classify/rules/RuleBasedCategoryClassifier.kt`  
**Спецификация:** `.sdd/specs/classify-rules/overview.md#6-эвристический-классификатор-правил-rulebasedcategoryclassifier`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.classify.rules

import com.example.npc.core.model.Event
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.PackageGatedRouter

class RuleBasedCategoryClassifier(
    private val router: PackageGatedRouter
) {
    fun classifyByRules(
        event: Event,
        packageName: String,
        fingerprint: String
    ): ClassificationResult
}
```

### Инварианты и алгоритм:
1. Если `router.canClassifyAsFinance(packageName, event.title, event.rawId... )`:
   - Анализ текста на финансовые токены («оплата», «списание», «покупка», «зачисление», «перевод», «баланс»).
   - При совпадении $\to$ `Category.FINANCE` (`confidence = 0.95`, `Engine.RULES`).
2. Если `packageName in MESSENGER_PACKAGES` $\to$ `Category.COMMUNICATION` (`confidence = 0.90`, `Engine.RULES`).
3. Если `event.source == SourceId.MEDIA` или плеер $\to$ `Category.MUSIC` (`confidence = 0.95`, `Engine.RULES`).
4. Если доставка/сервисы (Ozon, Wildberries, такси) $\to$ `Category.SERVICES` (`confidence = 0.85`, `Engine.RULES`).
5. Рекламные маркеры (скидки, распродажи, акции без транзакций) $\to$ `Category.ADVERTISEMENT` (`confidence = 0.80`, `Engine.RULES`).
6. Fallback: `Category.UNCLASSIFIED` (`confidence = 0.0`, `Engine.NONE`).

### Критерии приемки (DoD):
- [ ] Финансовые пуши от банков получают `Category.FINANCE`.
- [ ] Пуши из Telegram/AyuGram никогда не получают `FINANCE`.
- [ ] 100% покрытие unit-тестами.
