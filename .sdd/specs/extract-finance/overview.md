# Спецификация: zone/extract-finance

## Модуль: `:extract:finance`   Зона: `zone/extract-finance`   Фаза 1: «Хардкод-MVP»   Статус: DRAFT / APPROVED FOR IMPLEMENTATION

---

### 1. Назначение и границы ответственности

Модуль `:extract:finance` является чистой JVM-библиотекой (`kotlin-jvm`) и реализует детерминированное, безопасное и изолированное извлечение финансовых параметров (сумма, валюта, направление операции, маска карты/счета, доступный остаток, мерчант, статус транзакции) из входящих текстовых уведомлений и SMS-сообщений авторизованных банков.

#### 1.1. Главные функциональные обязанности:
1. **Региональная санитизация текста (`RegionalTextSanitizer`):**  
   Предварительная нормализация типографики (NBSP, Narrow NBSP, диакритики румынского языка, удаление мусорных нулевых символов Unicode) и жесткое усечение длины до 1024 символов для защиты от ReDoS.
2. **Линейный O(n) парсинг сумм (`AmountParser`):**  
   Преобразование строковых представлений денежных сумм в целочисленные копейки/центы (`Long`) без использования регулярных выражений с поддержкой региональных десятичных разделителей.
3. **Региональный валютный резолвинг (`BankCurrencyResolver`):**  
   Контекстно-зависимый маппинг валютных токенов. Строгая изоляция Приднестровского рубля (`CurrencyCode.RUP`) от Российского рубля (`RUB`) для банков ПМР. Мультивалютная поддержка для банков Молдовы (`MDL`, `EUR`, `USD`).
4. **Специализированные банковские экстракторы (Domain Extractors):**
   - `ApbNotificationExtractor`: ЗАО «Агропромбанк» (ПМР) — списания, пополнения, переводы «Клевер», резервирования (Hold), отмены/возвраты.
   - `PrisbankNotificationExtractor`: ЗАО «Приднестровский Сбербанк» (ПМР) — микроплатежи за транспорт (4.40 RUP), покупки, зачисления.
   - `MaibNotificationExtractor`: BC «MAIB» S.A. (Молдова) — мультивалютные покупки, трансграничные заказы (Temu), обязательное распознавание отказов («Tranzactie respinsa» / «ОТКЛОНЕН» $\to$ `TransactionStatus.DECLINED`).
   - `BankSmsExtractor`: Fallback-экстрактор для доверенных коротких номеров и банковских SMS (900, Tinkoff/T-Bank, и др.).
5. **Безопасность движка регулярных выражений (ReDoS Guard):**
   - Использование исключительно чистого линейного движка **RE2/J** (`com.google.re2j.Pattern`), гарантирующего сопоставление за время $O(n)$ от длины строки без катастрофического бэктрекинга.
   - Runtime `CircuitBreaker` с лимитом времени 50 мс на одно событие и изоляцией сбоев.
6. **Golden Test Suite:**  
   100% покрытие 23 реальных банковских транзакций из базы телеметрии `event_engine.db` (Poco M7) и защита от ложноположительных срабатываний (кейс спама туроператора InTour и пушей Яндекс.Погоды).

#### 1.2. Границы и запреты:
- **ЗАПРЕЩЕНО:** Использовать классы `android.*` (Context, Log, Handler, Intent и др.).
- **ЗАПРЕЩЕНО:** Использовать стандартный `java.util.regex.*` и `kotlin.text.Regex` для пользовательского/внешнего неконтролируемого текста во избежание неуправляемого нативного бэктрекинга в Android ICU.
- **ЗАПРЕЩЕНО:** Производить прямой ввод-вывод (Room DAO, базы данных, файловую систему, сетевые сокеты).
- **ЗАПРЕЩЕНО:** Зависеть от модуля `:classify:rules` (связывание выполняется на уровне `:app` через `EventProcessingOrchestrator`).
- **ЗАПРЕЩЕНО:** Использовать типы с плавающей точкой (`Float`, `Double`) для хранения или расчёта денежных сумм. Все суммы хранятся строго в минимальных неделимых единицах (`Long` minor units).

---

### 2. Архитектурное окружение и компонентная структура

```
extract/finance/
├── build.gradle.kts                         [dependencies: com.google.re2j:re2j, :core:model]
└── src/
    ├── main/kotlin/com/example/npc/extract/finance/
    │   ├── RegionalTextSanitizer.kt         # Нормализация NBSP, диакритик, усечение 1024
    │   ├── AmountParser.kt                  # Рукописный O(n) парсер сумм в minor units (Long)
    │   ├── BankCurrencyResolver.kt          # Контекстный резолвер RUP / MDL / EUR / USD / RUB
    │   ├── IsolatedExtractorRunner.kt       # Обёртка запуска с таймаутом и обработкой ошибок
    │   ├── CircuitBreaker.kt                # Предохранитель (budget 50ms, threshold 3, cooldown 10m)
    │   ├── model/
    │   │   ├── TransactionStatus.kt         # SUCCESS, DECLINED, PENDING
    │   │   └── ExtractorMetrics.kt          # Метрики исполнения для health-мониторинга
    │   ├── apb/
    │   │   └── ApbNotificationExtractor.kt  # Парсер Агропромбанк ПМР (com.apb.mobile)
    │   ├── prisbank/
    │   │   └── PrisbankNotificationExtractor.kt # Парсер Приднестровский Сбербанк
    │   ├── maib/
    │   │   └── MaibNotificationExtractor.kt # Парсер MAIB Молдова (md.maib.maibank)
    │   └── sms/
    │       └── BankSmsExtractor.kt          # Fallback парсер банковских SMS (900, Tinkoff)
    └── test/kotlin/com/example/npc/extract/finance/
        ├── RegionalTextSanitizerTest.kt
        ├── AmountParserTest.kt
        ├── BankCurrencyResolverTest.kt
        ├── CircuitBreakerTest.kt
        ├── AdversarialRegexTest.kt          # Стресс-тесты ReDoS (5000 символов, вложенные скобки)
        └── GoldenBankTransactionsTest.kt    # 23 реальные транзакции из event_engine.db
```

