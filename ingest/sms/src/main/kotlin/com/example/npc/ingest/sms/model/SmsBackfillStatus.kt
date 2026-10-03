package com.example.npc.ingest.sms.model

import java.time.Instant

sealed interface SmsBackfillStatus {
    data object NotStarted : SmsBackfillStatus
    data class InProgress(val processedCount: Int, val duplicateCount: Int) : SmsBackfillStatus
    data class Completed(val totalImported: Int, val totalDuplicates: Int, val completedAt: Instant) : SmsBackfillStatus
    data class Failed(val error: String, val failedAt: Instant) : SmsBackfillStatus
}
