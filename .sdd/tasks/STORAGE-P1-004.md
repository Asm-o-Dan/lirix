## Задача STORAGE-P1-004: Создать сущность FinancialTransactionEntity

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/FinancialTransactionEntity.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#42-financialtransactionentity`  
**Архитектура:** `.sdd/architecture_phase1.md#54`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.entity

import androidx.room.*

@Entity(
    tableName = "financial_transaction",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["event_id"], unique = true),
        Index(value = ["occurred_at"]),
        Index(value = ["bank"])
    ]
)
data class FinancialTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "event_id")
    val eventId: Long?,

    @ColumnInfo(name = "bank")
    val bank: String,

    @ColumnInfo(name = "direction")
    val direction: String,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "balance_minor")
    val balanceMinor: Long?,

    @ColumnInfo(name = "balance_currency")
    val balanceCurrency: String?,

    @ColumnInfo(name = "merchant")
    val merchant: String?,

    @ColumnInfo(name = "account_mask")
    val accountMask: String?,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    @ColumnInfo(name = "extractor_id")
    val extractorId: String,

    @ColumnInfo(name = "extractor_version")
    val extractorVersion: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
```

### Инварианты и алгоритм:
1. `amountMinor`: целочисленные неделимые единицы (`Long`).
2. Внешний ключ `event_id` имеет политику `onDelete = ForeignKey.SET_NULL`.
3. Уникальный индекс на `event_id` предотвращает дублирование транзакций по одному событию.

### Критерии приемки (DoD):
- [ ] Сущность скомпилирована в Room KSP.
- [ ] Соответствует DDL в `MIGRATION_1_2`.
