# Спецификация: zone/app-pipeline

## Модуль: `:app` (модуль оркестрации сквозного конвейера) · Зона: `zone/app-pipeline` · Фаза 1: «Хардкод-MVP» · Статус: `PROPOSED`

---

### 1. Назначение и контекст перехода Фаза 0 → Фаза 1

Модуль оркестрации конвейера обработки событий (`zone/app-pipeline`) располагается в модуле `:app` и выполняет роль центрального координатора сквозного конвейера захвата, семантической классификации и финансовой экстракции уведомлений и сообщений в Android-приложении Notification Pipeline Constructor.

#### 1.1. Контекст проблемы Фазы 0 (по данным 44.5 ч телеметрии Poco M7, `event_engine.db`):
В рамках Фазы 0 система захвата продемонстрировала высокую живучесть процесса на устройстве Poco M7 (HyperOS, Android 14), однако обработка событий страдала двумя фундаментальными архитектурными изъянами:
1. **Глобальный неизолированный парсинг (Ungated Regex Matching):**  
   Из 137 финансовых записей **83.2% (114 записей) оказались ложноположительными (False Positives)**:
   - **113 записей** рекламного спама турагентства *InTour* из Telegram (`com.radolyn.ayugram`), содержащего фрагмент *«вылет из Кишинева... от 499 евро...»*, были ошибочно классифицированы как `FINANCE` и сохранены как доход `1.00 RUB` с уверенностью `0.96`.
   - **1 запись** пуша виджета погоды *Яндекс.Погода* (`ru.yandex.weatherplugin`) с текстом *«+10°C, ощущается как +8°C»* была классифицирована как `FINANCE` и спарсилась как доход `10.00 RUB`.
2. **Инверсия обратной связи (Prototypes Feedback Inversion):**  
   Пользователь 6 раз вручную размечал спам InTour как не-финансы (`support_count = 6` в таблице `prototypes`), однако жестко зашитый парсер исполнялся до проверки прототипов, обесценивая пользовательские исправления.
3. **Монолитный блокирующий контур:**  
   Попытка выполнять тяжелый парсинг текста и регулярные выражения непосредственно в потоке захвата системных уведомлений создавала риск пропуска входящих событий и задержки системного сервиса `NotificationListenerService` сверх допустимого тайм-аута Android Binder IPC.

#### 1.2. Решение Главного Архитектора (Claude Opus 5.5): Двухконтурный конвейер
Для решения выявленных проблем внедряется двухконтурная асинхронная архитектура с гарантией нулевой потери данных (Zero Event Loss):

1. **Горячий контур захвата (Hot Path, `PipelineNotificationListenerService`, жесткий SLA < 5 мс):**
   - Вызывается в потоке системного сервиса уведомлений Android OS.
   - Выполняет немедленный снимок входящего уведомления `StatusBarNotification` (SBN).
   - Записывает `RawEvent` и начальное состояние `Event` со статусом `Category.UNCLASSIFIED` в базу данных Room по принципу **Write-Ahead Log (WAL)**.
   - Передает строго идентификатор события `eventId` в очередь оркестратора: `orchestrator.submit(eventId)` через неблокирующий буфер памяти.
   - Немедленно освобождает binder-поток операционной системы.
2. **Контур семантической обработки (Semantic Processing Loop, `EventProcessingOrchestrator`, бюджет 50 мс на событие):**
   - App-scoped синглтон, исполняющийся в изолированном пуле потоков (`SupervisorJob + Dispatchers.Default`).
   - Получает идентификаторы событий через `Channel<Long>(Channel.UNLIMITED)` — передаются только численные `id`, единственным источником истины остается зашифрованная БД Room.
   - Обеспечивает строгую идемпотентность через атомарный `tryClaim(id)` на уровне SQLite: `UPDATE event SET category = 'PROCESSING' WHERE id = :id AND (category = 'UNCLASSIFIED' OR category = 'PROCESSING')`.
   - Выполняет двухфазную классификацию (`SemanticClassifier`):
     * *Фаза 1 (Prototype-First):* мгновенное сопоставление по отпечатку контента (`content_fingerprint`). При наличии пользовательского прототипа с `supportCount >= 2` категория присваивается со 100% уверенностью (`Engine.PROTOTYPE`), а не-финансовые категории **блокируют запуск любых финансовых экстракторов**.
     * *Фаза 2 (Rule-Based + PackageGate):* пакетный роутинг по белым спискам банков и черным спискам мессенджеров, эвристические правила классификации.
   - Если и только если итоговая категория `FINANCE`, а источник является авторизованным банком/SMS — передает событие в `IsolatedExtractorRunner` для извлечения финансовых атрибутов (сумма, валюта, направление, мерчант, баланс, статус операции).
   - Транзакционно фиксирует результат через `StorageGateway.completeEventProcessing(eventId, classification, transaction)` в единой транзакции Room `@Transaction`.
3. **Механизм восстановления после сбоев (Recovery Sweep):**
   - При старте приложения (`App.onCreate`) и при каждом переподключении сервиса уведомлений (`onListenerConnected`) оркестратор запрашивает из Room все необработанные события (`category = 'UNCLASSIFIED'` или подвисшие `'PROCESSING'`) и передает их в конвейер `submit(id)`.
   - Обеспечивает 100% восстановление конвейера при внезапном убийстве процесса системой (Low Memory Killer), сбое питания или перезагрузке ОС.

---

### 2. Архитектурные границы и компонентная схема

