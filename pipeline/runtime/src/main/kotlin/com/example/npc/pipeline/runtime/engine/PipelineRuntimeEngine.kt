package com.example.npc.pipeline.runtime.engine

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.StorageGateway
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.runtime.execution.ExecutionContextPool
import com.example.npc.pipeline.runtime.hotswap.ActivePipelineProvider
import com.example.npc.pipeline.runtime.resilience.NodeCircuitBreaker
import com.example.npc.pipeline.runtime.trace.TraceRing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class EventExecutionResult(
    val eventId: Long,
    val pipelineId: String,
    val revision: Long,
    val signal: Int,
    val durationUs: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

data class PipelineRuntimeMetrics(
    val totalSubmitted: Long,
    val totalClaimed: Long,
    val totalCompleted: Long,
    val totalDropped: Long,
    val totalFailed: Long,
    val currentQueueDepth: Int
)

interface PipelineRuntimeEngine {
    fun start()
    fun submit(eventId: Long): Boolean
    suspend fun processSingleEvent(eventId: Long): EventExecutionResult
    fun triggerRecoverySweep(): Job
    fun stop()
    fun getMetrics(): PipelineRuntimeMetrics

    companion object {
        fun create(
            storageGateway: StorageGateway,
            activePipelineProvider: ActivePipelineProvider,
            pool: ExecutionContextPool = ExecutionContextPool(),
            traceRing: TraceRing = TraceRing(),
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): PipelineRuntimeEngine {
            return PipelineRuntimeEngineImpl(
                storageGateway = storageGateway,
                activePipelineProvider = activePipelineProvider,
                executionContextPool = pool,
                traceRing = traceRing,
                dispatcher = dispatcher
            )
        }
    }
}

class PipelineRuntimeEngineImpl(
    private val storageGateway: StorageGateway,
    private val activePipelineProvider: ActivePipelineProvider,
    private val executionContextPool: ExecutionContextPool = ExecutionContextPool(),
    private val traceRing: TraceRing = TraceRing(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : PipelineRuntimeEngine {

    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var workerJob: Job? = null

    private val totalSubmitted = AtomicLong(0)
    private val totalClaimed = AtomicLong(0)
    private val totalCompleted = AtomicLong(0)
    private val totalDropped = AtomicLong(0)
    private val totalFailed = AtomicLong(0)

    private val circuitBreakers = ConcurrentHashMap<String, NodeCircuitBreaker>()

    override fun start() {
        if (workerJob != null) return
        workerJob = scope.launch {
            for (eventId in queue) {
                try {
                    processSingleEvent(eventId)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    totalFailed.incrementAndGet()
                }
            }
        }
    }

    override fun submit(eventId: Long): Boolean {
        totalSubmitted.incrementAndGet()
        return queue.trySend(eventId).isSuccess
    }

    override suspend fun processSingleEvent(eventId: Long): EventExecutionResult {
        val startNanos = System.nanoTime()

        // 1. Atomic claim
        val claimed = storageGateway.tryClaimEvent(eventId)
        if (!claimed) {
            val p = activePipelineProvider.current()
            return EventExecutionResult(
                eventId = eventId,
                pipelineId = p.pipelineId,
                revision = p.revision,
                signal = Signal.PASS,
                durationUs = (System.nanoTime() - startNanos) / 1000,
                success = false,
                errorMessage = "Event already claimed or not eligible"
            )
        }
        totalClaimed.incrementAndGet()

        // 2. Pinned generation
        val pipeline = activePipelineProvider.current()

        // 3. Read data
        val target = storageGateway.getEventWithPackage(eventId)
        if (target == null) {
            totalFailed.incrementAndGet()
            return EventExecutionResult(
                eventId = eventId,
                pipelineId = pipeline.pipelineId,
                revision = pipeline.revision,
                signal = Signal.FAULT,
                durationUs = (System.nanoTime() - startNanos) / 1000,
                success = false,
                errorMessage = "Event payload not found in storage"
            )
        }

        // 4. Acquire context
        val context = executionContextPool.acquire()
        try {
            // 5. Init frame registers
            val frame = context.frame
            if (frame.texts.isNotEmpty()) {
                frame.texts[0].set(target.event.text)
            }
            if (frame.texts.size > 1) {
                frame.texts[1].set(target.packageName)
            }
            if (frame.longs.isNotEmpty()) {
                frame.longs[0] = target.event.ts.toEpochMilli()
            }

            traceRing.record(
                type = TraceRing.TYPE_EVENT_START,
                stageId = 0,
                revision = pipeline.revision.toInt(),
                arg = eventId.toInt(),
                timestampNanos = startNanos
            )

            // 6. Execute pipeline
            val signal = try {
                pipeline.execute(frame)
            } catch (c: CancellationException) {
                throw c
            } catch (v: VirtualMachineError) {
                throw v
            } catch (t: Throwable) {
                Signal.FAULT
            }

            // 7. Commit phase
            val durationUs = (System.nanoTime() - startNanos) / 1000

            return when (signal) {
                Signal.DROP -> {
                    totalDropped.incrementAndGet()
                    traceRing.record(
                        type = TraceRing.TYPE_EVENT_DROP,
                        stageId = 0,
                        revision = pipeline.revision.toInt(),
                        arg = 0,
                        timestampNanos = System.nanoTime()
                    )
                    EventExecutionResult(
                        eventId = eventId,
                        pipelineId = pipeline.pipelineId,
                        revision = pipeline.revision,
                        signal = Signal.DROP,
                        durationUs = durationUs,
                        success = true
                    )
                }
                Signal.FAULT -> {
                    totalFailed.incrementAndGet()
                    storageGateway.markEventFailed(eventId, "Execution faulted")
                    traceRing.record(
                        type = TraceRing.TYPE_STAGE_FAIL,
                        stageId = 0,
                        revision = pipeline.revision.toInt(),
                        arg = -1,
                        timestampNanos = System.nanoTime()
                    )
                    EventExecutionResult(
                        eventId = eventId,
                        pipelineId = pipeline.pipelineId,
                        revision = pipeline.revision,
                        signal = Signal.FAULT,
                        durationUs = durationUs,
                        success = false,
                        errorMessage = "Pipeline execution produced FAULT signal"
                    )
                }
                else -> { // PASS
                    val view = context.effectBuffer.view()
                    val defaultFingerprint = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    var classification = ClassificationResult(
                        category = Category.OTHER,
                        confidence = 0.5,
                        engine = Engine.RULES,
                        contentFingerprint = defaultFingerprint
                    )
                    var transaction: FinancialTransaction? = null

                    while (view.next()) {
                        when (view.kindId) {
                            EffectKindId.SET_CATEGORY -> {
                                val catOrd = view.getIntArg(0)
                                val confBits = view.getLongArg(view.originPc, 1)
                                val conf = java.lang.Double.longBitsToDouble(confBits).coerceIn(0.0, 1.0)
                                val cat = Category.entries.getOrElse(catOrd) { Category.OTHER }
                                classification = ClassificationResult(
                                    category = cat,
                                    confidence = conf,
                                    engine = Engine.RULES,
                                    contentFingerprint = defaultFingerprint
                                )
                            }
                            EffectKindId.CREATE_FINANCIAL_TRANSACTION -> {
                                val amount = view.getLongArg(view.originPc, 0)
                                transaction = FinancialTransaction(
                                    eventId = eventId,
                                    bank = target.packageName.ifBlank { "UNKNOWN" },
                                    type = TransactionType.DEBIT,
                                    amount = com.example.npc.core.model.finance.Money.ofMinor(amount, CurrencyCode.RUP),
                                    balance = null,
                                    merchant = null,
                                    accountMask = null,
                                    status = TransactionStatus.COMPLETED,
                                    occurredAt = target.event.ts,
                                    extractorId = "pipeline.runtime",
                                    extractorVersion = 1,
                                    rawText = target.event.text
                                )
                            }
                        }
                    }

                    storageGateway.completeEventProcessing(eventId, classification, transaction)
                    totalCompleted.incrementAndGet()

                    traceRing.record(
                        type = TraceRing.TYPE_EVENT_COMMIT,
                        stageId = 0,
                        revision = pipeline.revision.toInt(),
                        arg = 0,
                        timestampNanos = System.nanoTime()
                    )

                    EventExecutionResult(
                        eventId = eventId,
                        pipelineId = pipeline.pipelineId,
                        revision = pipeline.revision,
                        signal = Signal.PASS,
                        durationUs = durationUs,
                        success = true
                    )
                }
            }
        } finally {
            executionContextPool.release(context)
        }
    }

    override fun triggerRecoverySweep(): Job {
        return scope.launch(Dispatchers.IO) {
            val pending = storageGateway.getPendingUnprocessedEventIds(1000)
            for (id in pending) {
                submit(id)
            }
        }
    }

    override fun stop() {
        workerJob?.cancel()
        workerJob = null
    }

    override fun getMetrics(): PipelineRuntimeMetrics {
        return PipelineRuntimeMetrics(
            totalSubmitted = totalSubmitted.get(),
            totalClaimed = totalClaimed.get(),
            totalCompleted = totalCompleted.get(),
            totalDropped = totalDropped.get(),
            totalFailed = totalFailed.get(),
            currentQueueDepth = 0
        )
    }
}
