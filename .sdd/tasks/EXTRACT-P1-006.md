## Задача EXTRACT-P1-006: Реализовать PrisbankNotificationExtractor (Приднестровский Сбербанк)

**Модуль:** `:extract:finance`  
**Целевой файл:** `extract/finance/src/main/kotlin/com/example/npc/extract/finance/prisbank/PrisbankNotificationExtractor.kt`  
**Спецификация:** `.sdd/specs/extract-finance/overview.md#52-prisbanknotificationextractor-приднестровский-сбербанк`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.extract.finance.prisbank

import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.extract.ParsedFinanceResult

class PrisbankNotificationExtractor : FinanceExtractor {
    override val id: String = "prisbank.push"
    override val version: Int = 1
    override val supportedBank: String = "PRISBANK"

    override fun extract(input: ExtractorInput): ParsedFinanceResult
}
```

### Инварианты и алгоритм:
1. RE2/J шаблоны для пушей Приднестровского Сбербанка (`com.prisbank.app`).
2. Распознавание микроплатежей за общественный транспорт:
   - Шаблон: `"Списание (\d+[\.,]\d{2})\s*(?:руб|р\.|RUP).*?(?:Оплата проезда|Транспорт).*?Остаток:\s*(\d+[\.,]\d{2})"` $\to$ сумма 4.40 RUP, мерчант "Оплата проезда", `type = DEBIT`.
3. Парсинг маски карты: `"\*(\d{4})"`.
4. Валюта «руб» строго резолвится в `CurrencyCode.RUP`.

### Критерии приемки (DoD):
- [ ] Распознает транзакции оплаты проезда 4.40 RUP и регулярные покупки.
- [ ] 100% покрытие unit-тестами.