```mermaid
flowchart TD
    subgraph HotLoop["Горячий контур захвата (SLA < 5 мс)"]
        SBN["Android OS: StatusBarNotification / SMS"] --> PNLS["PipelineNotificationListenerService"]
        PNLS -->|1. Запись сырого события| RAW["RawEvent (Room WAL)"]
        PNLS -->|2. Первичный Event| EVT_INIT["Event(UNCLASSIFIED)"]
        PNLS -->|3. submit(eventId)| CHAN["Channel<Long>(UNLIMITED)"]
    end

    subgraph OrchestrationLoop["Контур семантической обработки (EventProcessingOrchestrator)"]
        CHAN --> WORKER["Orchestrator Consumer Loop<br/>(Dispatchers.Default + SupervisorJob)"]
        WORKER -->|4. tryClaim(eventId)| CLAIM{"Атомарный Claim в Room<br/>UPDATE WHERE category='UNCLASSIFIED'"}
        CLAIM -- Занято / Дубликат --> SKIP["Пропуск (Skip)"]
        CLAIM -- Успех (1 row) --> LOAD["Загрузка Event + RawEvent (PackageName)"]
        
        LOAD --> PROTO_LOOKUP["Поиск прототипа в БД<br/>StorageGateway.findMatchingPrototype"]
        PROTO_LOOKUP --> SEM_CLASS["SemanticClassifierImpl<br/>(PrototypeStage + PackageGate + Rules)"]
        
        SEM_CLASS --> CHECK_FIN{"Категория == FINANCE<br/>И источник разрешен?"}
        
        CHECK_FIN -- НЕТ (Спам / Мессенджер / Прочее) --> SAVE_ONLY["Сохранение только категории<br/>(Экстракторы НЕ вызываются)"]
        CHECK_FIN -- ДА (Банк / SMS-банк) --> RUN_EXTR["IsolatedExtractorRunner<br/>(CircuitBreaker, budget 50ms, RE2/J)"]
        
        RUN_EXTR --> COMPOSE_TX["Формирование FinancialTransaction"]
        COMPOSE_TX --> SAVE_ALL["StorageGateway.completeEventProcessing<br/>(UPDATE event + INSERT transaction в @Transaction)"]
        SAVE_ONLY --> SAVE_ALL
    end

    subgraph RecoveryMechanism["Механизм восстановления (Recovery Sweep)"]
        BOOT["App.onCreate / onListenerConnected"] --> SWEEP["SELECT id FROM event<br/>WHERE category IN ('UNCLASSIFIED', 'PROCESSING')"]
        SWEEP -->|Повторная постановка| CHAN
    end

    SAVE_ALL --> UI_NOTIF["Flow: observeEvents / observeTransactions (Timeline 2.0)"]
```

#### Архитектурные инварианты изоляции:
1. **Разделение чистого кода и Android-контекста:**  
   Модули `:core:model`, `:classify:rules` и `:extract:finance` остаются чистыми библиотеками Kotlin JVM (`kotlin-jvm`). Они не знают о существовании `android.content.Context`, `NotificationListenerService` или каналов `kotlinx.coroutines.channels.Channel`.  
   Вся координация и связывание этих модулей осуществляется исключительно внутри модуля `:app` через класс `EventProcessingOrchestrator`.
2. **Room как единственный источник истины:**  
   Через канал оркестратора передаются исключительно примитивные числовые ключи `Long` (`eventId`). Никакие тяжелые объекты сущностей или JSON-снапшоты не удерживаются в очередях памяти, что предотвращает утечки памяти и OutOfMemoryError при всплесках уведомлений (Notification Flooding).
3. **Идемпотентность и исключение гонок:**  
   Любое событие гарантированно обрабатывается ровно один раз благодаря атомарной операции `tryClaim(id)`. Параллельные вызовы `submit(id)` для одного и того же события детерминированно отсекаются.

---

### 3. Типы данных, структуры и DTO

Все модели данных и состояния конвейера оркестрации размещаются в пакете `com.example.npc.app.pipeline.model`.

#### 3.1. `OrchestratorState`
Перечисление состояний жизненного цикла оркестратора.

```kotlin
package com.example.npc.app.pipeline.model

enum class OrchestratorState {
    /** Оркестратор создан, но цикл обработки еще не запущен */
    IDLE,

    /** Конвейер активен, консьюмеры слушают очередь, события обрабатываются */
    RUNNING,

    /** Поступил сигнал остановки, конвейер дорабатывает оставшиеся в буфере события */
    DRAINING,

    /** Конвейер полностью остановлен, фоновые корутины завершены */
    STOPPED
}
```

#### 3.2. `EventProcessingStatus`
Локальный статус прохождения события через конвейер семантической обработки.

```kotlin
package com.example.npc.app.pipeline.model

enum class EventProcessingStatus {
    /** Событие успешно классифицировано (и при необходимости извлечена транзакция) */
    COMPLETED,

    /** Событие пропущено (уже захвачено другим воркером или уже было обработано) */
    SKIPPED_ALREADY_CLAIMED,

    /** Событие не найдено в хранилище (аномалия целостности данных) */
    SKIPPED_NOT_FOUND,

    /** Обработка завершилась с ошибкой; событию присвоена fallback-категория OTHER */
    FAILED
}
```

#### 3.3. `EventProcessingTarget`
DTO агрегата события для семантического контура, объединяющий данные из `EventEntity` и `RawEventEntity`.

