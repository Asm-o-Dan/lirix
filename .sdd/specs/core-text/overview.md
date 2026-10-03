# Спецификация: zone/core-text

Архитектурная зона: `zone/core-text` (`:core:text`)  
Контракт: [core-text__universal-inducer.md](file:///.sdd/contracts/core-text__universal-inducer.md)  
Архитектура: [architecture_phase3.md](file:///.sdd/architecture_phase3.md) (§1 ADR-301, §2.2 Зона A, §3.2)  
Статус зоны: **FROZEN**

---

## 1. Архитектурный контекст и фундаментальные инварианты

Модуль `:core:text` является фундаментальным чистым Kotlin JVM модулем (Zero Android SDK dependencies, Zero Room/SQLite, Zero Reflection, Zero Backtracking Regex).

Согласно **ADR-301**, и Universal Slot-Based Extractor (`:extract:universal`), и Dynamic Template Induction Engine (`:induction`), и Hot-Path Runtime (`:pipeline:runtime`) обязаны работать поверх строго единого потока токенов `TokenStream` и детерминированной нормализации `NormalizedText` с двунаправленной проекцией смещений `OffsetMap`. Это предотвращает рассинхронизацию между фазой синтеза шаблона и фазой сопоставления в рантайме.

### Общие инварианты зоны:
1. **Zero Regex на лексическом этапе:** Все разборы чисел, дат, масок и слов выполняются рукописным детерминированным конечным автоматом (FSM) по кодовым точкам Unicode за строго линейное время $O(N)$.
2. **Лимит длины ввода:** Максимальная длина входящего необработанного текста составляет $4096$ символов. При превышении бросается `IllegalArgumentException`.
3. **Бюджет производительности (Poco M7 / MediaTek Helio G99):**
   - Нормализация строки из 500 символов: $\le 0.15$ мс.
   - Полная токенизация FSM: $\le 0.35$ мс.
   - Поиск по смещению `OffsetMap` и `TokenStream`: $\le 0.005$ мс ($O(\log K)$).
4. **Безопасность памяти (Zero Garbage Spikes):** Все внутренние структуры используют примитивные массивы (`IntArray`) для хранения смещений.

---

## Модуль: TextNormalizer  Зона: zone/core-text  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Выполняет детерминированную каноническую нормализацию входящего текста: удаление невидимых/управляющих символов Unicode, канонизацию NFKC, унификацию всех видов пробелов к `\u0020`, нормализацию переводов строк к `\n` и построение поисковой формы `keyForm` (нижний регистр, снятие диакритики, замена латино-кириллических гомоглифов) с одновременным построением монотонной карты смещений `OffsetMap`. НЕ производит синтаксический разбор, токенизацию, удаление знаков препинания из основного текста и распознавание чисел или валют.

### Типы данных
```kotlin
package com.example.npc.core.text

/**
 * Неизменяемый результат нормализации входящего текста с картой смещений к исходной строке.
 */
data class NormalizedText(
    val original: String,
    val normalized: String,
    val keyForm: String,
    val offsetMap: OffsetMap
) {
    init {
        require(original.length <= MAX_TEXT_LENGTH) { 
            "Original text exceeds limit: ${original.length} > $MAX_TEXT_LENGTH" 
        }
        require(normalized.isNotEmpty() || original.isEmpty()) { 
            "Normalized text cannot be empty if original is non-empty" 
        }
        require(keyForm.length == normalized.length) {
            "keyForm length must match normalized text length: ${keyForm.length} vs ${normalized.length}"
        }
    }

    companion object {
        const val MAX_TEXT_LENGTH = 4096
    }
}

/**
 * Конфигурация процесса нормализации текста.
 */
data class NormalizerConfig(
    val stripZeroWidth: Boolean = true,
    val stripBidiControls: Boolean = true,
    val normalizeSpaces: Boolean = true,
    val mapHomoglyphsInKeyForm: Boolean = true,
    val stripDiacriticsInKeyForm: Boolean = true
)
```

- **Инварианты и ограничения:**
  - `original.length in 0..4096`. При нарушении выбрасывается `IllegalArgumentException`.
  - `normalized` сохраняет регистр букв и пунктуацию оригинала, за исключением схлопнутых пробелов и удаленных управляющих кодовых точек.
  - `keyForm` имеет строго равную длину с `normalized` (`keyForm.length == normalized.length`), что обеспечивает посимвольное взаимно-однозначное соответствие между отображаемым текстом и поисковой формой без пересчета смещений.
  - `offsetMap` строго связывает каждый индекс в `normalized` с исходным индексом в `original`.

### Публичный API

#### `TextNormalizer.normalize`
```kotlin
fun normalize(rawText: String, config: NormalizerConfig = NormalizerConfig()): NormalizedText
```
- **Предусловия:** `rawText != null`, `rawText.length <= 4096`.
- **Постусловия:**
  - Возвращает `NormalizedText`.
  - В `normalized` удалены управляющие символы (диапазоны `\u0000..\u0008`, `\u000B..\u000C`, `\u000E..\u001F`, `\u007F..\u009F`), скрытые невидимые маркеры (`\u200B`..`\u200F`, `\uFEFF`, `\u00AD`), bidi-переопределения (`\u202A`..`\u202E`, `\u2066`..`\u2069`).
  - Все пробельные символы (`\u00A0`, `\u202F`, `\u2000`..`\u200A`, `\t`) схлопнуты в единичный `\u0020`.
  - Переносы строк приведены к единичному `\n` (последовательности `\r\n` и `\r` заменены на `\n`), концевые пробелы перед `\n` удалены.
  - Карта `offsetMap` содержит точные границы всех модификаций.
- **Пошаговое поведение:**
  1. Проверить `rawText.length <= 4096`. Если больше — выбросить `IllegalArgumentException("Text length exceeds 4096: ${rawText.length}")`.
  2. Если `rawText.isEmpty()`, вернуть `NormalizedText("", "", "", IdentityOffsetMap)`.
  3. Инициализировать билдер смещений `OffsetMapBuilder(rawText.length)`.
  4. **Фаза 1: Фильтрация управляющих кодовых точек и канонизация переносов строк.**
     - Проход по символам `rawText`. Символы `\r\n` сжимаются в один `\n` (со сдвигом длины на -1).
     - Управляющие кодовые точки и невидимые пробелы отбрасываются (билдер регистрирует удаление символа).
  5. **Фаза 2: Каноническая декомпозиция и композиция Unicode NFKC.**
     - Применяется `java.text.Normalizer.normalize(cleaned, Normalizer.Form.NFKC)`.
     - Лигатуры (например, `ﬁ` `\uFB01` -> `fi`, `№` -> `No`) заменяются на составные символы; регистрируется локальное увеличение длины.
  6. **Фаза 3: Схлопывание пробелов.**
     - Любая непрерывная последовательность пробелов `[ \t\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]+` заменяется на одиночный пробел `\u0020`.
     - Пробелы, непосредственно примыкающие к началу строки, концу строки или символу `\n`, удаляются.
     - Билдер фиксирует все схлопывания.
  7. **Фаза 4: Сборка `OffsetMap`.**
     - Вызывается `builder.build()`, формирующий компактный иммутабельный `SegmentedOffsetMap`.
  8. **Фаза 5: Построение `keyForm` (посимвольное соответствие длине `normalized`).**
     - На основе `normalized` создается `CharArray(normalized.length)`.
     - Каждый символ переводится в нижний регистр.
     - Диакритика румынского/молдавского/русского языков снимается посимвольной заменой:
       `ă`->`a`, `â`->`a`, `î`->`i`, `ș`->`s`, `ț`->`t`, `é`->`e`, `è`->`e`, `ë`->`e`, `ö`->`o`, `ü`->`u`, `й`->`и`.
     - Гомоглифы латиницы и кириллицы унифицируются в сторону латинского алфавита:
       кириллические `а`->`a`, `с`->`c`, `е`->`e`, `о`->`o`, `р`->`p`, `х`->`x`, `у`->`y`, `і`->`i`, `ј`->`j`, `ѕ`->`s`, `В`->`b`, `М`->`m`, `Т`->`t`, `Н`->`h`, `К`->`k`.
     - Если символ не требует замены, сохраняется его строчный вариант.
  9. Сконструировать и вернуть `NormalizedText(rawText, normalizedStr, keyFormStr, offsetMap)`.
- **Ошибки:** `IllegalArgumentException` при превышении длины текста.
- **Побочные эффекты:** Отсутствуют (чистая функция).
- **Граничные случаи:**
  - Строка из неразрывных пробелов и невидимых символов `"\u00A0\u200B\uFEFF"` -> `normalized = ""`, `keyForm = ""`.
  - Строка с суррогатными парами (эмодзи): корректно прогоняются без разрушения пары codepoint'ов.
  - Текст без изменений: `offsetMap` работает как identity-маппинг без оверхеда.
- **Примеры:**
  1. *Вход:* `"Sold:\u00A012\u00A0345,67\u00A0MDL"`  
     *Выход:*
     - `normalized = "Sold: 12 345,67 MDL"`
     - `keyForm = "sold: 12 345,67 mdl"`
     - `offsetMap.toOriginal(6)` -> `6` (индекс `'1'`)
  2. *Вход:* `"Restituire  \t 245,90  MDL\r\nCard *1234"`  
     *Выход:*
     - `normalized = "Restituire 245,90 MDL\nCard *1234"`
     - `keyForm = "restituire 245,90 mdl\ncard *1234"`
  3. *Вход:* `"Plată  refuzată:\u200B fonduri insuficiente"`  
     *Выход:*
     - `normalized = "Plată refuzată: fonduri insuficiente"`
     - `keyForm = "plata refuzata: fonduri insuficiente"`

### Внутренние функции
- `private fun filterControlAndBidi(input: String, b: OffsetMapBuilder): String`: поточная фильтрация нежелательных символов.
- `private fun applyNfkcWithOffsets(input: String, b: OffsetMapBuilder): String`: вызов NFKC с сохранением соответствия позиций.
- `private fun collapseSpaces(input: String, b: OffsetMapBuilder): String`: схлопывание пробелов и тримминг строк.
- `private fun buildKeyFormPreservingLength(normalized: String, config: NormalizerConfig): String`: посимвольная генерация `keyForm`.

### Зависимости
- `core-text__universal-inducer.md`.
- `java.text.Normalizer` (JVM Core).

### Вне скоупа
- Морфологический анализ слов и лемматизация.
- Извлечение валют и сумм.

---

## Модуль: OffsetMap  Зона: zone/core-text  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Обеспечивает строго монотонную, двунаправленную проекцию позиций символов и интервалов (`TextSpan`) между нормализованной и исходной строками за логарифмическое время $O(\log K)$, где $K$ — число точек модификации строки при нормализации. НЕ хранит полные копии исходного текста, не мутирует внутреннее состояние после создания и не выбрасывает непроверяемых исключений при попадании в диапазон текста.

### Типы данных
```kotlin
package com.example.npc.core.text

/**
 * Непрерывный полуоткрытый интервал [start, end) в тексте.
 */
data class TextSpan(val start: Int, val end: Int) {
    init {
        require(start >= 0) { "Start offset must be non-negative: $start" }
        require(end >= start) { "End offset ($end) must be >= start offset ($start)" }
    }

    val length: Int get() = end - start
    fun isEmpty(): Boolean = start == end
    fun contains(offset: Int): Boolean = offset in start until end
    fun intersects(other: TextSpan): Boolean = maxOf(start, other.start) < minOf(end, other.end)
}

/**
 * Интерфейс двунаправленной проекции смещений.
 */
interface OffsetMap {
    val originalLength: Int
    val normalizedLength: Int

    fun toOriginal(normalizedOffset: Int): Int
    fun toNormalized(originalOffset: Int): Int
    fun toOriginalSpan(normalizedStart: Int, normalizedEnd: Int): TextSpan
    fun toNormalizedSpan(originalStart: Int, originalEnd: Int): TextSpan
}

/**
 * Компактный дельта-сегмент для lock-free бинарного поиска.
 */
data class DeltaSegment(
    val normOffset: Int,
    val origOffset: Int,
    val delta: Int // origOffset - normOffset
)
```

- **Инварианты:**
  - Монотонность: для любых $a \le b$:
    `toOriginal(a) <= toOriginal(b)` и `toNormalized(a) <= toNormalized(b)`.
  - Граничные инварианты:
    `toOriginal(0) == 0`, `toOriginal(normalizedLength) == originalLength`.
    `toNormalized(0) == 0`, `toNormalized(originalLength) == normalizedLength`.
  - Идемпотентность спанов:
    `toOriginalSpan(start, end).length >= 0`.
  - Массивы смещений отсортированы строго по возрастанию:
    `normOffsets[i] < normOffsets[i + 1]`.

### Публичный API

#### `OffsetMap.toOriginal`
```kotlin
fun toOriginal(normalizedOffset: Int): Int
```
- **Предусловия:** `normalizedOffset in 0..normalizedLength`.
- **Постусловия:** Возвращает соответствующий индекс в исходном тексте в диапазоне `0..originalLength`.
- **Пошаговое поведение:**
  1. Проверить `normalizedOffset in 0..normalizedLength`. При нарушении бросить `IndexOutOfBoundsException`.
  2. Если массив сегментов пуст (текст не менялся): вернуть `normalizedOffset`.
  3. Выполнить бинарный поиск наибольшего индекса $i$, такого что `normOffsets[i] <= normalizedOffset`.
  4. Если такой сегмент найден, итоговое смещение = `normalizedOffset + deltas[i]`.
  5. Ограничить результат диапазоном `[0, originalLength]`.
- **Ошибки:** `IndexOutOfBoundsException` при выходе за границы `0..normalizedLength`.
- **Побочные эффекты:** Отсутствуют.
- **Граничные случаи:** `normalizedOffset = 0` возвращает `0`; `normalizedOffset = normalizedLength` возвращает `originalLength`.

#### `OffsetMap.toNormalized`
```kotlin
fun toNormalized(originalOffset: Int): Int
```
- **Предусловия:** `originalOffset in 0..originalLength`.
- **Постусловия:** Возвращает соответствующий индекс в нормализованной строке в диапазоне `0..normalizedLength`.
- **Пошаговое поведение:**
  1. Выполнить бинарный поиск наибольшего индекса $j$, такого что `origOffsets[j] <= originalOffset`.
  2. Итоговое смещение = `originalOffset - deltas[j]`.
  3. Ограничить результат `0..normalizedLength`.
- **Ошибки:** `IndexOutOfBoundsException`.

#### `OffsetMap.toOriginalSpan`
```kotlin
fun toOriginalSpan(normalizedStart: Int, normalizedEnd: Int): TextSpan
```
- **Предусловия:** `0 <= normalizedStart <= normalizedEnd <= normalizedLength`.
- **Постусловия:** Возвращает `TextSpan(toOriginal(normalizedStart), toOriginal(normalizedEnd))`.

#### Примеры:
1. *Вход:* Исходный текст `"A\u200BB"`, нормализованный `"AB"` (`\u200B` вырезан на позиции 1).  
   *Запрос:* `toOriginal(1)` (позиция символа `'B'`).  
   *Выход:* `2` (в исходной строке `'B'` находится на индексе 2).
2. *Вход:* Исходный текст `"A   B"`, нормализованный `"A B"` (два лишних пробела удалены).  
   *Запрос:* `toOriginalSpan(2, 3)` (спан слова `"B"` в нормализованной строке).  
   *Выход:* `TextSpan(start = 4, end = 5)`.
3. *Вход:* Лигатура `"ﬁnance"`, нормализованная в `"finance"` (длина увеличилась на 1).  
   *Запрос:* `toOriginal(2)` (символ `'n'`).  
   *Выход:* `1` (в оригинале лигатура занимала 1 символ на позиции 0).

### Внутренние функции
- `private fun binarySearchNorm(offset: Int): Int`: бинарный поиск сегмента дельт по нормализованному смещению.
- `private fun binarySearchOrig(offset: Int): Int`: бинарный поиск сегмента дельт по оригинальному смещению.

### Зависимости
- Контракт: `.sdd/contracts/core-text__universal-inducer.md`.

### Вне скоупа
- Хранение текста строк.
- Сериализация в JSON (используется только в памяти процесса).

---

## Модуль: Lexer FSM O(N)  Зона: zone/core-text  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Выполняет детерминированный однопроходный лексический анализ нормализованного текста по кодовым точкам Unicode без использования регулярных выражений с алгоритмической сложностью строго $O(N)$ и нулевым backtracking'ом, формируя последовательность токенов: числа, валюты, маски карт, ключевые слова, даты, время, разделители и знаки. НЕ определяет роли сумм (`TX_AMOUNT` vs `BALANCE`), не фильтрует спам и не обращается к БД.

### Типы данных
```kotlin
package com.example.npc.core.text

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

data class Token(
    val type: TokenType,
    val text: String,
    val span: TextSpan,
    val keywordKind: KeywordKind? = null,
    val interpretations: List<NumberInterpretation> = emptyList()
) {
    init {
        require(text.length == span.length) { 
            "Token text length mismatch: ${text.length} vs span ${span.length}" 
        }
        if (type == TokenType.KEYWORD) {
            require(keywordKind != null) { "Keyword token must have non-null keywordKind" }
        }
        if (type == TokenType.NUMBER) {
            require(interpretations.isNotEmpty()) { "Number token must have at least one interpretation" }
        }
    }
}

data class NumberInterpretation(
    val rawValue: String,
    val integerPart: Long,
    val fractionPart: Int?, // minor units (e.g. 90 for .90)
    val decimalSeparator: Char?,
    val groupingSeparator: Char?
) {
    val totalMinorUnits: Long
        get() = Math.addExact(Math.multiplyExact(integerPart, 100L), (fractionPart ?: 0).toLong())
}
```

- **Инварианты:**
  - Сложность парсинга строго $O(N)$, где $N = \text{normalized.length}$.
  - Каждый непробельный символ принадлежит ровно одному токену; спаны токенов не пересекаются и монотонно возрастают.
  - Токены `NUMBER` поддерживают мультивалютные форматы разделителей:
    - СНГ/Европа: `12 345,67` и `12.345,67` -> целая 12345, дробная 67.
    - США/UK: `12,345.67` -> целая 12345, дробная 67.
    - Целые числа: `500` -> целая 500, дробная 0.
  - Токены `KEYWORD` классифицируются строго по `LexiconRepository`.

### Публичный API

#### `Lexer.tokenize`
```kotlin
fun tokenize(text: NormalizedText, lexicon: LexiconRepository): TokenStream
```
- **Предусловия:** `text.normalized.length <= 4096`, `lexicon != null`.
- **Постусловия:**
  - Возвращает иммутабельный `TokenStream`.
  - Токены полностью покрывают значимый текст сообщения без пропусков символов (кроме пробелов).
- **Пошаговое поведение FSM:**
  1. Инициализировать указатель $i = 0$, $L = \text{text.normalized.length}$.
  2. Буфер токенов `tokens = ArrayList<Token>()`.
  3. Цикл `while (i < L)`:
     - $cp = text.normalized.codePointAt(i)$, $charCount = Character.charCount(cp)$.
     - **Состояние 1 (NEWLINE):**
       - Если $cp == '\n'$: добавить `Token(TokenType.NEWLINE, "\n", TextSpan(i, i + 1))`, $i++$.
     - **Состояние 2 (WHITESPACE):**
       - Если `Character.isWhitespace(cp)`: $i += charCount$ (пропуск).
     - **Состояние 3 (CARD_MASK):**
       - Если $cp$ равен `*`, `#`, `x`, `X` или слову `Card`:
         - Проверить наличие шаблона маски: `*1234`, `****1234`, `* 1234`, `Card *1234`.
         - Если совпало: создать токен `CARD_MASK` со спаном всей конструкции, сдвинуть $i$.
     - **Состояние 4 (DATE / TIME / NUMBER):**
       - Если `Character.isDigit(cp)` или ($cp$ в `'+', '-'` и за ним следует цифра):
         - Проверить шаблон даты `DD.MM.YYYY` или `DD/MM/YYYY`: если совпало, сформировать `DATE`.
         - Проверить шаблон времени `HH:MM` или `HH:MM:SS`: если совпало, сформировать `TIME`.
         - Иначе: считывать числовой кластер FSM автоматом:
           - Накапливать цифры, точки, запятые, неразрывные пробелы, апострофы.
           - Завершить кластер при встрече буквы или пробела, за которым не следуют цифры.
           - Распарсить кластер функцией `parseNumberCluster()`.
           - Сформировать токен `NUMBER` со списком `interpretations`.
     - **Состояние 5 (PERCENT / SIGN):**
       - Если $cp == '%'$: создать `PERCENT`, $i++$.
       - Если $cp$ в `'+', '-'`: создать `SIGN`, $i++$.
     - **Состояние 6 (WORD / KEYWORD / CURRENCY):**
       - Если `Character.isLetter(cp)`:
         - Считать непрерывную буквенную последовательность до границы слова.
         - Сверить `keyForm`-эквивалент подстроки с `lexicon.matchCurrency()`:
           - При совпадении -> сформировать токен `CURRENCY`.
         - Сверить с `lexicon.matchKeyword()`:
           - При совпадении -> сформировать токен `KEYWORD(keywordKind = ...)`.
         - Иначе -> сформировать токен `WORD`.
     - **Состояние 7 (PUNCT):**
       - Если символ пунктуации (`:`, `,`, `.`, `;`, `(`, `)`):
         - Сформировать токен `PUNCT`, $i++$.
  4. Обернуть результирующий список в `ImmutableTokenStream(tokens)`.
- **Ошибки:** `IllegalArgumentException` при превышении длины текста.
- **Побочные эффекты:** Отсутствуют.
- **Граничные случаи:**
  - Двойные разделители в числах (`12..34`): разделяются на число `12` и пунктуацию `..34`.
  - Число с двоеточием (`12:34`): распознается как `TIME`, а не два числа.
  - Маска карты без пробела (`*1234`): распознается как `CARD_MASK`.
- **Примеры:**
  1. *Вход:* `"Restituire 245,90 MDL"`  
     *Выход:* `[KEYWORD("Restituire", kind=REFUND), NUMBER("245,90", minor=24590), CURRENCY("MDL")]`.
  2. *Вход:* `"Card *1234 Sold: 12 345,67 MDL"`  
     *Выход:* `[WORD("Card"), CARD_MASK("*1234"), KEYWORD("Sold", kind=BALANCE), PUNCT(":"), NUMBER("12 345,67", minor=1234567), CURRENCY("MDL")]`.
  3. *Вход:* `"Kod podtverzhdeniya: 9845"`  
     *Выход:* `[KEYWORD("Kod", kind=OTP), WORD("podtverzhdeniya"), PUNCT(":"), NUMBER("9845", minor=984500)]`.

### Внутренние функции
- `private fun parseNumberCluster(text: String, span: TextSpan): List<NumberInterpretation>`: эвристический разбор целой/дробной части по типам разделителей.
- `private fun matchCardMaskPrefix(text: String, start: Int): Int?`: проверка шаблона маски карты.
- `private fun matchDateTimePrefix(text: String, start: Int): Pair<TokenType, Int>?`: проверка шаблонов дат и времени.

### Зависимости
- `LexiconRepository`, `NormalizedText`, `TextSpan`, `core-text__universal-inducer.md`.

### Вне скоупа
- Регулярные выражения (полный запрет `java.util.regex`).
- Выбор транзакционной роли для числа.

---

## Модуль: TokenStream  Зона: zone/core-text  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Предоставляет неизменяемый, индексно-оптимизированный поток токенов с доступом по порядковому номеру за $O(1)$ и поиском токена по строковому смещению нормализованного текста за $O(\log M)$ с помощью бинарного поиска по массивам примитивов. НЕ допускает мутации коллекции токенов, фильтрации стоп-слов и модификации спанов.

### Типы данных
```kotlin
package com.example.npc.core.text

interface TokenStream : Iterable<Token> {
    val size: Int
    operator fun get(index: Int): Token
    fun tokenAtOffset(offset: Int): Int?
    fun tokensInRange(startOffset: Int, endOffset: Int): List<Token>
    fun findFirst(predicate: (Token) -> Boolean): Token?
    fun findAll(predicate: (Token) -> Boolean): List<Token>
    fun findTokensByKeyword(kind: KeywordKind): List<Token>
}

class ImmutableTokenStream(
    private val tokens: List<Token>
) : TokenStream {
    private val startOffsets = IntArray(tokens.size) { tokens[it].span.start }
    private val endOffsets = IntArray(tokens.size) { tokens[it].span.end }
    private val keywordIndices: Map<KeywordKind, IntArray> = buildKeywordIndex(tokens)
}
```

- **Инварианты:**
  - Неизменяемость: класс полностью иммутабелен.
  - Монотонность спанов: `startOffsets[i] < endOffsets[i] <= startOffsets[i + 1]` для всех $0 \le i < size - 1$.
  - Быстрый поиск: поиск токена по смещению требует строго $\le \lceil \log_2(M) \rceil$ сравнений.
  - Нулевая аллокация при индексном доступе `get(i)`.

### Публичный API

#### `TokenStream.get`
```kotlin
operator fun get(index: Int): Token
```
- **Предусловия:** `0 <= index < size`.
- **Постусловия:** Возвращает токен за $O(1)$.
- **Ошибки:** `IndexOutOfBoundsException` при некорректном индексе.

#### `TokenStream.tokenAtOffset`
```kotlin
fun tokenAtOffset(offset: Int): Int?
```
- **Предусловия:** `offset >= 0`.
- **Постусловия:** Возвращает индекс токена в потоке, если `span.start <= offset < span.end`, иначе `null`.
- **Пошаговое поведение:**
  1. Выполнить бинарный поиск по массиву `startOffsets` для поиска наибольшего индекса $i$, где `startOffsets[i] <= offset`.
  2. Если такой $i$ найден и `offset < endOffsets[i]`: вернуть $i$.
  3. Иначе вернуть `null` (смещение приходится на межсловный пробел).
- **Сложность:** $O(\log M)$.

#### `TokenStream.findTokensByKeyword`
```kotlin
fun findTokensByKeyword(kind: KeywordKind): List<Token>
```
- **Предусловия:** `kind != null`.
- **Постусловия:** Возвращает предвычисленный список токенов заданной категории ключевых слов за $O(1)$.

#### Примеры:
1. *Вход:* Поток `[Token(0..4, "Card"), Token(5..10, "*1234")]`. Запрос: `tokenAtOffset(2)`.  
   *Выход:* `0` (токен `"Card"`).
2. *Вход:* Тот же поток. Запрос: `tokenAtOffset(4)` (смещение на пробеле между токенами).  
   *Выход:* `null`.
3. *Вход:* Вызов `findTokensByKeyword(KeywordKind.REFUND)` на потоке пуша MAIB.  
   *Выход:* `[Token(span=0..10, text="Restituire", kind=REFUND)]`.

### Внутренние функции
- `private fun binarySearchOffset(offset: Int): Int`: бинарный поиск по массиву `startOffsets`.
- `private fun buildKeywordIndex(tokens: List<Token>): Map<KeywordKind, IntArray>`: прекомпиляция индексов ключевых слов при инициализации.

### Зависимости
- `Token`, `TextSpan`, `KeywordKind`, `core-text__universal-inducer.md`.

### Вне скоупа
- Модификация и удаление токенов.

---

## Модуль: LexiconRepository  Зона: zone/core-text  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Хранит и предоставляет статический версионированный словарь стем, префиксов и валютных маркеров банковского домена (`ru`, `ro`, `en`) в виде сжатого префиксного дерева (Compact Trie), обеспечивая поиск и классификацию ключевых слов за $O(K)$, где $K$ — длина входного слова. НЕ загружает данные из сети, не производит нечеткий поиск (fuzzy matching) и не разрешает региональную неоднозначность валюты «руб» (маркирует ее как `CurrencyMatchResult.Ambiguous`).

### Типы данных
```kotlin
package com.example.npc.core.text

enum class KeywordKind {
    DECLINED,   // отказ, respins, refuz, declin
    REFUND,     // возврат, restitu, rambursa, refund
    CREDIT,     // пополн, зачисл, alimentar, incasar
    TRANSFER,   // перевод, transfer
    DEBIT,      // покупк, оплат, списан, plata
    BALANCE,    // остаток, баланс, sold, disponibil
    OTP,        // код, cod, otp, parola
    PROMO,      // скидк, акци, reducer, promo
    FEE         // комисси, comision, fee
}

sealed interface CurrencyMatchResult {
    data class Exact(val currencyCode: String) : CurrencyMatchResult
    data class Ambiguous(val candidateCodes: List<String>) : CurrencyMatchResult
}

interface LexiconRepository {
    val version: Int
    fun matchKeyword(keyFormWord: String): KeywordKind?
    fun matchCurrency(keyFormWord: String): CurrencyMatchResult?
}
```

- **Инварианты:**
  - `version >= 1`.
  - Потокобезопасность: неизменяемая структура данных (Trie) в памяти JVM.
  - Токены `руб`, `р.`, `рублей` всегда возвращают `CurrencyMatchResult.Ambiguous(listOf("RUP", "RUB"))` (ADR-173, ADR-302).
  - Поиск осуществляется строго по нормализованной строке `keyForm` (нижний регистр, латинизированные гомоглифы, без диакритики).

### Содержимое словаря стем (Канонический реестр)

1. **`DECLINED`**: `otkaz`, `otklon`, `respins`, `refuz`, `declin`, `insufficient`, `nedostatoch`, `ne uspeshn`, `neavtoriz`.
2. **`REFUND`**: `vozvrat`, `restitu`, `rambursa`, `retur`, `refund`, `reversal`, `stornar`, `vozmecsh`.
3. **`CREDIT`**: `popoln`, `zachisl`, `postupl`, `alimentar`, `incasar`, `credit`, `zarplat`, `depuner`.
4. **`TRANSFER`**: `perevod`, `transfer`, `p2p`, `remitent`.
5. **`DEBIT`**: `pokupk`, `oplat`, `spisan`, `plata`, `cumparatur`, `achizit`, `purchase`, `payment`, `extragere`.
6. **`BALANCE`**: `ostat`, `balans`, `sold`, `disponibil`, `dostupn`, `balance`.
7. **`OTP`**: `kod`, `cod`, `code`, `otp`, `parol`, `parola`.
8. **`PROMO`**: `skidk`, `akci`, `reducer`, `promo`, `ofert`, `cashback`, `bonus`.
9. **`FEE`**: `komissi`, `comision`, `fee`, `taxa`.

### Публичный API

#### `LexiconRepository.matchKeyword`
```kotlin
fun matchKeyword(keyFormWord: String): KeywordKind?
```
- **Предусловия:** `keyFormWord` приведен к `keyForm` (нижний регистр, очистка диакритики).
- **Постусловия:** Возвращает `KeywordKind` при совпадении с префиксом/стемой в Trie, иначе `null`.
- **Пошаговое поведение:**
  1. Выполнить спуск по Compact Trie символов `keyFormWord`.
  2. Если достигнут терминальный узел, содержащий `KeywordKind`, вернуть его.
  3. Если слово продолжается после совпадения основы (например, `restituire` содержит префикс `restitu`), вернуть категорию префикса.
  4. При коллизиях возвращается категория с наивысшим приоритетом согласно ADR-185:
     `DECLINED > REFUND > TRANSFER > CREDIT > DEBIT`.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Чистая функция.
- **Граничные случаи:** Слово короче 3 букв -> `null`; слово с пунктуацией на конце -> отсекается до вызова метода.

#### `LexiconRepository.matchCurrency`
```kotlin
fun matchCurrency(keyFormWord: String): CurrencyMatchResult?
```
- **Предусловия:** Строка `keyFormWord`.
- **Постусловия:**
  - `"mdl"`, `"lei"`, `"лей"`, `"леев"` -> `Exact("MDL")`.
  - `"rup"`, `"приднестровских"` -> `Exact("RUP")`.
  - `"usd"`, `"$"`, `"dollar"` -> `Exact("USD")`.
  - `"eur"`, `"€"`, `"euro"` -> `Exact("EUR")`.
  - `"rub"` -> `Exact("RUB")`.
  - `"руб"`, `"р"`, `"rublej"` -> `Ambiguous(["RUP", "RUB"])`.
  - Прочие слова -> `null`.

#### Примеры:
1. *Вход:* `"restituire"` -> *Выход:* `KeywordKind.REFUND`.
2. *Вход:* `"alimentare"` -> *Выход:* `KeywordKind.CREDIT`.
3. *Вход:* `"lei"` -> *Выход:* `CurrencyMatchResult.Exact("MDL")`.
4. *Вход:* `"руб"` -> *Выход:* `CurrencyMatchResult.Ambiguous(["RUP", "RUB"])`.

### Внутренние функции
- `private fun buildCompactTrie(entries: Map<String, KeywordKind>): TrieNode`
- `private fun matchPrefix(node: TrieNode, text: CharSequence): KeywordKind?`

### Зависимости
- `core-text__universal-inducer.md`.

### Вне скоупа
- Сетевые обновления словаря (OTA).
- Полнотекстовый поиск.
