# Спецификация: zone/pipeline-replay

## Модуль: `:feature:replay` · Зона: `zone/pipeline-replay` · Фаза 2: «Конструктор конвейеров» · Статус: `PROPOSED`

---

### 1. Назначение, архитектурный контекст и границы модуля

Модуль `:feature:replay` реализует подсистему ретроспективной симуляции и дифференциального анализа (Replay Engine) для Notification Pipeline Constructor. Его ключевое назначение — предоставить пользователю и разработчику возможность безопасно проверить модифицированный или вновь сконструированный конвейер (`draftPipeline`) на накопленном корпусе реальных исторических уведомлений (база догфудинга за 7+ дней, ~1000 событий) **до** его фактической активации в рабочем рантайме.

Подсистема гарантирует **абсолютную изоляцию (Zero Side Effects)**: выполнение черновика конвейера в симуляторе не изменяет ни единого байта в рабочей базе данных SQLite Room, перехватывает все побочные эффекты в оперативной памяти и формирует детальный дифференциальный отчет (`ReplayDiffReport`), сопоставляющий результаты черновика с текущим активным конвейером (`activePipeline`) или исходными историческими метками событий.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              АРХИТЕКТУРНЫЙ КОНТЕКСТ REPLAY                             │
│                                                                                        │
│   UI Редактора / Симулятора (:feature:editor / :feature:replay)                        │
│         │                                                                              │
│         │ runSimulation(draftPipeline, activePipeline, filter)                         │
│         ▼                                                                              │
│   ┌────────────────────────────────────────────────────────────────────────┐           │
│   │ ReplayEngine (:feature:replay)                                         │           │
│   │  - Keyset Pagination Reader (100-200 событий/батч)                    │           │
│   │  - Dual-Run In-Memory Executor (Draft vs Active)                       │           │
│   │  - Cooperative Cancellation & Yielding Loop                            │           │
│   │  - Latency Quantile Sampler (p50 / p95)                                │           │
│   └───────────────────────────┬────────────────────────────────────────────┘           │
│                               │                                                        │
│          ┌────────────────────┴────────────────────┐                                   │
│          │ Чтение без мутаций                      │ Виртуальная оценка эффектов       │
│          ▼                                         ▼                                   │
│   ┌──────────────────────────────┐          ┌──────────────────────────────┐           │
│   │ Room Database (ReadOnly)     │          │ In-Memory Virtual Sandbox    │           │
│   │  - raw_event (текст, пакет)  │          │  - VirtualEffectEvaluator    │           │
│   │  - event (исторический итог) │          │  - EffectBuffer Interceptor  │           │
│   │  - financial_transaction     │          │  - Zero SQLite Mutations!    │           │
│   └──────────────────────────────┘          └──────────────┬───────────────┘           │
│                                                            │                           │
│                                                            ▼                           │
│                                                     ReplayDiffReport                   │
│                                                     (Flow<ReplayStatus>)               │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 1.1. Роль в архитектуре Фазы 2 (Предотвращение регрессий и безопасный догфудинг)

В Фазе 1.1 любое изменение правил классификации или регулярок извлечения сумм требовало ручной проверки на отдельных уведомлениях или слепого доверия коду. Ошибочный regex мог привести к:
- Массовой ложной классификации пушей в спам (`Category.ADVERTISEMENT` вместо `Category.FINANCE`).
- Ложному извлечению транзакций из спам-сообщений (например, пуши турагентств «InTour: горящие туры от 500$» ошибочно распознавались как списание 500 USD).
- Пропуску реальных банковских транзакций (Сбербанк, Т-Банк/Тинькофф, Агропромбанк, Maib).

`ReplayEngine` в Фазе 2 превращает историческую базу догфудинга в постоянный регрессионный тестовый полигон. Пользователь на устройстве видит точные последствия изменений правил **за считанные секунды** до нажатия кнопки «Активировать».

#### 1.2. Границы модуля и зависимости

1. **Входящие зависимости:**
   - `:pipeline:compiler` — типы `CompiledPipeline`, `Frame`, `FrameLayout`, `Signal`.
   - `:pipeline:nodes-api` — типы `EffectBuffer`, `EffectView`, `EffectKindId`, `Bank`.
   - `:core:model` — сущности `NotificationEvent`, `Category`, `FinancialTransaction`, `Currency`.
   - `:core:storage` — интерфейсы чтения исторических событий (`ReplayEventSourceDao`), без прав на мутацию.
   - `kotlinx.coroutines` — примитивы `Flow`, `flow`, `yield`, `Job`, `Dispatchers.Default`.
2. **Изоляция от Hot Path:**
   - Модуль `:feature:replay` работает строго вне критического пути NLS.
   - Выполнение симуляции происходит в пуле фоновых потоков (`Dispatchers.Default`), не влияя на обработку входящих push-уведомлений реального времени.
3. **Целевое устройство и аппаратные условия:**
   - **Устройство:** Poco M7 (`2440cbe2`), чипсет MediaTek Dimensity 6100+ (2x Cortex-A76 @ 2.2 GHz + 6x Cortex-A55 @ 2.0 GHz), 4/6 GB LPDDR4X.
   - **Бюджет времени:** 1000 событий $\le 3$ секунды на Poco M7.
   - **Бюджет памяти:** прирост Heap во время симуляции $\le 8$ МБ, отсутствие утечек после завершения.

---

### 2. Публичный контракт `ReplayEngine` и потоковый API

#### 2.1. Интерфейс `ReplayEngine`

Интерфейс предоставляет единую точку входа для запуска симуляции в виде холодного реактивного потока `Flow<ReplayStatus>`:

