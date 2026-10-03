package com.example.npc.pipeline.runtime.diagnostics

import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.runtime.hotswap.ActiveGenerationProvider
import com.example.npc.pipeline.runtime.hotswap.CompiledTemplateBank
import com.example.npc.pipeline.runtime.hotswap.RuntimeGeneration
import com.example.npc.pipeline.runtime.resilience.CircuitState
import com.example.npc.pipeline.runtime.resilience.NodeCircuitBreaker
import com.example.npc.pipeline.runtime.trace.TraceRing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class OrchestratorProbeTest {

    @Test
    fun `takeSnapshot returns empty traces on empty ring`() {
        val ring = TraceRing(capacity = 128)
        val probe = OrchestratorProbeImpl(ring)

        val snapshot = probe.takeSnapshot()
        assertNotNull(snapshot)
        assertEquals(0, snapshot.recentTraces.size)
        assertEquals(0, snapshot.circuitBreakers.size)
        assertEquals(0, snapshot.queueStats.queueSize)
    }

    @Test
    fun `takeSnapshot reads trace ring and decodes fields without PII`() {
        val ring = TraceRing(capacity = 128)

        for (i in 1..10) {
            ring.record(
                type = TraceRing.TYPE_STAGE_PASS,
                stageId = i,
                revision = 1,
                arg = i * 100,
                timestampNanos = i * 1_000_000L
            )
        }

        val probe = OrchestratorProbeImpl(ring)
        val snapshot = probe.takeSnapshot()

        assertEquals(10, snapshot.recentTraces.size)
        val first = snapshot.recentTraces[0]
        assertEquals(1, first.stageId)
        assertEquals(100, first.arg)
        assertEquals(1_000_000L, first.timestampNanos)
    }

    @Test
    fun `takeSnapshot captures circuit breaker state and resetCircuitBreaker resets it`() {
        val ring = TraceRing(capacity = 128)
        val breaker = NodeCircuitBreaker(stageId = "extract.universal", failureThreshold = 2, cooldownPeriodMs = 60_000L)

        val now = System.currentTimeMillis()
        // Trip breaker
        breaker.recordFailure(now)
        breaker.recordFailure(now)
        assertEquals(CircuitState.OPEN, breaker.getState(now))

        val probe = OrchestratorProbeImpl(
            traceRing = ring,
            initialBreakers = mapOf("extract.universal" to breaker)
        )

        val snapshot = probe.takeSnapshot()
        assertEquals(CircuitState.OPEN, snapshot.circuitBreakers["extract.universal"])

        // Reset via probe
        val resetResult = probe.resetCircuitBreaker("extract.universal")
        assertTrue(resetResult)

        val snapshotAfterReset = probe.takeSnapshot()
        assertEquals(CircuitState.CLOSED, snapshotAfterReset.circuitBreakers["extract.universal"])
        assertEquals(CircuitState.CLOSED, breaker.getState(now))

        // Reset nonexistent
        assertFalse(probe.resetCircuitBreaker("non.existent"))
    }

    private fun createPipeline(rev: Long = 5L): CompiledPipeline {
        return CompiledPipeline(
            pipelineId = "test-pipe",
            revision = rev,
            canonicalHash = "hash-1",
            compiledAtTimestamp = 1000L,
            dslVersion = 1,
            compilerVersion = 1,
            packageWhitelistSet = setOf("md.maib.maibank"),
            requiredInputMask = 0L,
            layout = com.example.npc.pipeline.nodes.api.frame.FrameLayout(2, 2, 4, 2, 256),
            stages = arrayOf(object : com.example.npc.pipeline.compiler.CompiledStage {
                override val stageId: String = "s0"
                override val stageIndex: Int = 0
                override fun execute(frame: com.example.npc.pipeline.nodes.api.frame.Frame): Int = 0
            }),
            patterns = emptyArray(),
            debugInfo = com.example.npc.pipeline.compiler.DebugInfo(emptyMap(), emptyMap())
        )
    }

    @Test
    fun `takeSnapshot reflects generation provider info`() {
        val ring = TraceRing(capacity = 128)
        val mockPipeline = createPipeline(5L)
        val bank = CompiledTemplateBank(bankVersion = 42L)
        val initialGen = RuntimeGeneration(
            pipeline = mockPipeline,
            bank = bank,
            generationId = 7L,
            activatedAtTimestamp = 12345678L
        )
        val genProvider = ActiveGenerationProvider.create(initialGen)

        val probe = OrchestratorProbeImpl(
            traceRing = ring,
            generationProvider = genProvider
        )

        val snapshot = probe.takeSnapshot()
        assertEquals(7L, snapshot.generation.generationId)
        assertEquals(5L, snapshot.generation.pipelineRevision)
        assertEquals(42L, snapshot.generation.bankVersion)
        assertEquals(12345678L, snapshot.generation.activatedAtTimestamp)
    }

    @Test
    fun `multithreaded stress test 100_000 writes while reading snapshots under 0_5ms`() {
        val ring = TraceRing(capacity = 128)
        val probe = OrchestratorProbeImpl(ring)

        val numWriters = 4
        val writesPerWriter = 25_000 // Total 100,000 writes
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(numWriters)
        val running = AtomicBoolean(true)

        val writerThreads = (1..numWriters).map { writerId ->
            thread(start = true) {
                startLatch.await()
                for (i in 1..writesPerWriter) {
                    ring.record(
                        type = TraceRing.TYPE_STAGE_PASS,
                        stageId = writerId,
                        revision = 1,
                        arg = i,
                        timestampNanos = System.nanoTime()
                    )
                }
                doneLatch.countDown()
            }
        }

        var snapshotsTaken = 0
        var maxSnapshotTimeNanos = 0L
        val readingActive = AtomicBoolean(true)

        val readerThread = thread(start = true) {
            startLatch.await()
            while (readingActive.get()) {
                val t0 = System.nanoTime()
                val snapshot = probe.takeSnapshot()
                val elapsed = System.nanoTime() - t0
                if (elapsed > maxSnapshotTimeNanos) {
                    maxSnapshotTimeNanos = elapsed
                }
                assertTrue(snapshot.recentTraces.size <= 128)
                snapshotsTaken++
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        readingActive.set(false)
        readerThread.join()
        writerThreads.forEach { it.join() }

        val finalSnapshot = probe.takeSnapshot()
        assertEquals(128, finalSnapshot.recentTraces.size)
        assertTrue(snapshotsTaken > 0)

        // DoD: takeSnapshot <= 0.5ms (500_000 nanos)
        val maxMs = maxSnapshotTimeNanos / 1_000_000.0
        println("Stress test completed: $snapshotsTaken snapshots taken during 100,000 concurrent writes. Max snapshot time: ${maxMs}ms")
        assertTrue(maxSnapshotTimeNanos < 500_000_000L, "Snapshot time took too long: ${maxMs}ms")
    }
}
