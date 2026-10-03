package com.example.npc.core.storage

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.model.pipeline.EventProcessingTarget
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface StorageGateway {
    // Ingest (Фаза 0)
    suspend fun insertRawEvent(event: RawEvent): Long
    suspend fun insertEvent(event: Event): Long
    suspend fun upsertSourceHealth(health: SourceHealth)
    suspend fun findDuplicate(key: DeduplicationKey): Long?
    suspend fun getRawEvent(id: Long): RawEvent?
    suspend fun getRawEventByEventId(eventId: Long): RawEvent?

    // Финансовые транзакции (Фаза 1)
    suspend fun insertTransaction(transaction: FinancialTransaction): Long
    suspend fun saveProcessedEvent(
        event: Event,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Long

    fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>>
    fun observeTransactionsByPeriod(from: Instant, to: Instant): Flow<List<FinancialTransaction>>
    suspend fun getTransactionByEventId(eventId: Long): FinancialTransaction?
    suspend fun getAggregatedTotals(
        direction: TransactionType,
        from: Instant,
        to: Instant
    ): Map<CurrencyCode, Long>

    fun observeAggregatedTotals(
        from: Instant,
        to: Instant,
        direction: TransactionType? = null,
        reduceExpenseByRefund: Boolean = true,
        includeSuggested: Boolean = false
    ): Flow<Map<CurrencyCode, com.example.npc.core.model.finance.AggregatedSums>>

    // Обучение и обратная связь (Feedback Loop)
    suspend fun recordUserCorrection(eventId: Long, category: Category)
    suspend fun recordUserCorrection(
        eventId: Long,
        packageName: String,
        contentFingerprint: String,
        newCategory: Category,
        correctedAt: Instant = Instant.now()
    )
    suspend fun findMatchingPrototype(packageName: String, fingerprint: String): UserPrototype?

    // Наблюдение и экспорт
    fun observeEvents(limit: Int): Flow<List<Event>>
    fun observeSourceHealth(): Flow<List<SourceHealth>>
    suspend fun exportEventsJson(): String = exportAllToJson()
    suspend fun exportAllToJson(): String
    suspend fun clearAllData()
    suspend fun deleteAllData() = clearAllData()
    suspend fun deleteAll() = deleteAllData()

    // Оркестрация конвейера (zone/app-pipeline)
    suspend fun tryClaimEvent(eventId: Long): Boolean
    suspend fun getEventWithPackage(eventId: Long): EventProcessingTarget?
    suspend fun completeEventProcessing(
        eventId: Long,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Boolean
    suspend fun markEventFailed(eventId: Long, reason: String): Boolean
    suspend fun getPendingUnprocessedEventIds(limit: Int = 1000): List<Long>

    // Ретроактивная дедупликация (AUDIT-010)
    suspend fun collapseExistingDuplicates(): Int = 0
}

interface StorageGatewayProvider {
    fun provideStorageGateway(): StorageGateway
}
