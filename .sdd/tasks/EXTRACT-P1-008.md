## Задача EXTRACT-P1-008: Реализовать BankSmsExtractor

**Модуль:** `:extract:finance`  
**Целевой файл:** `extract/finance/src/main/kotlin/com/example/npc/extract/finance/sms/BankSmsExtractor.kt`  
**Спецификация:** `.sdd/specs/extract-finance/overview.md#54-banksmsextractor`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.extract.finance.sms

import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.extract.ParsedFinanceResult

class BankSmsExtractor : FinanceExtractor {
    override val id: String = "bank.sms"
    override val version: Int = 1
    override val supportedBank: String = "GENERIC_BANK_SMS"

    override fun extract(input: ExtractorInput): ParsedFinanceResult
}
```

### Инварианты и алгоритм:
1. Fallback-экстрактор для SMS от авторизованных отправителей (`"APB"`, `"PRISBANK"`, `"MAIB"`, `"900"`).
2. Обработка форматов SMS Сбербанка РФ (номер 900):
   - `"Покупка (\d+[\.,]\d{2})\s*(?:руб|р\.|₽).*?(?:Баланс|Остаток):\s*(\d+[\.,]\d{2})"` $\to$ `RUB`, `DEBIT`.
3. Обработка транзакционных SMS APB и Сбербанка ПМР при отсутствии мобильного приложения.

### Критерии приемки (DoD):
- [ ] SMS от 900 и APB корректно парсятся.
- [ ] Обычные SMS от людей не парсятся (возвращается `NotApplicable`).