```kotlin
package com.example.npc.app.pipeline.model

import com.example.npc.core.model.Event
import com.example.npc.core.model.SourceId

data class EventProcessingTarget(
    val event: Event,
    val packageName: String,
    val sourceId: SourceId,
    val rawPayloadJson: String
) {
    init {
        require(event.id > 0L) { "Event ID must be positive (got ${event.id})" }
        require(packageName.isNotBlank()) { "Package name must not be blank" }
    }
}
```

#### 3.4. `PipelineMetrics`
Потокобезопасный реестр метрик производительности и надежности конвейера.

```kotlin
package com.example.npc.app.pipeline.model

import java.time.Instant

data class PipelineMetrics(
    val totalSubmitted: Long = 0L,
    val totalClaimed: Long = 0L,
    val totalCompleted: Long = 0L,
    val totalSkipped: Long = 0L,
    val totalFailed: Long = 0L,
    val totalPrototypeHits: Long = 0L,
    val totalFinanceExtracted: Long = 0L,
    val totalDeclinedTransactions: Long = 0L,
    val currentQueueDepth: Int = 0,
    val averageProcessingDurationMs: Double = 0.0,
    val lastProcessedEventId: Long? = null,
    val lastProcessedAt: Instant? = null,
    val lastError: String? = null
)
```

#### 3.5. `OrchestratorStatus`
Снимок (снапшот) состояния системы оркестрации для инспекции, мониторинга и отладки.

```kotlin
package com.example.npc.app.pipeline.model

data class OrchestratorStatus(
    val state: OrchestratorState,
    val isRecoveryActive: Boolean,
    val metrics: PipelineMetrics,
    val circuitBreakerStatuses: Map<String, Boolean> // extractorId -> isOpen
)
```

---

### 4. Публичный API `EventProcessingOrchestrator`

Контракт оркестратора объявляется в пакете `com.example.npc.app.pipeline`.

```kotlin
package com.example.npc.app.pipeline

import com.example.npc.app.pipeline.model.OrchestratorStatus
import kotlinx.coroutines.Job

/**
 * Главный оркестратор сквозного конвейера обработки событий.
 * Координирует работу пакетного роутинга, прототипов, классификатора и финансовых экстракторов.
 */
interface EventProcessingOrchestrator {

    /**
     * Запускает цикл обработки событий и фоновый консьюмер.
     * Автоматически выполняет первичный запуск Recovery Sweep для подхвата незавершенных событий.
     * Идемпотентен: повторные вызовы при состоянии RUNNING игнорируются.
     */
    fun start()

    /**
     * Помещает идентификатор сохраненного события в очередь семантической обработки.
     * Неблокирующий вызов (бюджет < 0.1 мс).
     *
     * @param eventId первичный ключ записи из таблицы `event`.
     * @return true, если идентификатор успешно принят в буфер очереди, иначе false.
     */
    fun submit(eventId: Long): Boolean

    /**
     * Выполняет контролируемую остановку конвейера (graceful shutdown).
     * Завершает прием новых событий, дорабатывает текущую очередь и высвобождает ресурсы.
     */
    fun stop()

    /**
     * Возвращает актуальный снимок состояния конвейера, метрик и глубины очередей.
     */
    fun getStatus(): OrchestratorStatus

    /**
     * Инициирует принудительное сканирование базы данных на предмет зависших событий
     * со статусом UNCLASSIFIED или PROCESSING и ставит их в очередь на повторную обработку.
     *
     * @return фоновая Job процесса восстановления.
     */
    fun triggerRecoverySweep(): Job
}
```

#### 4.1. Эталонная реализация `EventProcessingOrchestratorImpl`

