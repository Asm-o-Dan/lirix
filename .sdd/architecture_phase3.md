# Архитектура Фазы 3 — Universal Extractor, Dynamic Template Induction, Financial Analytics & Diagnostics

> **Статус:** DRAFT ARCHITECTURE (GATE 2)  
> **Консультант:** Claude Opus 5.5  
> **Целевая платформа:** Android 14+ (HyperOS / Poco M7, 60 FPS, ограниченный CPU/GPU)  
> **Базовые зависимости:** Kotlin 2.0+, Jetpack Compose, Room (SQLCipher), Coroutines/Flow, RE2/J, kotlinx-collections-immutable

---

## 0. Контекст и инцидент на реальном девайсе

### 0.1 Описание инцидента
На тестовом устройстве Poco M7 было зафиксировано входящее пуш-уведомление от приложения **MAIB** (`md.maib.maibank`):
```text
Restituire 245,90 MDL
TEMU.COM
Card *1234
Sold: 12 345,67 MDL
```
- **Классификатор (Фаза 1):** Уверенно классифицировал уведомление как `FINANCE` с вероятностью **98%**.
- **Экстрактор (Фаза 1/2):** Встроенный `MaibNotificationExtractor` вернул `ParsedFinanceResult.NotApplicable`, в результате чего событие было сохранено в Room **без финансового блока**.
- **Причина сбоя:** Статический экстрактор `MaibNotificationExtractor` содержал исключительно регулярные выражения для операций `DEBIT` (покупка) и `DECLINED` (отказ). Операции пополнения/возврата (`CREDIT`, `REFUND`: *Restituire*, *Alimentare*, *Пополнение*) не были заложены в статический regex.

### 0.2 Уроки и требования к Фазе 3
1. **Никакой банковский пуш не должен терять финансовые данные.** Если статический экстрактор не распознал структуру пуша, конвейер обязан передать текст в **Universal Slot-Based Extractor**, работающий без хардкодных банковских регулярок.
2. **Система должна самообучаться (Dynamic Template Induction).** Нераспознанный или скорректированный пользователем пуш должен автоматически индуцировать безопасное регулярное выражение (RE2/J), компилироваться через `PipelineCompiler` и сохраняться в базу как активный шаблон.
3. **One-Tap Human-in-the-Loop.** Пользователь в интерфейсе `EventDetailsDialog` должен в один клик подтверждать или корректировать найденные слоты и сохранять шаблон в рантайм.
4. **Финансовая аналитика и прозрачность рантайма.** Агрегация мультивалютных транзакций (MDL, RUP, USD, EUR, RUB) и диагностический экран с мониторингом очередей, предохранителей `NodeCircuitBreaker` и трейсов `TraceRing`.

---

## 1. Архитектурные принципы и ADR (Decisions)

| # | Решение | Альтернатива | Обоснование |
|---|---|---|---|
| **ADR-301** | Единый рукописный линейный лексер `Lexer` O(N) в `:core:text`, общий для Universal Extractor и Inducer | Regex-токенизация | Шаблон индуцируется над тем же потоком токенов, по которому сопоставляется. Исключает рассинхронизацию между нормализацией, индуктором и рантайм-матчингом. |
| **ADR-302** | Сумма валидна **только в связке с валютой** (currency-bound constraint) | Любое число считать кандидатом на сумму | Исключает ложные срабатывания на SMS-кодах подтверждения (OTP), номерах заказов, телефонах и скидках. |
| **ADR-303** | Динамические шаблоны хранятся в изолированном банке **TemplateBank** (Room v4) со своей версией `bankVersion`. В `PipelineDefinition` конвейера фиксируется узел `extract.template_bank` | Новая ревизия всего пайплайна на каждый шаблон | Предотвращает комбинаторный взрыв версий конвейера; обеспечивает поштучный карантин шаблонов при сбоях; гарантирует детерминированный Replay по паре `(pipelineRevision, bankVersion)`. |
| **ADR-304** | Синхронная горячая подмена поколения через `AtomicReference<RuntimeGeneration(pipeline, bank)>` | Два независимых AtomicReference | Исключает race condition, когда событие обрабатывается несовместимой комбинацией ревизии конвейера и версии банка шаблонов. |
| **ADR-305** | Мультивалютность без автоконвертации: `Money(minorUnits: Long, currency: CurrencyCode)`. Аналитика агрегирует по валютам раздельно | Конвертация в базовую валюту по курсу | RUP (приднестровский рубль) не котируется на внешних валютных рынках; отсутствие сетевых вызовов в оффлайн-приложении. |
| **ADR-306** | Возврат (Refund) моделируется как `TransactionType.CREDIT` с флагом `isRefund = true`. В аналитике по умолчанию уменьшает расход | Считать возврат доходом | Возврат с TEMU не является заработком пользователя, он компенсирует ранее совершенную покупку. В UI предусмотрен переключатель режима учета. |
| **ADR-307** | Шаблоны от пользователя переходят сразу в `ACTIVE`. Авто-индуцированные шаблоны без подтверждения переходят в `SHADOW` | Сразу активировать авто-шаблоны | Эвристика не должна молча искажать финансовые агрегаты. Промоушен из `SHADOW` в `ACTIVE` происходит после 5 подтвержденных бессбойных совпадений. |