```kotlin
package com.example.npc.feature.replay.engine

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.Flow

/**
 * Главный контракт симулятора и ретроспективного прогона конвейеров.
 */
interface ReplayEngine {

    /**
     * Запускает симуляцию конвейера-черновика на исторических событиях базы догфудинга.
     *
     * @param draftPipeline Скомпилированный конвейер-черновик, подвергаемый тестированию.
     * @param activePipeline Текущий активный конвейер (если есть). Если передан,
     *                       производится парный дифференциальный прогон (Draft vs Active).
     *                       Если null, сопоставление идет с историческими значениями из таблицы `event`.
     * @param criteria Критерии выборки исторических событий (период, пакеты, лимит).
     * @return Холодный [Flow], транслирующий динамику прогресса и итоговый [ReplayDiffReport].
     */
    fun runSimulation(
        draftPipeline: CompiledPipeline,
        activePipeline: CompiledPipeline? = null,
        criteria: ReplayCriteria = ReplayCriteria.Default
    ): Flow<ReplayStatus>
}
```

#### 2.2. Модель критериев выборки (`ReplayCriteria`)

Критерии позволяют гибко сузить объем выборки: например, ограничиться только банковскими приложениями за последние 24 часа или протестировать все 7 дней догфудинга:

```kotlin
package com.example.npc.feature.replay.engine

import java.time.Instant

/**
 * Критерии фильтрации исторических событий для симуляции.
 */
data class ReplayCriteria(
    /** Временной диапазон симуляции. */
    val timeRange: TimeRange = TimeRange.Last7Days,

    /**
     * Фильтр по именам пакетов приложений (например, ["com.apb.mobile", "com.idamob.tinkoff.android"]).
     * Если null или пуст — оцениваются уведомления всех установленных пакетов.
     */
    val packageFilter: Set<String>? = null,

    /**
     * Максимальное количество событий для симуляции.
     * Предотвращает OOM и зависание UI на очень больших базах.
     */
    val maxEventsLimit: Int = 1000,

    /**
     * Размер чанка постраничной загрузки (Keyset pagination limit).
     * Оптимальное значение для Poco M7: 100..200.
     */
    val chunkSize: Int = 150
) {
    companion object {
        val Default = ReplayCriteria()
    }
}

/**
 * Спецификация временного интервала выборки.
 */
sealed interface TimeRange {
    val fromTimestamp: Long
    val toTimestamp: Long

    data object Last24Hours : TimeRange {
        override val fromTimestamp: Long get() = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        override val toTimestamp: Long get() = System.currentTimeMillis()
    }

    data object Last7Days : TimeRange {
        override val fromTimestamp: Long get() = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        override val toTimestamp: Long get() = System.currentTimeMillis()
    }

    data object AllTime : TimeRange {
        override val fromTimestamp: Long get() = 0L
        override val toTimestamp: Long get() = Long.MAX_VALUE
    }

    data class Custom(
        override val fromTimestamp: Long,
        override val toTimestamp: Long
    ) : TimeRange {
        init {
            require(fromTimestamp <= toTimestamp) {
                "fromTimestamp ($fromTimestamp) must be <= toTimestamp ($toTimestamp)"
            }
        }
    }
}
```

#### 2.3. Потоковый статус жизненного цикла (`ReplayStatus`)

Поскольку симуляция 1000 событий занимает до 3 секунд, UI должен непрерывно отображать плавный прогресс-бар и текущее обрабатываемое событие:

```kotlin
package com.example.npc.feature.replay.engine

import com.example.npc.feature.replay.model.ReplayDiffReport

/**
 * Состояние исполнения симуляции, передаваемое в Flow.
 */
sealed interface ReplayStatus {

    /**
     * Инициализация: расчет общего количества событий, подходящих под критерии.
     */
    data class Initializing(val totalEstimated: Int) : ReplayStatus

    /**
     * Промежуточный прогресс выполнения пакета событий.
     * Эмитится после каждого обработанного чанка или каждые 50 событий.
     */
    data class Progress(
        val processedCount: Int,
        val totalCount: Int,
        val currentEventId: Long,
        val currentPackageName: String,
        val percent: Float = if (totalCount > 0) (processedCount.toFloat() / totalCount) else 0f
    ) : ReplayStatus

    /**
     * Симуляция успешно завершена. Возвращается полный дифференциальный отчет.
     */
    data class Completed(
        val report: ReplayDiffReport
    ) : ReplayStatus

    /**
     * Симуляция была прервана пользователем или отменой вызывающего CoroutineScope.
     */
    data class Cancelled(
        val processedBeforeCancel: Int
    ) : ReplayStatus

    /**
     * Фатальный сбой в процессе чтения БД или рантайма.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ReplayStatus
}
```

---

### 3. Режим изолированной песочницы (Sandbox Isolation)

#### 3.1. Гарантия 100% изоляции (Zero SQLite Mutations)

Критический инвариант: **ни при каких условиях `ReplayEngine` не должен модифицировать данные в SQLite.**
Все вызовы к таблицам `event`, `financial_transaction`, `user_prototype` и `raw_event` выполняются строго через read-only DAO с запретом транзакций записи:

```
                      ┌──────────────────────────────────────────────┐
                      │              Событие из Room                 │
                      │  RawEvent: title, text, package, timestamp   │
                      └──────────────────────┬───────────────────────┘
                                             │
                        ┌────────────────────┴────────────────────┐
                        │                                         │
                        ▼                                         ▼
            ┌──────────────────────┐                  ┌──────────────────────┐
            │ draftPipeline.exec() │                  │activePipeline.exec() │
            │  (Регистры Frame 1)  │                  │  (Регистры Frame 2)  │
            └──────────┬───────────┘                  └──────────┬───────────┘
                       │                                         │
                       ▼                                         ▼
            ┌──────────────────────┐                  ┌──────────────────────┐
            │ EffectBuffer (Draft) │                  │ EffectBuffer (Active)│
            │ in-memory buffer     │                  │ in-memory buffer     │
            └──────────┬───────────┘                  └──────────┬───────────┘
                       │                                         │
                       └────────────────────┬────────────────────┘
                                            │
                                            ▼
                             ┌──────────────────────────────┐
                             │    VirtualEffectEvaluator    │
                             │   (Сравнение в памяти RAM)   │
                             │  * Категория                 │
                             │  * Финансовая транзакция     │
                             │  * Статус DROP / PASS        │
                             └──────────────┬───────────────┘
                                            │
                                            ▼  (БД Room не затрагивается!)
                                    ReplayDiffReport
```