---

### 3. Контракты предметной области (`:core:model`)

Модуль реализует контракты, определённые в `:core:model`:

```kotlin
package com.example.npc.core.model.extract

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.Money
import java.time.Instant

/**
 * Входные данные для доменного экстрактора.
 */
data class ExtractorInput(
    val text: String,
    val senderOrTitle: String?,
    val postedAt: Instant,
    val currencyResolver: CurrencyResolver
)

/**
 * Результат финансовой экстракции.
 */
sealed interface ExtractionResult {
    /** Успешно извлечённая подтверждённая финансовая транзакция. */
    data class Success(
        val direction: Direction,
        val amount: Money,
        val balance: Money?,
        val merchant: String?,
        val accountMask: String?
    ) : ExtractionResult

    /**
     * Транзакция отклонена банком (недостаточно средств, лимит, блокировка).
     * ВНИМАНИЕ: Не должна списывать баланс или создавать расход в аналитике!
     */
    data class Declined(
        val reason: String,
        val amount: Money?,
        val merchant: String?,
        val accountMask: String?
    ) : ExtractionResult

    /** Уведомление банковское, но не содержит транзакции (2FA/OTP код, баланс, маркетинг). */
    data object NotApplicable : ExtractionResult

    /** Сбой разбора формата при авторизованном банке. */
    data class Failed(val reason: String) : ExtractionResult
}

/**
 * Базовый контракт финансового экстрактора.
 */
interface FinanceExtractor {
    val id: String           // Уникальный строковый идентификатор ("apb.notification", "maib.notification")
    val version: Int         // Версия парсера (инкремент триггерит перепарсинг)
    fun extract(input: ExtractorInput): ExtractionResult
}

/**
 * Резолвер строковых токенов валют в закрытый перечень CurrencyCode.
 */
interface CurrencyResolver {
    fun resolve(token: String): CurrencyCode?
}
```

---

### 4. Санитизация текста (`RegionalTextSanitizer`)

Банковские пуши и SMS в Восточной Европе и Приднестровье содержат специфические символы пробелов (разделители групп разрядов в суммах) и региональные диакритические знаки. Перед сопоставлением с регулярными выражениями текст проходит обязательную детерминированную санитизацию за $O(n)$.

```kotlin
package com.example.npc.extract.finance

object RegionalTextSanitizer {

    private const val MAX_INPUT_LENGTH = 1024

    /**
     * Выполняет нормализацию текста:
     * 1. Жесткое усечение длины до 1024 символов (защита от ReDoS).
     * 2. Удаление невидимых служебных символов Unicode (Zero-Width Space, Joiners, BOM, Soft Hyphen).
     * 3. Замена всех разновидностей неразрывных пробелов (NBSP \u00A0, Narrow NBSP \u202F, Thin Space \u2009)
     *    и множественных пробелов/переводов строк на одиночный стандартный ASCII-пробел (0x20).
     * 4. Каноническая нормализация румынских диакритик: приведение седили (ş/ţ) к стандартным запятым снизу (ș/ț),
     *    а также генерация нормализованного представления для устойчивого сопоставления.
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""

        val input = if (raw.length > MAX_INPUT_LENGTH) raw.substring(0, MAX_INPUT_LENGTH) else raw
        val sb = java.lang.StringBuilder(input.length)
        var lastWasSpace = false

        for (i in 0 until input.length) {
            val ch = input[i]

            // 1. Фильтрация невидимых и мусорных символов Unicode
            if (isZeroWidthOrGarbage(ch)) {
                continue
            }

            // 2. Унификация пробельных символов
            if (isCustomWhitespace(ch)) {
                if (!lastWasSpace && sb.isNotEmpty()) {
                    sb.append(' ')
                    lastWasSpace = true
                }
                continue
            }

            // 3. Нормализация румынских диакритик (Cedilla -> Comma-below)
            val normalizedChar = normalizeRomanianDiacritic(ch)
            sb.append(normalizedChar)
            lastWasSpace = false
        }

        // Удаление хвостового пробела
        while (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
            sb.setLength(sb.length - 1)
        }

        return sb.toString()
    }

    private fun isZeroWidthOrGarbage(ch: Char): Boolean = when (ch) {
        '\u200B', // Zero-Width Space
        '\u200C', // Zero-Width Non-Joiner
        '\u200D', // Zero-Width Joiner
        '\uFEFF', // Zero-Width No-Break Space (BOM)
        '\u00AD'  // Soft Hyphen
        -> true
        else -> false
    }

    private fun isCustomWhitespace(ch: Char): Boolean = when (ch) {
        ' ',
        '\t',
        '\r',
        '\n',
        '\u00A0', // Non-Breaking Space (NBSP)
        '\u202F', // Narrow No-Break Space (NNBSP)
        '\u2009', // Thin Space
        '\u2002', // En Space
        '\u2003', // Em Space
        '\u3000'  // Ideographic Space
        -> true
        else -> false
    }

    /**
     * Румынские буквы с седилями (ş/ţ) исторически часто подменяются в Android-шрифтах
     * на турецкие символы (U+015E, U+015F, U+0162, U+0163).
     * Официальный стандарт Румынии и Молдовы использует буквы с запятой снизу (U+0218, U+0219, U+021A, U+021B).
     */
    private fun normalizeRomanianDiacritic(ch: Char): Char = when (ch) {
        '\u015E' -> '\u0218' // 'Ş' (Latin S with cedilla) -> 'Ș' (Latin S with comma below)
        '\u015F' -> '\u0219' // 'ş' (Latin s with cedilla) -> 'ș' (Latin s with comma below)
        '\u0162' -> '\u021A' // 'Ţ' (Latin T with cedilla) -> 'Ț' (Latin T with comma below)
        '\u0163' -> '\u021B' // 'ţ' (Latin t with cedilla) -> 'ț' (Latin t with comma below)
        else -> ch
    }
}
```