---

## 2. Граф модулей и зоны ответственности

### 2.1 Схема модульных зависимостей

```
                               ┌──────────────┐
                               │     :app     │  DI wiring, навигация
                               └──────┬───────┘
           ┌──────────────────────────┼─────────────────────────┐
           ▼                          ▼                         ▼
   ┌──────────────┐          ┌─────────────────┐       ┌─────────────────┐
   │:feature:     │          │:feature:        │       │:feature:        │
   │editor [NEW]  │          │analytics [NEW]  │       │diagnostics [NEW]│
   └──────┬───────┘          └────────┬────────┘       └────────┬────────┘
          │                           │                         │
          └───────────────────────────┼─────────────────────────┘
                                      ▼
                             ┌────────────────┐
                             │    :domain     │  UseCases, MVI Store
                             └────────┬───────┘
           ┌──────────────────────────┼─────────────────────────┐
           ▼                          ▼                         ▼
┌────────────────────┐     ┌─────────────────────┐   ┌───────────────────────┐
│ :data:storage      │     │  :pipeline:runtime  │   │  :pipeline:compiler   │
│ (Room v4, Gateway) │     │  (Generation, Probe)│   │  (compileTemplate)    │
└──────────┬─────────┘     └──────────┬──────────┘   └──────────┬────────────┘
           │                          │                         │
           ▼                          ▼                         ▼
┌────────────────────┐     ┌─────────────────────┐   ┌───────────────────────┐
│ :extract:universal │     │     :induction      │   │ :pipeline:nodes-api   │
│ (Solver, Gates)    │     │  (TemplateBuilder)  │   │ (SPI)                 │
└──────────┬─────────┘     └──────────┬──────────┘   └───────────────────────┘
           │                          │
           └────────────┬─────────────┘
                        ▼
               ┌────────────────┐
               │   :core:text   │  (Чистый Kotlin: TextNormalizer, Lexer, Tokens)
               └────────┬───────┘
                        ▼
               ┌────────────────┐
               │  :core:model   │  (FinancialTransaction, Money, Provenance)
               └────────────────┘
```

### 2.2 Зоны ответственности
1. **Зона A: Text Core (`:core:text`)**
   - Нормализация строк (NFKC, диакритика, гомоглифы латиницы/кириллицы, выравнивание пробелов).
   - Двунаправленный `OffsetMap` для проецирования спанов между нормализованным и оригинальным текстом.
   - Линейный детерминированный конечный автомат `Lexer` без regex.
   - Загрузчик мультиязычного лексикона ключевых слов (`ru`, `ro`, `en`).
2. **Зона B: Universal Slot-Based Extractor (`:extract:universal`)**
   - Извлечение кандидатов: `AmountCandidate` (жестко связанный с валютой), `CardMaskCandidate`, `BalanceAnchorCandidate`.
   - Вычисление векторов признаков и оценка уверенности слотов.
   - Иерархический резолвер типов операций (`DECLINED > REFUND > TRANSFER > CREDIT > DEBIT`).
   - `RoleAssignmentSolver`: комбинаторный перебор распределения ролей `TX_AMOUNT`, `BALANCE`, `FEE`, `OTHER`.
   - Многоуровневый `SafetyGate`: Veto на OTP-коды и промо-акции, учет априорного банковского профиля.
   - Исполнитель узла `extract.universal` для конвейера рантайма.