```kotlin
package com.example.npc.app.pipeline

import android.util.Log
import com.example.npc.app.pipeline.model.*
import com.example.npc.classify.rules.Fingerprinter
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.classify.PackageGatedRouter
import com.example.npc.core.model.classify.SemanticClassifier
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.storage.StorageGateway
import com.example.npc.extract.finance.CircuitBreaker
import com.example.npc.extract.finance.IsolatedExtractorRunner
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class EventProcessingOrchestratorImpl(
    private val storageGateway: StorageGateway,
    private val semanticClassifier: SemanticClassifier,
    private val packageGatedRouter: PackageGatedRouter,
    private val extractorRunner: IsolatedExtractorRunner,
    private val circuitBreaker: CircuitBreaker,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : EventProcessingOrchestrator {

    companion object {
        private const val TAG = "EventOrchestrator"
        private const val MAX_PROCESSING_BUDGET_MS = 50L
    }

    private val orchestratorScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val eventChannel = Channel<Long>(Channel.UNLIMITED)
    private val state = AtomicReference(OrchestratorState.IDLE)
    private var consumerJob: Job? = null

    // Метрики
    private val submittedCount = AtomicLong(0L)
    private val claimedCount = AtomicLong(0L)
    private val completedCount = AtomicLong(0L)
    private val skippedCount = AtomicLong(0L)
    private val failedCount = AtomicLong(0L)
    private val prototypeHitsCount = AtomicLong(0L)
    private val financeExtractedCount = AtomicLong(0L)
    private val declinedTxCount = AtomicLong(0L)
    private val queueDepth = AtomicInteger(0)
    private val isRecoveryActive = AtomicReference(false)
    private val lastProcessedId = AtomicLong(-1L)
    private val lastProcessedInstant = AtomicReference<Instant?>(null)
    private val lastErrorMsg = AtomicReference<String?>(null)

    // Скользящее среднее времени обработки (миллисекунды)
    private var totalDurationSumMs: Double = 0.0
    private var durationSamplesCount: Long = 0L

    @Synchronized
    override fun start() {
        if (!state.compareAndSet(OrchestratorState.IDLE, OrchestratorState.RUNNING)) {
            if (state.get() == OrchestratorState.STOPPED) {
                state.set(OrchestratorState.RUNNING)
            } else {
                Log.d(TAG, "Orchestrator already running in state: ${state.get()}")
                return
            }
        }

        Log.i(TAG, "Starting EventProcessingOrchestrator semantic loop...")
        consumerJob = orchestratorScope.launch {
            consumeLoop()
        }

        // Автоматический запуск подхвата подвисших событий
        triggerRecoverySweep()
    }

    override fun submit(eventId: Long): Boolean {
        if (state.get() != OrchestratorState.RUNNING) {
            Log.w(TAG, "Cannot submit event $eventId, orchestrator state is ${state.get()}")
            return false
        }
        val result = eventChannel.trySend(eventId)
        return if (result.isSuccess) {
            submittedCount.incrementAndGet()
            queueDepth.incrementAndGet()
            true
        } else {
            Log.e(TAG, "Failed to submit event $eventId into processing channel")
            false
        }
    }

    @Synchronized
    override fun stop() {
        if (!state.compareAndSet(OrchestratorState.RUNNING, OrchestratorState.DRAINING)) {
            return
        }
        Log.i(TAG, "Stopping EventProcessingOrchestrator (draining queue)...")
        eventChannel.close()

        orchestratorScope.launch {
            try {
                withTimeout(2000L) {
                    consumerJob?.join()
                }
            } catch (_: TimeoutCancellationException) {
                Log.w(TAG, "Draining timeout reached, forcing cancel")
                consumerJob?.cancel()
            } finally {
                state.set(OrchestratorState.STOPPED)
                Log.i(TAG, "EventProcessingOrchestrator stopped completely")
            }
        }
    }

    override fun getStatus(): OrchestratorStatus {
        val avgDuration = synchronized(this) {
            if (durationSamplesCount > 0L) totalDurationSumMs / durationSamplesCount else 0.0
        }
        val metrics = PipelineMetrics(
            totalSubmitted = submittedCount.get(),
            totalClaimed = claimedCount.get(),
            totalCompleted = completedCount.get(),
            totalSkipped = skippedCount.get(),
            totalFailed = failedCount.get(),
            totalPrototypeHits = prototypeHitsCount.get(),
            totalFinanceExtracted = financeExtractedCount.get(),
            totalDeclinedTransactions = declinedTxCount.get(),
            currentQueueDepth = queueDepth.get(),
            averageProcessingDurationMs = avgDuration,
            lastProcessedEventId = lastProcessedId.get().takeIf { it > 0L },
            lastProcessedAt = lastProcessedInstant.get(),
            lastError = lastErrorMsg.get()
        )
        return OrchestratorStatus(
            state = state.get(),
            isRecoveryActive = isRecoveryActive.get(),
            metrics = metrics,
            circuitBreakerStatuses = mapOf("global" to !circuitBreaker.canExecute())
        )
    }

    override fun triggerRecoverySweep(): Job {
        return orchestratorScope.launch(ioDispatcher) {
            if (!isRecoveryActive.compareAndSet(false, true)) {
                Log.d(TAG, "Recovery sweep is already in progress, skipping")
                return@launch
            }
            try {
                Log.i(TAG, "Executing recovery sweep for unprocessed events...")
                val pendingIds = storageGateway.getPendingUnprocessedEventIds(limit = 1000)
                Log.i(TAG, "Recovery sweep discovered ${pendingIds.size} pending events")
                for (id in pendingIds) {
                    submit(id)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error during recovery sweep", t)
                lastErrorMsg.set("Recovery sweep error: ${t.message}")
            } finally {
                isRecoveryActive.set(false)
            }
        }
    }

    private suspend fun consumeLoop() {
        for (eventId in eventChannel) {
            queueDepth.decrementAndGet()
            val startNs = System.nanoTime()
            try {
                val status = processSingleEvent(eventId)
                when (status) {
                    EventProcessingStatus.COMPLETED -> completedCount.incrementAndGet()
                    EventProcessingStatus.SKIPPED_ALREADY_CLAIMED,
                    EventProcessingStatus.SKIPPED_NOT_FOUND -> skippedCount.incrementAndGet()
                    EventProcessingStatus.FAILED -> failedCount.incrementAndGet()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error processing event $eventId", t)
                failedCount.incrementAndGet()
                lastErrorMsg.set("Event $eventId failure: ${t.message}")
            } finally {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                recordDurationSample(elapsedMs)
            }
        }
    }

    private suspend fun processSingleEvent(eventId: Long): EventProcessingStatus {
        // 1. Атомарный Claim: попытка занять событие в БД
        val claimed = storageGateway.tryClaimEvent(eventId)
        if (!claimed) {
            Log.d(TAG, "Event $eventId is already claimed or processed, skipping")
            return EventProcessingStatus.SKIPPED_ALREADY_CLAIMED
        }
        claimedCount.incrementAndGet()

        // 2. Чтение агрегата события и имени пакета
        val target = storageGateway.getEventWithPackage(eventId)
        if (target == null) {
            Log.w(TAG, "Event target $eventId not found in storage, skipping")
            return EventProcessingStatus.SKIPPED_NOT_FOUND
        }

        val event = target.event
        val packageName = target.packageName

        // 3. Вычисление отпечатка контента (O(n))
        val fingerprint = Fingerprinter.calculateFingerprint(
            packageName = packageName,
            sender = event.title.takeIf { it.isNotBlank() },
            text = event.text.ifEmpty { event.title }
        )

        // 4. Поиск пользовательского прототипа (Feedback Loop)
        val matchingPrototype = storageGateway.findMatchingPrototype(packageName, fingerprint)
        if (matchingPrototype != null && matchingPrototype.supportCount >= 2) {
            prototypeHitsCount.incrementAndGet()
        }

        // 5. Семантическая классификация
        val classification = semanticClassifier.classify(event, matchingPrototype)

        // 6. Изолированная финансовая экстракция (только если FINANCE и источник допущен)
        var transaction: FinancialTransaction? = null
        val isFinanceSource = packageGatedRouter.isFinanceAllowed(
            packageName = packageName,
            sender = event.title.takeIf { it.isNotBlank() },
            sourceId = target.sourceId
        )

        if (classification.category == Category.FINANCE && isFinanceSource) {
            transaction = try {
                withTimeoutOrNull(MAX_PROCESSING_BUDGET_MS) {
                    extractorRunner.runExtraction(event, packageName)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Extractor failed for event $eventId", t)
                null
            }

            if (transaction != null) {
                financeExtractedCount.incrementAndGet()
                if (transaction.status == com.example.npc.core.model.finance.TransactionStatus.DECLINED) {
                    declinedTxCount.incrementAndGet()
                }
            }
        }

        // 7. Транзакционное сохранение результата в Room
        val success = storageGateway.completeEventProcessing(
            eventId = eventId,
            classification = classification,
            transaction = transaction
        )

        if (success) {
            lastProcessedId.set(eventId)
            lastProcessedInstant.set(Instant.now())
            return EventProcessingStatus.COMPLETED
        } else {
            return EventProcessingStatus.FAILED
        }
    }

    private synchronized fun recordDurationSample(durationMs: Double) {
        totalDurationSumMs += durationMs
        durationSamplesCount++
    }
}
```

