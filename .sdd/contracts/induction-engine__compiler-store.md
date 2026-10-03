# Межзонный контракт: Induction Engine ↔ Pipeline Compiler & Replay

**Версия:** DRAFT v4 (Фаза 3)  
**Дата:** 2026-09-28  
**Статус:** REVIEW / PROPOSED  
**Стороны контракта:**
- Провайдер синтеза шаблонов: `zone/induction-engine` (`:induction`)
- Валидатор и линтер шаблонов: `zone/pipeline-compiler` (`:pipeline:compiler`)
- Потребитель симуляции и регрессий: `zone/pipeline-replay` (`:feature:replay`)
- Потребитель хранилища: `zone/pipeline-store` (`:core:storage`)

---

### 1. Архитектурный контекст и границы

Модуль `:induction` принимает размеченные слоты уведомления (от `UniversalExtractor` или из UI редактора) и синтезирует строго ограниченный шаблон RE2/J.
Для допуска шаблона в рантайм он передается в `PipelineCompiler.compileTemplate(spec)`:
- Проход статической проверки (`TemplateLint`): длина, arity групп, запрет произвольных квантификаторов.
- Проход Round-Trip: проверка совпадения результата извлечения на исходном тексте.
- Проход Replay-симуляции: верификация отсутствия ложных срабатываний на выборке не-финансовых и OTP событий.

```
┌────────────────────────────────────────────────────────┐
│               zone/induction-engine                    │
│  - Segmenter: Tokens -> Literals / Slots / Variables   │
│  - SafeFragments: Bounded RE2/J snippets               │
│  - TemplateBuilder: Right-bounded regex constructor    │
└───────────────────────────┬────────────────────────────┘
                            │ TemplateSpec
                            ▼
┌────────────────────────────────────────────────────────┐
│             zone/pipeline-compiler                     │
│  - compileTemplate(spec): Pass 1 (Lint) + Pass 2 (RE2) │
│  - RoundTripValidator: Match against source sample     │
└───────────────────────────┬────────────────────────────┘
                            │ CompiledTemplate
                            ▼
┌────────────────────────────────────────────────────────┐
│             zone/pipeline-replay                       │
│  - ReplayValidator: Positive / Negative / Conflict     │
└────────────────────────────────────────────────────────┘
```

---

### 2. Спецификация типов данных

```kotlin
package com.example.npc.induction

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.NormalizedText
import com.example.npc.core.text.TextSpan
import com.example.npc.core.text.TokenStream

/**
 * Роли токенов при сегментации шаблона.
 */
enum class TokenRole {
    LITERAL,        // Фиксированный текст (Pattern.quote)
    SLOT,           // Именованная группа захвата (Amount, Currency, Card...)
    VARIABLE,       // Обобщаемая переменная (Дата, Служебный номер)
    WHITESPACE      // Разделитель
}

enum class SlotType {
    AMOUNT,
    CURRENCY,
    CARD_MASK,
    BALANCE,
    BALANCE_CURRENCY,
    MERCHANT
}

data class SlotAssignment(
    val slot: SlotType,
    val tokenIndices: List<Int>,
    val span: TextSpan
)

data class SlotConstants(
    val opType: TransactionType? = null,
    val isRefund: Boolean = false,
    val fixedCurrency: CurrencyCode? = null
)

data class AmountFormat(
    val decimalSeparator: Char,
    val groupingSeparator: Char?
)

/**
 * Входные данные для индукции шаблона.
 */
data class InductionInput(
    val sampleText: NormalizedText,
    val tokenStream: TokenStream,
    val assignments: List<SlotAssignment>,
    val tokenRoles: Map<Int, TokenRole> = emptyMap(),
    val constants: SlotConstants = SlotConstants(),
    val sourcePackage: String
)

/**
 * Спецификация сгенерированного динамического шаблона.
 */
data class TemplateSpec(
    val sourceKey: String,
    val pattern: String,
    val groupBindings: Map<String, SlotType>,
    val constants: SlotConstants,
    val amountFormat: AmountFormat,
    val requiredLiterals: List<String>,
    val specificity: Double,
    val canonicalHash: String
)

sealed interface InductionOutcome {
    data class Success(val spec: TemplateSpec) : InductionOutcome
    data class Rejected(val reason: String, val violationCode: String) : InductionOutcome
}
```

---

### 3. Контракты интерфейсов

```kotlin
package com.example.npc.induction

interface TemplateInducer {
    /**
     * Построение регулярного выражения по входным данным разметки.
     */
    fun induce(input: InductionInput): InductionOutcome
}

interface TemplateValidator {
    /**
     * Проверка сгенерированного шаблона: Round-trip + Replay + Lint.
     */
    suspend fun validate(
        spec: TemplateSpec,
        sourceSample: NormalizedText
    ): TemplateValidationReport
}

data class TemplateValidationReport(
    val isRoundTripValid: Boolean,
    val positiveMatchesCount: Int,
    val negativeViolationsCount: Int,
    val conflictCount: Int,
    val diagnostics: List<String>
) {
    val canActivate: Boolean
        get() = isRoundTripValid && negativeViolationsCount == 0 && diagnostics.isEmpty()
}
```

---

### 4. Инварианты безопасности
1. **Запрет свободных квантификаторов:** В паттерне запрещены выражения вида `.*` или `.+`. Любой промежуточный текст использует `\s+` или квантификаторы с верхним порогом (`{1,64}`).
2. **Правая ограниченность:** Переменные группы (`MERCHANT`, `WORD`) обязаны быть ограничены справа литералом или концом строки.
3. **Лимит специфичности:** Шаблон обязан содержать не менее 2 литералов суммарной длиной от 8 символов.
4. **Строгий Round-Trip:** Если при прогоне скомпилированного паттерна на исходном сообщении значения слотов не совпадают со `SlotAssignment`, шаблон отклоняется со статусом `Rejected`.