3. **Зона C: Dynamic Template Induction (`:induction`)**
   - `Segmenter`: разбиение текста на литералы, слоты и обобщаемые переменные.
   - Библиотека безопасных квантифицированных фрагментов `SafeFragments` (RE2/J-совместимых).
   - `TemplateBuilder`: генерация регулярного выражения с правилом правой ограниченности и отсечением рекламного хвоста.
   - `TemplateLint`: валидация сложности, длины, вложенности и специфичности шаблона в `:pipeline:compiler`.
   - Валидация: Round-trip верификация и Replay-тестирование на историческом корпусе.
4. **Зона D: Хранение и Рантайм (`:data:storage`, `:pipeline:runtime`)**
   - Room v4: таблицы `dynamic_template`, `template_stats`, `template_bank_version`, `template_bank_membership`.
   - Расширение `FinancialTransactionEntity` метаданными происхождения (`provenance`, `confidence`, `isRefund`).
   - `TemplateBankManager`: потокобезопасная машина состояний (DRAFT, VALIDATED, ACTIVE, SHADOW, QUARANTINED, DISABLED).
   - Атомарная замена `RuntimeGeneration` в рантайме.
   - `OrchestratorProbe`: lock-free seqlock снэпшот трейсов `TraceRing`, метрик и состояния предохранителей `NodeCircuitBreaker`.
   - `StorageGateway`: высокопроизводительные SQL-агрегации сумм по валютам и периодам.
5. **Зона E: Пользовательский интерфейс и HITL (`:feature:*`)**
   - `One-Tap Template Editor`: интерактивная токенная сетка, визуальный выбор ролей, живая валидация, сохранение и Undo.
   - `Financial Analytics UI`: раздельные мультивалютные карточки, переключатель режима возвратов, графики Canvas, список транзакций с Paging 3.
   - `Orchestrator Diagnostics Screen`: мониторинг конвейера в реальном времени, сброс предохранителей, инспекция сбоев.

---

## 3. Архитектура Universal Slot-Based Extractor

### 3.1 Поток обработки и интеграция в конвейер Фазы 2

В конвейере Фазы 2 (`PipelineDefinition`) `Universal Slot-Based Extractor` встраивается как **гарантированный фоллбэк** для финансовых уведомлений после статических и динамических экстракторов:

```kotlin
// Условие срабатывания фоллбэка в DSL конвейера:
val universalFallbackGate = AndCondition(
    conditions = listOf(
        CategoryCondition(expectedCategory = Category.FINANCE),
        PackageWhitelistCondition(whitelist = BankingWhitelist.PACKAGES),
        RegisterNullCondition(register = RegisterId.R_TX) // Только если предыдущие экстракторы не извлекли транзакцию
    )
)
```

Цепочка стадий конвейера:
```
classify ──► [category == FINANCE]
                 │
                 ├──► extract.template_bank(tier = OVERRIDE)
                 │         │ (если сработал -> emit)
                 │         ▼ (miss)
                 ├──► extract.static(MAIB | APB | Prisbank)
                 │         │ (если сработал -> emit)
                 │         ▼ (miss)
                 ├──► extract.template_bank(tier = FALLBACK)
                 │         │ (если сработал -> emit)
                 │         ▼ (miss)
                 └──► extract.universal (Gate: category == FINANCE && packageName in Whitelist)
                           │
                           ▼
                    ExtractionResult ──► ACCEPT (>= 0.80) ──► R_TX (CONFIRMED_AUTO)
                                         SUGGEST (0.50..0.80) ─► R_TX (SUGGESTED) + UI Banner
                                         REJECT (< 0.50) ───► R_TX empty + trace
```

#### Банковский Whitelist (`BankingWhitelist.kt`):
- `md.maib.maibank` (MAIB Bank)
- `com.apb.mobile` (Агропромбанк)
- `com.prisbank.app` (Приднестровский Сбербанк)
- `md.victoriabank.vbapp` (Victoriabank)
- `md.micb.mobile` (Moldindconbank)
- SMS-шлюзы региональных банков (числовые и буквенные SenderId)

