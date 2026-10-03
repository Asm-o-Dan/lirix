package com.example.npc.feature.analytics

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.model.finance.AggregatedSums
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.model.pipeline.EventProcessingTarget
import com.example.npc.core.storage.StorageGateway
import com.example.npc.feature.analytics.model.AnalyticsUiIntent
import com.example.npc.feature.analytics.model.RefundCalculationMode
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialAnalyticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class FakeStorageGateway : StorageGateway {
        val totalsFlow = MutableSharedFlow<Map<CurrencyCode, AggregatedSums>>(replay = 1)
        val txFlow = MutableSharedFlow<List<FinancialTransaction>>(replay = 1)

        override fun observeAggregatedTotals(
            from: Instant,
            to: Instant,
            direction: TransactionType?,
            reduceExpenseByRefund: Boolean,
            includeSuggested: Boolean
        ): Flow<Map<CurrencyCode, AggregatedSums>> = totalsFlow

        override fun observeTransactionsByPeriod(from: Instant, to: Instant): Flow<List<FinancialTransaction>> = txFlow

        override suspend fun insertRawEvent(event: RawEvent): Long = 0L
        override suspend fun insertEvent(event: Event): Long = 0L
        override suspend fun upsertSourceHealth(health: SourceHealth) {}
        override suspend fun findDuplicate(key: DeduplicationKey): Long? = null
        override suspend fun getRawEvent(id: Long): RawEvent? = null
        override suspend fun getRawEventByEventId(eventId: Long): RawEvent? = null
        override suspend fun insertTransaction(transaction: FinancialTransaction): Long = 0L
        override suspend fun saveProcessedEvent(event: Event, classification: ClassificationResult, transaction: FinancialTransaction?): Long = 0L
        override fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>> = flowOf(emptyList())
        override suspend fun getTransactionByEventId(eventId: Long): FinancialTransaction? = null
        override suspend fun getAggregatedTotals(direction: TransactionType, from: Instant, to: Instant): Map<CurrencyCode, Long> = emptyMap()
        override suspend fun recordUserCorrection(eventId: Long, category: Category) {}
        override suspend fun recordUserCorrection(eventId: Long, packageName: String, contentFingerprint: String, newCategory: Category, correctedAt: Instant) {}
        override suspend fun findMatchingPrototype(packageName: String, fingerprint: String): UserPrototype? = null
        override fun observeEvents(limit: Int): Flow<List<Event>> = flowOf(emptyList())
        override fun observeSourceHealth(): Flow<List<SourceHealth>> = flowOf(emptyList())
        override suspend fun exportAllToJson(): String = "{}"
        override suspend fun clearAllData() {}
        override suspend fun tryClaimEvent(eventId: Long): Boolean = false
        override suspend fun getEventWithPackage(eventId: Long): EventProcessingTarget? = null
        override suspend fun completeEventProcessing(eventId: Long, classification: ClassificationResult, transaction: FinancialTransaction?): Boolean = false
        override suspend fun markEventFailed(eventId: Long, reason: String): Boolean = false
        override suspend fun getPendingUnprocessedEventIds(limit: Int): List<Long> = emptyList()
    }

    @Test
    fun `observes aggregated totals and populates distinct cards for MDL, RUP, USD, EUR, RUB without conversion`() = testScope.runTest {
        val fakeStorage = FakeStorageGateway()
        fakeStorage.totalsFlow.tryEmit(
            mapOf(
                CurrencyCode.MDL to AggregatedSums(expenseMinor = 15000L, incomeMinor = 5000L, refundMinor = 2000L, count = 10),
                CurrencyCode.RUP to AggregatedSums(expenseMinor = 8000L, incomeMinor = 0L, refundMinor = 0L, count = 4),
                CurrencyCode.USD to AggregatedSums(expenseMinor = 3500L, incomeMinor = 10000L, refundMinor = 500L, count = 2)
            )
        )
        fakeStorage.txFlow.tryEmit(emptyList())

        val viewModel = FinancialAnalyticsViewModel(
            storageGateway = fakeStorage,
            coroutineScope = this
        )

        try {
            testScheduler.advanceUntilIdle()

            val cards = viewModel.currentState.cards
            cards shouldHaveSize 5 // MDL, RUP, USD, EUR, RUB

            val mdlCard = cards.first { it.currency == CurrencyCode.MDL }
            mdlCard.totalExpenseMinor shouldBe 13000L // 15000 - 2000 (REDUCE_EXPENSE by default)
            mdlCard.totalIncomeMinor shouldBe 5000L
            mdlCard.totalRefundMinor shouldBe 2000L
            mdlCard.transactionCount shouldBe 10

            val rupCard = cards.first { it.currency == CurrencyCode.RUP }
            rupCard.totalExpenseMinor shouldBe 8000L
            rupCard.totalIncomeMinor shouldBe 0L
            rupCard.transactionCount shouldBe 4

            val eurCard = cards.first { it.currency == CurrencyCode.EUR }
            eurCard.totalExpenseMinor shouldBe 0L
            eurCard.transactionCount shouldBe 0
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `toggling refund mode adjusts expense and income according to ADR-306`() = testScope.runTest {
        val fakeStorage = FakeStorageGateway()
        fakeStorage.totalsFlow.tryEmit(
            mapOf(
                CurrencyCode.MDL to AggregatedSums(expenseMinor = 10000L, incomeMinor = 2000L, refundMinor = 3000L, count = 5)
            )
        )
        fakeStorage.txFlow.tryEmit(emptyList())

        val viewModel = FinancialAnalyticsViewModel(
            storageGateway = fakeStorage,
            coroutineScope = this
        )

        try {
            testScheduler.advanceUntilIdle()

            // По умолчанию REDUCE_EXPENSE
            var mdlCard = viewModel.currentState.cards.first { it.currency == CurrencyCode.MDL }
            mdlCard.totalExpenseMinor shouldBe 7000L // 10000 - 3000
            mdlCard.totalIncomeMinor shouldBe 2000L

            // Переключаем в TREAT_AS_INCOME
            viewModel.dispatch(AnalyticsUiIntent.SetRefundMode(RefundCalculationMode.TREAT_AS_INCOME))
            testScheduler.advanceUntilIdle()

            mdlCard = viewModel.currentState.cards.first { it.currency == CurrencyCode.MDL }
            mdlCard.totalExpenseMinor shouldBe 10000L // без вычета
            mdlCard.totalIncomeMinor shouldBe 5000L  // 2000 + 3000
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `currency selection toggles active filter`() = testScope.runTest {
        val fakeStorage = FakeStorageGateway()
        val viewModel = FinancialAnalyticsViewModel(
            storageGateway = fakeStorage,
            coroutineScope = this
        )

        try {
            viewModel.currentState.activeCurrencyFilter shouldBe null

            viewModel.dispatch(AnalyticsUiIntent.SelectCurrency(CurrencyCode.RUP))
            viewModel.currentState.activeCurrencyFilter shouldBe CurrencyCode.RUP

            // Повторный клик сбрасывает фильтр
            viewModel.dispatch(AnalyticsUiIntent.SelectCurrency(CurrencyCode.RUP))
            viewModel.currentState.activeCurrencyFilter shouldBe null
        } finally {
            viewModel.close()
        }
    }
}
