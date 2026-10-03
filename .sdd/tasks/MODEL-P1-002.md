## Задача MODEL-P1-002: Реализовать value class CurrencyCode

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/finance/CurrencyCode.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#32-currencycode`  
**Контракт:** `.sdd/contracts/core-model__extract.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.finance

@JvmInline
value class CurrencyCode(val value: String) {
    init {
        require(value in SUPPORTED_CODES) { "Unsupported currency code: '$value'" }
    }

    val minorDigits: Int get() = 2
    val symbol: String
    val isIso4217: Boolean

    override fun toString(): String = value

    companion object {
        const val CODE_RUP = "RUP"
        const val CODE_MDL = "MDL"
        const val CODE_RUB = "RUB"
        const val CODE_EUR = "EUR"
        const val CODE_USD = "USD"

        val RUP: CurrencyCode
        val MDL: CurrencyCode
        val RUB: CurrencyCode
        val EUR: CurrencyCode
        val USD: CurrencyCode

        val SUPPORTED_CODES: Set<String>
        fun of(code: String): CurrencyCode
        fun ofOrNull(code: String?): CurrencyCode?
    }
}
```

### Инварианты и алгоритм:
1. `SUPPORTED_CODES` строго ограничен: `setOf("RUP", "MDL", "RUB", "EUR", "USD")`.
2. Конструктор валидирует код: при недопустимом коде выбрасывается `IllegalArgumentException`.
3. Символы: `RUP` $\to$ `"р."`, `MDL` $\to$ `"L"`, `RUB` $\to$ `"₽"`, `EUR` $\to$ `"€"`, `USD` $\to$ `"$"`.
4. `isIso4217`: `false` для `RUP`, `true` для остальных 4 валют.
5. Изоляция: `CurrencyCode` ни при каких условиях не обращается к `java.util.Currency`.

### Критерии приемки (DoD):
- [ ] `@JvmInline` обеспечивает нулевой runtime overhead в JVM.
- [ ] `CurrencyCode.of("rup")` возвращает `CurrencyCode.RUP`.
- [ ] `CurrencyCode.ofOrNull("BTC")` возвращает `null`.
- [ ] Создание некорректного кода выбрасывает `IllegalArgumentException`.
- [ ] Unit-тесты покрывают все методы и свойства.
