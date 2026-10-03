package com.example.npc.feature.replay.report

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.FinancialTransaction

enum class DiscrepancyType {
    CATEGORY_MISMATCH,
    CONFIDENCE_DROP,
    TRANSACTION_EXTRACTED_VS_NONE,
    TRANSACTION_MISSED,
    TRANSACTION_AMOUNT_DIFF,
    TRANSACTION_CURRENCY_DIFF,
    EXECUTION_FAILED,
    DROPPED_VS_PASSED
}

data class ConfidenceShift(
    val averageDraft: Double,
    val averageActive: Double,
    val delta: Double
)

data class EventDiscrepancy(
    val eventId: Long,
    val packageName: String,
    val notificationTitle: String,
    val notificationText: String,
    val discrepancyType: DiscrepancyType,
    val baselineCategory: Category,
    val draftCategory: Category,
    val baselineTransaction: FinancialTransaction?,
    val draftTransaction: FinancialTransaction?,
    val baselineLatencyUs: Long,
    val draftLatencyUs: Long
)

data class DiffMetrics(
    val totalProcessedEvents: Int,
    val identicalOutcomesCount: Int,
    val discrepancyCount: Int,
    val matchRatePercent: Float,
    val avgDraftLatencyUs: Long,
    val avgActiveLatencyUs: Long,
    val p95DraftLatencyUs: Long,
    val p95ActiveLatencyUs: Long,
    val confidenceShift: ConfidenceShift
) {
    init {
        require(matchRatePercent in 0.0f..100.0f) {
            "matchRatePercent must be between 0.0 and 100.0, but was $matchRatePercent"
        }
    }
}

data class ReplayDiffReport(
    val metrics: DiffMetrics,
    val discrepancies: List<EventDiscrepancy>,
    val executionDurationMs: Long,
    val isTruncated: Boolean = false
)
