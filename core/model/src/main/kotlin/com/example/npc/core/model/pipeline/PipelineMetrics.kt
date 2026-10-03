package com.example.npc.core.model.pipeline

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
    val lastError: String? = null,
    val financeWithoutPayload: Long = 0L
)