### 3.2 Лексер и токенизация
- Алгоритм: детерминированный конечный автомат по кодовым точкам Unicode без использования regex. Сложность строго $O(N)$.
- **NUMBER:** Собирает числовой кластер (`12 345,67`, `1.234,56`, `245.9`). Формирует список гипотез нормализации (целая часть + дробная часть).
- **CURRENCY:** Идентифицирует явные коды (`MDL`, `RUP`, `USD`, `EUR`, `RUB`) и символы/сокращения (`lei`, `лей`, `руб`, `р.`, `$`, `€`).
  - *Неоднозначность:* Токен `руб` помечается как `CurrencyAmbiguous(RUB|RUP)`. Разрешение выполняется через `SourceProfile`: для источников ПМР (`com.prisbank.app`, `com.apb.mobile`, SMS от местных шлюзов) разрешается в `RUP`, для остальных — в `RUB`.
- **KEYWORD(kind):** Сопоставление со словарем стем:
  - `DECLINED`: отказ, отклон, respins, refuz, declin, insufficient, недостаточно.
  - `REFUND`: возврат, restitu, rambursa, retur, refund, reversal.
  - `CREDIT`: пополн, зачисл, поступл, alimentar, incasar, credit.
  - `TRANSFER`: перевод, transfer.
  - `DEBIT`: покупк, оплат, списан, plata, cumparatur, achizit, purchase, payment.
  - `BALANCE`: остаток, баланс, sold, disponibil, bal, balance.
  - `OTP`: код, cod, code, otp, parola, пароль.
  - `PROMO`: скидк, акци, reducer, promo, oferta, cashback до, распродаж.

### 3.3 Иерархия разрешения типов операций (OpType Resolution)
При обнаружении нескольких ключевых слов применяется строгая иерархия специфичности:
$$\text{DECLINED} \succ \text{REFUND} \succ \text{TRANSFER} \succ \text{CREDIT} \succ \text{DEBIT}$$
- Пуш «Отказ в покупке» классифицируется как `DECLINED`.
- Пуш «Restituire ... plata cu cardul» (возврат оплаты) классифицируется как `CREDIT` с флагом `isRefund = true`. **Это ликвидирует инцидент с возвратом TEMU.**

### 3.4 Role Assignment Solver
В реальном пуше присутствует от 1 до 4 валютных чисел. Вместо тяжелых внешних солверов используется оптимизированный перебор всех перестановок ролей:
- Ограничение: ровно один `TX_AMOUNT`.
- Ограничение: не более одного `BALANCE` (обязателен якорь баланса слева или отдельная строка).
- Целевая функция: максимизация $\sum \ln(P(\text{role}_i))$. Перебор занимает менее 0.05 мс.

### 3.5 Защитные шлюзы (Safety Gate)
- **OTP Veto:** Присутствие ключевого слова OTP и изолированного 4-8 значного числа блокирует авто-подтверждение транзакции (статус принудительно снижается до `REJECT` или `SUGGEST` с флагом `isOtpContext`).
- **Promo Veto:** Наличие скидочных маркеров (`%`, слова «до», «распродажа») без маски карты и остатка отсекает рекламные сообщения.
- **Пороги вердикта:**
  - $\text{Score} \ge 0.80$: `ACCEPT` (автоматическая запись в базу).
  - $0.50 \le \text{Score} < 0.80$: `SUGGEST` (запись со статусом ожидания подтверждения).
  - $\text{Score} < 0.50$: `REJECT` (игнорирование, запись метрики `finance_without_payload`).

---

## 4. Архитектура Dynamic Template Induction

### 4.1 Концепция индукции
Когда Universal Extractor или пользователь в UI определил позиции слотов в сообщении, система синтезирует минимально-жадное, безопасное регулярное выражение RE2/J:

```
[Исходный текст] ──► [Segmenter] ──► [SafeFragments] ──► [Right-Bounded Regex]
       ▲                                                        │
       └─────────── [Round-Trip Verification] ◀─────────────────┘
                                │ PASS
                                ▼
                   [PipelineCompiler Lint]
                                │ PASS
                                ▼
                     [Replay Safety Check]
                                │ PASS
                                ▼
                     [TemplateBank Storage]
```

### 4.2 Библиотека безопасных фрагментов (SafeFragments)
Запрещено построение произвольных квантификаторов `.*` или `.+`. Используются строгие ограниченные фрагменты:
```kotlin
object SafeFragments {
    const val AMOUNT   = """[+\-]?[0-9]{1,3}(?:[ .,'][0-9]{3}){0,4}(?:[.,][0-9]{1,2})?|[+\-]?[0-9]{1,9}(?:[.,][0-9]{1,2})?"""
    const val CURRENCY = """MDL|RUP|USD|EUR|RUB|lei|лей|леев|руб\.?|р\.|\$|€"""
    const val CARD4    = """[0-9]{4}"""
    const val MERCHANT = """[^\n]{2,64}?""" // Обязательно ленивый и справа ограниченный
    const val DATE     = """[0-9]{2}[./\-][0-9]{2}(?:[./\-][0-9]{2,4})?"""
    const val TIME     = """[0-9]{2}:[0-9]{2}(?::[0-9]{2})?"""
    const val WS       = """\s+"""
    const val WS_OPT   = """\s*"""
}
```

