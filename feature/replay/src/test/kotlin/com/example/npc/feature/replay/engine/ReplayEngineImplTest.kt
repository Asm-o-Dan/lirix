package com.example.npc.feature.replay.engine

import app.cash.turbine.test
import com.example.npc.core.model.classify.Category
import com.example.npc.core.storage.dao.ReplayEventSourceDao
import com.example.npc.core.storage.dao.ReplayHistoricalEvent
import com.example.npc.feature.replay.report.DiscrepancyType
import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.compiler.CompiledStage
import com.example.npc.pipeline.compiler.DebugInfo
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayEngineImplTest {

    private val eventSourceDao: ReplayEventSourceDao = mockk(relaxed = true)
    private val replayEngine = ReplayEngineImpl(eventSourceDao)

    private fun createPipeline(onExecute: (Frame) -> Int): CompiledPipeline {
        val stage = object : CompiledStage {
            override val stageId: String = "test-stage"
            override val stageIndex: Int = 0
            override fun execute(frame: Frame): Int = onExecute(frame)
        }
        return CompiledPipeline(
            pipelineId = "test-pipeline",
            revision = 1L,
            canonicalHash = "canonical-hash-123",
            compiledAtTimestamp = 1000L,
            dslVersion = 1,
            compilerVersion = 1,
            packageWhitelistSet = emptySet(),
            requiredInputMask = 0L,
            layout = FrameLayout(10, 10, 10, 2, 2048),
            stages = arrayOf(stage),
            patterns = emptyArray(),
            debugInfo = DebugInfo(emptyMap(), emptyMap())
        )
    }

    @Test
    fun `runSimulation on empty database emits Completed with zero processed events and 100 percent match`() = runTest {
        coEvery { eventSourceDao.countEvents(any(), any()) } returns 0

        val pipeline = createPipeline { Signal.PASS }

        replayEngine.runSimulation(pipeline).test {
            val completed = awaitItem()
            completed.shouldBeInstanceOf<ReplayStatus.Completed>()
            completed.report.metrics.totalProcessedEvents shouldBe 0
            completed.report.metrics.matchRatePercent shouldBe 100.0f
            completed.report.discrepancies.isEmpty() shouldBe true
            awaitComplete()
        }
    }

    @Test
    fun `runSimulation executes draft pipeline and reports match against historical events`() = runTest {
        val events = listOf(
            ReplayHistoricalEvent(
                eventId = 1L,
                rawId = 10L,
                packageName = "com.bank.app",
                title = "Payment",
                text = "Spent 100 MDL",
                postTime = 1000L,
                historicalCategory = "FINANCE",
                historicalConfidence = 0.95,
                historicalTransactionJson = null
            ),
            ReplayHistoricalEvent(
                eventId = 2L,
                rawId = 11L,
                packageName = "com.bank.app",
                title = "Payment 2",
                text = "Spent 200 MDL",
                postTime = 2000L,
                historicalCategory = "FINANCE",
                historicalConfidence = 0.95,
                historicalTransactionJson = null
            )
        )

        coEvery { eventSourceDao.countEvents(any(), any()) } returns 2
        coEvery { eventSourceDao.getEventsAfterId(0L, any(), any(), any()) } returns events

        // Draft pipeline emits Category.FINANCE
        val draftPipeline = createPipeline { frame ->
            frame.effects.begin(EffectKindId.SET_CATEGORY)
            frame.effects.putLong(Category.FINANCE.ordinal.toLong())
            frame.effects.putLong(java.lang.Double.doubleToRawLongBits(0.95))
            frame.effects.end()
            Signal.PASS
        }

        replayEngine.runSimulation(draftPipeline).test {
            val running = awaitItem()
            running.shouldBeInstanceOf<ReplayStatus.Running>()
            running.processedCount shouldBe 2
            running.totalCount shouldBe 2
            running.percentProgress shouldBe 1.0f

            val completed = awaitItem()
            completed.shouldBeInstanceOf<ReplayStatus.Completed>()
            completed.report.metrics.totalProcessedEvents shouldBe 2
            completed.report.metrics.identicalOutcomesCount shouldBe 2
            completed.report.metrics.discrepancyCount shouldBe 0
            completed.report.metrics.matchRatePercent shouldBe 100.0f
            awaitComplete()
        }
    }

    @Test
    fun `runSimulation detects CATEGORY_MISMATCH when draft diverges from baseline`() = runTest {
        val event = ReplayHistoricalEvent(
            eventId = 1L,
            rawId = 10L,
            packageName = "com.chat.app",
            title = "Chat Message",
            text = "Hello there",
            postTime = 1000L,
            historicalCategory = "COMMUNICATION",
            historicalConfidence = 0.90,
            historicalTransactionJson = null
        )

        coEvery { eventSourceDao.countEvents(any(), any()) } returns 1
        coEvery { eventSourceDao.getEventsAfterId(0L, any(), any(), any()) } returns listOf(event)

        // Draft classifies as Category.OTHER
        val draftPipeline = createPipeline { frame ->
            frame.effects.begin(EffectKindId.SET_CATEGORY)
            frame.effects.putLong(Category.OTHER.ordinal.toLong())
            frame.effects.putLong(java.lang.Double.doubleToRawLongBits(0.80))
            frame.effects.end()
            Signal.PASS
        }

        replayEngine.runSimulation(draftPipeline).test {
            awaitItem().shouldBeInstanceOf<ReplayStatus.Running>()
            val completed = awaitItem()
            completed.shouldBeInstanceOf<ReplayStatus.Completed>()
            completed.report.metrics.totalProcessedEvents shouldBe 1
            completed.report.metrics.discrepancyCount shouldBe 1
            completed.report.metrics.matchRatePercent shouldBe 0.0f

            val discrepancy = completed.report.discrepancies.first()
            discrepancy.discrepancyType shouldBe DiscrepancyType.CATEGORY_MISMATCH
            discrepancy.baselineCategory shouldBe Category.COMMUNICATION
            discrepancy.draftCategory shouldBe Category.OTHER
            awaitComplete()
        }
    }

    @Test
    fun `runSimulation emits Failed status when eventSourceDao throws`() = runTest {
        val exception = RuntimeException("SQLite connection failed")
        coEvery { eventSourceDao.countEvents(any(), any()) } throws exception

        val pipeline = createPipeline { Signal.PASS }

        replayEngine.runSimulation(pipeline).test {
            val failed = awaitItem()
            failed.shouldBeInstanceOf<ReplayStatus.Failed>()
            failed.throwable shouldBe exception
            failed.processedBeforeFailure shouldBe 0
            awaitComplete()
        }
    }
}
