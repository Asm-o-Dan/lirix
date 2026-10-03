package com.example.npc.feature.replay.report

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.FinancialTransaction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ReplayDiffReportTest {

    @Test
    fun `DiffMetrics validates matchRatePercent bounds`() {
        val validMetrics = DiffMetrics(
            totalProcessedEvents = 100,
            identicalOutcomesCount = 95,
            discrepancyCount = 5,
            matchRatePercent = 95.0f,
            avgDraftLatencyUs = 120,
            avgActiveLatencyUs = 130,
            p95DraftLatencyUs = 250,
            p95ActiveLatencyUs = 260,
            confidenceShift = ConfidenceShift(0.92, 0.90, 0.02)
        )
        validMetrics.matchRatePercent shouldBe 95.0f

        assertThrows<IllegalArgumentException> {
            validMetrics.copy(matchRatePercent = -1.0f)
        }
        assertThrows<IllegalArgumentException> {
            validMetrics.copy(matchRatePercent = 100.5f)
        }
    }

    @Test
    fun `EventDiscrepancy holds all discrepancy types`() {
        val tx = FinancialTransaction(
            id = 1L,
            eventId = 10L,
            bank = "APB",
            type = com.example.npc.core.model.finance.TransactionType.DEBIT,
            amount = com.example.npc.core.model.finance.Money(5000L, com.example.npc.core.model.finance.CurrencyCode.MDL),
            balance = null,
            merchant = "Store",
            accountMask = "*1234",
            status = com.example.npc.core.model.finance.TransactionStatus.COMPLETED,
            occurredAt = Instant.now(),
            extractorId = "test",
            extractorVersion = 1,
            rawText = "Spent 50 MDL"
        )

        for (type in DiscrepancyType.entries) {
            val discrepancy = EventDiscrepancy(
                eventId = 42L,
                packageName = "com.apb.mobile",
                notificationTitle = "Payment",
                notificationText = "Spent 50 MDL",
                discrepancyType = type,
                baselineCategory = Category.FINANCE,
                draftCategory = Category.UNCLASSIFIED,
                baselineTransaction = tx,
                draftTransaction = null,
                baselineLatencyUs = 80L,
                draftLatencyUs = 120L
            )
            discrepancy.discrepancyType shouldBe type
            discrepancy.eventId shouldBe 42L
        }
    }

    @Test
    fun `ReplayDiffReport encapsulates metrics and discrepancies list`() {
        val metrics = DiffMetrics(
            totalProcessedEvents = 1,
            identicalOutcomesCount = 0,
            discrepancyCount = 1,
            matchRatePercent = 0.0f,
            avgDraftLatencyUs = 50,
            avgActiveLatencyUs = 40,
            p95DraftLatencyUs = 50,
            p95ActiveLatencyUs = 40,
            confidenceShift = ConfidenceShift(0.5, 0.9, -0.4)
        )
        val discrepancy = EventDiscrepancy(
            eventId = 1L,
            packageName = "com.pkg",
            notificationTitle = "T",
            notificationText = "B",
            discrepancyType = DiscrepancyType.CATEGORY_MISMATCH,
            baselineCategory = Category.COMMUNICATION,
            draftCategory = Category.UNCLASSIFIED,
            baselineTransaction = null,
            draftTransaction = null,
            baselineLatencyUs = 40L,
            draftLatencyUs = 50L
        )

        val report = ReplayDiffReport(
            metrics = metrics,
            discrepancies = listOf(discrepancy),
            executionDurationMs = 250,
            isTruncated = false
        )

        report.metrics shouldBe metrics
        report.discrepancies.size shouldBe 1
        report.executionDurationMs shouldBe 250
        report.isTruncated shouldBe false
    }
}