### 4.2.1 Алгоритм замены найденных слотов на именованные группы RE2/J

Синтез регулярного выражения выполняется по строго детерминированному пошаговому алгоритму:

1. **Нормализация и построение последовательности токенов:**
   - Входной текст нормализуется (NFKC, замена пробелов на `\u0020`, приведение диакритики в `keyForm`).
   - Полученный `TokenStream` покрывает текст от 0 до $L$.
2. **Отображение слотов на токены:**
   - Каждому размеченному слоту сопоставляется непрерывный диапазон индексов токенов $[T_{start}, T_{end}]$.
3. **Замена слотов на именованные группы (`named capture groups`):**
   - Слот `AMOUNT` заменяется на именованную группу: `(?P<amount>${SafeFragments.AMOUNT})`.
   - Слот `CURRENCY` заменяется на: `(?P<curr>${SafeFragments.CURRENCY})`.
   - Слот `CARD_MASK` (например, `*1234`) разделяется на якорный префикс литерала и группу: `\*(?P<card>${SafeFragments.CARD4})`.
   - Слот `BALANCE` заменяется на: `(?P<bal>${SafeFragments.AMOUNT})`.
   - Слот `BALANCE_CURRENCY` заменяется на: `(?P<balcurr>${SafeFragments.CURRENCY})`.
   - Слот `MERCHANT` заменяется на ленивую правую-ограниченную группу: `(?P<merchant>${SafeFragments.MERCHANT})`.
4. **Обобщение не-слотовых переменных (`VARIABLE`):**
   - Токены `DATE` вне слотов заменяются на `(?:${SafeFragments.DATE})` (без захвата).
   - Токены `TIME` вне слотов заменяются на `(?:${SafeFragments.TIME})` (без захвата).
5. **Экранирование фиксированного контекста (`LITERAL`):**
   - Все промежуточные токены-слова экранируются через `Pattern.quote` (`\Qслово\E`).
   - Пробельные разделители заменяются на `\s+` (или `\s*` около пунктуации).
6. **Правая ограниченность:**
   - Если за слотом `MERCHANT` следует литерал (например, слово *Card*), формируется связка: `(?P<merchant>[^\n]{2,64}?)\s+\Qcard\E`.
   - Если слот находится в конце строки, добавляется ограничитель `(?:\n|$)`.
7. **Формирование результирующего RE2/J паттерна:**
   - Добавляется глобальный флаг регистронезависимости `(?i)`.
   - Пример для инцидента MAIB TEMU:
     ```regex
     (?i)\Qrestituire\E\s+(?P<amount>[+\-]?[0-9]{1,3}(?:[ .,'][0-9]{3}){0,4}(?:[.,][0-9]{1,2})?|[+\-]?[0-9]{1,9}(?:[.,][0-9]{1,2})?)\s*(?P<curr>MDL|RUP|USD|EUR|RUB|lei|лей|леев|руб\.?|р\.|\$|€)\s+(?P<merchant>[^\n]{2,64}?)\s+\Qcard\E\s+\*(?P<card>[0-9]{4})\s+\Qsold\E\s*:\s*(?P<bal>[+\-]?[0-9]{1,3}(?:[ .,'][0-9]{3}){0,4}(?:[.,][0-9]{1,2})?|[+\-]?[0-9]{1,9}(?:[.,][0-9]{1,2})?)\s*(?P<balcurr>MDL|RUP|USD|EUR|RUB|lei|лей|леев|руб\.?|р\.|\$|€)
     ```
