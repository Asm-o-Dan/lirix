## Задача STORAGE-P1-009: Реализовать FinancialTransactionMapper

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/mapper/FinancialTransactionMapper.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#61-financialtransactionmapper`  
**Архитектура:** `.sdd/architecture_phase1.md#4`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.mapper

import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.storage.entity.FinancialTransactionEntity

internal object FinancialTransactionMapper {
    fun toEntity(domain: FinancialTransaction): FinancialTransactionEntity
    fun toDomain(entity: FinancialTransactionEntity): FinancialTransaction
}
```

### Инварианты и алгоритм:
1. Маппинг `Instant` $\leftrightarrow$ epoch millisecond `Long`.
2. Маппинг `Money` $\leftrightarrow$ `amount_minor: Long` и `currency: String`.
3. Маппинг `TransactionType` и `TransactionStatus` через строковые enum names.
4. Безопасное восстановление nullable полей `balance`, `merchant`, `accountMask`.

### Критерии приемки (DoD):
- [ ] Двусторонний маппинг `toDomain(toEntity(model)) == model`.
- [ ] 100% покрытие unit-тестами для всех комбинаций полей (с balance и без, с merchant и без).