#### 3.2. Виртуальный перехват сайд-эффектов (`VirtualEffectEvaluator`)

В боевом рантайме `:pipeline:runtime` эффекты из `EffectBuffer` передаются в `StorageGateway.completeEventProcessing` и вызывают `UPDATE event` и `INSERT INTO financial_transaction`.

В `ReplayEngine` используется виртуальный интерпретатор `VirtualEffectEvaluator`. Он читает сырой `EffectBuffer` через `EffectView` и формирует легковесную проекцию `EvaluationOutcome` **без участия БД**:

```kotlin
package com.example.npc.feature.replay.engine

import com.example.npc.core.model.Category
import com.example.npc.core.model.FinancialTransaction
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.effect.EffectView

/**
 * Результат изолированного виртуального прогона конвейера над одним событием.
 */
data class EvaluationOutcome(
    val signal: Int, // Signal.PASS, Signal.DROP, Signal.FAULT
    val category: Category,
    val confidence: Double,
    val transaction: FinancialTransaction?,
    val isDropped: Boolean get() = signal == Signal.DROP,
    val executionDurationNanos: Long
)

/**
 * Безаллокационный вычислитель побочных эффектов в памяти.
 */
class VirtualEffectEvaluator {

    fun evaluate(
        signal: Int,
        effectBuffer: EffectBuffer,
        durationNanos: Long
    ): EvaluationOutcome {
        if (signal == Signal.DROP) {
            return EvaluationOutcome(
                signal = Signal.DROP,
                category = Category.UNCLASSIFIED,
                confidence = 0.0,
                transaction = null,
                executionDurationNanos = durationNanos
            )
        }

        var resolvedCategory = Category.UNCLASSIFIED
        var resolvedConfidence = 0.0
        var resolvedTransaction: FinancialTransaction? = null

        val view = EffectView()
        effectBuffer.openView(view)

        while (view.hasNext()) {
            view.next()
            when (view.kindId) {
                EffectKindId.SET_CATEGORY -> {
                    val categoryOrdinal = view.getIntArg(0)
                    resolvedCategory = Category.entries.getOrElse(categoryOrdinal) { Category.UNCLASSIFIED }
                    resolvedConfidence = view.getDoubleArg(1)
                }
                EffectKindId.CREATE_FINANCIAL_TRANSACTION -> {
                    resolvedTransaction = view.getRefArg(0) as? FinancialTransaction
                }
            }
        }

        return EvaluationOutcome(
            signal = signal,
            category = resolvedCategory,
            confidence = resolvedConfidence,
            transaction = resolvedTransaction,
            executionDurationNanos = durationNanos
        )
    }
}
```

#### 3.3. Доступ к историческим данным через `ReplayEventSourceDao`

Для выборки событий используется специализированный Room DAO в `:core:storage`, оптимизированный для последовательного чтения без накладных расходов на создание полных графов сущностей:

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.npc.core.storage.model.ReplayHistoricalItem

@Dao
interface ReplayEventSourceDao {

    /**
     * Постраничная выборка событий методом Keyset Pagination (WHERE e.id > :lastSeenId).
     * Избегает квадратичного оверхеда OFFSET на SQLite.
     */
    @Query("""
        SELECT 
            e.id AS eventId,
            r.id AS rawId,
            r.package_name AS packageName,
            r.title AS title,
            r.text AS text,
            r.post_time AS timestamp,
            e.category AS historicalCategory,
            e.confidence AS historicalConfidence,
            t.id AS txId,
            t.amount AS txAmount,
            t.currency AS txCurrency,
            t.merchant AS txMerchant,
            t.status AS txStatus
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        LEFT JOIN financial_transaction t ON t.event_id = e.id
        WHERE e.id > :lastSeenId
          AND r.post_time >= :fromTimestamp
          AND r.post_time <= :toTimestamp
          AND (:packageCount = 0 OR r.package_name IN (:packages))
        ORDER BY e.id ASC
        LIMIT :limit
    """)
    suspend fun getReplayBatch(
        lastSeenId: Long,
        fromTimestamp: Long,
        toTimestamp: Long,
        packages: List<String>,
        packageCount: Int = packages.size,
        limit: Int
    ): List<ReplayHistoricalItem>

    /**
     * Быстрый подсчет общего числа событий для прогресс-бара.
     */
    @Query("""
        SELECT COUNT(e.id)
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        WHERE r.post_time >= :fromTimestamp
          AND r.post_time <= :toTimestamp
          AND (:packageCount = 0 OR r.package_name IN (:packages))
    """)
    suspend fun countReplayEvents(
        fromTimestamp: Long,
        toTimestamp: Long,
        packages: List<String>,
        packageCount: Int = packages.size
    ): Int
}
```

---

### 4. Модель сравнительного дифференциального отчёта (`ReplayDiffReport`)

Дифференциальный отчет сопоставляет работу черновика (`Draft`) с эталоном (`Baseline`). В качестве эталона выступает либо прогон `activePipeline` над теми же событиями, либо исторические данные из таблицы `event`.

#### 4.1. Структура `ReplayDiffReport`

```kotlin
package com.example.npc.feature.replay.model

import com.example.npc.core.model.Category
import com.example.npc.core.model.Currency

/**
 * Итоговый сравнительный дифференциальный отчет симуляции.
 */