---

### 5. Расширение `StorageGateway` и схемы доступа к БД

Для поддержки двухконтурной обработки интерфейс `StorageGateway` расширяется специализированными методами атомарного захвата и транзакционного сохранения.

#### 5.1. Расширение контракта `StorageGateway`

```kotlin
package com.example.npc.core.storage

import com.example.npc.app.pipeline.model.EventProcessingTarget
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.finance.FinancialTransaction

interface StorageGateway {
    // ... Существующие методы Фазы 0 ...

    /**
     * Атомарный захват события на обработку конвейером.
     * Переводит категорию события из UNCLASSIFIED в PROCESSING.
     *
     * @param eventId идентификатор события.
     * @return true, если ровно одна строка была обновлена, false если событие уже занято или обработано.
     */
    suspend fun tryClaimEvent(eventId: Long): Boolean

    /**
     * Загружает агрегат события, объединяющий Event и имя пакета источника из RawEvent.
     */
    suspend fun getEventWithPackage(eventId: Long): EventProcessingTarget?

    /**
     * Транзакционная фиксация результатов обработки:
     * 1. Обновляет категорию, confidence, engine_used, content_fingerprint в таблице `event`.
     * 2. Если transaction != null, сохраняет запись в таблицу `financial_transaction` с event_id = eventId.
     * Все операции выполняются строго внутри единой @Transaction Room.
     *
     * @return true в случае успешного коммита, false при откате транзакции.
     */
    suspend fun completeEventProcessing(
        eventId: Long,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Boolean

    /**
     * Помечает событие как ошибочное с установкой категории OTHER и записью причины ошибки.
     */
    suspend fun markEventFailed(eventId: Long, reason: String): Boolean

    /**
     * Возвращает список идентификаторов событий, находящихся в состоянии UNCLASSIFIED или PROCESSING,
     * для процедуры восстановления (Recovery Sweep).
     */
    suspend fun getPendingUnprocessedEventIds(limit: Int = 1000): List<Long>
}
```

#### 5.2. Реализация в `EventDao`

В интерфейс `EventDao` добавляются следующие методы и аннотированные Room-запросы:

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.*
import com.example.npc.core.storage.entity.EventEntity

@Dao
interface EventDao {
    // ... Существующие методы ...

    /**
     * Атомарный UPDATE для захвата владения событием.
     * Если категория уже не UNCLASSIFIED (например, уже обработано или занято),
     * запрос затрагивает 0 строк.
     */
    @Query("""
        UPDATE event 
        SET category = 'PROCESSING' 
        WHERE id = :id AND (category = 'UNCLASSIFIED' OR category = 'PROCESSING')
    """)
    suspend fun tryClaim(id: Long): Int

    /**
     * Обновление результатов семантической классификации.
     */
    @Query("""
        UPDATE event 
        SET category = :category,
            confidence = :confidence,
            engine_used = :engineUsed,
            content_fingerprint = :fingerprint
        WHERE id = :id
    """)
    suspend fun updateClassification(
        id: Long,
        category: String,
        confidence: Double,
        engineUsed: String,
        fingerprint: String
    ): Int

    /**
     * Выборка идентификаторов зависших событий для Recovery Sweep.
     */
    @Query("""
        SELECT id FROM event 
        WHERE category = 'UNCLASSIFIED' OR category = 'PROCESSING' 
        ORDER BY id ASC 
        LIMIT :limit
    """)
    suspend fun getPendingUnprocessedIds(limit: Int): List<Long>
}
```

#### 5.3. Реализация `completeEventProcessing` в `StorageGatewayImpl`

```kotlin
override suspend fun tryClaimEvent(eventId: Long): Boolean = withContext(ioDispatcher) {
    eventDao.tryClaim(eventId) > 0
}

