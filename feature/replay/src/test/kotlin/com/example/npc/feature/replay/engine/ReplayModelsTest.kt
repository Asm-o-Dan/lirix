package com.example.npc.feature.replay.engine

import com.example.npc.feature.replay.report.ConfidenceShift
import com.example.npc.feature.replay.report.DiffMetrics
import com.example.npc.feature.replay.report.ReplayDiffReport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ReplayModelsTest {

    @Test
    fun `ReplayCriteria default parameters adhere to contract`() {
        val criteria = ReplayCriteria.Default

        criteria.timeRange shouldBe TimeRange.Last7Days
        criteria.packageFilter shouldBe null
        criteria.maxEventsLimit shouldBe 1000
        criteria.chunkSize shouldBe 150
    }

    @Test
    fun `TimeRange Custom accepts valid intervals and rejects inverted intervals`() {
        val now = Instant.now()
        val later = now.plusSeconds(3600)

        val validRange = TimeRange.Custom(start = now, end = later)
        validRange.start shouldBe now
        validRange.end shouldBe later

        val sameInstant = TimeRange.Custom(start = now, end = now)
        sameInstant.start shouldBe now

        assertThrows<IllegalArgumentException> {
            TimeRange.Custom(start = later, end = now)
        }
    }

    @Test
    fun `ReplayCriteria validates maxEventsLimit and chunkSize`() {
        assertThrows<IllegalArgumentException> {
            ReplayCriteria(maxEventsLimit = 0)
        }
        assertThrows<IllegalArgumentException> {
            ReplayCriteria(maxEventsLimit = -10)
        }
        assertThrows<IllegalArgumentException> {
            ReplayCriteria(chunkSize = 0)
        }
        assertThrows<IllegalArgumentException> {
            ReplayCriteria(chunkSize = -5)
        }
    }

    @Test
    fun `ReplayStatus states structure and invariants`() {
        val idle: ReplayStatus = ReplayStatus.Idle
        idle shouldBe ReplayStatus.Idle

        val running = ReplayStatus.Running(
            processedCount = 50,
            totalCount = 100,
            percentProgress = 0.5f,
            currentEventId = 1234L
        )
        running.processedCount shouldBe 50
        running.totalCount shouldBe 100
        running.percentProgress shouldBe 0.5f
        running.currentEventId shouldBe 1234L

        val report = ReplayDiffReport(
            metrics = DiffMetrics(
                totalProcessedEvents = 0,
                identicalOutcomesCount = 0,
                discrepancyCount = 0,
                matchRatePercent = 100f,
                avgDraftLatencyUs = 0,
                avgActiveLatencyUs = 0,
                p95DraftLatencyUs = 0,
                p95ActiveLatencyUs = 0,
                confidenceShift = ConfidenceShift(0.0, 0.0, 0.0)
            ),
            discrepancies = emptyList(),
            executionDurationMs = 10
        )
        val completed = ReplayStatus.Completed(report)
        completed.report shouldBe report

        val error = RuntimeException("Boom")
        val failed = ReplayStatus.Failed(error, 42)
        failed.throwable shouldBe error
        failed.processedBeforeFailure shouldBe 42
    }
}