---

### 5. Линейный O(n) парсер сумм (`AmountParser`)

Использование регулярных выражений для парсинга чисел влечёт риски переполнения стека и неоднозначности разделителей. Класс `AmountParser` разбирает строковые суммы строго за один проход за $O(n)$ времени и $O(1)$ памяти.

#### 5.1. Алгоритмические требования:
1. Поддержка обоих вариантов десятичного разделителя: запятая (`,`) и точка (`.`).
2. Автоматическое определение разделителя групп разрядов (пробел, точка, запятая). Если после разделителя ровно 2 цифры в конце строки — это десятичный разделитель копеек/центов. Если после разделителя ровно 3 цифры — это разделитель тысяч (`1.250 RUP` или `1,250 MDL`).
3. Защита от переполнения: суммарная длина строкового числа не может превышать 32 символов; целая часть ограничена 15 цифрами ($< 10^{15}$).
4. Возврат целочисленного значения в неделимых минимальных единицах (`Long` minor units, например: `125.50` $\to$ `12550L`).

```kotlin
package com.example.npc.extract.finance

object AmountParser {

    private const val MAX_RAW_LENGTH = 32
    private const val MAX_INTEGER_DIGITS = 15

    /**
     * Разбирает строковое представление суммы в целочисленное количество минорных единиц (копеек/центов).
     *
     * @param raw Строка с суммой (например: "5,47", "50.00", "1 250,50", "4.40")
     * @param minorDigits Количество знаков дробной части для целевой валюты (для RUP/MDL/EUR/RUB/USD = 2)
     * @return Сумма в копейках/центах (Long) или null при невалидном формате
     */
    fun parseMinor(raw: String, minorDigits: Int = 2): Long? {
        if (raw.isEmpty() || raw.length > MAX_RAW_LENGTH) return null

        // 1. Очистка: оставляем только цифры, запятые и точки
        val clean = StringBuilder(raw.length)
        for (i in 0 until raw.length) {
            val ch = raw[i]
            if (ch.isDigit() || ch == ',' || ch == '.') {
                clean.append(ch)
            }
        }

        if (clean.isEmpty() || !clean[0].isDigit()) return null

        // 2. Поиск последнего десятичного разделителя
        var lastSepIndex = -1
        for (i in clean.length - 1 downTo 0) {
            val ch = clean[i]
            if (ch == ',' || ch == '.') {
                lastSepIndex = i
                break
            }
        }

        val fracLen = if (lastSepIndex >= 0) clean.length - lastSepIndex - 1 else 0
        val isDecimalSeparator = lastSepIndex >= 0 && fracLen in 1..minorDigits

        // 3. Формирование целой части
        val intBuilder = StringBuilder(MAX_INTEGER_DIGITS)
        val intEndIndex = if (isDecimalSeparator) lastSepIndex else clean.length

        for (i in 0 until intEndIndex) {
            val ch = clean[i]
            if (ch.isDigit()) {
                intBuilder.append(ch)
            }
        }

        if (intBuilder.isEmpty() || intBuilder.length > MAX_INTEGER_DIGITS) return null

        // 4. Формирование дробной части
        val fracBuilder = StringBuilder(minorDigits)
        if (isDecimalSeparator) {
            for (i in lastSepIndex + 1 until clean.length) {
                val ch = clean[i]
                if (ch.isDigit()) {
                    fracBuilder.append(ch)
                }
            }
        }

        // Дополнение нулями справа (например: "4.4" -> "4.40")
        while (fracBuilder.length < minorDigits) {
            fracBuilder.append('0')
        }

        val combined = intBuilder.toString() + fracBuilder.toString()
        return combined.toLongOrNull()
    }
}
```

---

### 6. Региональный валютный резолвер (`BankCurrencyResolver`)

Приднестровский рубль (`RUP`) отсутствует в международном классификаторе ISO-4217, однако является основной валютой финансовых транзакций пользователя на Poco M7.

#### 6.1. Правила контекстного сопоставления:
1. **Контекст банков ПМР (`bank.apb`, `bank.prisbank`):**  
   Токены `"руб"`, `"руб."`, `"р."`, `"rup"`, `"rub"` **однозначно резолвятся как `CurrencyCode.RUP`**. Подмена на `RUB` (Российский рубль) категорически запрещена!
2. **Контекст банков Молдовы (`bank.maib`):**  
   - `"mdl"`, `"лей"`, `"lei"`, `"l"` $\to$ `CurrencyCode.MDL`
   - `"eur"`, `"евро"`, `"€"` $\to$ `CurrencyCode.EUR`
   - `"usd"`, `"долл"`, `"долларов"`, `"$"`, `"$"` $\to$ `CurrencyCode.USD`
   - `"rub"`, `"руб"` $\to$ `CurrencyCode.RUB`
3. **Контекст банковских SMS (`bank.sms`):**  
   - Если отправитель — `900`, `Tinkoff`, `T-Bank`, `VTB`, `Alfa-Bank` $\to$ токен `"руб"` резолвится как `CurrencyCode.RUB`.
   - Если отправитель — `APB`, `Agroprombank`, `Prisbank`, `Sberbank` $\to$ токен `"руб"` резолвится как `CurrencyCode.RUP`.