### 4.3 Инварианты индукции и компиляции
1. **Правило правой ограниченности:** Любой переменный слот (`MERCHANT`) обязан завершаться фиксированным строковым литералом или концом строки `(?:\n|$)`.
2. **Семантические константы:** Если тип операции однозначно определен литералом (например, слово *Restituire*), значение `opType = CREDIT, isRefund = true` помещается в константы шаблона (`constantsJson`), а не захватывается динамической группой.
3. **Лимиты сложности компилятора (`TemplateLint`):**
   - Длина паттерна: не более 1024 символов.
   - Именованные группы захвата: не более 12, строго из разрешенного словаря слотов (`amount`, `curr`, `card`, `bal`, `balcurr`, `merchant`).
   - Специфичность: не менее 2 литеральных блоков суммарной длиной от 8 символов.
4. **Round-Trip инвариант:** Скомпилированный шаблон запускается на исходном сообщении. Результат извлечения обязан побайтово совпасть с целевой разметкой. При любом расхождении шаблон бракуется.
5. **Replay регрессионный тест:** Прогон шаблона по выборке из 500 последних событий:
   - Негативные события (не-финансы, спам, OTP): строго 0 ложных срабатываний.
   - Конфликты с существующими шаблонами: детектируются и отображаются пользователю в виде диффа.

---

## 5. Хранение и жизненный цикл в Room v4

### 5.1 Схема таблиц БД

```sql
-- Динамические шаблоны
CREATE TABLE dynamic_template (
    id TEXT PRIMARY KEY NOT NULL,
    sourceKey TEXT NOT NULL,
    tier TEXT NOT NULL,               -- 'OVERRIDE' | 'FALLBACK'
    origin TEXT NOT NULL,             -- 'USER' | 'AUTO' | 'AUTO_REFINED'
    state TEXT NOT NULL,              -- 'DRAFT' | 'ACTIVE' | 'SHADOW' | 'QUARANTINED' | 'DISABLED' | 'SUPERSEDED'
    priority INTEGER NOT NULL,
    pattern TEXT NOT NULL,
    bindingsJson TEXT NOT NULL,
    constantsJson TEXT NOT NULL,
    amountFormatJson TEXT NOT NULL,
    specVersion INTEGER NOT NULL,
    compilerVersion INTEGER NOT NULL,
    canonicalHash TEXT NOT NULL,
    specificity REAL NOT NULL,
    parentTemplateId TEXT,
    sampleEventId TEXT,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    stateReason TEXT
);

CREATE INDEX idx_dyn_tmpl_src_state ON dynamic_template(sourceKey, state);
CREATE UNIQUE INDEX idx_dyn_tmpl_hash ON dynamic_template(canonicalHash);

-- Статистика исполнения шаблонов
CREATE TABLE template_stats (
    templateId TEXT PRIMARY KEY NOT NULL,
    hits INTEGER NOT NULL DEFAULT 0,
    parseFailures INTEGER NOT NULL DEFAULT 0,
    userCorrections INTEGER NOT NULL DEFAULT 0,
    shadowAgreements INTEGER NOT NULL DEFAULT 0,
    shadowDisagreements INTEGER NOT NULL DEFAULT 0,
    lastHitAt INTEGER,
    FOREIGN KEY(templateId) REFERENCES dynamic_template(id) ON DELETE CASCADE
);

-- Версионирование банка шаблонов
CREATE TABLE template_bank_version (
    version INTEGER PRIMARY KEY NOT NULL,
    parentVersion INTEGER,
    membershipHash TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    cause TEXT NOT NULL
);

CREATE TABLE template_bank_membership (
    version INTEGER NOT NULL,
    templateId TEXT NOT NULL,
    PRIMARY KEY(version, templateId)
);
```

### 5.2 Модификация FinancialTransactionEntity
Добавляются поля провенанса для сквозной аудируемости:
```kotlin
val extractorKind: ExtractorKind, // STATIC | TEMPLATE | UNIVERSAL | MANUAL
val templateId: String?,
val pipelineRevision: Long,
val bankVersion: Long,
val confidence: Float,
val status: TxStatus,             // CONFIRMED_AUTO | SUGGESTED | USER_CONFIRMED | USER_EDITED
val isRefund: Boolean
```

### 5.3 Интеграция в рантайм: RuntimeGeneration и горячая подмена
Для исключения рассинхронизации рантайм оперирует единым контейнером поколения:
```kotlin
data class RuntimeGeneration(
    val pipeline: CompiledPipeline,
    val bank: CompiledTemplateBank,
    val generationId: Long
)
```
Подмена происходит атомарно через `AtomicReference<RuntimeGeneration>.updateAndGet { ... }` без остановки входящих потоков NLS/SMS.

---

