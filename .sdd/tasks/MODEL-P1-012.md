## Задача MODEL-P1-012: Реализовать контракты экстракции FinanceExtractor, ParsedFinanceResult, CurrencyResolver

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/extract/FinanceExtractor.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#42-экстракция-financeextractor-currencyresolver`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.extract

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import java.time.Instant

data class ExtractorInput(
    val text: String,
    val senderOrTitle: String?,
    val postedAt: Instant,
    val currencyResolver: CurrencyResolver
)

sealed interface ParsedFinanceResult {
    data class Success(
        val type: TransactionType,
        val amount: Money,
        val balance: Money?,
        val merchant: String?,
        val accountMask: String?,
        val status: TransactionStatus = TransactionStatus.COMPLETED
    ) : ParsedFinanceResult

    data class Declined(
        val reason: String,
        val type: TransactionType,
        val amount: Money,
        val merchant: String?,
        val accountMask: String?
    ) : ParsedFinanceResult

    data object NotApplicable : ParsedFinanceResult
    data class Failed(val reason: String) : ParsedFinanceResult
}

interface FinanceExtractor {
    val id: String
    val version: Int
    val supportedBank: String
    fun extract(input: ExtractorInput): ParsedFinanceResult
}

interface CurrencyResolver {
    fun resolve(token: String, contextPackage: String? = null): CurrencyCode?
}
```

### Инварианты и алгоритм:
1. `ParsedFinanceResult` является `sealed interface` с 4 вариантами исхода: `Success`, `Declined`, `NotApplicable`, `Failed`.
2. Экстрактор изолирован: не имеет побочных эффектов (pure functional interface).

### Критерии приемки (DoD):
- [ ] Все типы скомпилированы в чисто JVM-модуле `:core:model`.
- [ ] Доступны для реализации в `:extract:finance` и `:app`.