```kotlin
package com.example.npc.extract.finance

import com.example.npc.core.model.extract.CurrencyResolver
import com.example.npc.core.model.finance.CurrencyCode
import java.util.Locale

class BankCurrencyResolver(
    private val bankProfile: String,
    private val defaultCurrency: CurrencyCode
) : CurrencyResolver {

    override fun resolve(token: String): CurrencyCode? {
        val clean = token.trim().lowercase(Locale.ROOT)
            .removeSuffix(".")
            .removeSuffix(",")

        return when (bankProfile) {
            "bank.apb", "bank.prisbank" -> resolvePmr(clean)
            "bank.maib" -> resolveMoldova(clean)
            "bank.sms.russia" -> resolveRussia(clean)
            else -> resolveGeneric(clean)
        } ?: defaultCurrency
    }

    private fun resolvePmr(token: String): CurrencyCode? = when (token) {
        "rup", "руб", "р", "рублей", "рубля" -> CurrencyCode.RUP
        "usd", "$", "долл" -> CurrencyCode.USD
        "eur", "€", "евро" -> CurrencyCode.EUR
        "mdl", "лей" -> CurrencyCode.MDL
        else -> null
    }

    private fun resolveMoldova(token: String): CurrencyCode? = when (token) {
        "mdl", "лей", "lei", "l" -> CurrencyCode.MDL
        "eur", "€", "евро" -> CurrencyCode.EUR
        "usd", "$", "долл", "долларов" -> CurrencyCode.USD
        "rup" -> CurrencyCode.RUP
        "rub", "руб" -> CurrencyCode.RUB
        else -> null
    }

    private fun resolveRussia(token: String): CurrencyCode? = when (token) {
        "rub", "руб", "р", "₽" -> CurrencyCode.RUB
        "usd", "$" -> CurrencyCode.USD
        "eur", "€" -> CurrencyCode.EUR
        else -> null
    }

    private fun resolveGeneric(token: String): CurrencyCode? = when (token) {
        "rup" -> CurrencyCode.RUP
        "mdl", "lei" -> CurrencyCode.MDL
        "rub", "₽" -> CurrencyCode.RUB
        "eur", "€" -> CurrencyCode.EUR
        "usd", "$" -> CurrencyCode.USD
        else -> CurrencyCode.ofOrNull(token)
    }
}
```

---

### 7. Спецификации банковских парсеров (Domain Extractors)

Все парсеры используют исключительно движок `com.google.re2j.Pattern` и `com.google.re2j.Matcher`.

#### 7.1. `ApbNotificationExtractor` (Агропромбанк ПМР)
- **Идентификатор:** `id = "apb.notification"`, `version = 1`.
- **Источники:**
  - Android-пакеты: `com.apb.mobile`, `com.agroprombank.*`, `ru.pridnestrovian.agroprombank`.
  - SMS-отправители: `"APB"`, `"Agroprombank"`.
- **Дефолтная валюта:** `CurrencyCode.RUP`.

##### Шаблоны регулярных выражений (RE2/J):

1. **Покупка / Оплата по карте (Debit/Expense):**
   ```regex
   ^(?:Покупка|Оплата) по карте (?P<mask>\S+) на сумму (?P<amount>[\d\s,.]+) (?P<curr>[A-Za-zА-Яа-я.]+)(?: Баланс (?P<bal>[\d\s,.]+) (?P<balcurr>[A-Za-zА-Яа-я.]+))?
   ```
   - Направление: `Direction.DEBIT`
   - Реальные примеры из базы:
     - `Покупка по карте **5576 на сумму 5,47 RUP Баланс 422,01 RUP`
     - `Покупка по карте **5576 на сумму 15,15 RUP Баланс 406,86 RUP`
     - `Оплата по карте **5576 на сумму 25,75 RUP Баланс 412,64 RUP`
     - `Покупка по карте **5576 на сумму 22,40 RUP Баланс 390,24 RUP`
     - `Покупка по карте **5576 на сумму 11,96 RUP Баланс 338,28 RUP`

2. **Резервирование по карте (Hold / Pre-auth):**
   ```regex
   ^Резервирование по карте (?P<mask>\S+) на сумму (?P<amount>[\d\s,.]+) (?P<curr>[A-Za-zА-Яа-я.]+)(?: Баланс (?P<bal>[\d\s,.]+) (?P<balcurr>[A-Za-zА-Яа-я.]+))?
   ```
   - Направление: `Direction.DEBIT`
   - Реальные примеры из базы:
     - `Резервирование по карте **5576 на сумму 50,00 RUP Баланс 388,39 RUP`
     - `Резервирование по карте **5576 на сумму 25,75 RUP Баланс 412,64 RUP`

3. **Пополнение счёта по карте Клевер (Credit/Income):**
   ```regex
   ^Пополнение счета по карте Клевер (?P<mask>\S+),\s*(?P<amount>[\d\s,.]+)\s*(?P<curr>[A-Za-zА-Яа-я.]+)
   ```
   - Направление: `Direction.CREDIT`
   - Реальные примеры из базы:
     - `Пополнение счета по карте Клевер 910401******5576, 50.00 RUP`

4. **Входящий P2P-перевод (Credit/Income):**
   ```regex
   ^Перевод на карту (?P<mask>\S+) от (?P<sender>.+?) зачислен,\s*(?P<amount>[\d\s,.]+)\s*(?P<curr>[A-Za-zА-Яа-я.]+)
   ```
   - Направление: `Direction.CREDIT`
   - Мерчант/отправитель: извлекается группа `sender` (например: `"Артур М."`)
   - Реальные примеры из базы:
     - `Перевод на карту *5576 от Артур М. зачислен, 50,00 RUP`

