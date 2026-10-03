## Задача MODEL-P1-005: Реализовать перечисление TransactionStatus

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/finance/TransactionStatus.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#35-transactionstatus`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.finance

enum class TransactionStatus {
    COMPLETED,  // Успешно выполнена / подтверждена
    DECLINED;   // Отказ в авторизации / отклонена банком

    companion object {
        val SUCCESS: TransactionStatus get() = COMPLETED

        fun fromStringOrDefault(raw: String?, default: TransactionStatus = COMPLETED): TransactionStatus
    }
}
```

### Инварианты и алгоритм:
1. `fromStringOrDefault`:
   - "COMPLETED", "SUCCESS", "completed", "success" $\to$ `COMPLETED`
   - "DECLINED", "REFUZATA", "REJECTED", "FAILED", "declined" $\to$ `DECLINED`
   - null, пустая строка или неопознанный маркер $\to$ `default` (по умолчанию `COMPLETED`)
2. Свойство-синоним `SUCCESS` указывает на `COMPLETED`.

### Критерии приемки (DoD):
- [ ] Тесты покрывают все региональные маркеры отказов (включая румынский `"REFUZATA"` из MAIB).