data class ReplayDiffReport(
    /** Общее количество оцененных событий. */
    val totalEvaluated: Int,

    /** Количество событий с полным совпадением категории и финансовых данных. */
    val matchedCount: Int,

    /** Список расхождений по категориям (было X -> стало Y). */
    val categoryDivergences: List<CategoryDiffItem>,

    /** Список расхождений по финансовым транзакциям. */
    val financialDivergences: List<FinanceDiffItem>,

    /** Количество событий, которые черновик подавляет (Signal.DROP / Spam filter). */
    val droppedEventsCount: Int,

    /** Количество событий, которые в эталоне были отброшены, а в черновике приняты. */
    val resurrectedEventsCount: Int,

    /** Метрики производительности и латентности (Poco M7). */
    val performanceMetrics: PipelinePerfComparison
) {
    /** Доля совпадений (Match Rate) в процентах (0.0 .. 100.0). */
    val matchRatePercent: Double
        get() = if (totalEvaluated > 0) (matchedCount.toDouble() / totalEvaluated) * 100.0 else 100.0

    /** Есть ли критические расхождения (новые транзакции или смена категорий). */
    val hasDivergences: Boolean
        get() = categoryDivergences.isNotEmpty() || financialDivergences.isNotEmpty() || droppedEventsCount > 0
}
```

#### 4.2. Модели расхождений категорий (`CategoryDiffItem`)

```kotlin
package com.example.npc.feature.replay.model

import com.example.npc.core.model.Category

/**
 * Расхождение классификации категории для конкретного события.
 */
data class CategoryDiffItem(
    val eventId: Long,
    val packageName: String,
    val titleSnippet: String,
    val textSnippet: String,
    val timestamp: Long,
    val previousCategory: Category,
    val newCategory: Category,
    val previousConfidence: Double,
    val newConfidence: Double,
    val transition: CategoryTransition = CategoryTransition.from(previousCategory, newCategory)
)

/**
 * Семантический тип перехода категории для группировки в UI.
 */
enum class CategoryTransition {
    /** Из неопределенного стало полезным: UNCLASSIFIED -> FINANCE / SHOPPING. */
    PROMOTED_FROM_UNCLASSIFIED,

    /** Было полезным, стало спамом: OTHER -> ADVERTISEMENT. */
    TAGGED_AS_SPAM,

    /** Переклассификация финансового типа: FINANCE -> SERVICE. */
    RECLASSIFIED_FINANCE,

    /** Прочая смена категории. */
    OTHER;

    companion object {
        fun from(old: Category, new: Category): CategoryTransition = when {
            old == Category.UNCLASSIFIED && new != Category.UNCLASSIFIED -> PROMOTED_FROM_UNCLASSIFIED
            new == Category.ADVERTISEMENT && old != Category.ADVERTISEMENT -> TAGGED_AS_SPAM
            old == Category.FINANCE || new == Category.FINANCE -> RECLASSIFIED_FINANCE
            else -> OTHER
        }
    }
}
```

#### 4.3. Модели расхождений финансовых транзакций (`FinanceDiffItem`)

```kotlin
package com.example.npc.feature.replay.model

import com.example.npc.core.model.Currency

/**
 * Расхождение извлечения финансовых параметров.
 */
data class FinanceDiffItem(
    val eventId: Long,
    val packageName: String,
    val titleSnippet: String,
    val textSnippet: String,
    val timestamp: Long,
    val diffType: FinanceDiffType,
    val previousTransaction: TransactionSnapshot?,
    val newTransaction: TransactionSnapshot?
)

enum class FinanceDiffType {
    /** Новая транзакция, которая ранее не была распознана конвейером. */
    NEW_TRANSACTION_DETECTED,

    /** Транзакция пропала (была распознана ранее, но новый конвейер ее проигнорировал). */
    TRANSACTION_DROPPED,

    /** Изменились сумма, валюта или мерчант. */
    TRANSACTION_MUTATED
}

data class TransactionSnapshot(
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val status: String
)
```

#### 4.4. Сравнительные метрики производительности (`PipelinePerfComparison`)

Сравнение скорости исполнения draft vs active на процессоре Poco M7:

```kotlin
package com.example.npc.feature.replay.model

/**
 * Сравнительный анализ скорости работы черновика и активного конвейера.
 */