5. **Исходящий перевод по карте (Transfer):**
   ```regex
   ^Перевод по карте (?P<mask>\S+) на сумму (?P<amount>[\d\s,.]+) (?P<curr>[A-Za-zА-Яа-я.]+)(?: Баланс (?P<bal>[\d\s,.]+) (?P<balcurr>[A-Za-zА-Яа-я.]+))?
   ```
   - Направление: `Direction.TRANSFER`
   - Реальные примеры из базы:
     - `Перевод по карте **5576 на сумму 40,00 RUP Баланс 350,24 RUP`

6. **Отмена операции / Возврат (Reversal/Refund):**
   ```regex
   ^Отмена операции по карте (?P<mask>\S+) на сумму (?P<amount>[\d\s,.]+)\s*(?P<curr>[A-Za-zА-Яа-я.]+)\.?(?:\s*Баланс:?\s*(?P<bal>[\d\s,.]+) (?P<balcurr>[A-Za-zА-Яа-я.]+))?
   ```
   - Направление: `Direction.CREDIT` (восстановление баланса карты)
   - Реальные примеры из базы:
     - `Отмена операции по карте ****5576 на сумму 50,00 RUP. Баланс: 438,39 RUP`

---

#### 7.2. `PrisbankNotificationExtractor` (Приднестровский Сбербанк)
- **Идентификатор:** `id = "prisbank.notification"`, `version = 1`.
- **Источники:**
  - Android-пакеты: `com.prisbank.app`, `com.sberbank.pmr.*`, `ru.pridnestrovian.sberbank`.
  - SMS-отправители: `"Sberbank"`, `"PRB"`, `"PRISBANK"`.
- **Дефолтная валюта:** `CurrencyCode.RUP`.

##### Специфика формата уведомлений:
В реальной базе `event_engine.db` пуши Сбербанка ПМР приходят в компактном виде:
- `title`: `💸 Деньги списаны`
- `text`: `4.40 RUP` (стоимость проезда в общественном транспорте ПМР), `7.00 RUP`, `50.00 RUP`, `12.80 RUP`

##### Шаблоны регулярных выражений (RE2/J):

1. **Компактное списание в шторке (Debit/Expense):**
   - Условие на `title`: содержит маркеры `"списаны"`, `"оплата"`, `"покупка"`, `"деньги списаны"`
   - Текст (RE2/J):
     ```regex
     ^(?P<amount>[\d\s,.]+)\s*(?P<curr>[A-Za-zА-Яа-я.]+)
     ```
   - Направление: `Direction.DEBIT`
   - Реальные примеры из базы:
     - Title: `💸 Деньги списаны`, Text: `4.40 RUP` $\to$ minor = `440L`, currency = `RUP`
     - Title: `💸 Деньги списаны`, Text: `7.00 RUP` $\to$ minor = `700L`, currency = `RUP`
     - Title: `💸 Деньги списаны`, Text: `50.00 RUP` $\to$ minor = `5000L`, currency = `RUP`
     - Title: `💸 Деньги списаны`, Text: `22.10 RUP` $\to$ minor = `2210L`, currency = `RUP`
     - Title: `💸 Деньги списаны`, Text: `26.24 RUP` $\to$ minor = `2624L`, currency = `RUP`

2. **Расширенный формат SMS / пуша:**
   ```regex
   ^(?:Оплата|Списание|Покупка):?\s*(?P<amount>[\d\s,.]+)\s*(?P<curr>[A-Za-zА-Яа-я.]+)(?:\.\s*Карта:?\s*(?P<mask>\S+))?(?:\.\s*Остаток:?\s*(?P<bal>[\d\s,.]+)\s*(?P<balcurr>[A-Za-zА-Яа-я.]+))?
   ```
   - Направление: `Direction.DEBIT`

3. **Зачисление / Пополнение счёта:**
   ```regex
   ^(?:Зачисление|Пополнение):?\s*(?P<amount>[\d\s,.]+)\s*(?P<curr>[A-Za-zА-Яа-я.]+)(?:\.\s*Карта:?\s*(?P<mask>\S+))?(?:\.\s*Остаток:?\s*(?P<bal>[\d\s,.]+)\s*(?P<balcurr>[A-Za-zА-Яа-я.]+))?
   ```
   - Направление: `Direction.CREDIT`

---

#### 7.3. `MaibNotificationExtractor` (MAIB Молдова)
- **Идентификатор:** `id = "maib.notification"`, `version = 1`.
- **Источники:**
  - Android-пакеты: `md.maib.maibank`, `md.maib.*`.
  - SMS-отправители: `"maib"`, `"MAIB"`.
- **Дефолтная валюта:** `CurrencyCode.MDL`.
- **Поддерживаемые валюты:** `CurrencyCode.MDL`, `CurrencyCode.EUR`, `CurrencyCode.USD`.

##### КРИТИЧЕСКОЕ ТРЕБОВАНИЕ: Распознавание отказов (Declined Transactions Gate)
В базе `event_engine.db` зафиксирован случай отклоненной оплаты:
- `title`: `Транзакция отклонена`
- `text`: `Платеж с карты ***6159 на сумму 664 MDL в Temu.com ОТКЛОНЕН из-за недостаточности средств. Пополните карту и попробуйте снова.`
Предыдущая наивная версия Фазы 0 ошибочно записала это событие как расход `664 MDL`.  
В Фазе 1 экстрактор **ОБЯЗАН** первым делом проверить маркеры отклонения:
- Маркеры: `"отклонен"`, `"отклонена"`, `"respins"`, `"respinsa"`, `"refuzat"`, `"refuzata"`, `"esuat"`, `"esuata"`, `"declined"`, `"failed"`.
- При совпадении возвращается: `ExtractionResult.Declined(...)`. Такой результат **НЕ порождает финансовую проводку списания**.