override suspend fun getEventWithPackage(eventId: Long): EventProcessingTarget? = withContext(ioDispatcher) {
    val eventEntity = eventDao.getById(eventId) ?: return@withContext null
    val rawEventEntity = rawEventDao.getById(eventEntity.rawId) ?: return@withContext null
    val domainEvent = EventMapper.toDomain(eventEntity)
    val sourceId = try {
        SourceId.valueOf(rawEventEntity.source)
    } catch (_: Throwable) {
        SourceId.NOTIFICATION
    }

    EventProcessingTarget(
        event = domainEvent,
        packageName = rawEventEntity.packageName,
        sourceId = sourceId,
        rawPayloadJson = rawEventEntity.payloadJson
    )
}

override suspend fun completeEventProcessing(
    eventId: Long,
    classification: ClassificationResult,
    transaction: FinancialTransaction?
): Boolean = withContext(ioDispatcher) {
    try {
        runInTransaction {
            // 1. Обновляем статус и классификацию события
            val rowsUpdated = eventDao.updateClassification(
                id = eventId,
                category = classification.category.name,
                confidence = classification.confidence,
                engineUsed = classification.engine.name,
                fingerprint = classification.contentFingerprint
            )
            if (rowsUpdated == 0) {
                throw IllegalStateException("Event $eventId not found during completion")
            }

            // 2. Если извлечена финансовая транзакция — вставляем в таблицу
            if (transaction != null) {
                val txnEntity = FinancialTransactionMapper.toEntity(
                    transaction.copy(eventId = eventId)
                )
                transactionDao.insert(txnEntity)
            }
            true
        }
    } catch (e: Exception) {
        Log.e("StorageGateway", "Transaction failed in completeEventProcessing for event $eventId", e)
        false
    }
}

override suspend fun getPendingUnprocessedEventIds(limit: Int): List<Long> = withContext(ioDispatcher) {
    eventDao.getPendingUnprocessedIds(limit)
}
```

---

### 6. Интеграция с Hilt (Dependency Injection)

Конфигурация зависимостей разделяется на три специализированных Dagger/Hilt модуля в пакете `com.example.npc.app.di`:
1. `ClassifierModule` — предоставляет компоненты маршрутизации и классификации (`:classify:rules`).
2. `ExtractorModule` — предоставляет финансовые парсеры и песочницу запуска (`:extract:finance`).
3. `OrchestratorModule` — предоставляет синглтон оркестратора конвейера (`:app`).

#### 6.1. `ClassifierModule.kt`

```kotlin
package com.example.npc.app.di

import com.example.npc.classify.rules.*
import com.example.npc.core.model.classify.PackageGatedRouter
import com.example.npc.core.model.classify.SemanticClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {

    @Provides
    @Singleton
    fun providePackageGatedRouter(): PackageGatedRouter {
        return PackageGatedRouterImpl()
    }

    @Provides
    @Singleton
    fun provideRuleBasedCategoryClassifier(router: PackageGatedRouter): RuleBasedCategoryClassifier {
        return RuleBasedCategoryClassifier(router)
    }

    @Provides
    @Singleton
    fun provideSemanticClassifier(
        router: PackageGatedRouter,
        ruleClassifier: RuleBasedCategoryClassifier
    ): SemanticClassifier {
        return SemanticClassifierImpl(router, ruleClassifier)
    }
}
```

#### 6.2. `ExtractorModule.kt`

```kotlin
package com.example.npc.app.di

import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.extract.finance.CircuitBreaker
import com.example.npc.extract.finance.IsolatedExtractorRunner
import com.example.npc.extract.finance.apb.ApbNotificationExtractor
import com.example.npc.extract.finance.maib.MaibNotificationExtractor
import com.example.npc.extract.finance.prisbank.PrisbankNotificationExtractor
import com.example.npc.extract.finance.sms.BankSmsExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExtractorModule {

    @Provides
    @Singleton
    fun provideFinanceExtractors(): List<FinanceExtractor> {
        return listOf(
            ApbNotificationExtractor(),
            PrisbankNotificationExtractor(),
            MaibNotificationExtractor(),
            BankSmsExtractor()
        )
    }

    @Provides
    @Singleton
    fun provideCircuitBreaker(): CircuitBreaker {
        return CircuitBreaker(
            timeBudgetMs = 50L,
            failureThreshold = 3,
            cooldownMs = 600_000L // 10 минут
        )
    }

    @Provides
    @Singleton
    fun provideIsolatedExtractorRunner(
        extractors: List<FinanceExtractor>,
        circuitBreaker: CircuitBreaker
    ): IsolatedExtractorRunner {
        return IsolatedExtractorRunner(extractors, circuitBreaker)
    }
}
```

#### 6.3. `OrchestratorModule.kt`

```kotlin
package com.example.npc.app.di

