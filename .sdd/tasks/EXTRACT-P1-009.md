## Задача EXTRACT-P1-009: Реализовать IsolatedExtractorRunner

**Модуль:** `:extract:finance`  
**Целевой файл:** `extract/finance/src/main/kotlin/com/example/npc/extract/finance/IsolatedExtractorRunner.kt`  
**Спецификация:** `.sdd/specs/extract-finance/overview.md#44-песочница-исполнения-isolatedextractorrunner`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.extract.finance

import com.example.npc.core.model.Event
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.finance.FinancialTransaction

class IsolatedExtractorRunner(
    private val extractors: List<FinanceExtractor>,
    private val circuitBreaker: CircuitBreaker
) {
    fun runExtraction(event: Event, packageName: String): FinancialTransaction?
}
```

### Инварианты и алгоритм:
1. Предварительная санитизация текста события через `RegionalTextSanitizer.sanitize(event.text)`.
2. Выбор подходящего экстрактора по `packageName` или отправителю.
3. Оборачивание вызова в `circuitBreaker.execute(extractor.id) { ... }`.
4. Если `circuitBreaker` заблокирован или экстрактор вернул `NotApplicable`/`Failed` $\to$ возвращает `null`.
5. При успешном исходе (`Success` или `Declined`) собирает доменный DTO `FinancialTransaction` с `eventId = event.id`, `occurredAt = event.ts`, `rawText = event.text`, `extractorId = extractor.id`, `extractorVersion = extractor.version`.
6. Сбой одного экстрактора полностью изолирован и не влияет на остальной конвейер.

### Критерии приемки (DoD):
- [ ] Экстракция выполняется с защитой от ReDoS и за лимит времени < 50 мс.
- [ ] 100% покрытие unit-тестами.