##### Шаблоны регулярных выражений (RE2/J):

1. **Отклонённая операция (Русский язык):**
   ```regex
   ^Платеж с карты (?P<mask>\S+) на сумму (?P<amount>[\d\s,.]+) (?P<curr>[A-Za-z]+) в (?P<merchant>.+?) ОТКЛОНЕН(?:\s+из-за\s+(?P<reason>.+))?
   ```
   - Результат: `ExtractionResult.Declined(reason = "из-за недостаточности средств", ...)`

2. **Отклонённая операция (Румынский язык):**
   ```regex
   ^Tranzactie respinsa: plata cu cardul (?P<mask>\S+) in suma de (?P<amount>[\d\s,.]+) (?P<curr>[A-Za-z]+) la (?P<merchant>.+?)(?:\.\s*Motiv:?\s*(?P<reason>.+))?
   ```
   - Результат: `ExtractionResult.Declined(...)`

3. **Успешная оплата (Русский язык):**
   ```regex
   ^Оплата на сумму (?P<amount>[\d\s,.]+) (?P<curr>[A-Za-z]+) в (?P<merchant>.+?) с карты (?P<mask>\S+) прошла успешно\.\s*Доступный остаток:\s*(?P<bal>[\d\s,.]+) (?P<balcurr>[A-Za-z]+)\.?
   ```
   - Направление: `Direction.DEBIT`
   - Мультивалютность: Сумма списания может быть в `MDL`, а доступный остаток по счету — в `EUR`!
   - Реальные примеры из базы:
     - `Оплата на сумму 664 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 66.83 EUR.`  
       $\to$ `amount = 66400L MDL`, `balance = 6683L EUR`, `merchant = "Temu.com"`, `accountMask = "***1555"`
     - `Оплата на сумму 364 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 48.63 EUR.`  
       $\to$ `amount = 36400L MDL`, `balance = 4863L EUR`, `merchant = "Temu.com"`, `accountMask = "***1555"`
     - `Оплата на сумму 541 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 21.58 EUR.`  
       $\to$ `amount = 54100L MDL`, `balance = 2158L EUR`, `merchant = "Temu.com"`, `accountMask = "***1555"`

4. **Успешная оплата (Румынский язык):**
   ```regex
   ^Plata in suma de (?P<amount>[\d\s,.]+) (?P<curr>[A-Za-z]+) la (?P<merchant>.+?) cu cardul (?P<mask>\S+) a fost efectuata cu succes\.\s*Sold disponibil:\s*(?P<bal>[\d\s,.]+) (?P<balcurr>[A-Za-z]+)\.?
   ```
   - Направление: `Direction.DEBIT`

---

#### 7.4. `BankSmsExtractor` (Fallback банковские SMS)
- **Идентификатор:** `id = "bank.sms"`, `version = 1`.
- **Источники:**
  - SMS-приложения: `com.google.android.apps.messaging`, `com.android.mms`.
  - Отправители (Allowlist): `"900"`, `"Tinkoff"`, `"T-Bank"`, `"VTB"`, `"Alfa-Bank"`, `"Raiffeisen"`.
- **Фильтрация 2FA / OTP:**
  Если сообщение содержит маркеры одноразовых кодов (`"код подтверждения"`, `"пароль"`, `"никому не сообщайте"`, `"auth code"`, `"verification code"`), экстрактор немедленно возвращает `ExtractionResult.NotApplicable`.

##### Шаблоны регулярных выражений (RE2/J):
1. **Сбербанк РФ (`900`):**
   ```regex
   ^(?:Сбербанк(?:\s+Онлайн)?\.?\s*)?(?P<type>Покупка|Оплата|Списание|Зачисление|Перевод)\s+(?P<amount>[\d\s,.]+)\s*(?P<curr>руб|RUB)\s*(?P<mask>карта\s+\*\*\d{4})?(?:\s+(?P<merchant>[^.]+?))?\.\s*(?:Остаток|Баланс):\s*(?P<bal>[\d\s,.]+)\s*(?P<balcurr>руб|RUB)?
   ```
2. **Тинькофф / Т-Банк:**
   ```regex
   ^(?P<type>Покупка|Оплата|Перевод)\.\s*Карта\s*(?P<mask>\*\S+)\.\s*(?P<amount>[\d\s,.]+)\s*(?P<curr>[A-Za-zА-Яа-я.]+)\.\s*(?P<merchant>[^.]+?)\.\s*Доступно\s*(?P<bal>[\d\s,.]+)\s*(?P<balcurr>[A-Za-zА-Яа-я.]+)
   ```

---

### 8. Безопасность движка регулярных выражений (ReDoS Guard)

#### 8.1. Выбор RE2/J (Thompson NFA Linear Engine)
В стандартном Android SDK класс `java.util.regex.Pattern` использует backtracking-движок ICU. На вредоносных или специфических строках время сопоставления может расти экспоненциально $O(2^n)$, вызывая Application Not Responding (ANR).  
В библиотеке **RE2/J** (`com.google.re2j`):
- Регулярные выражения компилируются в недетерминированные конечные автоматы (NFA) с алгоритмом Томпсона.
- Гарантированное время выполнения: строго **$O(n)$ от длины входящей строки**, где $n \le 1024$.
- Гарантированный расход памяти: $O(m)$ от количества состояний автомата.

