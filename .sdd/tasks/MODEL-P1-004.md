## Задача MODEL-P1-004: Реализовать перечисление TransactionType

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/finance/TransactionType.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#34-transactiontype`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.finance

enum class TransactionType {
    DEBIT,      // Списание / Покупка / Оплата услуг (Расход)
    CREDIT,     // Пополнение / Зарплата / Входящий перевод (Доход)
    TRANSFER;   // Перевод между своими счетами / P2P-перевод

    companion object {
        val EXPENSE: TransactionType get() = DEBIT
        val INCOME: TransactionType get() = CREDIT

        fun fromStringOrNull(raw: String?): TransactionType?
    }
}
```

### Инварианты и алгоритм:
1. `fromStringOrNull` обрабатывает:
   - "DEBIT", "EXPENSE", "debit", "expense" $\to$ `DEBIT`
   - "CREDIT", "INCOME", "credit", "income" $\to$ `CREDIT`
   - "TRANSFER", "transfer" $\to$ `TRANSFER`
   - null, пустую строку или неизвестные токены $\to$ `null`
2. Свойства-синонимы `EXPENSE` и `INCOME` обеспечивают обратную совместимость с ранними спецификациями.

### Критерии приемки (DoD):
- [ ] 100% покрытие unit-тестами маппинга строк и синонимов.
