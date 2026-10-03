# Спецификация: zone/extract-universal

Архитектурная зона: `zone/extract-universal` (`:extract:universal`)  
Контракты: [core-text__universal-inducer.md](file:///.sdd/contracts/core-text__universal-inducer.md), [core-model/overview.md](file:///.sdd/specs/core-model/overview.md)  
Архитектура: [architecture_phase3.md](file:///.sdd/architecture_phase3.md) (§2.2 Зона B, §3)  
Статус зоны: **FROZEN**

---

## Модуль: AmountCandidateGenerator (Currency-bound)  Зона: zone/extract-universal  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Сканирует поток токенов `TokenStream` и формирует список кандидатов на финансовые суммы, строго удовлетворяющих правилу связки с валютой (Currency-bound constraint, ADR-302) в локальном окне $\le 2$ токенов слева или справа. НЕ считает изолированные числа (без валютного маркера) кандидатами на сумму, не производит назначение ролей (`TX_AMOUNT` vs `BALANCE`) и не отсекает OTP-коды.

### Типы данных
```kotlin
package com.example.npc.extract.universal

import com.example.npc.core.model.CurrencyCode
import com.example.npc.core.text.TextSpan

sealed interface Candidate {
    val span: TextSpan
    val tokenIndex: Int
}

/**
 * Кандидат на денежную сумму, строго привязанный к конкретному валютному маркеру (ADR-302).
 */
data class AmountCandidate(
    val id: Int,
    override val tokenIndex: Int,
    val currencyTokenIndex: Int,
    val minorUnits: Long,
    val currencyCode: CurrencyCode,
    val isPrefixCurrency: Boolean,
    val distanceTokens: Int,
    override val span: TextSpan
) : Candidate {
    init {
        require(minorUnits >= 0L) { "Amount minor units must be non-negative: $minorUnits" }
        require(distanceTokens in 1..2) { 
            "Distance between number and currency must be strictly 1 or 2 tokens: $distanceTokens" 
        }
    }
}

/**
 * Кандидат на маску банковской карты или счета.
 */
data class CardMaskCandidate(
    override val tokenIndex: Int,
    val rawMask: String,
    val last4Digits: String,
    override val span: TextSpan
) : Candidate {
    init {
        require(last4Digits.length == 4 && last4Digits.all { it.isDigit() }) {
            "Card mask must contain exactly 4 trailing digits: $last4Digits"
        }
        require(rawMask.isNotBlank()) { "Raw mask must not be blank" }
    }
}
```
- **Инварианты:**
  - `minorUnits >= 0L` (знак транзакции определяется отдельно через `OpTypeResolution`).
  - `distanceTokens` равен строго 1 (соседний токен) или 2 (через разделитель двоеточия, пробела или знака препинания).
  - Если токен валюты является амбивалентным (`руб`, `р.`), валюта детерминированно разрешается через `SourceProfile` пакета-источника: для пакетов ПМР (`com.apb.mobile`, `com.prisbank.app`) -> `RUP`, для остальных -> `RUB` (ADR-173).

### Публичный API

#### `AmountCandidateGenerator.generate`
```kotlin
fun generate(tokenStream: TokenStream, sourcePackage: String = ""): List<AmountCandidate>
```
- **Предусловия:** `tokenStream != null`, `tokenStream.size in 0..2048`.
- **Постусловия:** Возвращает список `List<AmountCandidate>`, упорядоченный по возрастанию `tokenIndex`. Изолированные числа без валюты строго отсекаются.
- **Пошаговое поведение:**
  1. Создать пустой список результатов `candidates = ArrayList<AmountCandidate>()`.
  2. Проитерировать токены $i \in [0, \text{tokenStream.size}-1]$:
     - Если `tokenStream[i].type != TokenType.NUMBER`, перейти к следующей итерации.
     - **Поиск валюты справа (суффиксная форма):**
       - Если $i + 1 < \text{size}$ и `tokenStream[i + 1].type == TokenType.CURRENCY`:
         - Зафиксировать $currIdx = i + 1$, $dist = 1$, $isPrefix = false$.
       - Иначе если $i + 2 < \text{size}$ и `tokenStream[i + 1].type in (PUNCT, SIGN)` и `tokenStream[i + 2].type == TokenType.CURRENCY`:
         - Зафиксировать $currIdx = i + 2$, $dist = 2$, $isPrefix = false$.
     - **Поиск валюты слева (префиксная форма, если справа не найдено):**
       - Если валюта справа не найдена:
         - Если $i - 1 \ge 0$ и `tokenStream[i - 1].type == TokenType.CURRENCY`:
           - Зафиксировать $currIdx = i - 1$, $dist = 1$, $isPrefix = true$.
         - Иначе если $i - 2 \ge 0$ и `tokenStream[i - 1].type in (PUNCT, SIGN)` и `tokenStream[i - 2].type == TokenType.CURRENCY`:
           - Зафиксировать $currIdx = i - 2$, $dist = 2$, $isPrefix = true$.
     - **Формирование кандидата:**
       - Если валюта найдена:
         - Извлечь приоритетную интерпретацию `tokenStream[i].interpretations.first()`.
         - Преобразовать значение в минорные единицы: `minorUnits = interpretation.totalMinorUnits`.
         - Разрешить код валюты из токена `tokenStream[currIdx]` с учетом `sourcePackage`.
         - Вычислить охватывающий спан `TextSpan(min(start_i, start_curr), max(end_i, end_curr))`.
         - Добавить `AmountCandidate(id = candidates.size, tokenIndex = i, currencyTokenIndex = currIdx, minorUnits = minorUnits, currencyCode = currCode, isPrefixCurrency = isPrefix, distanceTokens = dist, span = span)`.
  3. Вернуть иммутабельный список кандидатов.
- **Ошибки:** Отсутствуют. При некорректных форматах возвращает пустой список.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:**
  - Текст `"Kod: 1234"` (без валюты) -> `[]` (число проигнорировано).
  - Текст `"Sold: 12 345,67 MDL"` -> `minorUnits = 1234567L`, `currencyCode = MDL`.
  - Префиксная валюта `"$ 150.00"` -> `minorUnits = 15000L`, `isPrefixCurrency = true`.
- **Примеры:**
  1. *Вход:* `"Restituire 245,90 MDL Card *1234 Sold: 12 345,67 MDL"`  
     *Выход:* 2 кандидата: `[245.90 MDL (id=0, dist=1), 12345.67 MDL (id=1, dist=1)]`.
  2. *Вход:* `"Perevod 100 руб"`, `sourcePackage = "com.apb.mobile"`  
     *Выход:* 1 кандидат: `[100.00 RUP (id=0, dist=1)]`.
  3. *Вход:* `"Vash kod avtorizacii: 849204"`  
     *Выход:* `[]` (число без валюты).

### Внутренние функции
- `private fun resolveCurrency(currToken: Token, sourcePackage: String): CurrencyCode`
- `private fun calculateSpan(t1: Token, t2: Token): TextSpan`

### Зависимости
- `:core:text` (`TokenStream`, `TokenType`, `Token`), `:core:model` (`CurrencyCode`).

### Вне скоупа
- Проверка OTP-кодов (выполняется в `SafetyGate`).
- Определение знака списания/пополнения.

---

## Модуль: FeatureExtractor  Зона: zone/extract-universal  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Вычисляет структурированный вектор признаков (`CandidateFeatures`) для каждого обнаруженного `AmountCandidate` на основе окружающего контекста в потоке токенов (семантические якоря `BALANCE`, `FEE`, `DEBIT`, `CREDIT`, позиция в строке, наличие явного знака `+`/`-`). НЕ выполняет оптимизацию целевой функции и не назначает роли кандидатам.

### Типы данных
```kotlin
package com.example.npc.extract.universal

import com.example.npc.core.text.KeywordKind

/**
 * Вектор признаков кандидата для солвера ролей.
 */
data class CandidateFeatures(
    val candidate: AmountCandidate,
    val distanceToNearestKeyword: Int,
    val nearestKeywordKind: KeywordKind?,
    val hasBalanceAnchorLeft: Boolean,
    val hasFeeAnchor: Boolean,
    val hasCardAnchor: Boolean,
    val isFirstInLine: Boolean,
    val isLastInLine: Boolean,
    val lineIndex: Int,
    val hasExplicitSign: Boolean,
    val isExplicitPlus: Boolean,
    val isExplicitMinus: Boolean
)
```
- **Инварианты:**
  - `distanceToNearestKeyword >= 0`. Если ключевых слов в радиусе $\pm 5$ токенов нет, значение равно `Int.MAX_VALUE`, `nearestKeywordKind = null`.
  - `hasBalanceAnchorLeft == true` тогда и только тогда, когда в пределах 3 токенов слева от числа обнаружен `KeywordKind.BALANCE`.

### Публичный API

#### `FeatureExtractor.extract`
```kotlin
fun extract(candidates: List<AmountCandidate>, tokens: TokenStream): List<CandidateFeatures>
```
- **Предусловия:** `candidates` сгенерированы из переданного `tokens`.
- **Постусловия:** Возвращает список `CandidateFeatures` равного размера, соответствующий порядку кандидатов.
- **Пошаговое поведение:**
  1. Для каждого кандидата $c$ из `candidates`:
     - Определить индекс токена числа $idx = c.tokenIndex$.
     - Просканировать окно $[idx - 5, idx + 5]$ на наличие `TokenType.KEYWORD`. Найти ключевое слово с минимальным расстоянием $|k - idx|$.
     - Проверить наличие токена с `KeywordKind.BALANCE` в окне $[idx - 3, idx - 1]$. Если найден — `hasBalanceAnchorLeft = true`.
     - Проверить наличие токена с `KeywordKind.FEE` в окне $[idx - 3, idx + 3]$. Если найден — `hasFeeAnchor = true`.
     - Проверить наличие токена `CARD_MASK` в пределах 4 токенов — `hasCardAnchor = true`.
     - Проанализировать позицию в строке (наличие `TokenType.NEWLINE` до и после).
     - Проверить, предшествует ли числу токен знака `+` или `-`.
     - Собрать объект `CandidateFeatures`.
  2. Вернуть список фичей.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:** Кандидат в начале текста; кандидат в конце текста.
- **Примеры:**
  1. *Вход:* `"Sold: 12 345,67 MDL"`  
     *Выход:* `CandidateFeatures(..., hasBalanceAnchorLeft = true, nearestKeywordKind = BALANCE, distanceToNearestKeyword = 2)`.
  2. *Вход:* `"- 245,90 MDL"`  
     *Выход:* `CandidateFeatures(..., hasExplicitSign = true, isExplicitMinus = true)`.
  3. *Вход:* `"Restituire 245,90 MDL"`  
     *Выход:* `CandidateFeatures(..., nearestKeywordKind = REFUND, distanceToNearestKeyword = 1)`.

### Внутренние функции
- `private fun scanWindowForKeyword(tokens: TokenStream, centerIdx: Int, maxDist: Int): Pair<Int, KeywordKind?>`

### Зависимости
- `AmountCandidate`, `:core:text` (`TokenStream`, `TokenType`, `KeywordKind`).

### Вне скоупа
- Расчет вероятностей и решение оптимизационной задачи.

---

## Модуль: OpTypeResolver  Зона: zone/extract-universal  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Определяет итоговый доменный тип финансовой операции (`TransactionType`: EXPENSE, INCOME, TRANSFER, DECLINED) и признак возврата средств (`isRefund`) на основе обнаруженных семантических маркеров в соответствии со строгой иерархией специфичности: $\text{DECLINED} \succ \text{REFUND} \succ \text{TRANSFER} \succ \text{CREDIT} \succ \text{DEBIT}$ (ADR-185/ADR-306). НЕ производит выбор транзакционной суммы и не изменяет баланс.

### Типы данных
```kotlin
package com.example.npc.extract.universal

import com.example.npc.core.model.TransactionType
import com.example.npc.core.text.KeywordKind
import com.example.npc.core.text.Token

data class OpTypeResolution(
    val transactionType: TransactionType,
    val isRefund: Boolean,
    val isDeclined: Boolean,
    val dominantKeyword: Token?,
    val confidence: Float
)
```
- **Инварианты:**
  - При `isDeclined == true`, `transactionType = TransactionType.EXPENSE`, операция отклонена банком.
  - При обнаружении маркера `REFUND` (например, *Restituire*, *Rambursare*, *Возврат*): `transactionType = TransactionType.INCOME`, `isRefund = true`.
  - При отсутствии явных маркеров по умолчанию принимается `TransactionType.EXPENSE` с пониженной уверенностью (0.60).

### Публичный API

#### `OpTypeResolver.resolve`
```kotlin
fun resolve(tokens: TokenStream): OpTypeResolution
```
- **Предусловия:** `tokens.size >= 0`.
- **Постусловия:** Возвращает детерминированное разрешение `OpTypeResolution`.
- **Пошаговое поведение:**
  1. Собрать все токены типа `TokenType.KEYWORD`, сгруппировав их по `keywordKind`.
  2. Иерархическая проверка приоритетов:
     - Шаг 1 (DECLINED): если есть ключевые слова `DECLINED` (отказ, refuz, respins, declin) -> вернуть `OpTypeResolution(EXPENSE, isRefund = false, isDeclined = true, dominantKeyword = ..., confidence = 0.95f)`.
     - Шаг 2 (REFUND): если есть ключевые слова `REFUND` (возврат, restitu, rambursa, retur) -> вернуть `OpTypeResolution(INCOME, isRefund = true, isDeclined = false, dominantKeyword = ..., confidence = 0.95f)`.
     - Шаг 3 (TRANSFER): если есть ключевые слова `TRANSFER` (перевод, transfer) -> по умолчанию `TRANSFER` с `confidence = 0.90f`.
     - Шаг 4 (CREDIT): если есть ключевые слова `CREDIT` (пополн, зачисл, alimentar, incasar) -> вернуть `OpTypeResolution(INCOME, isRefund = false, isDeclined = false, dominantKeyword = ..., confidence = 0.90f)`.
     - Шаг 5 (DEBIT): если есть ключевые слова `DEBIT` (покупк, оплат, списан, plata, achizit) -> вернуть `OpTypeResolution(EXPENSE, isRefund = false, isDeclined = false, dominantKeyword = ..., confidence = 0.90f)`.
  3. Если ключевые слова не найдены: проверить явные знаки перед кандидатами сумм (`+` -> INCOME, `-` -> EXPENSE). Если знаков нет -> вернуть `EXPENSE, confidence = 0.60f`.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:** Пуш «Restituire ... plata cu cardul» содержит и `REFUND` (*Restituire*), и `DEBIT` (*plata*). Иерархия $\text{REFUND} \succ \text{DEBIT}$ однозначно выбирает `REFUND` (`isRefund = true`).
- **Примеры:**
  1. *Вход:* `"Restituire 245,90 MDL TEMU.COM Card *1234 Sold: 12 345,67 MDL"`  
     *Выход:* `OpTypeResolution(INCOME, isRefund = true, isDeclined = false, dominantKeyword = Token("Restituire"), confidence = 0.95f)`.
  2. *Вход:* `"Refuz plata 50 MDL: fonduri insuficiente"`  
     *Выход:* `OpTypeResolution(EXPENSE, isRefund = false, isDeclined = true, dominantKeyword = Token("Refuz"), confidence = 0.95f)`.
  3. *Вход:* `"Plata 100 MDL Magazin"`  
     *Выход:* `OpTypeResolution(EXPENSE, isRefund = false, isDeclined = false, dominantKeyword = Token("Plata"), confidence = 0.90f)`.

### Внутренние функции
- `private fun findHighestPriorityKeyword(tokens: TokenStream): Pair<KeywordKind, Token>?`

### Зависимости
- `:core:model` (`TransactionType`), `:core:text` (`TokenStream`, `KeywordKind`).

### Вне скоупа
- Проверка подлинности подписи сообщения.

---

## Модуль: RoleAssignmentSolver  Зона: zone/extract-universal  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Осуществляет оптимальное распределение ролей слотов (`TX_AMOUNT`, `BALANCE`, `FEE`, `OTHER`) среди кандидатов сумм через комбинаторный перебор допустимых конфигураций с максимизацией целевой функции правдоподобия при жестких структурных инвариантах (ровно 1 `TX_AMOUNT`, не более 1 `BALANCE`). НЕ применяет эвристики для текстов без валют и не выполняет сетевых взаимодействий.

### Типы данных
```kotlin
package com.example.npc.extract.universal

enum class SlotRole {
    TX_AMOUNT,
    BALANCE,
    FEE,
    OTHER
}

data class RoleAssignment(
    val candidate: AmountCandidate,
    val role: SlotRole,
    val logProbability: Double
)

data class SolverSolution(
    val assignments: List<RoleAssignment>,
    val txAmount: AmountCandidate,
    val balance: AmountCandidate?,
    val fee: AmountCandidate?,
    val totalScore: Double
)
```
- **Инварианты:**
  - Ровно один кандидат обязан получить роль `TX_AMOUNT`.
  - Не более одного кандидата может получить роль `BALANCE`. Роль `BALANCE` запрещена для кандидата, если у него отсутствует `hasBalanceAnchorLeft = true` либо отдельная строка «Sold/Остаток».
  - Максимальное число кандидатов на входе $\le 4$ (при превышении берутся первые 4). Перебор $\le 256$ комбинаций гарантированно завершается за $\le 0.05$ мс на Poco M7.

### Публичный API

#### `RoleAssignmentSolver.solve`
```kotlin
fun solve(features: List<CandidateFeatures>): SolverSolution?
```
- **Предусловия:** `features.isNotEmpty()`.
- **Постусловия:** Возвращает `SolverSolution` с максимальным `totalScore`, либо `null`, если валидная конфигурация не найдена.
- **Пошаговое поведение:**
  1. Если `features.isEmpty()`, вернуть `null`.
  2. Ограничить список кандидатов первыми 4 элементами.
  3. Сгенерировать все допустимые кортежи ролей длины $K$ ($K = features.size$), удовлетворяющие правилам:
     - Количество `SlotRole.TX_AMOUNT` строго равно 1.
     - Количество `SlotRole.BALANCE` $\le 1$.
     - Если кандидату $i$ назначена роль `BALANCE`, то `features[i].hasBalanceAnchorLeft == true` или позиция строго в строке с маркером остатка.
     - Если кандидату $i$ назначена роль `FEE`, то `features[i].hasFeeAnchor == true`.
  4. Для каждой валидной комбинации вычислить суммарный логарифм правдоподобия:
     $$\text{Score} = \sum_{i=1}^K \ln P(\text{role}_i \mid \text{features}_i)$$
  5. Выбрать комбинацию с максимальным скором.
  6. Сконструировать и вернуть `SolverSolution`.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:**
  - 1 кандидат: безоговорочно назначается `TX_AMOUNT`.
  - 2 кандидата (первый без якоря, второй после слова «Sold»): первый -> `TX_AMOUNT`, второй -> `BALANCE`.
  - 2 кандидата (оба без якорей): первый по порядку следования -> `TX_AMOUNT`, второй -> `OTHER`.
- **Примеры:**
  1. *Вход:* Кандидаты `[245.90 MDL (no anchor), 12345.67 MDL (balance anchor)]`  
     *Выход:* `txAmount = 245.90 MDL`, `balance = 12345.67 MDL`.
  2. *Вход:* Кандидат `[50.00 MDL]`  
     *Выход:* `txAmount = 50.00 MDL`, `balance = null`.
  3. *Вход:* Кандидаты `[100 MDL (no anchor), 5 MDL (fee anchor)]`  
     *Выход:* `txAmount = 100 MDL`, `fee = 5 MDL`, `balance = null`.

### Внутренние функции
- `private fun computeLogProb(feat: CandidateFeatures, role: SlotRole): Double`
- `private fun isValidConfiguration(roles: Array<SlotRole>, features: List<CandidateFeatures>): Boolean`

### Зависимости
- `CandidateFeatures`, `AmountCandidate`.

### Вне скоупа
- Обучение весов логистической регрессии на устройстве (веса фиксированы константами).

---

## Модуль: SafetyGate (OTP/Promo вето)  Зона: zone/extract-universal  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Осуществляет финальную многоуровневую фильтрацию результатов извлечения, накладывая вето на одноразовые пароли подтверждения (OTP) и рекламные промо-рассылки, и выносит вердикт надежности (`ACCEPT`, `SUGGEST`, `REJECT`) по порогам уверенности (ADR-196/ADR-200). НЕ изменяет извлеченные числовые данные и не производит запись в Room.

### Типы данных
```kotlin
package com.example.npc.extract.universal

enum class ExtractionVerdict {
    ACCEPT,   // Score >= 0.80 -> Автоматическая запись в базу (CONFIRMED_AUTO)
    SUGGEST,  // 0.50 <= Score < 0.80 -> Запись в ожидании подтверждения (SUGGESTED)
    REJECT    // Score < 0.50 -> Отклонение, отбрасывание финансового блока
}

data class SafetyCheckResult(
    val verdict: ExtractionVerdict,
    val finalScore: Float,
    val isOtpVeto: Boolean,
    val isPromoVeto: Boolean,
    val reason: String?
)
```
- **Инварианты:**
  - При `isOtpVeto == true`: вердикт всегда `REJECT`, сохранение транзакции блокируется.
  - При `isPromoVeto == true`: вердикт всегда `REJECT`.
  - При `finalScore >= 0.80f` без вето: вердикт строго `ACCEPT`.
  - При $0.50f \le finalScore < 0.80f$: вердикт строго `SUGGEST`.
  - При $finalScore < 0.50f$: вердикт строго `REJECT`.

### Публичный API

#### `SafetyGate.evaluate`
```kotlin
fun evaluate(
    tokens: TokenStream,
    solution: SolverSolution?,
    opType: OpTypeResolution,
    sourcePackage: String
): SafetyCheckResult
```
- **Предусловия:** Все аргументы не `null`.
- **Постусловия:** Возвращает детерминированный результат `SafetyCheckResult`.
- **Пошаговое поведение:**
  1. Если `solution == null` или `solution.txAmount == null`: вернуть `SafetyCheckResult(REJECT, 0.0f, false, false, "No valid TX_AMOUNT candidate")`.
  2. **Проверка OTP Veto:**
     - Проверить наличие токена с `KeywordKind.OTP` (*код, cod, code, otp, parola, пароль*).
     - Если маркер OTP присутствует, проверить, является ли сумма или соседнее число изолированным 4-8 значным числом без знака:
       - Если найдено: установить `isOtpVeto = true`, вернуть `SafetyCheckResult(REJECT, 0.0f, true, false, "OTP code detected in financial context")`.
  3. **Проверка Promo Veto:**
     - Проверить наличие токена `TokenType.PERCENT` или маркеров `KeywordKind.PROMO` (*скидк, акци, reducer, promo, cashback до, распродаж*).
     - Если маркер промо присутствует:
       - Если в сообщении отсутствует маска карты (`CARD_MASK`) И отсутствует остаток (`BALANCE`): установить `isPromoVeto = true`, вернуть `SafetyCheckResult(REJECT, 0.1f, false, true, "Promo broadcast message detected")`.
  4. **Расчет итоговой уверенности (Final Score):**
     - Базовая уверенность = `opType.confidence * 0.4 + normalizedSolverScore * 0.4`.
     - Бонус за маску карты (+0.10f), бонус за наличие остатка (+0.10f).
     - Ограничить диапазон $[0.0f, 1.0f]$.
  5. **Определение вердикта:**
     - Если $Score \ge 0.80f$ -> `ACCEPT`.
     - Если $0.50f \le Score < 0.80f$ -> `SUGGEST`.
     - Иначе -> `REJECT`.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:**
  - SMS: `"Vash kod 5849 dlya vhoda v Maib. Nikomu ne soobshchayte"` -> `REJECT` (OTP Veto).
  - Пуш: `"Skidki do 50% na vse tovary v MAIB Park! Prihodite!"` -> `REJECT` (Promo Veto).
  - Пуш MAIB TEMU с возвратом -> `ACCEPT` (Score $\approx 0.95$).
- **Примеры:**
  1. *Вход:* Пуш MAIB TEMU: `opType = CREDIT, isRefund = true`, сумма `245.90 MDL`, карта `*1234`, остаток `12 345,67 MDL`  
     *Выход:* `SafetyCheckResult(verdict = ACCEPT, finalScore = 0.95f, isOtpVeto = false, isPromoVeto = false, reason = null)`.
  2. *Вход:* SMS: `"Parola unica este 4482 pentru autorizarea platii de 100 MDL"`  
     *Выход:* `SafetyCheckResult(verdict = REJECT, finalScore = 0.0f, isOtpVeto = true, isPromoVeto = false, reason = "OTP code detected...")`.
  3. *Вход:* Пуш: `"Oplata 50 MDL v apteke"` (без карты и остатка)  
     *Выход:* `SafetyCheckResult(verdict = SUGGEST, finalScore = 0.72f, isOtpVeto = false, isPromoVeto = false, reason = null)`.

### Внутренние функции
- `private fun detectOtpPattern(tokens: TokenStream): Boolean`
- `private fun detectPromoPattern(tokens: TokenStream, hasCard: Boolean, hasBalance: Boolean): Boolean`

### Зависимости
- `SolverSolution`, `OpTypeResolution`, `:core:text` (`TokenStream`, `TokenType`, `KeywordKind`).

### Вне скоупа
- Проверка номеров телефонов по черным спискам.

---

## Модуль: ExtractUniversalNode  Зона: zone/extract-universal  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Реализует интерфейс узла конвейера рантайма (`PipelineNode`), связывающий нормализацию, токенизацию, генерацию кандидатов, разрешение типа операции, солвер ролей и защитный шлюз для гарантированного извлечения `FinancialTransaction` при отсутствии срабатывания статических и шаблонных экстракторов. НЕ сохраняет данные в базу данных напрямую (запись выполняется downstream узлом конвейера `store.transaction`).

### Типы данных
```kotlin
package com.example.npc.extract.universal

import com.example.npc.core.model.FinancialTransaction
import com.example.npc.pipeline.spi.NodeExecutionResult
import com.example.npc.pipeline.spi.PipelineExecutionContext
import com.example.npc.pipeline.spi.PipelineNode

/**
 * Метаданные извлеченных слотов для дальнейшей индукции шаблонов.
 */
data class ExtractedSlotBinding(
    val role: SlotRole,
    val tokenIndex: Int,
    val text: String,
    val spanStart: Int,
    val spanEnd: Int
)

data class UniversalExtractionOutput(
    val transaction: FinancialTransaction?,
    val slots: List<ExtractedSlotBinding>,
    val verdict: ExtractionVerdict,
    val confidence: Float
)
```
- **Инварианты:**
  - Узел исполняется только в том случае, если регистр транзакции `R_TX` пуст (`null`).
  - При вердикте `ACCEPT` или `SUGGEST` в регистр конвейера `R_TX` записывается сконструированный `FinancialTransaction`.
  - При вердикте `REJECT` регистр `R_TX` остается пустым, в трейс записывается диагностика `E_UNIVERSAL_REJECT`.

### Публичный API

#### `ExtractUniversalNode.execute`
```kotlin
override fun execute(context: PipelineExecutionContext): NodeExecutionResult
```
- **Предусловия:** `context.rawText != null`, `context.packageName != null`.
- **Постусловия:** Возвращает `NodeExecutionResult.Pass` с сохранением транзакции в регистр `R_TX` при `ACCEPT`/`SUGGEST`, либо `NodeExecutionResult.Pass` без модификации регистра при `REJECT`.
- **Пошаговое поведение:**
  1. Проверить регистр транзакции в контексте: если уже заполнен, немедленно вернуть `NodeExecutionResult.Pass` (пропуск).
  2. Вызвать `TextNormalizer.normalize(context.rawText)`.
  3. Вызвать `Lexer.tokenize(normalizedText)`.
  4. Сгенерировать кандидаты через `AmountCandidateGenerator.generate(tokens, context.packageName)`.
  5. Если список кандидатов пуст: зафиксировать метрику `finance_without_payload` и вернуть `Pass`.
  6. Вычислить признаки через `FeatureExtractor.extract(candidates, tokens)`.
  7. Определить тип операции через `OpTypeResolver.resolve(tokens)`.
  8. Найти оптимальное распределение ролей через `RoleAssignmentSolver.solve(features)`.
  9. Выполнить проверку через `SafetyGate.evaluate(tokens, solution, opType, context.packageName)`.
  10. Если вердикт `REJECT`: вернуть `NodeExecutionResult.Pass`.
  11. Если вердикт `ACCEPT` или `SUGGEST`:
      - Извлечь мерчанта: строка/токены между суммой и картой, либо вокруг суммы.
      - Извлечь маску карты из токена `CARD_MASK`.
      - Сконструировать `FinancialTransaction` со статусом `CONFIRMED_AUTO` (для ACCEPT) или `SUGGESTED` (для SUGGEST), `extractorKind = ExtractorKind.UNIVERSAL`, `isRefund = opType.isRefund`.
      - Записать транзакцию в регистр `context.setRegister(RegisterId.R_TX, transaction)`.
      - Записать слот-биндинги в метаданные контекста для индукции шаблона.
  12. Вернуть `NodeExecutionResult.Pass`.
- **Ошибки:** Любое непредвиденное исключение перехватывается, изолируется через `NodeCircuitBreaker` узла и преобразуется в `NodeExecutionResult.Degraded`.
- **Побочные эффекты:** Запись в регистры контекста конвейера.
- **Граничные случаи:** Пустой текст сообщения; текст с отсутствием финансовых данных; банковский пуш с возвратом средств.
- **Примеры:**
  1. *Вход:* Контекст события MAIB TEMU: `"Restituire 245,90 MDL TEMU.COM Card *1234 Sold: 12 345,67 MDL"`  
     *Выход:* `R_TX` содержит `FinancialTransaction(amountMinor=24590, currency=MDL, type=INCOME, isRefund=true, merchant="TEMU.COM", cardMask="*1234", balanceMinor=1234567, status=CONFIRMED_AUTO)`.
  2. *Вход:* Контекст с SMS-кодом: `"Kod 4892"`  
     *Выход:* `R_TX` равен `null`, статус `Pass`.
  3. *Вход:* Контекст с неоднозначной операцией (уверенность 0.65)  
     *Выход:* `R_TX` содержит `FinancialTransaction(..., status=SUGGESTED)`.

### Внутренние функции
- `private fun extractMerchantString(tokens: TokenStream, txAmountCandidate: AmountCandidate): String?`
- `private fun extractCardMaskString(tokens: TokenStream): String?`
- `private fun assembleTransaction(...): FinancialTransaction`

### Зависимости
- `AmountCandidateGenerator`, `FeatureExtractor`, `OpTypeResolver`, `RoleAssignmentSolver`, `SafetyGate`.
- `:core:text` (`TextNormalizer`, `Lexer`), `:core:model` (`FinancialTransaction`), `:pipeline:spi` (`PipelineNode`).

### Вне скоупа
- Прямая вставка в SQL/Room.
- Отрисовка UI баннеров.
