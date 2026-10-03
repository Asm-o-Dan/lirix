## Задача EXTRACT-P1-004: Реализовать защитный CircuitBreaker для экстракторов

**Модуль:** `:extract:finance`  
**Целевой файл:** `extract/finance/src/main/kotlin/com/example/npc/extract/finance/CircuitBreaker.kt`  
**Спецификация:** `.sdd/specs/extract-finance/overview.md#45-circuit-breaker-и-контроль-бюджета-времени`  
**Архитектура:** `.sdd/architecture_phase1.md#34`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.extract.finance

class CircuitBreaker(
    private val timeBudgetMs: Long = 50L,
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 600_000L // 10 минут
) {
    fun <T> execute(extractorId: String, block: () -> T): Result<T>
    fun isCircuitOpen(extractorId: String): Boolean
    fun reset(extractorId: String)
}
```

### Инварианты и алгоритм:
1. Замеряет время выполнения `block()`.
2. Если время выполнения превышает `timeBudgetMs` (50 мс) или блок выбросил исключение:
   - Инкрементирует счетчик сбоев для данного `extractorId`.
   - Если число сбоев достигло `failureThreshold` (3) $\to$ переводит Circuit в состояние `OPEN` на `cooldownMs`.
3. Если Circuit в состоянии `OPEN`:
   - Мгновенный отказ без вызова `block()`.
   - По истечении `cooldownMs` переходит в `HALF_OPEN` для пробного запуска.

### Критерии приемки (DoD):
- [ ] Защита от зависания конвейера при деградации парсера.
- [ ] Unit-тесты проверяют переход состояний: `CLOSED` $\to$ `OPEN` $\to$ `HALF_OPEN` $\to$ `CLOSED`.