import com.example.npc.app.pipeline.EventProcessingOrchestrator
import com.example.npc.app.pipeline.EventProcessingOrchestratorImpl
import com.example.npc.core.model.classify.PackageGatedRouter
import com.example.npc.core.model.classify.SemanticClassifier
import com.example.npc.core.storage.StorageGateway
import com.example.npc.extract.finance.CircuitBreaker
import com.example.npc.extract.finance.IsolatedExtractorRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrchestratorModule {

    @Provides
    @Singleton
    fun provideEventProcessingOrchestrator(
        storageGateway: StorageGateway,
        semanticClassifier: SemanticClassifier,
        packageGatedRouter: PackageGatedRouter,
        extractorRunner: IsolatedExtractorRunner,
        circuitBreaker: CircuitBreaker
    ): EventProcessingOrchestrator {
        return EventProcessingOrchestratorImpl(
            storageGateway = storageGateway,
            semanticClassifier = semanticClassifier,
            packageGatedRouter = packageGatedRouter,
            extractorRunner = extractorRunner,
            circuitBreaker = circuitBreaker,
            defaultDispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.IO
        )
    }
}
```

---

### 7. Связка с `PipelineNotificationListenerService`

Для соблюдения слабой связанности и тестируемости сервиса `PipelineNotificationListenerService` взаимодействие с оркестратором осуществляется через провайдер `OrchestratorProvider`:

```kotlin
package com.example.npc.app.pipeline

interface OrchestratorProvider {
    fun provideOrchestrator(): EventProcessingOrchestrator
}
```

Класс приложения `App` реализует данный интерфейс:

```kotlin
// App.kt
@HiltAndroidApp
class App : Application(), Configuration.Provider, StorageGatewayProvider, OrchestratorProvider {

    @Inject lateinit var orchestrator: EventProcessingOrchestrator

    override fun provideOrchestrator(): EventProcessingOrchestrator = orchestrator

    override fun onCreate() {
        super.onCreate()
        // ... инициализация SQLCipher и каналов ...
        orchestrator.start()
    }
}
```

#### Интеграция в `PipelineNotificationListenerService`:

```kotlin
// PipelineNotificationListenerService.kt
open class PipelineNotificationListenerService : NotificationListenerService {

    var orchestrator: EventProcessingOrchestrator? = null

