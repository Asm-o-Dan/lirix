## Задача EXTRACT-P1-007: Реализовать MaibNotificationExtractor (BC MAIB Молдова)

**Модуль:** `:extract:finance`  
**Целевой файл:** `extract/finance/src/main/kotlin/com/example/npc/extract/finance/maib/MaibNotificationExtractor.kt`  
**Спецификация:** `.sdd/specs/extract-finance/overview.md#53-maibnotificationextractor-bc-maib-молдова`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.extract.finance.maib

import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.extract.ParsedFinanceResult

class MaibNotificationExtractor : FinanceExtractor {
    override val id: String = "maib.push"
    override val version: Int = 1
    override val supportedBank: String = "MAIB"

    override fun extract(input: ExtractorInput): ParsedFinanceResult
}
```

### Инварианты и алгоритм:
1. Мультивалютная поддержка: распознает `MDL`, `EUR`, `USD`.
2. **Критическая обработка отказов (Declined Transactions):**
   - Румынский маркер: `"Tranzactie respinsa"` / `"Refuzata"`.
   - Русский маркер: `"Операция отклонена"` / `"Отказ"`.
   - При обнаружении маркера формирует `ParsedFinanceResult.Declined(reason = ..., type = DEBIT, amount = ..., merchant = ...)` со статусом `TransactionStatus.DECLINED`.
   - Отклонённые операции **не списывают баланс** в аналитике!
3. Успешные списания: `"Plata: (\d+[\.,]\d{2})\s*(MDL|EUR|USD)"` или `"Оплата: ..."`.

### Критерии приемки (DoD):
- [ ] Ошибочные/отклонённые операции MAIB четко классифицируются как `Declined`.
- [ ] 100% покрытие unit-тестами на румынском и русском языках.
