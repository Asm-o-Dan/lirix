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
import com.example.npc.core.model.finance.TransactionStatus
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
    private val financeWithoutPayloadCount = AtomicLong(0L)
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
                try { Log.d(TAG, "Orchestrator already running in state: ${state.get()}") } catch (_: Throwable) {}
                return
            }
        }

        try { Log.i(TAG, "Starting EventProcessingOrchestrator semantic loop...") } catch (_: Throwable) {}
        consumerJob = orchestratorScope.launch {
            consumeLoop()
        }

        // Автоматический запуск подхвата подвисших событий
        triggerRecoverySweep()
    }

    override fun submit(eventId: Long): Boolean {
        if (state.get() != OrchestratorState.RUNNING) {
            try { Log.w(TAG, "Cannot submit event $eventId, orchestrator state is ${state.get()}") } catch (_: Throwable) {}
            return false
        }
        val result = eventChannel.trySend(eventId)
        return if (result.isSuccess) {
            submittedCount.incrementAndGet()
            queueDepth.incrementAndGet()
            true
        } else {
            try { Log.e(TAG, "Failed to submit event $eventId into processing channel") } catch (_: Throwable) {}
            false
        }
    }

    @Synchronized
    override fun stop() {
        if (!state.compareAndSet(OrchestratorState.RUNNING, OrchestratorState.DRAINING)) {
            return
        }
        try { Log.i(TAG, "Stopping EventProcessingOrchestrator (draining queue)...") } catch (_: Throwable) {}
        eventChannel.close()

        orchestratorScope.launch {
            try {
                withTimeout(2000L) {
                    consumerJob?.join()
                }
            } catch (_: TimeoutCancellationException) {
                try { Log.w(TAG, "Draining timeout reached, forcing cancel") } catch (_: Throwable) {}
                consumerJob?.cancel()
            } finally {
                state.set(OrchestratorState.STOPPED)
                try { Log.i(TAG, "EventProcessingOrchestrator stopped completely") } catch (_: Throwable) {}
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
            lastError = lastErrorMsg.get(),
            financeWithoutPayload = financeWithoutPayloadCount.get()
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
                try { Log.d(TAG, "Recovery sweep is already in progress, skipping") } catch (_: Throwable) {}
                return@launch
            }
            try {
                try { Log.i(TAG, "Executing recovery sweep for unprocessed events...") } catch (_: Throwable) {}
                val pendingIds = storageGateway.getPendingUnprocessedEventIds(limit = 1000)
                try { Log.i(TAG, "Recovery sweep discovered ${pendingIds.size} pending events") } catch (_: Throwable) {}
                for (id in pendingIds) {
                    submit(id)
                }
            } catch (t: Throwable) {
                try { Log.e(TAG, "Error during recovery sweep", t) } catch (_: Throwable) {}
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
                try { Log.e(TAG, "Unexpected error processing event $eventId", t) } catch (_: Throwable) {}
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
            try { Log.d(TAG, "Event $eventId is already claimed or processed, skipping") } catch (_: Throwable) {}
            return EventProcessingStatus.SKIPPED_ALREADY_CLAIMED
        }
        claimedCount.incrementAndGet()

        // 2. Чтение агрегата события и имени пакета
        val target = storageGateway.getEventWithPackage(eventId)
        if (target == null) {
            try { Log.w(TAG, "Event target $eventId not found in storage, skipping") } catch (_: Throwable) {}
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
        val classification = semanticClassifier.classify(event, packageName, matchingPrototype)

        // 6. Изолированная финансовая экстракция (только если FINANCE и источник допущен)
        var transaction: FinancialTransaction? = null
        val isFinanceSource = packageGatedRouter.canClassifyAsFinance(
            packageName = packageName,
            senderOrTitle = event.title.takeIf { it.isNotBlank() },
            sourceId = target.sourceId
        )

        if (classification.category == Category.FINANCE && isFinanceSource) {
            transaction = try {
                withTimeoutOrNull(MAX_PROCESSING_BUDGET_MS) {
                    extractorRunner.runExtraction(event, packageName)
                }
            } catch (t: Throwable) {
                try { Log.e(TAG, "Extractor failed for event $eventId", t) } catch (_: Throwable) {}
                null
            }

            if (transaction != null) {
                financeExtractedCount.incrementAndGet()
                if (transaction.status == TransactionStatus.DECLINED) {
                    declinedTxCount.incrementAndGet()
                }
            }
        }

        // Метрика financeWithoutPayload (P3-UNI-008):
        // Если классификатор определил категорию FINANCE с высокой вероятностью (confidence >= 0.85),
        // но ни один экстрактор не смог извлечь финансовую информацию (transaction == null), инкрементировать счетчик
        if (classification.category == Category.FINANCE && classification.confidence >= 0.85 && transaction == null) {
            financeWithoutPayloadCount.incrementAndGet()
        }

        // 7. Транзакционное сохранение результата в Room
        val success = storageGateway.completeEventProcessing(
            eventId = eventId,
            classification = classification,
            transaction = transaction
        )

        return if (success) {
            lastProcessedId.set(eventId)
            lastProcessedInstant.set(Instant.now())
            EventProcessingStatus.COMPLETED
        } else {
            EventProcessingStatus.FAILED
        }
    }

    @Synchronized
    private fun recordDurationSample(durationMs: Double) {
        totalDurationSumMs += durationMs
        durationSamplesCount++
    }
}
