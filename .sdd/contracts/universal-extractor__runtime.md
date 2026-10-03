# Межзонный контракт: Universal Extractor ↔ Pipeline Runtime & Storage

**Версия:** DRAFT v4 (Фаза 3)  
**Дата:** 2026-09-28  
**Статус:** REVIEW / PROPOSED  
**Стороны контракта:**
- Провайдер эвристического извлечения: `zone/universal-extractor` (`:extract:universal`)
- Потребитель узлов рантайма: `zone/pipeline-runtime` (`:pipeline:runtime`)
- Потребитель хранилища: `zone/core-storage` (`:core:storage`)

---

### 1. Архитектурный контекст и границы

Модуль `:extract:universal` реализует отказоустойчивое, эвристическое извлечение финансовых транзакций без предварительно захардкоженных регулярных выражений банков.
В рантайме конвейера он регистрируется как узел `extract.universal` через SPI `NodeExecutor` (Фаза 2).
При успешном извлечении (`ACCEPT` или `SUGGEST`) узел записывает результат в регистровый банк потока `R_TX` и устанавливает метаданные провенанса.

```
┌────────────────────────────────────────────────────────┐
│           zone/universal-extractor                     │
│  - Candidate Generators (Amount, Card, Balance)       │
│  - Scorer & RoleAssignmentSolver                      │
│  - SafetyGate (OTP/Promo veto, SourceProfile prior)    │
└───────────────────────────┬────────────────────────────┘
                            │ ExtractionResult
                            ▼
┌────────────────────────────────────────────────────────┐
│           zone/pipeline-runtime                        │
│  - NodeExecutor: "extract.universal"                   │
│  - Sets R_TX, R_TX_PROVENANCE, NodeCircuitBreaker      │
└───────────────────────────┬────────────────────────────┘
                            │ FinancialTransaction + Provenance
                            ▼
┌────────────────────────────────────────────────────────┐
│           zone/core-storage                            │
│  - StorageGateway.saveProcessedEvent                   │
│  - Status: CONFIRMED_AUTO | SUGGESTED                  │
└────────────────────────────────────────────────────────┘
```

---

### 2. Спецификация типов данных

```kotlin
package com.example.npc.extract.universal

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.TextSpan

/**
 * Вердикт защитного шлюза (Gate).
 */
enum class ExtractionVerdict {
    ACCEPT,     // Уверенность >= 0.80, авто-подтверждение
    SUGGEST,    // Уверенность 0.50..0.79, требует подтверждения человеком
    REJECT      // Уверенность < 0.50 или сработал Veto
}

/**
 * Назначение ролей извлеченным сущностям.
 */
enum class SlotRole {
    TX_AMOUNT,
    BALANCE,
    FEE,
    OTHER
}

data class ExtractedSlot(
    val role: SlotRole,
    val span: TextSpan,
    val rawText: String,
    val confidence: Float
)

/**
 * Полный результат работы Universal Extractor.
 */
data class UniversalExtractionResult(
    val verdict: ExtractionVerdict,
    val transaction: FinancialTransaction?,
    val slots: List<ExtractedSlot>,
    val confidence: Float,
    val features: Map<String, Float>,
    val isRefund: Boolean = false,
    val vetoReason: String? = null
)

/**
 * Профиль источника для разрешения контекстных неоднозначностей.
 */
data class SourceProfile(
    val packageName: String,
    val isKnownBankingApp: Boolean,
    val defaultCurrency: CurrencyCode?,
    val regionalAmbiguityResolver: (String) -> CurrencyCode?
)
```

---

### 3. Контракты интерфейсов

```kotlin
package com.example.npc.extract.universal

import com.example.npc.core.text.NormalizedText
import com.example.npc.core.text.TokenStream

interface UniversalExtractor {
    /**
     * Основная точка входа для эвристического разбора сообщения.
     * Выполняется за O(N) по токенам + комбинаторный перебор слотов.
     */
    fun extract(
        normalizedText: NormalizedText,
        tokenStream: TokenStream,
        sourceProfile: SourceProfile
    ): UniversalExtractionResult
}

interface SourceProfileRegistry {
    /**
     * Возвращает профиль источника по его пакету.
     */
    fun getProfile(packageName: String): SourceProfile
}
```

---

### 4. Инварианты безопасности и правила исполнения
1. **Currency-Bound инвариант (ADR-302):** Ни одно число не может получить роль `TX_AMOUNT`, если в пределах $\pm 1$ токена от него отсутствует распознанный токен валюты `CURRENCY`.
2. **Иерархия OpType:** Тип операции определяется по правилу доминирования:
   `DECLINED` > `REFUND` > `TRANSFER` > `CREDIT` > `DEBIT`.
3. **Строгое OTP Veto:** Если в тексте найден токен `KEYWORD(OTP)` и 4-8 значный числовой код, вердикт `ACCEPT` запрещен.
4. **Бюджет времени:** Метод `extract()` обязан укладываться в $3.0$ мс на 95-м перцентиле (тестируется на Poco M7). При превышении лимита узел прерывается предохранителем `NodeCircuitBreaker`.