## 6. Пользовательский интерфейс и наблюдаемость

### 6.1 One-Tap Template Editor (Human-in-the-Loop)
- **Точка входа:** Кнопка «Создать шаблон» / «Изменить шаблон» в `EventDetailsDialog`.
- **Токенная сетка:** Вместо ручного выделения символов текст сообщения разбивается на визуальные интерактивные чипы-токены.
- **Назначение ролей:** Тап по токену вызывает селектор: `[Сумма] [Валюта] [Карта] [Баланс] [Мерчант] [Литерал] [Игнорировать]`.
- **Живая реактивная валидация:** При любом изменении во Flow с задержкой `debounce(150ms)` на фоновом пуле выполняется индукция, проверка компилятором и Replay по локальной базе.
- **One-Tap Save:** Если Universal Extractor правильно предсказал слоты, пользователю достаточно нажать одну кнопку **«Сохранить и активировать»**.

### 6.2 Financial Analytics UI
- **Дашборд:** Отображение агрегированных сумм раздельно по валютам (MDL, RUP, USD, EUR, RUB).
- **Фильтрация:** Выбор периодов (Сегодня, Неделя, Месяц, Произвольный диапазон), фильтр направления (Расход, Доход, Все).
- **Управление возвратами:** Переключатель «Уменьшать расходы» (по умолчанию) / «Учитывать как доход».
- **Графики:** Отрисовка динамики через легковесный аппаратный `Canvas` (без тяжелых сторонних библиотек) с сохранением 60 FPS на Poco M7.
- **Список транзакций:** Paging 3 с фильтром по статусам (`CONFIRMED` vs `SUGGESTED`).

### 6.3 Orchestrator Diagnostics Screen
- **Снэпшот рантайма (`OrchestratorProbe`):** Чтение кольцевого буфера `TraceRing` (128 записей) без блокировки горячего пути через lock-free seqlock алгоритм.
- **Метрики очередей:** Текущий размер очереди `Channel`, пропускная способность (событий в минуту), количество сброшенных событий.
- **Состояние предохранителей (`NodeCircuitBreaker`):** Визуализация статусов `CLOSED`, `OPEN`, `HALF_OPEN` для каждого узла с возможностью ручного сброса (Reset Breaker).
- **Алерты качества:** Счётчик `financeWithoutPayload` (пуши, классифицированные как Finance, но не имеющие финансовых данных).

---

## 7. Бюджеты производительности и инварианты для Poco M7

| Операция | Бюджет (p95) | Пояснение / Механизм контроля |
|---|---|---|
| Universal Extractor | $\le 3.0$ мс | Однопроходный лексер O(N), комбинаторный solver $\le 256$ итераций |
| TemplateBank Match | $\le 1.0$ мс | Быстрый префильтр по `requiredLiterals` до вызова RE2 |
| Редактор: валидация и Replay | $\le 100$ мс | Replay по 500 последним событиям на выделенном потоке |
| UI Frame Time (Analytics / Diagnostics) | $\le 16.6$ мс (60 FPS) | Строгая иммутабельность, Canvas-графики, отсутствие аллокаций в Recomposition |
| Hot-swap подмена поколения | $\le 0.1$ мс | Атомарная ссылка volatile, компиляция выполняется асинхронно заранее |
| Память под банк шаблонов | $\le 2$ МБ | До 500 активных скомпилированных шаблонов RE2 в пуле |

---

## 8. План миграции и безопасность

1. **Миграция БД (v3 → v4):** Аддитивная миграция Room `MIGRATION_3_4`. Добавление таблиц динамических шаблонов и новых колонок в `financial_transactions`. Существующие транзакции Фазы 1 и 2 получают провенанс `STATIC` со 100% сохранением целостности.
2. **ReDoS защита:** Отсутствие обратного поиска (backtracking) в RE2/J и запрет бесконечных квантификаторов гарантируют линейное время сопоставления $O(M)$ относительно длины строки при любых пользовательских паттернах.
3. **Изоляция сбоев:** При возникновении исключения или тайм-аута в динамическом шаблоне срабатывает `NodeCircuitBreaker`, шаблон автоматически переводится в `QUARANTINED`, а конвейер откатывается на `Universal Extractor`.
4. **Приватность (PII):** В трейсы диагностики и снэпшоты попадают только хеши и идентификаторы. Полные тексты уведомлений не покидают зашифрованное хранилище SQLCipher.