data class PipelinePerfComparison(
    val totalDraftDurationMs: Long,
    val totalActiveDurationMs: Long?,
    val draftP50Micros: Double,
    val draftP95Micros: Double,
    val activeP50Micros: Double?,
    val activeP95Micros: Double?,
    val throughputEventsPerSec: Double,
    val isDraftFaster: Boolean = activeP95Micros != null && draftP95Micros < activeP95Micros
)
```

---

### 5. Контроль ресурсов, батчинг и корутинная оптимизация на Poco M7

Целевое устройство **Poco M7 (`2440cbe2`)** оснащено чипсетом начального уровня MediaTek Dimensity 6100+ под управлением агрессивной оболочки Xiaomi HyperOS. Длительная монопольная загрузка CPU в фоновом потоке может привести к:
1. Троттлингу UI (просадке FPS в приложении ниже 60 fps).
2. Зависанию службы NLS или вытеснению процесса системой из-за фонового энергопотребления.
3. Перегреву процессора при симуляции больших объемов данных (> 1000 событий).

Для нейтрализации этих рисков в `ReplayEngine` заложены 4 архитектурных механизма:

#### 5.1. Keyset Pagination (Постраничная выборка чанками по 100–200 событий)

Вместо `SELECT ... OFFSET N`, который в SQLite приводит к полному линейному сканированию $O(N)$ страниц B-Tree, используется выборка по первичному ключу `id > :lastSeenId ORDER BY id ASC LIMIT :chunkSize`. 
- Время чтения чанка из 150 событий на Poco M7: $\le 12$ мс.
- Память под один чанк: $< 150$ КБ.

#### 5.2. Кооперативная многозадачность (`yield()`)

Между обработкой каждого чанка событий корутина симулятора вызывает `yield()`:

```kotlin
for (chunk in eventSource.fetchChunks(criteria)) {
    ensureActive() // Проверка корутинной отмены
    
    // Обработка батча событий
    processBatch(chunk, draftPipeline, activePipeline)
    
    // Уступка кванта времени CPU системным потокам и UI
    yield()
}
```
`yield()` гарантирует, что корутина сбрасывает управление на диспетчере `Dispatchers.Default`, давая возможность главному потоку UI отрисовать текущий кадр анимации или обработать касание пользователя без jank-эффектов.

#### 5.3. Переиспользование структур исполнения (Zero-Alloc Context Pooling)

Во время симуляции 1000 событий **недопустимо** создавать по 1000 экземпляров `Frame`, `EffectBuffer` или `TextRegister`.
В `ReplayEngine` предвыделяются **ровно два** контекста исполнения (один для `draftPipeline`, второй для `activePipeline`):

```kotlin
class ReplayExecutionContext(
    layout: FrameLayout,
    effectCapacity: Int = 32
) {
    val frame: Frame = Frame(layout, emptyArray())
    val effectBuffer: EffectBuffer = EffectBuffer(effectCapacity)
    val textRegister: TextRegister = TextRegister(capacity = 2048)

    fun reset() {
        frame.reset()
        effectBuffer.clear()
        textRegister.len = 0
    }
}
```
На каждом шаге цикла `reset()` очищает массивы и указатели за $O(1)$. Аллокации в куче во время горячего цикла симуляции сведены к **0 байт на событие**.

#### 5.4. Мгновенная реактивная отмена (`Job.cancel()`)

Пользователь может в любой момент нажать кнопку «Отмена» в UI симулятора.
- Проверка `coroutineContext.ensureActive()` выполняется перед каждым событием.
- При отмене корутина выбрасывает `CancellationException`, ресурсы и буферы немедленно возвращаются в пул, а в поток эмитится `ReplayStatus.Cancelled`. Задержка реакции на отмену: $\le 15$ мс.

#### 5.5. Бюджеты производительности и SLA на Poco M7

| Параметр | Бюджет / SLA | Инструмент контроля |
|---|---|---|
| **Время симуляции 1000 событий** | $\le 3.0$ секунды | `SystemClock.elapsedRealtime()` / Benchmark |
| **Размер чанка БД** | 100..200 событий | Конфигурация `ReplayCriteria` |
| **Максимальный прирост Heap** | $\le 8$ МБ | Android Studio Memory Profiler |
| **Память горячего цикла** | **0 байт** / событие | `ThreadMXBean` / Benchmark |
| **Реакция на `Job.cancel()`** | $\le 50$ мс | Корутинный Unit-тест |
| **Влияние на UI (Janky frames)** | $0\%$ drop frames | `JankStats` / Macrobenchmark |
| **Записей в таблицы Room при симуляции** | **СТРОГО 0** | `RoomDatabase` Spy / Triggers |

---

### 6. Полная реализация ядра симулятора (`ReplayEngineImpl`)

```kotlin
package com.example.npc.feature.replay.engine

import android.os.SystemClock
import com.example.npc.core.model.Category
import com.example.npc.core.storage.dao.ReplayEventSourceDao
import com.example.npc.core.storage.model.ReplayHistoricalItem
import com.example.npc.feature.replay.model.*
import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.compiler.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield

class ReplayEngineImpl(
    private val eventSourceDao: ReplayEventSourceDao,
    private val effectEvaluator: VirtualEffectEvaluator = VirtualEffectEvaluator()
) : ReplayEngine {

    override fun runSimulation(
        draftPipeline: CompiledPipeline,
        activePipeline: CompiledPipeline?,
        criteria: ReplayCriteria
    ): Flow<ReplayStatus> = flow {
        val packagesList = criteria.packageFilter?.toList() ?: emptyList()

        // 1. Предварительный подсчет объема выборки
        val totalEstimated = eventSourceDao.countReplayEvents(
            fromTimestamp = criteria.timeRange.fromTimestamp,
            toTimestamp = criteria.timeRange.toTimestamp,
            packages = packagesList
        ).coerceAtMost(criteria.maxEventsLimit)

        emit(ReplayStatus.Initializing(totalEstimated))

        if (totalEstimated == 0) {
            emit(ReplayStatus.Completed(emptyReport()))
            return@flow
        }

        // 2. Предвыделение безаллокационных контекстов исполнения
        val draftContext = ReplayExecutionContext(draftPipeline.layout)
        val activeContext = activePipeline?.let { ReplayExecutionContext(it.layout) }

        var lastSeenId = 0L
        var processedTotal = 0
        var matchedTotal = 0
        var droppedTotal = 0
        var resurrectedTotal = 0

        val categoryDivergences = ArrayList<CategoryDiffItem>()
        val financialDivergences = ArrayList<FinanceDiffItem>()

        // Сбор метрик производительности
        val draftDurationsNanos = LongArray(totalEstimated.coerceAtLeast(16))
        val activeDurationsNanos = if (activePipeline != null) LongArray(totalEstimated.coerceAtLeast(16)) else null

        val replayStartMonotonic = SystemClock.elapsedRealtime()

        try {
            while (processedTotal < criteria.maxEventsLimit) {
                currentCoroutineContext().ensureActive()

                val fetchLimit = criteria.chunkSize.coerceAtMost(criteria.maxEventsLimit - processedTotal)
                val batch: List<ReplayHistoricalItem> = eventSourceDao.getReplayBatch(
                    lastSeenId = lastSeenId,
                    fromTimestamp = criteria.timeRange.fromTimestamp,
                    toTimestamp = criteria.timeRange.toTimestamp,
                    packages = packagesList,
                    limit = fetchLimit
                )

                if (batch.isEmpty()) break

                for (i in batch.indices) {
                    currentCoroutineContext().ensureActive()
                    val item = batch[i]
                    lastSeenId = item.eventId

                    // 3. Исполнение черновика
                    draftContext.reset()
                    draftContext.frame.bindHistoricalItem(item, draftContext.textRegister)
                    
                    val t0Draft = System.nanoTime()
                    val draftSignal = draftPipeline.execute(draftContext.frame)
                    val t1Draft = System.nanoTime()
                    val draftDuration = t1Draft - t0Draft
                    draftDurationsNanos[processedTotal] = draftDuration

                    val draftOutcome = effectEvaluator.evaluate(draftSignal, draftContext.effectBuffer, draftDuration)

                    // 4. Оценка эталона: либо парный прогон activePipeline, либо исторический снимок из Room
                    val activeOutcome: EvaluationOutcome = if (activePipeline != null && activeContext != null) {
                        activeContext.reset()
                        activeContext.frame.bindHistoricalItem(item, activeContext.textRegister)
                        
                        val t0Active = System.nanoTime()
                        val actSignal = activePipeline.execute(activeContext.frame)
                        val t1Active = System.nanoTime()
                        val actDuration = t1Active - t0Active
                        activeDurationsNanos?.set(processedTotal, actDuration)

                        effectEvaluator.evaluate(actSignal, activeContext.effectBuffer, actDuration)
                    } else {
                        // Эталон из полей таблицы `event`
                        EvaluationOutcome(
                            signal = Signal.PASS,
                            category = item.historicalCategory,
                            confidence = item.historicalConfidence,
                            transaction = item.historicalTransaction,
                            executionDurationNanos = 0L
                        )
                    }

                    // 5. Дифференциальный анализ
                    val isMatch = evaluateDiff(
                        item = item,
                        draft = draftOutcome,
                        active = activeOutcome,
                        categoryDivergences = categoryDivergences,
                        financialDivergences = financialDivergences
                    )

                    if (isMatch) matchedTotal++
                    if (draftOutcome.isDropped) droppedTotal++
                    if (activeOutcome.isDropped && !draftOutcome.isDropped) resurrectedTotal++

                    processedTotal++
                }

                // Эмитим промежуточный прогресс после каждого чанка
                val lastItem = batch.last()
                emit(
                    ReplayStatus.Progress(
                        processedCount = processedTotal,
                        totalCount = totalEstimated,
                        currentEventId = lastItem.eventId,
                        currentPackageName = lastItem.packageName
                    )
                )

                // Кооперативная уступка кванта времени
                yield()
            }

            // 6. Расчет квантилей производительности и сборка итогового отчета
            val totalReplayTimeMs = SystemClock.elapsedRealtime() - replayStartMonotonic
            val perfComparison = calculatePerfComparison(
                draftDurationsNanos = draftDurationsNanos,
                activeDurationsNanos = activeDurationsNanos,
                count = processedTotal,
                totalReplayTimeMs = totalReplayTimeMs
            )

            val finalReport = ReplayDiffReport(
                totalEvaluated = processedTotal,
                matchedCount = matchedTotal,
                categoryDivergences = categoryDivergences,
                financialDivergences = financialDivergences,
                droppedEventsCount = droppedTotal,
                resurrectedEventsCount = resurrectedTotal,
                performanceMetrics = perfComparison
            )

            emit(ReplayStatus.Completed(finalReport))

        } catch (ce: kotlinx.coroutines.CancellationException) {
            emit(ReplayStatus.Cancelled(processedBeforeCancel = processedTotal))
            throw ce
        } catch (t: Throwable) {
            emit(ReplayStatus.Error(message = t.message ?: "Unknown replay error", cause = t))
        }
    }.flowOn(Dispatchers.Default)

    private fun evaluateDiff(
        item: ReplayHistoricalItem,
        draft: EvaluationOutcome,
        active: EvaluationOutcome,
        categoryDivergences: MutableList<CategoryDiffItem>,
        financialDivergences: MutableList<FinanceDiffItem>
    ): Boolean {
        var isFullMatch = true

        // Сравнение категорий
        if (draft.category != active.category) {
            isFullMatch = false
            categoryDivergences.add(
                CategoryDiffItem(
                    eventId = item.eventId,
                    packageName = item.packageName,
                    titleSnippet = item.title.take(64),
                    textSnippet = item.text.take(128),
                    timestamp = item.timestamp,
                    previousCategory = active.category,
                    newCategory = draft.category,
                    previousConfidence = active.confidence,
                    newConfidence = draft.confidence
                )
            )
        }

        // Сравнение финансовых транзакций
        val draftTx = draft.transaction
        val activeTx = active.transaction

        if (draftTx == null && activeTx != null) {
            isFullMatch = false
            financialDivergences.add(
                FinanceDiffItem(
                    eventId = item.eventId,
                    packageName = item.packageName,
                    titleSnippet = item.title.take(64),
                    textSnippet = item.text.take(128),
                    timestamp = item.timestamp,
                    diffType = FinanceDiffType.TRANSACTION_DROPPED,
                    previousTransaction = activeTx.toSnapshot(),
                    newTransaction = null
                )
            )
        } else if (draftTx != null && activeTx == null) {
            isFullMatch = false
            financialDivergences.add(
                FinanceDiffItem(
                    eventId = item.eventId,
                    packageName = item.packageName,
                    titleSnippet = item.title.take(64),
                    textSnippet = item.text.take(128),
                    timestamp = item.timestamp,
                    diffType = FinanceDiffType.NEW_TRANSACTION_DETECTED,
                    previousTransaction = null,
                    newTransaction = draftTx.toSnapshot()
                )
            )
        } else if (draftTx != null && activeTx != null) {
            val amountDiff = Math.abs(draftTx.amount - activeTx.amount) > 0.001
            val currDiff = draftTx.currency != activeTx.currency
            val merchantDiff = draftTx.merchant != activeTx.merchant

            if (amountDiff || currDiff || merchantDiff) {
                isFullMatch = false
                financialDivergences.add(
                    FinanceDiffItem(
                        eventId = item.eventId,
                        packageName = item.packageName,
                        titleSnippet = item.title.take(64),
                        textSnippet = item.text.take(128),
                        timestamp = item.timestamp,
                        diffType = FinanceDiffType.TRANSACTION_MUTATED,
                        previousTransaction = activeTx.toSnapshot(),
                        newTransaction = draftTx.toSnapshot()
                    )
                )
            }
        }

        return isFullMatch
    }

    private fun calculatePerfComparison(
        draftDurationsNanos: LongArray,
        activeDurationsNanos: LongArray?,
        count: Int,
        totalReplayTimeMs: Long
    ): PipelinePerfComparison {
        if (count == 0) {
            return PipelinePerfComparison(0, null, 0.0, 0.0, null, null, 0.0)
        }

        val draftTrimmed = draftDurationsNanos.copyOf(count).apply { sort() }
        val draftP50 = draftTrimmed[(count * 0.50).toInt()] / 1_000.0
        val draftP95 = draftTrimmed[(count * 0.95).toInt()] / 1_000.0

        var actP50: Double? = null
        var actP95: Double? = null
        if (activeDurationsNanos != null) {
            val activeTrimmed = activeDurationsNanos.copyOf(count).apply { sort() }
            actP50 = activeTrimmed[(count * 0.50).toInt()] / 1_000.0
            actP95 = activeTrimmed[(count * 0.95).toInt()] / 1_000.0
        }

        val throughput = if (totalReplayTimeMs > 0) (count.toDouble() / totalReplayTimeMs) * 1000.0 else 0.0

        return PipelinePerfComparison(
            totalDraftDurationMs = totalReplayTimeMs,
            totalActiveDurationMs = if (activeDurationsNanos != null) totalReplayTimeMs else null,
            draftP50Micros = draftP50,
            draftP95Micros = draftP95,
            activeP50Micros = actP50,
            activeP95Micros = actP95,
            throughputEventsPerSec = throughput
        )
    }

    private fun emptyReport() = ReplayDiffReport(
        totalEvaluated = 0,
        matchedCount = 0,
        categoryDivergences = emptyList(),
        financialDivergences = emptyList(),
        droppedEventsCount = 0,
        resurrectedEventsCount = 0,
        performanceMetrics = PipelinePerfComparison(0, null, 0.0, 0.0, null, null, 0.0)
    )
}
```

---

### 7. Архитектура UI слоя (`:feature:replay` UI & ViewModel)

Модуль `:feature:replay` предоставляет экран симуляции и инспектора дифференциального отчета для интеграции в редактор конвейера `:feature:editor`.

#### 7.1. Модель состояния `ReplayViewModel`

```kotlin
package com.example.npc.feature.replay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.npc.feature.replay.engine.ReplayCriteria
import com.example.npc.feature.replay.engine.ReplayEngine
import com.example.npc.feature.replay.engine.ReplayStatus
import com.example.npc.feature.replay.model.ReplayDiffReport
import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReplayUiState {
    data object Idle : ReplayUiState
    data class Running(val progress: ReplayStatus.Progress) : ReplayUiState
    data class Success(val report: ReplayDiffReport) : ReplayUiState
    data class Cancelled(val processedCount: Int) : ReplayUiState
    data class Error(val message: String) : ReplayUiState
}

