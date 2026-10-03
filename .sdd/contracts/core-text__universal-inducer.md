# Межзонный контракт: Core Text ↔ Universal Extractor & Induction Engine

**Версия:** DRAFT v4 (Фаза 3)  
**Дата:** 2026-09-28  
**Статус:** REVIEW / PROPOSED  
**Стороны контракта:**
- Провайдер текстовой инфраструктуры: `zone/core-text` (`:core:text`)
- Потребитель для извлечения сущностей: `zone/universal-extractor` (`:extract:universal`)
- Потребитель для синтеза шаблонов: `zone/induction-engine` (`:induction`)

---

### 1. Архитектурный контекст и границы

Модуль `:core:text` является чистым Kotlin JVM модулем (Zero Android SDK, Zero Room dependencies, Zero Reflection). Он реализует единый детерминированный пайплайн нормализации и линейного лексического анализа O(N).
Согласно **ADR-301**, и Universal Extractor, и Induction Engine обязаны использовать идентичный поток токенов и отображение смещений (`OffsetMap`), чтобы исключить расхождения между синтезированными регулярными выражениями и поведением рантайма.

```
┌────────────────────────────────────────────────────────┐
│                   zone/core-text                       │
│  - TextNormalizer: NFKC, Diacritics, Homoglyphs        │
│  - OffsetMap: bi-directional span projection           │
│  - Lexer: FSM Unicode codepoint token stream           │
│  - LexiconLoader: Multilingual semantic keywords       │
└───────────────────────────┬────────────────────────────┘
                            │ TokenStream, NormalizedText, OffsetMap
              ┌─────────────┴─────────────┐
              ▼                           ▼
┌───────────────────────────┐ ┌──────────────────────────┐
│  zone/universal-extractor │ │  zone/induction-engine   │
│  (:extract:universal)     │ │  (:induction)            │
└───────────────────────────┘ └──────────────────────────┘
```

---

### 2. Спецификация типов и моделей данных

```kotlin
package com.example.npc.core.text

/**
 * Результат нормализации входящего текста с картой смещений к исходной строке.
 */
data class NormalizedText(
    val original: String,
    val normalized: String,
    val keyForm: String, // lowercase, stripped diacritics, homoglyphs mapped
    val offsetMap: OffsetMap
)

/**
 * Двунаправленная проекция позиций символов между оригиналом и нормализованной строкой.
 */
interface OffsetMap {
    fun toOriginal(normalizedOffset: Int): Int
    fun toNormalized(originalOffset: Int): Int
    fun toOriginalSpan(normalizedStart: Int, normalizedEnd: Int): TextSpan
}

data class TextSpan(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end >= start) { "Invalid span: [$start, $end)" }
    }
    val length: Int get() = end - start
}

/**
 * Типы токенов, формируемые лексером.
 */
enum class TokenType {
    NUMBER,
    CURRENCY,
    CARD_MASK,
    KEYWORD,
    DATE,
    TIME,
    WORD,
    PUNCT,
    NEWLINE,
    URL,
    PERCENT,
    SIGN
}

/**
 * Категории семантических ключевых слов из версионируемого лексикона.
 */
enum class KeywordKind {
    DECLINED,
    REFUND,
    CREDIT,
    TRANSFER,
    DEBIT,
    BALANCE,
    OTP,
    PROMO,
    FEE
}

/**
 * Единичный токен из входного потока.
 */
data class Token(
    val type: TokenType,
    val text: String,
    val span: TextSpan,
    val keywordKind: KeywordKind? = null,
    val interpretations: List<NumberInterpretation> = emptyList()
)

/**
 * Варианты интерпретации числовых кластеров (например, 1,234 -> 1234 или 1.234).
 */
data class NumberInterpretation(
    val rawValue: String,
    val integerPart: Long,
    val fractionPart: Int?, // minor units
    val decimalSeparator: Char?,
    val groupingSeparator: Char?
)

/**
 * Неизменяемый упорядоченный поток токенов с возможностью навигации по индексам.
 */
interface TokenStream : Iterable<Token> {
    val size: Int
    operator fun get(index: Int): Token
    fun tokenAtOffset(offset: Int): Int?
    fun tokensInRange(start: Int, end: Int): List<Token>
}
```

---

### 3. Контракты интерфейсов

```kotlin
package com.example.npc.core.text

interface TextNormalizer {
    /**
     * Нормализует строку, удаляет шумные управляющие символы и строит OffsetMap.
     */
    fun normalize(rawText: String): NormalizedText
}

interface Lexer {
    /**
     * Линейный лексический анализ нормализованного текста за O(N).
     * @throws IllegalArgumentException при превышении максимальной длины текста (> 4096 символов).
     */
    fun tokenize(text: NormalizedText): TokenStream
}

interface LexiconRepository {
    /**
     * Возвращает текущую активную версию словаря стем и ключевых слов.
     */
    val version: Int

    /**
     * Определяет категорию ключевого слова по его нормализованной основе.
     */
    fun matchKeyword(stemOrWord: String): KeywordKind?
}
```

---

### 4. Инварианты и ограничения
1. **Zero Regex на лексическом этапе:** `Lexer` обязан быть реализован как детерминированный конечный автомат (FSM) по Unicode codepoint'ам.
2. **Линейное время исполнения:** Для текста длиной до 1000 символов лексический анализ должен занимать $\le 0.5$ мс на стандартном ядре ARM Cortex-A55.
3. **Биективность OffsetMap:** Для любого токена `span` в нормализованном тексте `offsetMap.toOriginalSpan(span.start, span.end)` обязан точно указывать на соответствующую подстроку в оригинале.
4. **Неоднозначность валюты:** Форма «руб» никогда не разрешается в модуле `:core:text` автоматически — лексер обязан маркировать ее как `CurrencyAmbiguous`, передавая контекст на уровень выше.