> [!IMPORTANT]
> RE2/J не поддерживает обратные ссылки (`\1`), а также lookahead/lookbehind проверки (`(?=...)`, `(?!...)`). Все паттерны специфицированы с учётом этих ограничений и используют именованные группы захвата `(?P<name>...)`.

#### 8.2. Предохранитель времени выполнения (`CircuitBreaker`)

```kotlin
package com.example.npc.extract.finance

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val cooldownDurationMs: Long = 10 * 60 * 1000L // 10 минут
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val state = AtomicReference(State.CLOSED)
    private val consecutiveFailures = AtomicInteger(0)
    private val lastOpenedTimestamp = AtomicLong(0L)

    fun canExecute(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return when (state.get()) {
            State.CLOSED -> true
            State.OPEN -> {
                if (currentTimeMs - lastOpenedTimestamp.get() > cooldownDurationMs) {
                    state.compareAndSet(State.OPEN, State.HALF_OPEN)
                    true
                } else {
                    false
                }
            }
            State.HALF_OPEN -> true
        }
    }

    fun recordSuccess() {
        consecutiveFailures.set(0)
        state.set(State.CLOSED)
    }

    fun recordFailure(currentTimeMs: Long = System.currentTimeMillis()) {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= failureThreshold) {
            state.set(State.OPEN)
            lastOpenedTimestamp.set(currentTimeMs)
        }
    }
}
```

#### 8.3. Изолированный исполнитель (`IsolatedExtractorRunner`)
- Ограничение времени: 50 мс на экстракцию одного события.
- Защита от сбоев: перехват любых `Throwable` с фиксацией в `ExtractionResult.Failed`.

```kotlin
package com.example.npc.extract.finance

import com.example.npc.core.model.extract.ExtractionResult
import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import kotlinx.coroutines.withTimeoutOrNull

class IsolatedExtractorRunner(
    private val timeoutMs: Long = 50L,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker()
) {
    suspend fun run(
        extractor: FinanceExtractor,
        input: ExtractorInput
    ): ExtractionResult {
        if (!circuitBreaker.canExecute()) {
            return ExtractionResult.Failed("Circuit breaker OPEN for extractor: ${extractor.id}")
        }

        val sanitizedText = RegionalTextSanitizer.sanitize(input.text)
        val sanitizedInput = input.copy(text = sanitizedText)

        return try {
            val result = withTimeoutOrNull(timeoutMs) {
                extractor.extract(sanitizedInput)
            }

            if (result == null) {
                circuitBreaker.recordFailure()
                ExtractionResult.Failed("Execution timeout exceeded (${timeoutMs} ms) for ${extractor.id}")
            } else {
                circuitBreaker.recordSuccess()
                result
            }
        } catch (t: Throwable) {
            circuitBreaker.recordFailure()
            ExtractionResult.Failed("Extractor exception: ${t.message}")
        }
    }
}
```

---

### 9. Тестовые сценарии Golden Test Suite

#### 9.1. 23 реальные транзакции из базы `event_engine.db` (Poco M7)

| № | Пакет источника | Входящий текст уведомления / Title | Ожидаемое направление | Ожидаемая сумма | Ожидаемый баланс | Маска карты | Мерчант / Примечание |
|---|---|---|:---:|:---:|:---:|:---:|---|
| **1** | `com.prisbank.app` | Title: `💸 Деньги списаны`<br>Text: `4.40 RUP` | `DEBIT` | `440L RUP` | `null` | `null` | Общественный транспорт ПМР |
| **2** | `com.apb.mobile` | `Покупка по карте **5576 на сумму 5,47 RUP Баланс 422,01 RUP` | `DEBIT` | `547L RUP` | `42201L RUP` | `**5576` | Покупка в супермаркете |
| **3** | `com.apb.mobile` | `Покупка по карте **5576 на сумму 15,15 RUP Баланс 406,86 RUP` | `DEBIT` | `1515L RUP` | `40686L RUP` | `**5576` | Покупка в аптеке |
| **4** | `com.apb.mobile` | `Пополнение счета по карте Клевер 910401******5576, 50.00 RUP` | `CREDIT` | `5000L RUP` | `null` | `910401******5576` | Зачисление зарплаты/пополнение |
| **5** | `md.maib.maibank` | Title: `Транзакция отклонена`<br>Text: `Платеж с карты ***6159 на сумму 664 MDL в Temu.com ОТКЛОНЕН из-за недостаточности средств. Пополните карту и попробуйте снова.` | — | `66400L MDL` | `null` | `***6159` | **`ExtractionResult.Declined`** (Temu, 0 списаний) |
| **6** | `md.maib.maibank` | `Оплата на сумму 664 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 66.83 EUR.` | `DEBIT` | `66400L MDL` | `6683L EUR` | `***1555` | `Temu.com` (мультивалютный остаток) |
| **7** | `com.apb.mobile` | `Резервирование по карте **5576 на сумму 50,00 RUP Баланс 388,39 RUP` | `DEBIT` | `5000L RUP` | `38839L RUP` | `**5576` | Pre-auth холдирование |
| **8** | `com.apb.mobile` | `Отмена операции по карте ****5576 на сумму 50,00 RUP. Баланс: 438,39 RUP` | `CREDIT` | `5000L RUP` | `43839L RUP` | `****5576` | Возврат / Reversal |
| **9** | `com.apb.mobile` | `Резервирование по карте **5576 на сумму 25,75 RUP Баланс 412,64 RUP` | `DEBIT` | `2575L RUP` | `41264L RUP` | `**5576` | Pre-auth холдирование |
| **10** | `com.apb.mobile` | `Оплата по карте **5576 на сумму 25,75 RUP Баланс 412,64 RUP` | `DEBIT` | `2575L RUP` | `41264L RUP` | `**5576` | Подтверждение списания |
| **11** | `com.apb.mobile` | `Покупка по карте **5576 на сумму 22,40 RUP Баланс 390,24 RUP` | `DEBIT` | `2240L RUP` | `39024L RUP` | `**5576` | Покупка в магазине |
| **12** | `com.apb.mobile` | `Перевод на карту *5576 от Артур М. зачислен, 50,00 RUP` | `CREDIT` | `5000L RUP` | `null` | `*5576` | Входящий перевод от `Артур М.` |
| **13** | `com.prisbank.app` | Title: `💸 Деньги списаны`<br>Text: `7.00 RUP` | `DEBIT` | `700L RUP` | `null` | `null` | Покупка |
| **14** | `com.prisbank.app` | Title: `💸 Деньги списаны`<br>Text: `5.47 RUP` | `DEBIT` | `547L RUP` | `null` | `null` | Покупка |
| **15** | `com.apb.mobile` | `Перевод по карте **5576 на сумму 40,00 RUP Баланс 350,24 RUP` | `TRANSFER` | `4000L RUP` | `35024L RUP` | `**5576` | Исходящий перевод P2P |
| **16** | `com.apb.mobile` | `Покупка по карте **5576 на сумму 11,96 RUP Баланс 338,28 RUP` | `DEBIT` | `1196L RUP` | `33828L RUP` | `**5576` | Покупка |
| **17** | `md.maib.maibank` | `Оплата на сумму 364 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 48.63 EUR.` | `DEBIT` | `36400L MDL` | `4863L EUR` | `***1555` | `Temu.com` |
| **18** | `com.prisbank.app` | Title: `💸 Деньги списаны`<br>Text: `50.00 RUP` | `DEBIT` | `5000L RUP` | `null` | `null` | Покупка |
| **19** | `md.maib.maibank` | `Оплата на сумму 541 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 21.58 EUR.` | `DEBIT` | `54100L MDL` | `2158L EUR` | `***1555` | `Temu.com` |
| **20** | `com.prisbank.app` | Title: `💸 Деньги списаны`<br>Text: `12.80 RUP` | `DEBIT` | `1280L RUP` | `null` | `null` | Покупка |
| **21** | `com.prisbank.app` | Title: `💸 Деньги списаны`<br>Text: `22.10 RUP` | `DEBIT` | `2210L RUP` | `null` | `null` | Покупка |
| **22** | `com.prisbank.app` | Title: `💸 Деньги списаны`<br>Text: `26.24 RUP` | `DEBIT` | `2624L RUP` | `null` | `null` | Покупка |
| **23** | `com.prisbank.app` | Title: `💸 Деньги списаны`<br>Text: `11.00 RUP` | `DEBIT` | `1100L RUP` | `null` | `null` | Покупка |