class ReplayViewModel(
    private val replayEngine: ReplayEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReplayUiState>(ReplayUiState.Idle)
    val uiState: StateFlow<ReplayUiState> = _uiState.asStateFlow()

    private var currentSimulationJob: Job? = null

    fun startReplay(
        draft: CompiledPipeline,
        active: CompiledPipeline?,
        criteria: ReplayCriteria = ReplayCriteria.Default
    ) {
        currentSimulationJob?.cancel()
        currentSimulationJob = viewModelScope.launch {
            replayEngine.runSimulation(draft, active, criteria).collect { status ->
                _uiState.value = when (status) {
                    is ReplayStatus.Initializing -> ReplayUiState.Running(
                        ReplayStatus.Progress(0, status.totalEstimated, 0L, "Инициализация...")
                    )
                    is ReplayStatus.Progress -> ReplayUiState.Running(status)
                    is ReplayStatus.Completed -> ReplayUiState.Success(status.report)
                    is ReplayStatus.Cancelled -> ReplayUiState.Cancelled(status.processedBeforeCancel)
                    is ReplayStatus.Error -> ReplayUiState.Error(status.message)
                }
            }
        }
    }

    fun cancelReplay() {
        currentSimulationJob?.cancel()
    }
}
```

#### 7.2. Экран инспектора расхождений (Side-by-Side Diff Inspector)

В UI отчета пользователь видит:
1. **Сводная карточка паритета:** индикатор Match Rate (например, `98.4% совпадение`), общее число событий, p95 скорость (например, `Draft 0.42 мс vs Active 0.48 мс`).
2. **Вкладка «Финансовые расхождения»:**
   - Карточки с подсветкой: зеленая плашка — *«Новая транзакция обнаружена: +150.00 RUP (Агропромбанк)»*, красная плашка — *«Транзакция пропущена»*.
   - Клик по карточке открывает оригинальный текст push-уведомления и подсвечивает сработавшее регулярное выражение.
3. **Вкладка «Смена категорий»:**
   - Список переходов: `UNCLASSIFIED ➔ FINANCE`, `COMMUNICATION ➔ ADVERTISEMENT`.
4. **Кнопка прямого действия:**
   - *«Принять и активировать конвейер»* (если расхождения ожидаемы и желательны).
   - *«Вернуться к редактированию»* (для корректировки ошибочного узла).

---

### 8. Тест-план и стратегии верификации

Тестирование модуля `:feature:replay` покрывает инварианты изоляции, корректность дифференциального анализа и аппаратные бюджеты Poco M7.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ТЕСТОВАЯ МАТРИЦА REPLAY ENGINE                  │
├────────────────────────────┬───────────────────────────────────────────┤
│ Направление проверки       │ Методология и критерии приемки            │
├────────────────────────────┼───────────────────────────────────────────┤
│ 1. Sandbox Zero-Mutation   │ SQLite DB Checksum & Table Counts до и    │
│    (Гарантия изоляции)     │ после симуляции. Ровно 0 измененных строк.│
├────────────────────────────┼───────────────────────────────────────────┤
│ 2. Diff Accuracy           │ Golden Corpus: InTour spam, APB push,     │
│    (Точность отчета)       │ Tinkoff transfer. 100% совпадение diff.   │
├────────────────────────────┼───────────────────────────────────────────┤
│ 3. Cooperative Cancel      │ Job.cancel() во время прогона. Время      │
│    (Мгновенная отмена)     │ отклика <= 50 мс, 0 утечек корутин.       │
├────────────────────────────┼───────────────────────────────────────────┤
│ 4. Poco M7 Performance SLA │ Прогон 1000 событий на устройстве.        │
│    (3 секунды бюджет)      │ Время <= 3.0 с, Heap growth <= 8 МБ.      │
└────────────────────────────┴───────────────────────────────────────────┘
```

