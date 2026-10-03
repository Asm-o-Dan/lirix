package com.example.npc.ui.timeline.ui

import app.cash.turbine.test
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.storage.StorageGateway
import com.example.npc.core.storage.bank.TemplateBankManager
import com.example.npc.core.storage.bank.TemplateState
import com.example.npc.feature.editor.model.EditorUiState
import com.example.npc.induction.model.AmountFormatSpec
import com.example.npc.induction.model.BuiltTemplate
import com.example.npc.ui.timeline.model.SourceFilter
import com.example.npc.ui.timeline.model.TimelineUiEffect
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private lateinit var healthFlow: MutableStateFlow<List<SourceHealth>>

    private val fixedTs = Instant.parse("2026-09-26T12:00:00Z")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        storageGateway = mockk(relaxed = true)
        eventsFlow = MutableStateFlow(emptyList())
        healthFlow = MutableStateFlow(emptyList())

        every { storageGateway.observeEvents(any()) } returns eventsFlow
        every { storageGateway.observeTransactions(any()) } returns MutableStateFlow(emptyList())
        every { storageGateway.observeSourceHealth() } returns healthFlow
        coEvery { storageGateway.exportAllToJson() } returns "{\"events\":[]}"
        coEvery { storageGateway.deleteAll() } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestEvent(
        id: Long,
        title: String,
        text: String,
        threadKey: String? = null
    ): Event = Event(
        id = id,
        rawId = id * 10,
        ts = fixedTs,
        title = title,
        text = text,
        normalizedText = text.lowercase(),
        lang = Lang.RU,
        threadKey = threadKey?.let { ThreadKey(it) }
    )

    @Test
    fun `initial state subscribes to flows from storageGateway and updates state`() = runTest(testDispatcher) {
        val testEvents = listOf(
            createTestEvent(1L, "Title 1", "Message 1"),
            createTestEvent(2L, "Title 2", "Message 2")
        )
        val testHealth = listOf(
            SourceHealth(
                source = SourceId.NOTIFICATION,
                lastEventAt = fixedTs,
                events24h = 5,
                lastError = null,
                queueDepth = 0
            )
        )

        eventsFlow.value = testEvents
        healthFlow.value = testHealth

        val viewModel = TimelineViewModel(storageGateway)

        verify(atLeast = 1) { storageGateway.observeEvents(100) }
        verify(atLeast = 1) { storageGateway.observeSourceHealth() }

        viewModel.uiState.test {
            val state = awaitItem()
            state.isLoading shouldBe false
            state.events.size shouldBe 2
            state.events[0].id shouldBe 1L
            state.events[1].id shouldBe 2L
            state.sourceHealth.size shouldBe 1
            state.sourceHealth[0].source shouldBe "notification"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reactive filtering by SourceFilter retains only selected source`() = runTest(testDispatcher) {
        val notifEvent = createTestEvent(1L, "Bank Notif", "Payment", null) // source: Уведомление
        val smsEvent = createTestEvent(2L, "900 SMS", "Code 4567", "sms:900") // source: SMS
        val mediaEvent = createTestEvent(3L, "Music Player", "Track playing", "media:player") // source: Медиа

        eventsFlow.value = listOf(notifEvent, smsEvent, mediaEvent)

        val viewModel = TimelineViewModel(storageGateway)

        // Initial state: ALL filter
        viewModel.uiState.value.events.size shouldBe 3
        viewModel.uiState.value.selectedFilter shouldBe SourceFilter.ALL

        // Filter by SMS
        viewModel.onFilterSelected(SourceFilter.SMS)
        viewModel.uiState.value.selectedFilter shouldBe SourceFilter.SMS
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().id shouldBe 2L
        viewModel.uiState.value.events.first().source shouldBe "SMS"

        // Filter by MEDIA
        viewModel.onFilterSelected(SourceFilter.MEDIA)
        viewModel.uiState.value.selectedFilter shouldBe SourceFilter.MEDIA
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().id shouldBe 3L
        viewModel.uiState.value.events.first().source shouldBe "Медиа"

        // Filter by NOTIFICATION
        viewModel.onFilterSelected(SourceFilter.NOTIFICATION)
        viewModel.uiState.value.selectedFilter shouldBe SourceFilter.NOTIFICATION
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().id shouldBe 1L
        viewModel.uiState.value.events.first().source shouldBe "Уведомление"

        // Reset to ALL
        viewModel.onFilterSelected(SourceFilter.ALL)
        viewModel.uiState.value.events.size shouldBe 3
    }

    @Test
    fun `full-text search by searchQuery filters case-insensitively across title and text`() = runTest(testDispatcher) {
        val event1 = createTestEvent(1L, "Сбербанк Онлайн", "Перевод 500 рублей выполнен успешно")
        val event2 = createTestEvent(2L, "Telegram", "Встреча в 14:00 перенесена на завтра")
        val event3 = createTestEvent(3L, "Яндекс.Маркет", "Заказ доставлен в пункт выдачи")

        eventsFlow.value = listOf(event1, event2, event3)

        val viewModel = TimelineViewModel(storageGateway)

        // Search by title (case-insensitive)
        viewModel.onSearchQueryChanged("сбер")
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().id shouldBe 1L

        // Search by text (case-insensitive)
        viewModel.onSearchQueryChanged("ПЕРЕНЕСЕНА")
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().id shouldBe 2L

        // Search across title and text
        viewModel.onSearchQueryChanged("доставлен")
        viewModel.uiState.value.events.size shouldBe 1
        viewModel.uiState.value.events.first().id shouldBe 3L

        // Non-matching query
        viewModel.onSearchQueryChanged("non_existing_query_xyz")
        viewModel.uiState.value.events.size shouldBe 0

        // Blank query restores all events
        viewModel.onSearchQueryChanged("   ")
        viewModel.uiState.value.events.size shouldBe 3
    }

    @Test
    fun `onExportJsonClicked invokes exportAllToJson and emits ShareJsonExport effect`() = runTest(testDispatcher) {
        val exportJsonPayload = "{\"version\":1,\"events\":[{\"id\":1}]}"
        coEvery { storageGateway.exportAllToJson() } returns exportJsonPayload

        val viewModel = TimelineViewModel(storageGateway)

        viewModel.effects.test {
            viewModel.onExportJsonClicked()

            val effect = awaitItem()
            (effect is TimelineUiEffect.ShareJsonExport) shouldBe true
            (effect as TimelineUiEffect.ShareJsonExport).json shouldBe exportJsonPayload

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { storageGateway.exportAllToJson() }
    }

    @Test
    fun `onConfirmDeleteAll invokes storageGateway deleteAll and closes dialog`() = runTest(testDispatcher) {
        val viewModel = TimelineViewModel(storageGateway)

        // Initially dialog is closed
        viewModel.uiState.value.isDeleteConfirmationVisible shouldBe false

        // Open dialog
        viewModel.onDeleteAllClicked()
        viewModel.uiState.value.isDeleteConfirmationVisible shouldBe true

        // Dismiss dialog test
        viewModel.onDismissDeleteDialog()
        viewModel.uiState.value.isDeleteConfirmationVisible shouldBe false

        // Open again and confirm
        viewModel.onDeleteAllClicked()
        viewModel.uiState.value.isDeleteConfirmationVisible shouldBe true

        viewModel.effects.test {
            viewModel.onConfirmDeleteAll()

            // Dialog is closed immediately
            viewModel.uiState.value.isDeleteConfirmationVisible shouldBe false

            val effect = awaitItem()
            (effect is TimelineUiEffect.ShowSnackbar) shouldBe true

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { storageGateway.deleteAll() }
    }

    @Test
    fun `currentLimit starts at 100 and increments on loadMore`() = runTest(testDispatcher) {
        val viewModel = TimelineViewModel(storageGateway)

        viewModel.currentLimit.value shouldBe 100

        viewModel.loadMore()
        viewModel.currentLimit.value shouldBe 200

        verify(atLeast = 1) { storageGateway.observeEvents(200) }

        viewModel.loadMore(50)
        viewModel.currentLimit.value shouldBe 250

        verify(atLeast = 1) { storageGateway.observeEvents(250) }
    }

    @Test
    fun `saveTemplate registers draft in TemplateBankManager and emits ShowSnackbar`() = runTest(testDispatcher) {
        val bankManager = mockk<TemplateBankManager>(relaxed = true)
        coEvery { bankManager.registerDraft(any(), any()) } returns "tmpl_registered_01"

        val viewModel = TimelineViewModel(
            storageGateway = storageGateway,
            templateBankManager = bankManager
        )

        val builtTemplate = BuiltTemplate(
            pattern = """Restituire\s+(?P<amount>\d+)""",
            namedGroups = listOf("amount"),
            constants = mapOf("op" to "REFUND"),
            amountFormat = AmountFormatSpec('.', null),
            requiredLiterals = listOf("Restituire")
        )

        val editorState = EditorUiState(
            eventId = 42L,
            packageName = "md.maib.app",
            tokens = persistentListOf(),
            detectedOpType = "REFUND",
            isRefund = true,
            isValidationInProgress = false
        )

        viewModel.effects.test {
            val templateId = viewModel.saveTemplate(builtTemplate, editorState)
            templateId shouldBe "tmpl_registered_01"

            coVerify(exactly = 1) {
                bankManager.registerDraft(
                    match { draft ->
                        draft.sourceKey == "md.maib.app" &&
                        draft.pattern == builtTemplate.pattern &&
                        draft.sampleEventId == "42" &&
                        draft.origin == "USER"
                    },
                    initialState = TemplateState.ACTIVE
                )
            }

            val effect = awaitItem()
            (effect is TimelineUiEffect.ShowSnackbar) shouldBe true
            (effect as TimelineUiEffect.ShowSnackbar).message.contains("успешно сохранен") shouldBe true
        }
    }

    @Test
    fun `onCopyPayloadJson emits CopyToClipboard effect with raw json`() = runTest(testDispatcher) {
        val viewModel = TimelineViewModel(storageGateway)
        val sampleJson = """{"amount": 100.5, "currency": "MDL"}"""

        viewModel.effects.test {
            viewModel.onCopyPayloadJson(sampleJson)
            val effect = awaitItem()
            (effect is TimelineUiEffect.CopyToClipboard) shouldBe true
            val copyEffect = effect as TimelineUiEffect.CopyToClipboard
            copyEffect.text shouldBe sampleJson
            copyEffect.label shouldBe "JSON Payload"
        }
    }

    @Test
    fun `saveTemplate with backfillEngine executes backfill and reports count in snackbar`() = runTest(testDispatcher) {
        val bankManager = mockk<TemplateBankManager>(relaxed = true)
        coEvery { bankManager.registerDraft(any(), any()) } returns "tmpl_registered_02"

        val backfillEngine = mockk<com.example.npc.domain.usecase.TemplateBackfillEngine>()
        coEvery { backfillEngine.executeBackfill(any(), any()) } returns com.example.npc.core.model.backfill.BackfillResult(
            templateId = "tmpl_registered_02",
            processedCount = 10,
            backfilledCount = 7,
            skippedUserProtectedCount = 0
        )

        val viewModel = TimelineViewModel(
            storageGateway = storageGateway,
            templateBankManager = bankManager,
            backfillEngine = backfillEngine
        )

        val builtTemplate = BuiltTemplate(
            pattern = """Restituire\s+(?P<amount>\d+)""",
            namedGroups = listOf("amount"),
            constants = mapOf("op" to "REFUND"),
            amountFormat = AmountFormatSpec('.', null),
            requiredLiterals = listOf("Restituire")
        )

        val editorState = EditorUiState(
            eventId = 42L,
            packageName = "md.maib.app",
            tokens = persistentListOf(),
            detectedOpType = "REFUND",
            isRefund = true
        )

        viewModel.effects.test {
            val templateId = viewModel.saveTemplate(builtTemplate, editorState)
            templateId shouldBe "tmpl_registered_02"

            coVerify(exactly = 1) {
                backfillEngine.executeBackfill(
                    match { target ->
                        target.id == "tmpl_registered_02" &&
                        target.sourcePackage == "md.maib.app" &&
                        target.pattern == builtTemplate.pattern
                    },
                    any()
                )
            }

            val effect = awaitItem()
            (effect is TimelineUiEffect.ShowSnackbar) shouldBe true
            (effect as TimelineUiEffect.ShowSnackbar).message shouldBe "Шаблон успешно сохранен и активирован (обновлено транзакций: 7)"
        }
    }
}