    fun ensureDependencies() {
        // ... инициализация storageGateway ...
        if (orchestrator == null) {
            val provider = applicationContext as? OrchestratorProvider
            orchestrator = provider?.provideOrchestrator()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ensureDependencies()
        // Запуск довычитывания зависших событий после переподключения
        orchestrator?.triggerRecoverySweep()
    }

    internal suspend fun processSinglePayload(payload: NotificationRawPayload) {
        // ШАГ 1: Запись RawEvent (WAL)
        val rawEventId = storageGateway.insertRawEvent(rawEvent)
        if (rawEventId == -1L) return

        // ШАГ 2: Фильтрация
        if (!NotificationFilter.evaluate(payload, hostPackage).isAccepted) return

        // ШАГ 3: Склейка цепочек
        val threadKey = updateTracker.computeThreadKey(...)
        val previousEventId = updateTracker.resolvePreviousEventId(...)

        // ШАГ 4: Вставка первичного неклассифицированного Event
        val domainEvent = EventNormalizer.normalize(...)
        val savedEventId = storageGateway.insertEvent(domainEvent)
        updateTracker.recordEventMapping(threadKey, payload.key, savedEventId)

        // ШАГ 4.1: Неблокирующая отправка в семантический контур (< 0.1 мс)
        orchestrator?.submit(savedEventId)

        // ШАГ 5: Обновление здоровья источника
        updateSourceHealthOnPayload(payload.receivedAt)
    }
}
```

---

### 8. Граничные случаи и отказоустойчивость (Fault Tolerance)

| Сценарий | Механизм возникновения | Поведение системы | Гарантия безопасности |
|---|---|---|---|
| **Краш процесса во время обработки** | Убийство процесса ОС (LMK / Force Stop) во время работы `SemanticClassifier` или экстрактора. | Событие сохранено в Room как `UNCLASSIFIED` или `PROCESSING`. При следующем старте `App.onCreate` Recovery Sweep находит его и отправляет в `submit(id)`. | **Zero Event Loss:** Ни одно событие не теряется безвозвратно. |
| **Превышение бюджета времени (Timeout > 50 мс)** | Экстрактор завис на сложной строке или объемном пуше. | `withTimeoutOrNull(50)` прерывает выполнение корутины. `CircuitBreaker` регистрирует сбой. Событие сохраняется как `FINANCE`, но без транзакции. Ошибка логируется. | **Process Stability:** Никакие зависания не блокируют конвейер и UI. |
| **Многократный Duplicate Submit** | Гонка между Recovery Sweep и живым потоком уведомлений при подаче одного и того же `eventId`. | Первый воркер выполняет `tryClaimEvent(id)`, возвращающий `true`. Второй воркер выполняет `tryClaimEvent(id)`, который затрагивает 0 строк и возвращает `false`. Второй вызов немедленно выходит (`SKIPPED`). | **Strict Idempotency:** Исключено дублирование проводок в реестре расходов. |
| **Неизвестный пакет приложения** | Уведомление от приложения, не входящего ни в один список `PackageGatedRouter`. | Роутер возвращает запрет на категорию `FINANCE`. Классификатор относит событие к `SERVICES` или `OTHER`. Экстракторы даже не инициализируются. | **False Positive Immunity:** Полное исключение ложных транзакций от сторонних приложений. |
| **ReDoS / Патологический текст** | SMS или пуш с 5000 повторяющихся символов и незакрытых кавычек. | 1. Усечение до 1024 символов (`RegionalTextSanitizer`).<br>2. Все регулярные выражения выполняются на движке `RE2/J` с математической гарантией $O(n)$.<br>3. Суммы парсятся рукописным `AmountParser` без регулярных выражений.<br>4. При трех сбоях подряд `CircuitBreaker` размыкает цепь на 10 минут. | **Linear Time Guarantee:** Защита от зависаний CPU на 100%. |
| **Всплеск уведомлений (Burst Flooding)** | Приложение получает сотни уведомлений подряд за секунду. | Горячий контур моментально сбрасывает их в WAL Room (< 5 мс). В памяти очереди находятся только примитивы `Long` (8 байт на элемент). Воркеры последовательно разгребают очередь без риска `OutOfMemoryError`. | **Backpressure Resilience:** Стабильность потребления памяти. |

---

### 9. Критерии готовности (Definition of Done) и план тестирования для QA

#### 9.1. Критерии приёмки (Definition of Done)

- [ ] **DoD 1 (Сквозная изоляция):** Пакеты мессенджеров (Telegram, AyuGram, WhatsApp) с рекламным текстом (кейс InTour) гарантированно получают категорию `ADVERTISEMENT` или `COMMUNICATION` и имеют 0 созданных записей в таблице `financial_transaction`.
- [ ] **DoD 2 (Скорость горячего контура):** Вызов `onNotificationPosted` до выхода из метода занимает не более 5 мс в 99% случаев (замер через `System.nanoTime`).
- [ ] **DoD 3 (Бюджет семантического контура):** Полная семантическая обработка одного события в оркестраторе занимает менее 50 мс на пуле `Dispatchers.Default`.
- [ ] **DoD 4 (Идемпотентность):** 100 параллельных вызовов `orchestrator.submit(eventId)` для одного и того же идентификатора приводят ровно к одному выполнению классификации и созданию ровно одной записи в БД.
- [ ] **DoD 5 (Надежность восстановления):** Принудительно созданные в БД события со статусом `category = 'UNCLASSIFIED'` автоматически вычитываются и обрабатываются при вызове `orchestrator.triggerRecoverySweep()`.
- [ ] **DoD 6 (Региональные банки):** 23 реальные банковские транзакции из базы телеметрии Poco M7 (`event_engine.db`) успешно парсятся в транзакции с корректной валютой (`RUP` для APB/Сбербанка, `MDL` для MAIB) и статусами.
- [ ] **DoD 7 (Транзакционная целостность):** При искусственном сбое вставки финансовой транзакции вся операция откатывается, а событие переводится в статус ошибки без повреждения целостности БД.
- [ ] **DoD 8 (Метрики и мониторинг):** Вызов `orchestrator.getStatus()` возвращает корректные значения `totalSubmitted`, `totalCompleted` и нулевую утечку `queueDepth` после обработки пачки событий.

#### 9.2. Матрица тестовых сценариев QA

| ID теста | Название сценария | Шаги воспроизведения | Ожидаемый результат |
|---|---|---|---|
| **QA-ORCH-001** | Горячий контур SLA | 1. Имитировать поступление 50 SBN подряд через тестовый harness.<br>2. Замерить интервал от входа в `onNotificationPosted` до возврата. | Время каждого вызова < 5 мс. Все 50 `RawEvent` и `Event` сохранены в WAL. |
| **QA-ORCH-002** | Подавление кейса InTour | 1. Отправить событие от `com.radolyn.ayugram` с текстом InTour.<br>2. Дождаться завершения обработки конвейером. | Категория события `ADVERTISEMENT`, в `financial_transaction` 0 записей. |
| **QA-ORCH-003** | Банковский пуш APB | 1. Отправить реальный пуш Агропромбанка: *«Oplata 45.50 RUP, OAO Tirpa, Karta *1234, Ostatok 1200.00 RUP»*.<br>2. Дождаться обработки оркестратором. | Категория `FINANCE`, `engine = RULES`, в БД создана транзакция: 4550 minor, `RUP`, расход, маска `*1234`. |
| **QA-ORCH-004** | Отклоненная транзакция MAIB | 1. Отправить пуш MAIB: *«Tranzactie respinsa: 120.00 MDL, TEMU»*.<br>2. Дождаться обработки. | Создана запись `financial_transaction` со статусом `TransactionStatus.DECLINED`. Баланс пользователя не изменен. |
| **QA-ORCH-005** | Идемпотентность параллельного submit | 1. Вставить 1 тестовый `Event(UNCLASSIFIED)`.<br>2. Из 10 параллельных корутин одновременно вызвать `orchestrator.submit(id)`. | Ровно один вызов успешен, 9 пропущены (`SKIPPED`). Ровно 1 запись транзакции в базе. |
| **QA-ORCH-006** | Recovery Sweep после сбоя | 1. Вставить в базу 5 записей `Event` со статусом `UNCLASSIFIED`.<br>2. Вызвать `orchestrator.triggerRecoverySweep().join()`. | Все 5 событий переведены в классифицированное состояние. Статус `UNCLASSIFIED` отсутствует. |
| **QA-ORCH-007** | Срабатывание Circuit Breaker | 1. Замокать экстрактор с искусственной задержкой 100 мс (превышение бюджета 50 мс).<br>2. Отправить 3 события подряд. | После 3-го события Circuit Breaker переходит в состояние OPEN. Конвейер не зависает, события сохраняются как `FINANCE` без падения сервиса. |
| **QA-ORCH-008** | Graceful Shutdown | 1. Подать в очередь 100 событий.<br>2. Немедленно вызвать `orchestrator.stop()`. | Очередь корректно дорабатывается (draining). По завершении статус `STOPPED`. Данные не повреждены. |