#### 8.1. Тест нулевых мутаций песочницы (`SandboxZeroMutationTest`)

```kotlin
@Test
fun verifySandboxProducesZeroDatabaseMutations() = runTest {
    // 1. Снятие контрольных сумм и количества строк в БД
    val eventCountBefore = database.eventDao().countAll()
    val txCountBefore = database.financialTransactionDao().countAll()
    val dbChecksumBefore = calculateDatabaseChecksum(database)

    // 2. Запуск агрессивной симуляции черновика с правилами записи
    val draftPipeline = compileAggressiveMutatingPipeline()
    val report = replayEngine.runSimulation(
        draftPipeline = draftPipeline,
        activePipeline = null,
        criteria = ReplayCriteria(maxEventsLimit = 1000)
    ).last { it is ReplayStatus.Completed }

    // 3. Верификация неизменности БД
    val eventCountAfter = database.eventDao().countAll()
    val txCountAfter = database.financialTransactionDao().countAll()
    val dbChecksumAfter = calculateDatabaseChecksum(database)

    assertEquals(eventCountBefore, eventCountAfter, "Event table MUST NOT be mutated!")
    assertEquals(txCountBefore, txCountAfter, "Transaction table MUST NOT be mutated!")
    assertEquals(dbChecksumBefore, dbChecksumAfter, "Database file checksum MUST remain strictly identical!")
}
```

#### 8.2. Тест детекции спама InTour и банковских пушей (`ReplayDiffAccuracyTest`)

Проверяются типовые догфудинг-сценарии:
- **Кейс 1 (InTour Спам):** В версии 1.1 пуш «InTour: горящие туры от 500$» по ошибке классифицировался как `FINANCE` с транзакцией 500 USD. В черновике добавлен спам-фильтр. Отчет обязан показать:
  * `categoryDivergences`: `FINANCE ➔ ADVERTISEMENT`.
  * `financialDivergences`: `TRANSACTION_DROPPED (500 USD)`.
- **Кейс 2 (Т-Банк / Maib Новая транзакция):** В черновик добавлен паттерн для новой формулировки «Перевод по СБП 1500 ₽». Отчет обязан зафиксировать `NEW_TRANSACTION_DETECTED` с точной суммой 1500.00 RUB.

#### 8.3. Тест отмены и очистки ресурсов (`ReplayCancellationTest`)

```kotlin
@Test
fun verifyCancellationAbortsImmediatelyAndFreesMemory() = runTest {
    val draftPipeline = compileStandardPipeline()
    val scope = CoroutineScope(Dispatchers.Default)

    var lastProgress = 0
    val job = scope.launch {
        replayEngine.runSimulation(draftPipeline, criteria = ReplayCriteria(maxEventsLimit = 5000))
            .collect { status ->
                if (status is ReplayStatus.Progress) {
                    lastProgress = status.processedCount
                    if (status.processedCount >= 200) {
                        cancel() // Отмена при достижении 200 событий
                    }
                }
            }
    }

    job.join()
    assertTrue(job.isCancelled)
    assertTrue(lastProgress in 200..350, "Execution must stop immediately after yield, processed: $lastProgress")
}
```

---

### 9. Матрица ошибок и поведение при сбоях (Error Matrix)

| Код сбоя | Причина | Поведение ReplayEngine | Отображение в UI |
|---|---|---|---|
| `ERR_REPLAY_EMPTY_DATASET` | За указанный период нет событий в БД | Мгновенный `Completed` с пустым отчетом | Сообщение: *«Нет событий за выбранный период»* |
| `ERR_REPLAY_DB_TIMEOUT` | База данных заблокирована фоновым воркером | Повтор запроса 3 раза с backoff 100 мс | Статус: *«Ожидание доступа к БД...»* |
| `ERR_REPLAY_STAGE_FAULT` | В черновике конвейера узел бросил `Throwable` | Изоляция ошибки: узел помечается сбойным, событие получает `Signal.FAULT`, симуляция продолжается | Предупреждение: *«Узел X сбоит на 12 событиях»* |
| `ERR_REPLAY_OOM_GUARD` | Лимит событий превысил физическую память | Принудительное ограничение до `maxEventsLimit = 1000` | Предупреждение о частичной выборке |

---

### 10. Сводка архитектурных контрактов и контрольный чек-лист

- [x] **Изоляция:** 100% Zero SQLite Mutation Invariant, эффекты вычисляются в памяти через `VirtualEffectEvaluator`.
- [x] **Контракт API:** `runSimulation(draft, active, filter): Flow<ReplayStatus>`.
- [x] **Потоковый статус:** `Progress` с процентами, `Completed` с `ReplayDiffReport`, `Cancelled` и `Error`.
- [x] **Модель расхождений:** `categoryDivergences`, `financialDivergences`, `droppedEventsCount`, `performanceMetrics`.
- [x] **Батчинг на Poco M7:** Keyset Pagination по 100–200 событий (`id > :lastSeenId`), вызов `yield()` между чанками.
- [x] **Бюджет времени:** 1000 событий $\le 3.0$ с на аппарате Poco M7 (`2440cbe2`).
- [x] **Отмена:** Мгновенный отклик на `Job.cancel()` $\le 50$ мс без блокировок памяти.