---

#### 9.2. Негативные сценарии (Adversarial & False Positive Prevention)

1. **Спам турагентства InTour (Telegram/AyuGram):**
   - Вход: `InTour Тур агентство ПМР: 499 евро, Турция из Кишинева, отель 5 звезд...`
   - Ожидание: `PackageGate` блокирует вызов экстракторов. При прямом вызове экстрактора: `ExtractionResult.NotApplicable`. Ложные транзакции: 0.
2. **Прогноз Яндекс.Погоды:**
   - Вход: `Яндекс Погода: +10°C, ощущается как +8°C`
   - Ожидание: `ExtractionResult.NotApplicable`. Никаких доходов 10.0 RUB.
3. **Одноразовый SMS-код 2FA:**
   - Вход: `Сбербанк Онлайн. Пароль для входа в приложение: 8492. Никому не сообщайте!`
   - Ожидание: `ExtractionResult.NotApplicable`.
4. **Смена PIN-кода:**
   - Вход: `ПИН-код для карты **5576 успешно изменен.`
   - Ожидание: `ExtractionResult.NotApplicable`.
5. **ReDoS Adversarial Fuzzing Test:**
   - Вход: Строка из 5000 символов `"9"` с вложенными скобками и запятыми (`"((((9,".repeat(500) + ")".repeat(500)`).
   - Ожидание: Время выполнения $< 5$ мс благодаря усечению до 1024 символов в `RegionalTextSanitizer` и движку `RE2/J`. Исключения по таймауту отсутствуют.

---

### 10. Definition of Done (DoD) зоны `zone/extract-finance`

1. **[DoD-1] Изоляция и чистота JVM:** Модуль `:extract:finance` компилируется без единого вызова Android SDK и Room.
2. **[DoD-2] Санитизация типографики:** Модуль `RegionalTextSanitizer` корректно очищает NBSP (`\u00A0`), Narrow NBSP (`\u202F`), Zero-Width символы и нормализует румынские диакритики за время $O(n)$.
3. **[DoD-3] Парсинг сумм в Long:** Модуль `AmountParser` проходит 100% тестов на дробные и целые суммы с запятыми и точками без переполнения.
4. **[DoD-4] 100% совпадение на Golden Dataset:** Все 23 реальные транзакции из `event_engine.db` (11 APB, 8 Сбербанк ПМР, 4 MAIB) парсятся с точностью 100% (совпадение сумм, валют, направлений и масок карт).
5. **[DoD-5] Защита от ложных списаний (Declined Gate):** Отклонённые транзакции (`md.maib.maibank` Temu 664 MDL) возвращают статус `ExtractionResult.Declined` и не создают проводок.
6. **[DoD-6] ReDoS & Resilience:** Все регулярные выражения скомпилированы в `RE2/J`. Стресс-тест `AdversarialRegexTest` выполняется без зависаний ($< 10$ мс на патологический ввод). `CircuitBreaker` корректно размыкает цепь при превышении лимита в 50 мс.
