package com.example.npc.ui.timeline

import app.cash.turbine.test
import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ui.timeline.model.CategoryFilter
import com.example.npc.ui.timeline.model.EventUiModel
import com.example.npc.ui.timeline.model.TimelineUiEffect
import com.example.npc.ui.timeline.ui.TimelineViewModel
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var storageGateway: StorageGateway
    private lateinit var eventsFlow: MutableStateFlow<List<Event>>
    private lateinit var transactionsFlow: MutableStateFlow<List<FinancialTransaction>>
    private lateinit var healthFlow: MutableStateFlow<List<SourceHealth>>

    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        storageGateway = mockk(relaxed = true)
        eventsFlow = MutableStateFlow(emptyList())
        transactionsFlow = MutableStateFlow(emptyList())
        healthFlow = MutableStateFlow(emptyList())

        every { storageGateway.observeEvents(any()) } returns eventsFlow
        every { storageGateway.observeTransactions(any()) } returns transactionsFlow
        every { storageGateway.observeSourceHealth() } returns healthFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createEvent(
        id: Long,
        title: String,
        text: String,
        category: Category = Category.UNKNOWN
    ): Event = Event(
        id = id,
        rawId = id * 10,
        ts = now,
        title = title,
        text = text,
        normalizedText = text.lowercase(),
        lang = Lang.RU,
        threadKey = null,
        isUpdateOf = null,
        category = category
    )

    private fun createTransaction(
        id: Long,
        eventId: Long,
        minor: Long = 1500L,
        currency: CurrencyCode = CurrencyCode.RUP,
        merchant: String? = "Шериф-15"
    ): FinancialTransaction = FinancialTransaction(
        id = id,
        eventId = eventId,
        bank = "APB",
        type = TransactionType.DEBIT,
        amount = Money(minor, currency),
        balance = null,
        merchant = merchant,
        accountMask = "**5576",
        status = TransactionStatus.COMPLETED,
        occurredAt = now,
        extractorId = "apb.notification",
        extractorVersion = 1,
        rawText = "Покупка",
        createdAt = now
    )

    @Test
    fun `merges observeEvents and observeTransactions associating financial data by eventId`() = runTest(testDispatcher) {
        val event1 = createEvent(1L, "Агропромбанк", "Покупка 15.00 RUP", Category.FINANCE)
        val event2 = createEvent(2L, "Telegram", "Привет", Category.COMMUNICATION)
        val txn1 = createTransaction(id = 100L, eventId = 1L, minor = 1500L, merchant = "Шериф-15")

        eventsFlow.value = listOf(event1, event2)
        transactionsFlow.value = listOf(txn1)

        val viewModel = TimelineViewModel(storageGateway)

        viewModel.uiState.test {
            val state = awaitItem()
            state.events.size shouldBe 2

            val uiEvent1 = state.events.first { it.id == 1L }
            uiEvent1.financialData shouldNotBe null
            uiEvent1.financialData?.formattedAmount shouldBe "-15.00"
            uiEvent1.financialData?.merchant shouldBe "Шериф-15"

            val uiEvent2 = state.events.first { it.id == 2L }
            uiEvent2.financialData shouldBe null

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters events reactively by CategoryFilter`() = runTest(testDispatcher) {
        val finEvent = createEvent(1L, "Bank", "Money", Category.FINANCE)
        val commEvent = createEvent(2L, "Chat", "Hello", Category.COMMUNICATION)
        val srvEvent = createEvent(3L, "Taxi", "Ride", Category.SERVICES)

        eventsFlow.value = listOf(finEvent, commEvent, srvEvent)

        val viewModel = TimelineViewModel(storageGateway)

        // ALL filter
        viewModel.uiState.value.events.size shouldBe 3

        // Filter by FINANCE
        viewModel.onCategoryFilterSelected(CategoryFilter.FINANCE)
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().category shouldBe Category.FINANCE

        // Filter by COMMUNICATION
        viewModel.onCategoryFilterSelected(CategoryFilter.COMMUNICATION)
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().category shouldBe Category.COMMUNICATION

        // Filter by SERVICES
        viewModel.onCategoryFilterSelected(CategoryFilter.SERVICES)
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().category shouldBe Category.SERVICES

        // Reset to ALL
        viewModel.onCategoryFilterSelected(CategoryFilter.ALL)
        viewModel.uiState.value.events.size shouldBe 3
    }

    @Test
    fun `search filters events across title, text, and financial merchant`() = runTest(testDispatcher) {
        val event1 = createEvent(1L, "APB", "Payment", Category.FINANCE)
        val event2 = createEvent(2L, "Friend", "Dinner tomorrow", Category.COMMUNICATION)
        val txn1 = createTransaction(id = 100L, eventId = 1L, merchant = "Linella")

        eventsFlow.value = listOf(event1, event2)
        transactionsFlow.value = listOf(txn1)

        val viewModel = TimelineViewModel(storageGateway)

        // Search by merchant name
        viewModel.onSearchQueryChanged("Linella")
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().id shouldBe 1L

        // Search by text
        viewModel.onSearchQueryChanged("dinner")
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().id shouldBe 2L
    }

    @Test
    fun `onCategoryCorrected calls storageGateway recordUserCorrection and emits snackbar`() = runTest(testDispatcher) {
        coEvery {
            storageGateway.recordUserCorrection(
                eventId = any(),
                packageName = any(),
                contentFingerprint = any(),
                newCategory = any(),
                correctedAt = any()
            )
        } returns Unit

        val viewModel = TimelineViewModel(storageGateway)

        viewModel.effects.test {
            viewModel.onCategoryCorrected(eventId = 42L, newCategory = Category.ADVERTISEMENT)

            val effect = awaitItem()
            (effect is TimelineUiEffect.ShowSnackbar) shouldBe true
            (effect as TimelineUiEffect.ShowSnackbar).message.contains("ADVERTISEMENT") shouldBe true

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            storageGateway.recordUserCorrection(
                eventId = 42L,
                packageName = any(),
                contentFingerprint = any(),
                newCategory = Category.ADVERTISEMENT,
                correctedAt = any()
            )
        }
    }

    @Test
    fun `onEventClicked asynchronously loads raw event and populates packageName and payloadJson`() = runTest(testDispatcher) {
        val raw = RawEvent(
            id = 55L,
            seq = 1L,
            source = SourceId.NOTIFICATION,
            packageName = "com.bank.app",
            receivedAt = now,
            payloadJson = """{"secret":"123"}""",
            hash = DeduplicationKey("f".repeat(64))
        )
        coEvery { storageGateway.getRawEventByEventId(10L) } returns raw

        val viewModel = TimelineViewModel(storageGateway)
        val eventUi = EventUiModel(
            id = 10L,
            rawId = 55L,
            displayTitle = "Title",
            displayText = "Text",
            normalizedText = "text",
            timeLabel = "12:00",
            sourceName = "Уведомление",
            sourceIconRes = 0,
            langLabel = "RU",
            isUpdate = false,
            threadKey = null,
            packageName = null,
            payloadJson = null,
            contentFingerprint = "fp_abc"
        )

        viewModel.onEventClicked(eventUi)

        val selected = viewModel.uiState.value.selectedEventForDetails
        selected shouldNotBe null
        selected?.id shouldBe 10L
        selected?.packageName shouldBe "com.bank.app"
        selected?.payloadJson shouldBe """{"secret":"123"}"""
    }

    @Test
    fun `loadMore dynamically increments currentLimit and updates subscription`() = runTest(testDispatcher) {
        val viewModel = TimelineViewModel(storageGateway)

        viewModel.currentLimit.value shouldBe 100

        viewModel.loadMore()
        viewModel.currentLimit.value shouldBe 200

        viewModel.loadMore(100)
        viewModel.currentLimit.value shouldBe 300
    }
}
