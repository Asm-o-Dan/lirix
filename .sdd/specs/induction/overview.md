# Спецификация: zone/induction

Архитектурная зона: `zone/induction` (`:induction`)  
Контракты: [core-text__universal-inducer.md](file:///.sdd/contracts/core-text__universal-inducer.md), [architecture_phase3.md](file:///.sdd/architecture_phase3.md)  
Статус зоны: **FROZEN**

---

## Модуль: TokenSegmenter  Зона: zone/induction  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Выполняет структурированное разбиение последовательности токенов сообщения на функциональные сегменты (`LITERAL`, `SLOT`, `VARIABLE_GENERALIZED`, `WHITESPACE`) на основе разметки слотов, полученной от Universal Extractor или пользователя. НЕ выполняет генерацию регулярных выражений, валидацию RE2/J и проверку на ReDoS.

### Типы данных
```kotlin
package com.example.npc.induction

import com.example.npc.core.text.Token
import com.example.npc.extract.universal.ExtractedSlotBinding
import com.example.npc.extract.universal.SlotRole

enum class VariableType {
    DATE,
    TIME,
    REFERENCE_CODE
}

sealed interface Segment {
    val tokens: List<Token>
    val text: String
}

data class LiteralSegment(
    override val tokens: List<Token>,
    override val text: String
) : Segment

data class SlotSegment(
    val slotName: String,
    val role: SlotRole,
    override val tokens: List<Token>,
    override val text: String
) : Segment

data class VariableSegment(
    val variableType: VariableType,
    override val tokens: List<Token>,
    override val text: String
) : Segment

data class WhitespaceSegment(
    val hasNewline: Boolean,
    override val tokens: List<Token> = emptyList(),
    override val text: String
) : Segment

data class SegmentedSequence(
    val segments: List<Segment>,
    val totalLength: Int
)
```
- **Инварианты:**
  - Каждый токен из входного потока принадлежит ровно одному сегменту.
  - Порядковый номер сегментов строго соответствует их позициям в исходном тексте.
  - Сегменты `SlotSegment` создаются только для подтвержденных слотов (`TX_AMOUNT`, `BALANCE`, `CARD_MASK`, `MERCHANT`, `FEE`).

### Публичный API

#### `TokenSegmenter.segment`
```kotlin
fun segment(
    tokens: TokenStream,
    slotBindings: List<ExtractedSlotBinding>
): SegmentedSequence
```
- **Предусловия:** `tokens.size >= 0`. `slotBindings` не содержат пересекающихся диапазонов токенов.
- **Постусловия:** Возвращает объект `SegmentedSequence`, содержащий непрерывную цепочку сегментов от начала до конца сообщения.
- **Пошаговое поведение:**
  1. Создать отсортированный по индексам токенов маппинг слотов: `slotMap: Map<Int, ExtractedSlotBinding>`.
  2. Инициализировать список сегментов `result = ArrayList<Segment>()`.
  3. Указатель $i = 0$.
  4. Пока $i < tokens.size$:
     - Если индекс $i$ входит в `slotBinding`:
       - Собрать все токены данного слота в `SlotSegment`.
       - Добавить сегмент в `result`, сдвинуть $i$ на число токенов в слоте.
     - Иначе если `tokens[i].type == TokenType.DATE`:
       - Создать `VariableSegment(VariableType.DATE, listOf(tokens[i]), tokens[i].text)`.
       - Добавить в `result`, инкрементировать $i$.
     - Иначе если `tokens[i].type == TokenType.TIME`:
       - Создать `VariableSegment(VariableType.TIME, listOf(tokens[i]), tokens[i].text)`.
       - Добавить в `result`, инкрементировать $i$.
     - Иначе если `tokens[i].type == TokenType.NEWLINE`:
       - Создать `WhitespaceSegment(hasNewline = true, ..., text = "\n")`.
       - Добавить в `result`, инкрементировать $i$.
     - Иначе (токены слов или пунктуации):
       - Накапливать последовательные токены в буфер литералов, пока не встретится слот, переменная или перенос строки.
       - Сформировать `LiteralSegment`, добавить в `result`.
  5. Схлопнуть смежные однотипные литералы, вернуть `SegmentedSequence(result, totalLength)`.
- **Ошибки:** `IllegalArgumentException` при обнаружении перекрывающихся слотов.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:** Сообщение без слотов (превращается в последовательность литералов и пробелов); сообщение, состоящее только из слотов.
- **Примеры:**
  1. *Вход:* `"Restituire 245,90 MDL Card *1234"` со слотом суммы на `"245,90 MDL"` и карты на `"*1234"`  
     *Выход:* `[LiteralSegment("Restituire "), SlotSegment(role=TX_AMOUNT, "245,90 MDL"), LiteralSegment(" Card "), SlotSegment(role=CARD_MASK, "*1234")]`.
  2. *Вход:* `"Oplata 100 MDL 28.09.2026 14:00"` со слотом на 100 MDL  
     *Выход:* `[LiteralSegment("Oplata "), SlotSegment(TX_AMOUNT), LiteralSegment(" "), VariableSegment(DATE), LiteralSegment(" "), VariableSegment(TIME)]`.
  3. *Вход:* `"Sold: 10 MDL"`  
     *Выход:* `[LiteralSegment("Sold: "), SlotSegment(BALANCE, "10 MDL")]`.

### Внутренние функции
- `private fun collectLiteralRun(tokens: TokenStream, startIndex: Int, slotIndices: Set<Int>): LiteralSegment`

### Зависимости
- `:core:text` (`TokenStream`, `Token`, `TokenType`), `:extract:universal` (`ExtractedSlotBinding`, `SlotRole`).

### Вне скоупа
- Проверка регулярных выражений на ReDoS.

---

## Модуль: SafeFragments  Зона: zone/induction  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Предоставляет каноническую библиотеку детерминированных, квантифицированных регулярных выражений фрагментов (safe snippets), гарантированно компилируемых в RE2/J за линейное время без риска ReDoS. НЕ генерирует регулярные выражения с открытыми неограниченными квантификаторами `.*` или `.+` и не производит синтаксический анализ предложений.

### Типы данных
```kotlin
package com.example.npc.induction

import com.example.npc.extract.universal.SlotRole

object SafeFragments {
    // Числовые суммы: до 4 триплетов тысяч, опциональная дробная часть из 1-2 знаков
    const val AMOUNT = """[+\-]?[0-9]{1,3}(?:[ .,'][0-9]{3}){0,4}(?:[.,][0-9]{1,2})?|[+\-]?[0-9]{1,9}(?:[.,][0-9]{1,2})?"""
    
    // Поддерживаемые валюты региона
    const val CURRENCY = """MDL|RUP|USD|EUR|RUB|lei|лей|леев|руб\.?|р\.|\$|€"""
    
    // 4 цифры маски карты
    const val CARD4 = """[0-9]{4}"""
    
    // Наименование мерчанта: ленивый квантификатор от 2 до 64 символов без переноса строки
    const val MERCHANT = """[^\n]{2,64}?"""
    
    // Дата: DD.MM.YYYY или DD/MM/YY
    const val DATE = """[0-9]{2}[./\-][0-9]{2}(?:[./\-][0-9]{2,4})?"""
    
    // Время: HH:MM или HH:MM:SS
    const val TIME = """[0-9]{2}:[0-9]{2}(?::[0-9]{2})?"""
    
    // Пробелы
    const val WS = """\s+"""
    const val WS_OPT = """\s*"""
    
    // Ограничитель конца строки или сообщения
    const val LINE_END = """(?:\n|$)"""
}
```
- **Инварианты:**
  - Ни один фрагмент не содержит бесконечных неограниченных квантификаторов `*` или `+` над широкими классами (`.` или `.*`).
  - Фрагмент `MERCHANT` обязательно квантифицирован с верхней границей `{2,64}?` и исключает символ новой строки `[^\n]`.

### Публичный API

#### `SafeFragments.fragmentForRole`
```kotlin
fun fragmentForRole(role: SlotRole): String
```
- **Предусловия:** `role` входит в перечисление `SlotRole`.
- **Постусловия:** Возвращает безопасный строковый regex-фрагмент.
- **Поведение:**
  - `TX_AMOUNT` -> `SafeFragments.AMOUNT`
  - `BALANCE` -> `SafeFragments.AMOUNT`
  - `FEE` -> `SafeFragments.AMOUNT`
  - `OTHER` -> `SafeFragments.AMOUNT`
- **Ошибки:** Отсутствуют.

#### `SafeFragments.quoteLiteral`
```kotlin
fun quoteLiteral(literal: String): String
```
- **Предусловия:** `literal` строка нормализованного текста.
- **Постусловия:** Возвращает литерал, экранированный конструкцией `\Q...\E` (с заменой вхождений `\E` на `\E\\E\Q` при их наличии).

#### Примеры:
1. *Вход:* `SlotRole.TX_AMOUNT` -> *Выход:* `"[+\-]?[0-9]{1,3}(?:[ .,'][0-9]{3}){0,4}(?:[.,][0-9]{1,2})?|[+\-]?[0-9]{1,9}(?:[.,][0-9]{1,2})?"`.
2. *Вход:* Литерал `"Restituire"` -> *Выход:* `"\Qrestituire\E"`.
3. *Вход:* `VariableType.DATE` -> *Выход:* `"[0-9]{2}[./\-][0-9]{2}(?:[./\-][0-9]{2,4})?"`.

### Внутренние функции
- `private fun escapeRegex(input: String): String`

### Зависимости
- `:extract:universal` (`SlotRole`).

### Вне скоупа
- Поддержка синтаксических конструкций PCRE (lookbehind, backreferences), отсутствующих в RE2.

---

## Модуль: TemplateBuilder (RE2/J)  Зона: zone/induction  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Синтезирует скомпилированное описание шаблона (`BuiltTemplate`) с именованными группами захвата RE2/J (`amount`, `curr`, `card`, `merchant`, `bal`, `balcurr`) и набором семантических констант на основе `SegmentedSequence` и `SafeFragments`. НЕ производит сохранение в базу данных и не перехватывает ошибки синтаксиса компилятора.

### Типы данных
```kotlin
package com.example.npc.induction

import com.example.npc.core.model.CurrencyCode
import com.example.npc.core.model.TransactionType

data class AmountFormatSpec(
    val decimalSeparator: Char?,
    val groupingSeparator: Char?
)

data class BuiltTemplate(
    val pattern: String,
    val namedGroups: List<String>,
    val constants: Map<String, String>,
    val amountFormat: AmountFormatSpec,
    val requiredLiterals: List<String>,
    val defaultCurrency: CurrencyCode?
)
```
- **Инварианты:**
  - Паттерн всегда начинается с флага регистронезависимости `(?i)`.
  - Все имена групп строго входят в закрытый словарь: `amount`, `curr`, `card`, `merchant`, `bal`, `balcurr`, `fee`.
  - Поле `requiredLiterals` содержит не менее двух литеральных ключевых слов длиной от 3 символов для быстрого префильтра в рантайме.

### Публичный API

#### `TemplateBuilder.build`
```kotlin
fun build(
    sequence: SegmentedSequence,
    opType: OpTypeResolution,
    defaultCurrency: CurrencyCode? = null
): BuiltTemplate
```
- **Предусловия:** `sequence.segments.isNotEmpty()`.
- **Постусловия:** Возвращает валидный объект `BuiltTemplate`.
- **Пошаговое поведение:**
  1. Создать `StringBuilder` для паттерна, добавить префикс `(?i)`.
  2. Инициализировать `namedGroups = ArrayList<String>()`, `requiredLiterals = ArrayList<String>()`.
  3. Для каждого сегмента $seg$ из `sequence.segments`:
     - Если $seg$ это `LiteralSegment`:
       - Добавить слово в `requiredLiterals`, если длина $\ge 3$ и оно не является знаком препинания.
       - Добавить экранированный литерал `\Q${seg.text.trim()}\E` и пробельный разделитель `\s+` (или `\s*` около пунктуации).
     - Если $seg$ это `SlotSegment`:
       - Если `role == TX_AMOUNT`: добавить `(?P<amount>${SafeFragments.AMOUNT})\s*(?P<curr>${SafeFragments.CURRENCY})`, зарегистрировать группы `amount` и `curr`.
       - Если `role == BALANCE`: добавить `(?P<bal>${SafeFragments.AMOUNT})\s*(?P<balcurr>${SafeFragments.CURRENCY})`, зарегистрировать группы `bal` и `balcurr`.
       - Если `role == CARD_MASK`: добавить `\*(?P<card>${SafeFragments.CARD4})`, зарегистрировать `card`.
       - Если `role == MERCHANT`: добавить `(?P<merchant>${SafeFragments.MERCHANT})`, зарегистрировать `merchant`.
     - Если $seg$ это `VariableSegment`:
       - Заменить на незахватывающую группу: `(?:${SafeFragments.DATE})` или `(?:${SafeFragments.TIME})`.
     - Если $seg$ это `WhitespaceSegment`:
       - Если `hasNewline == true` -> добавить `(?:\n|\s+)`, иначе `\s+`.
  4. Сформировать словарь семантических констант:
     - `constants["transactionType"] = opType.transactionType.name`
     - `constants["isRefund"] = opType.isRefund.toString()`
     - `constants["isDeclined"] = opType.isDeclined.toString()`
  5. Сконструировать и вернуть `BuiltTemplate`.
- **Ошибки:** `IllegalStateException` при невозможности построения корректного паттерна.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:** Текст с отсутствием мерчанта; текст с префиксным положением валюты.
- **Примеры:**
  1. *Вход:* Сегменты инцидента MAIB TEMU  
     *Выход:* `pattern = "(?i)\\Qrestituire\\E\\s+(?P<amount>...)\\s*(?P<curr>...)\\s+(?P<merchant>[^\\n]{2,64}?)\\s+\\Qcard\\E\\s+\\*(?P<card>[0-9]{4})\\s+\\Qsold\\E\\s*:\\s*(?P<bal>...)\\s*(?P<balcurr>...)"`.
  2. *Вход:* Сегменты списания APB  
     *Выход:* Паттерн с группами `amount`, `curr`, `merchant`, `bal`.
  3. *Вход:* Сегменты без остатка (только сумма и мерчант)  
     *Выход:* Паттерн с группами `amount`, `curr`, `merchant`.

### Внутренние функции
- `private fun determineAmountFormat(slotText: String): AmountFormatSpec`

### Зависимости
- `SegmentedSequence`, `SafeFragments`, `OpTypeResolution`, `:core:model` (`CurrencyCode`).

### Вне скоупа
- Сохранение в Room SQLite.

---

## Модуль: RightBoundedRule  Зона: zone/induction  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Проверяет и гарантирует соблюдение правила правой ограниченности (Right-Bounded Rule, ADR-273) для открытых и ленивых захватывающих групп (`merchant`), требуя обязательного наличия фиксированного строкового литерала справа или терминального маркера строки `(?:\n|$)`. НЕ модифицирует числовые фрагменты с жестко заданным числом цифр.

### Типы данных
```kotlin
package com.example.npc.induction

data class BoundingEnforcementResult(
    val isValid: Boolean,
    val correctedPattern: String,
    val diagnostic: String?
)
```
- **Инварианты:**
  - Ни одна именованная группа `(?P<merchant>...)` не может граничить справа с неопределенным концом или другим открытым выражением.
  - Если за мерчантом следует конец текста сообщения, паттерн завершается терминатором `(?:\n|$)`.

### Публичный API

#### `RightBoundedRule.enforce`
```kotlin
fun enforce(segments: List<Segment>, rawPattern: String): BoundingEnforcementResult
```
- **Предусловия:** `rawPattern` не пустой.
- **Постусловия:** Возвращает `BoundingEnforcementResult`. Если паттерн не имел правой границы, возвращается скорректированная строка с принудительно добавленным ограничителем.
- **Пошаговое поведение:**
  1. Найти индекс сегмента `SlotSegment` с ролью `MERCHANT`.
  2. Если мерчант отсутствует, вернуть `BoundingEnforcementResult(isValid = true, rawPattern, null)`.
  3. Проверить следующий за ним значащий сегмент:
     - Если следующий сегмент `LiteralSegment`: условие выполнено (ограничен литералом справа). Вернуть `isValid = true`.
     - Если следующий сегмент отсутствует (мерчант в конце текста) или является переводом строки:
       - Добавить в конец группы мерчанта маркер `(?:\n|$)`.
       - Вернуть `BoundingEnforcementResult(isValid = true, correctedPattern, "Appended line-end bound")`.
     - Если следующий сегмент другой слот без разделяющего литерала: вернуть `BoundingEnforcementResult(isValid = false, rawPattern, "Merchant slot must be bounded by a literal")`.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:** Мерчант на последней строке сообщения; мерчант, за которым сразу идет маска карты.
- **Примеры:**
  1. *Вход:* `"... (?P<merchant>[^\\n]{2,64}?)\\s+\\Qcard\\E ..."`  
     *Выход:* `isValid = true` (ограничен литералом "card").
  2. *Вход:* `"... (?P<merchant>[^\\n]{2,64}?)"` (в самом конце)  
     *Выход:* `isValid = true, correctedPattern = "... (?P<merchant>[^\\n]{2,64}?)(?:\\n|$)"`.
  3. *Вход:* `"... (?P<merchant>[^\\n]{2,64}?)(?P<card>[0-9]{4})"` (без литерала между ними)  
     *Выход:* `isValid = false, diagnostic = "Direct adjacency of unbounded slots"`.

### Внутренние функции
- `private fun findNextSignificantSegment(segments: List<Segment>, currentIndex: Int): Segment?`

### Зависимости
- `Segment`, `SlotSegment`, `LiteralSegment`.

### Вне скоупа
- Анализ динамического стека парсинга в рантайме.

---

## Модуль: TemplateLint  Зона: zone/induction  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Выполняет статическую валидацию сложности, корректности и специфичности сгенерированного шаблона в соответствии с лимитами компилятора (`PipelineCompiler`), предотвращая попадание вырожденных или слишком широких регулярок в рантайм. НЕ исполняет матчинг по корпусу сообщений и не делает обращений к файловой системе.

### Типы данных
```kotlin
package com.example.npc.induction

sealed interface LintResult {
    object Pass : LintResult
    data class Failed(val errors: List<LintError>) : LintResult
}

data class LintError(
    val code: String,
    val message: String
)
```
- **Инварианты и лимиты компилятора:**
  - Длина паттерна: строго $\le 1024$ символов (код ошибки `E_PATTERN_TOO_LONG`).
  - Количество именованных групп: строго $\le 12$ (код ошибки `E_TOO_MANY_GROUPS`).
  - Разрешенные имена групп: `amount`, `curr`, `card`, `merchant`, `bal`, `balcurr`, `fee` (код ошибки `E_ILLEGAL_GROUP_NAME`).
  - Специфичность: суммарная длина фиксированных литералов $\ge 8$ символов, число независимых литералов $\ge 2$ (код ошибки `E_LOW_SPECIFICITY`).
  - Компилируемость в RE2/J: паттерн компилируется без синтаксических ошибок `com.google.re2j.PatternSyntaxException` (код ошибки `E_RE2_SYNTAX_ERROR`).

### Публичный API

#### `TemplateLint.lint`
```kotlin
fun lint(template: BuiltTemplate): LintResult
```
- **Предусловия:** `template` не `null`.
- **Постусловия:** Возвращает `LintResult.Pass` при отсутствии нарушений, либо `LintResult.Failed` со списком ошибок.
- **Пошаговое поведение:**
  1. Проверить `template.pattern.length <= 1024`. Если больше — добавить `E_PATTERN_TOO_LONG`.
  2. Проверить `template.namedGroups.size <= 12`. Если больше — добавить `E_TOO_MANY_GROUPS`.
  3. Проверить имена всех групп по белому списку разрешенных слотов. При наличии посторонних имен добавить `E_ILLEGAL_GROUP_NAME`.
  4. Проверить специфичность:
     - Рассчитать суммарную длину строк в `template.requiredLiterals`.
     - Если сумма $< 8$ или размер списка $< 2$: добавить `E_LOW_SPECIFICITY`.
  5. Попытаться скомпилировать паттерн через `com.google.re2j.Pattern.compile(template.pattern)`:
     - При исключении `PatternSyntaxException`: добавить `E_RE2_SYNTAX_ERROR`.
  6. Если список ошибок пуст, вернуть `LintResult.Pass`, иначе `LintResult.Failed(errors)`.
- **Ошибки:** Отсутствуют (все ошибки трансформируются в список `LintError`).
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:** Паттерн ровно из 1024 символов -> Pass; паттерн из 1025 символов -> Failed.
- **Примеры:**
  1. *Вход:* Валидный шаблон инцидента MAIB TEMU (длина 340, литералы "restituire", "card", "sold")  
     *Выход:* `LintResult.Pass`.
  2. *Вход:* Шаблон `(?i)(?P<amount>[0-9]+)` (без литералов)  
     *Выход:* `LintResult.Failed([LintError("E_LOW_SPECIFICITY", ...)])`.
  3. *Вход:* Шаблон с запрещенной группой `(?P<unknown_slot>.*)`  
     *Выход:* `LintResult.Failed([LintError("E_ILLEGAL_GROUP_NAME", ...)])`.

### Внутренние функции
- `private fun validateSpecificity(requiredLiterals: List<String>): LintError?`
- `private fun testRe2Compilation(pattern: String): LintError?`

### Зависимости
- `BuiltTemplate`, `com.google.re2j.Pattern`.

### Вне скоупа
- Проверка покрытия базы данных историческими событиями.

---

## Модуль: RoundTripValidator  Зона: zone/induction  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Выполняет обязательную верификацию синтезированного шаблона: прогоняет скомпилированный RE2/J-паттерн по исходному сообщению, сличая полученные слоты с эталонной разметкой побайтово (инвариант Round-Trip), и тестирует шаблон на выборке негативных событий (OTP, спам, другие банки) на отсутствие ложных срабатываний. НЕ сохраняет результаты в базу данных и не модифицирует конвейер рантайма.

### Типы данных
```kotlin
package com.example.npc.induction

import com.example.npc.extract.universal.ExtractedSlotBinding

sealed interface RoundTripResult {
    object Success : RoundTripResult
    data class SlotMismatch(
        val slotName: String,
        val expectedValue: String,
        val actualValue: String?
    ) : RoundTripResult
    data class NoMatchOnOriginal(val pattern: String) : RoundTripResult
    data class NegativeCorpusViolation(
        val falsePositiveEventText: String
    ) : RoundTripResult
}
```
- **Инварианты:**
  - Шаблон допускается к активации только при получении `RoundTripResult.Success`.
  - Значения всех захваченных слотов `amount`, `curr`, `card`, `bal`, `merchant` обязаны посимвольно совпасть с ожидаемыми значениями исходной разметки.

### Публичный API

#### `RoundTripValidator.validate`
```kotlin
fun validate(
    template: BuiltTemplate,
    originalNormalizedText: String,
    expectedSlots: List<ExtractedSlotBinding>,
    negativeCorpus: List<String> = emptyList()
): RoundTripResult
```
- **Предусловия:** `template` успешно прошел проверку `TemplateLint`.
- **Постусловия:** Возвращает `RoundTripResult.Success`, либо детализированную причину расхождения.
- **Пошаговое поведение:**
  1. Скомпилировать `pattern = com.google.re2j.Pattern.compile(template.pattern)`.
  2. Выполнить сопоставление `matcher = pattern.matcher(originalNormalizedText)`.
  3. Если `matcher.find() == false`:
     - Вернуть `RoundTripResult.NoMatchOnOriginal(template.pattern)`.
  4. Для каждого ожидаемого слота из `expectedSlots`:
     - Определить имя группы в регулярке (`amount`, `curr`, `card`, `merchant`, `bal`).
     - Извлечь захваченную подстроку `actual = matcher.group(groupName)`.
     - Если `actual != slot.text`:
       - Вернуть `RoundTripResult.SlotMismatch(groupName, slot.text, actual)`.
  5. Проверка негативного корпуса:
     - Для каждой строки $negText$ из `negativeCorpus`:
       - Выполнить `negMatcher = pattern.matcher(negText)`.
       - Если `negMatcher.find() == true`:
         - Вернуть `RoundTripResult.NegativeCorpusViolation(negText)`.
  6. Вернуть `RoundTripResult.Success`.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:** Пустой негативный корпус (проверяется только round-trip); несовпадение регистра (флаг `(?i)` должен нивелировать различия).
- **Примеры:**
  1. *Вход:* Исходный текст MAIB TEMU, ожидаемые слоты `[amount="245,90", curr="MDL", card="1234"]`  
     *Выход:* `RoundTripResult.Success`.
  2. *Вход:* Тот же текст, но регулярка захватила лишний пробел в сумму `"245,90 "`  
     *Выход:* `RoundTripResult.SlotMismatch("amount", "245,90", "245,90 ")`.
  3. *Вход:* Негативное сообщение `"Vash kod: 4492"`, на котором шаблон сработал  
     *Выход:* `RoundTripResult.NegativeCorpusViolation("Vash kod: 4492")`.

### Внутренние функции
- `private fun extractGroupSafely(matcher: com.google.re2j.Matcher, groupName: String): String?`

### Зависимости
- `BuiltTemplate`, `ExtractedSlotBinding`, `com.google.re2j.Pattern`.

### Вне скоупа
- Автоматическая правка сломанного регулярного выражения (при сбое индукция бракуется).
