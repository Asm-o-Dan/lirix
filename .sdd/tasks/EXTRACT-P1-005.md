## Задача EXTRACT-P1-005: Реализовать ApbNotificationExtractor (Агропромбанк ПМР)

**Модуль:** `:extract:finance`  
**Целевой файл:** `extract/finance/src/main/kotlin/com/example/npc/extract/finance/apb/ApbNotificationExtractor.kt`  
**Спецификация:** `.sdd/specs/extract-finance/overview.md#51-apbnotificationextractor-агропромбанк-пмр`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.extract.finance.apb

import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.extract.ParsedFinanceResult

class ApbNotificationExtractor : FinanceExtractor {
    override val id: String = "apb.push"
    override val version: Int = 1
    override val supportedBank: String = "APB"

    override fun extract(input: ExtractorInput): ParsedFinanceResult
}
```

### Инварианты и алгоритм:
1. Использует чистый RE2/J (`com.google.re2j.Pattern`).
2. Распознает шаблоны пушей Агропромбанка:
   - Списание: `"Оплата: (\d+[\.,]\d{2})\s*(руб|р\.|RUP).*?(?:В:|в)\s*([^\.]+).*?Остаток:\s*(\d+[\.,]\d{2})"` $\to$ `type = DEBIT`.
   - Пополнение / Перевод: `"Пополнение: (\d+[\.,]\d{2})\s*(руб|р\.|RUP)"` $\to$ `type = CREDIT`.
   - Перевод «Клевер»: `"Перевод: (\d+[\.,]\d{2})\s*(руб|р\.|RUP)"` $\to$ `type = TRANSFER`.
3. Парсинг сумм через `AmountParser.parseToMinor`.
4. Разрешение валюты через `BankCurrencyResolver` с пакетом `"com.apb.mobile"` (всегда `CurrencyCode.RUP` для рублей).
5. Нефинансовые пуши (баланс, OTP-пароли) $\to$ `ParsedFinanceResult.NotApplicable`.

### Критерии приемки (DoD):
- [ ] 100% точность на реальных пушах APB из базы dogfooding (`event_engine.db`).
- [ ] Время работы экстрактора < 5 мс.
