## Задача EXTRACT-P1-003: Реализовать контекстный валютный резолвер BankCurrencyResolver

**Модуль:** `:extract:finance`  
**Целевой файл:** `extract/finance/src/main/kotlin/com/example/npc/extract/finance/BankCurrencyResolver.kt`  
**Спецификация:** `.sdd/specs/extract-finance/overview.md#43-региональный-валютный-резолвинг-bankcurrencyresolver`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.extract.finance

import com.example.npc.core.model.extract.CurrencyResolver
import com.example.npc.core.model.finance.CurrencyCode

object BankCurrencyResolver : CurrencyResolver {
    override fun resolve(token: String, contextPackage: String?): CurrencyCode?
}
```

### Инварианты и алгоритм:
1. Контекст банков ПМР (`com.apb.mobile`, `com.prisbank.app`, или SMS от `"APB"`, `"PRISBANK"`):
   - Токены `"руб"`, `"р."`, `"руб."`, `"RUP"`, `"PRB"` $\to$ строго `CurrencyCode.RUP` (Приднестровский рубль).
2. Контекст банков РФ (пакеты РФ или SMS от `"900"`, `"SBER"`):
   - Токены `"руб"`, `"р."`, `"руб."`, `"₽"`, `"RUB"` $\to$ `CurrencyCode.RUB`.
3. Контекст банков Молдовы (`md.maib.maibank` или SMS от `"MAIB"`):
   - Токены `"MDL"`, `"L"`, `"лей"`, `"lei"` $\to$ `CurrencyCode.MDL`.
4. Общемировые маркеры (независимо от банка):
   - `"$"`, `"USD"` $\to$ `CurrencyCode.USD`.
   - `"€"`, `"EUR"` $\to$ `CurrencyCode.EUR`.
5. Неизвестные токены $\to$ `null`.

### Критерии приемки (DoD):
- [ ] Токен «руб» в пуше Агропромбанка резолвится как `RUP`, а не `RUB`!
- [ ] 100% покрытие unit-тестами для всех комбинаций токенов и пакетов.
