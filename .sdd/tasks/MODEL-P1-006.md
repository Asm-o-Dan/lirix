## Задача MODEL-P1-006: Реализовать доменную модель FinancialTransaction

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/finance/FinancialTransaction.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#36-financialtransaction`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.finance

import java.time.Instant

data class FinancialTransaction(
    val id: Long = 0L,
    val eventId: Long?,
    val bank: String,
    val type: TransactionType,
    val amount: Money,
    val balance: Money?,
    val merchant: String?,
    val accountMask: String?,
    val status: TransactionStatus,
    val occurredAt: Instant,
    val extractorId: String,
    val extractorVersion: Int,
    val rawText: String,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(id >= 0L) { "id must be >= 0 (got $id)" }
        require(eventId == null || eventId > 0L) { "eventId must be > 0 if specified (got $eventId)" }
        require(bank.isNotBlank()) { "bank must not be blank" }
        require(extractorVersion >= 1) { "extractorVersion must be >= 1 (got $extractorVersion)" }
        require(rawText.isNotBlank()) { "rawText must not be blank" }
        require(merchant == null || merchant.isNotBlank()) { "merchant must not be blank if specified" }
        if (balance != null) {
            require(balance.currency == amount.currency) {
                "Balance currency (${balance.currency}) must match transaction currency (${amount.currency})"
            }
        }
    }
}
```

### Инварианты и алгоритм:
1. `id == 0L` — transient сущность перед записью в Room.
2. `eventId` — nullable внешний ключ. Если задан, обязан быть строго `> 0L`.
3. Если указан `balance`, его валюта `balance.currency` обязана совпадать с `amount.currency`.
4. Иммутабельность: все поля `val`.

### Критерии приемки (DoD):
- [ ] Нельзя создать транзакцию с расходящимися валютами суммы и баланса.
- [ ] Пустой `bank` или `rawText` выбрасывает `IllegalArgumentException`.
- [ ] Unit-тесты проверяют валидацию всех полей и инвариантов.
