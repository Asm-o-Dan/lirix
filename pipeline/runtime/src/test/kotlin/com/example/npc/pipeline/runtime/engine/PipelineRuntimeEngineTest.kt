package com.example.npc.pipeline.runtime.engine

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TxStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.model.pipeline.EventProcessingTarget
import com.example.npc.core.storage.StorageGateway
import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.compiler.CompiledStage
import com.example.npc.pipeline.compiler.DebugInfo
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.example.npc.pipeline.runtime.hotswap.ActivePipelineProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PipelineRuntimeEngineTest {

    private class FakeStorageGateway : StorageGateway {
        val claimedEvents = mutableSetOf<Long>()
        val completedEvents = mutableMapOf<Long, Pair<ClassificationResult, FinancialTransaction?>>()
        val failedEvents = mutableMapOf<Long, String>()
        val targets = mutableMapOf<Long, EventProcessingTarget>()
        val pendingIds = mutableListOf<Long>()

        override suspend fun tryClaimEvent(eventId: Long): Boolean {
            return if (eventId in claimedEvents) false else {
                claimedEvents.add(eventId)
                true
            }
        }

        override suspend fun getEventWithPackage(eventId: Long): EventProcessingTarget? = targets[eventId]

        override suspend fun completeEventProcessing(
            eventId: Long,
            classification: ClassificationResult,
            transaction: FinancialTransaction?
        ): Boolean {
            completedEvents[eventId] = classification to transaction
            return true
        }

        override suspend fun markEventFailed(eventId: Long, reason: String): Boolean {
            failedEvents[eventId] = reason
            return true
        }

        override suspend fun getPendingUnprocessedEventIds(limit: Int): List<Long> = pendingIds.take(limit)

        override suspend fun insertRawEvent(event: RawEvent): Long = 1L
        override suspend fun insertEvent(event: Event): Long = 1L
        override suspend fun upsertSourceHealth(health: SourceHealth) {}
        override suspend fun findDuplicate(key: DeduplicationKey): Long? = null
        override suspend fun getRawEvent(id: Long): RawEvent? = null
        override suspend fun getRawEventByEventId(eventId: Long): RawEvent? = null
        override suspend fun insertTransaction(transaction: FinancialTransaction): Long = 1L
        override suspend fun saveProcessedEvent(
            event: Event,
            classification: ClassificationResult,
            transaction: FinancialTransaction?
        ): Long = 1L

        override fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>> = emptyFlow()
        override fun observeTransactionsByPeriod(from: Instant, to: Instant): Flow<List<FinancialTransaction>> = emptyFlow()
        override suspend fun getTransactionByEventId(eventId: Long): FinancialTransaction? = null
        override suspend fun getAggregatedTotals(direction: TransactionType, from: Instant, to: Instant): Map<CurrencyCode, Long> = emptyMap()
        override fun observeAggregatedTotals(
            from: Instant,
            to: Instant,
            direction: TransactionType?,
            reduceExpenseByRefund: Boolean,
            includeSuggested: Boolean
        ): Flow<Map<CurrencyCode, com.example.npc.core.model.finance.AggregatedSums>> = emptyFlow()
        override suspend fun recordUserCorrection(eventId: Long, category: Category) {}
        override suspend fun recordUserCorrection(eventId: Long, packageName: String, contentFingerprint: String, newCategory: Category, correctedAt: Instant) {}
        override suspend fun findMatchingPrototype(packageName: String, fingerprint: String): UserPrototype? = null
        override fun observeEvents(limit: Int): Flow<List<Event>> = emptyFlow()
        override fun observeSourceHealth(): Flow<List<SourceHealth>> = emptyFlow()
        override suspend fun exportAllToJson(): String = "{}"
        override suspend fun clearAllData() {}
    }

    private fun createPipeline(
        stageAction: (Frame) -> Int = { Signal.PASS },
        rev: Long = 1L
    ): CompiledPipeline {
        return CompiledPipeline(
            pipelineId = "test-pipe",
            revision = rev,
            canonicalHash = "hash-$rev",
            compiledAtTimestamp = 1000L,
            dslVersion = 1,
            compilerVersion = 1,
            packageWhitelistSet = setOf("com.apb.mobile"),
            requiredInputMask = 0L,
            layout = FrameLayout(2, 2, 2, 2, 64),
            stages = arrayOf(object : CompiledStage {
                override val stageId: String = "s0"
                override val stageIndex: Int = 0
                override fun execute(frame: Frame): Int = stageAction(frame)
            }),
            patterns = emptyArray(),
            debugInfo = DebugInfo(emptyMap(), emptyMap())
        )
    }

    private fun createSampleTarget(eventId: Long): EventProcessingTarget {
        return EventProcessingTarget(
            event = Event(
                id = eventId,
                rawId = eventId,
                ts = Instant.now(),
                title = "Notification",
                text = "Perevod 500 RUP",
                normalizedText = "perevod 500 rup",
                lang = Lang.RU
            ),
            packageName = "com.apb.mobile",
            sourceId = SourceId.NOTIFICATION,
            rawPayloadJson = "{}"
        )
    }

    @Test
    fun processSingleEvent_completesAndPersists() = runBlocking {
        val storage = FakeStorageGateway()
        val target = createSampleTarget(101L)
        storage.targets[101L] = target

        val pipeline = createPipeline({ Signal.PASS })
        val provider = ActivePipelineProvider.create(pipeline)
        val engine = PipelineRuntimeEngine.create(storage, provider)

        val res = engine.processSingleEvent(101L)
        assertTrue(res.success)
        assertEquals(Signal.PASS, res.signal)
        assertTrue(storage.completedEvents.containsKey(101L))

        val metrics = engine.getMetrics()
        assertEquals(1L, metrics.totalClaimed)
        assertEquals(1L, metrics.totalCompleted)
        assertEquals(0L, metrics.totalFailed)
    }

    @Test
    fun processSingleEvent_handlesDropSignal() = runBlocking {
        val storage = FakeStorageGateway()
        storage.targets[202L] = createSampleTarget(202L)

        val pipeline = createPipeline({ Signal.DROP })
        val provider = ActivePipelineProvider.create(pipeline)
        val engine = PipelineRuntimeEngine.create(storage, provider)

        val res = engine.processSingleEvent(202L)
        assertTrue(res.success)
        assertEquals(Signal.DROP, res.signal)
        assertFalse(storage.completedEvents.containsKey(202L))

        val metrics = engine.getMetrics()
        assertEquals(1L, metrics.totalDropped)
    }

    @Test
    fun faultIsolation_exceptionsDoNotCrashEngine() = runBlocking {
        val storage = FakeStorageGateway()
        storage.targets[303L] = createSampleTarget(303L)

        val throwingPipeline = createPipeline({
            throw IllegalStateException("Catastrophic stage failure")
        })
        val provider = ActivePipelineProvider.create(throwingPipeline)
        val engine = PipelineRuntimeEngine.create(storage, provider)

        val res = engine.processSingleEvent(303L)
        assertFalse(res.success)
        assertEquals(Signal.FAULT, res.signal)
        assertTrue(storage.failedEvents.containsKey(303L))

        val metrics = engine.getMetrics()
        assertEquals(1L, metrics.totalFailed)
    }

    @Test
    fun `legacy numeric transaction effect resolves title direction and uses current effect index`() = runBlocking {
        val storage = FakeStorageGateway()
        val base = createSampleTarget(401L)
        storage.targets[401L] = base.copy(event = base.event.copy(title = "Зачислено", text = "100 RUP"))
        val pipeline = createPipeline({ frame ->
            // originPc is a program counter, never an effect index.
            frame.effects.currentPc = 12
            frame.effects.begin(EffectKindId.SET_CATEGORY)
            frame.effects.putLong(Category.FINANCE.ordinal.toLong())
            frame.effects.putLong(0.95.toRawBits())
            frame.effects.end()
            frame.effects.begin(EffectKindId.CREATE_FINANCIAL_TRANSACTION)
            frame.effects.putLong(10000L)
            frame.effects.end()
            Signal.PASS
        })
        val engine = PipelineRuntimeEngine.create(storage, ActivePipelineProvider.create(pipeline))
        assertTrue(engine.processSingleEvent(401L).success)
        val (classification, tx) = storage.completedEvents.getValue(401L)
        assertEquals(Category.FINANCE, classification.category)
        assertEquals(TransactionType.CREDIT, tx!!.type)
        assertEquals(10000L, tx.amount.minor)
    }

    @Test
    fun `unresolved numeric transaction remains suggested and OTP is vetoed`() = runBlocking {
        for ((title, text, suppressed) in listOf(
            Triple("Bank", "Операция 100 RUP", false),
            Triple("OTP code", "Payment 100 RUP code 123456", true)
        )) {
            val storage = FakeStorageGateway()
            val base = createSampleTarget(402L)
            storage.targets[402L] = base.copy(event = base.event.copy(title = title, text = text))
            val pipeline = createPipeline({ frame ->
                frame.effects.begin(EffectKindId.CREATE_FINANCIAL_TRANSACTION)
                frame.effects.putLong(10000L)
                frame.effects.end()
                Signal.PASS
            })
            val engine = PipelineRuntimeEngine.create(storage, ActivePipelineProvider.create(pipeline))
            assertTrue(engine.processSingleEvent(402L).success)
            val tx = storage.completedEvents.getValue(402L).second
            if (suppressed) assertEquals(null, tx) else {
                assertEquals(TransactionType.UNKNOWN, tx!!.type)
                assertEquals(TxStatus.SUGGESTED, tx.txStatus)
            }
        }
    }
}
