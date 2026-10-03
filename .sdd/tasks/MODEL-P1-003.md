## Задача MODEL-P1-003: Реализовать модель денежной суммы Money

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/finance/Money.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#33-money`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.finance

import java.math.BigDecimal

data class Money(
    val minor: Long,
    val currency: CurrencyCode
) : Comparable<Money> {
    init {
        require(minor >= 0L) { "Money minor must be non-negative (got $minor)" }
    }

    operator fun plus(other: Money): Money
    operator fun minus(other: Money): Money
    override fun compareTo(other: Money): Int
    fun toMajorBigDecimal(): BigDecimal
    fun formatDisplay(): String

    companion object {
        fun ofMajor(major: BigDecimal, currency: CurrencyCode): Money
        fun ofMinor(minor: Long, currency: CurrencyCode): Money
    }
}
```

### Инварианты и алгоритм:
1. **Инвариант знака:** `minor >= 0L`. Отрицательные значения строго запрещены.
2. **Арифметика:**
   - `plus`: проверяет совпадение валют (`require(currency == other.currency)`), выполняет `Math.addExact(minor, other.minor)`.
   - `minus`: проверяет совпадение валют, вычисляет `Math.subtractExact(minor, other.minor)`, валидирует неотрицательность результата (`require(result >= 0L)`).
3. **Форматирование:**
   - `toMajorBigDecimal`: `BigDecimal.valueOf(minor, currency.minorDigits)`.
   - `formatDisplay`: мажорное представление с 2 знаками после запятой + пробел + `currency.symbol` (например, `"150.50 р."`, `"49.00 $"`).
4. **Конвертация `ofMajor`:** проверяет `major.signum() >= 0`, масштабирует до `minorDigits` с `RoundingMode.UNNECESSARY`, преобразует unscaledValue в `Long`.

### Критерии приемки (DoD):
- [ ] Попытка создания `Money(-1L, CurrencyCode.RUP)` выбрасывает `IllegalArgumentException`.
- [ ] Сложение сумм разных валют выбрасывает `IllegalArgumentException`.
- [ ] Переполнение Long при сложении выбрасывает `ArithmeticException`.
- [ ] Корректный `formatDisplay` для всех 5 валют.
- [ ] Unit-тесты проверяют граничные случаи: `0L`, `Long.MAX_VALUE`, различные валюты.
