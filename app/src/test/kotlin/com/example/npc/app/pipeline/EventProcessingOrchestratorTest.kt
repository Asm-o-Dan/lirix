package com.example.npc.app.pipeline

import com.example.npc.app.pipeline.model.EventProcessingStatus
import com.example.npc.app.pipeline.model.EventProcessingTarget
import com.example.npc.app.pipeline.model.OrchestratorState
import com.example.npc.classify.rules.PackageGatedRouterImpl
import com.example.npc.classify.rules.RuleBasedCategoryClassifier
import com.example.npc.classify.rules.SemanticClassifierImpl
import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.StorageGateway
import com.example.npc.core.storage.dedup.FinancialTransactionDeduplicator
import com.example.npc.extract.finance.CircuitBreaker
import com.example.npc.extract.finance.IsolatedExtractorRunner
import com.example.npc.extract.finance.apb.ApbNotificationExtractor
import com.example.npc.extract.finance.maib.MaibNotificationExtractor
import com.example.npc.extract.finance.prisbank.PrisbankNotificationExtractor
import com.example.npc.extract.finance.sms.BankSmsExtractor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class EventProcessingOrchestratorTest {

    private lateinit var fakeStorage: FakeStorageGateway
    private lateinit var orchestrator: EventProcessingOrchestratorImpl
    private val testDispatcher = Dispatchers.Default

    @BeforeEach
    fun setUp() {
        fakeStorage = FakeStorageGateway()

        val router = PackageGatedRouterImpl()
        val ruleClassifier = RuleBasedCategoryClassifier(router)
        val semanticClassifier = SemanticClassifierImpl(router, ruleClassifier)

        val extractors = listOf(
            ApbNotificationExtractor(),
            PrisbankNotificationExtractor(),
            MaibNotificationExtractor(),
            BankSmsExtractor()
        )
        val circuitBreaker = CircuitBreaker(timeBudgetMs = 50L, failureThreshold = 3, cooldownMs = 600_000L)
        val extractorRunner = IsolatedExtractorRunner(extractors, circuitBreaker)

        orchestrator = EventProcessingOrchestratorImpl(
            storageGateway = fakeStorage,
            semanticClassifier = semanticClassifier,
            packageGatedRouter = router,
            extractorRunner = extractorRunner,
            circuitBreaker = circuitBreaker,
            defaultDispatcher = testDispatcher,
            ioDispatcher = testDispatcher
        )
    }

    @AfterEach
    fun tearDown() {
        orchestrator.stop()
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition timed out after ${timeoutMs}ms")
            }
            delay(20)
        }
    }

    @Test
    fun test_inTour_spam_classified_as_advertisement_and_zero_transactions() = runTest {
        orchestrator.start()

        val eventId = fakeStorage.addTestEvent(
            packageName = "com.radolyn.ayugram",
            title = "InTour",
            text = "Горящие туры: вылет из Кишинева... от 499 евро... бронируйте прямо сейчас!",
            sourceId = SourceId.NOTIFICATION
        )

        val submitted = orchestrator.submit(eventId)
        submitted shouldBe true

        waitUntil { orchestrator.getStatus().metrics.totalCompleted == 1L }

        val stored = fakeStorage.getStoredEvent(eventId)
        stored shouldNotBe null
        (stored!!.category == Category.ADVERTISEMENT.name || stored.category == Category.COMMUNICATION.name) shouldBe true
        stored.category shouldNotBe Category.FINANCE.name
        fakeStorage.getAllTransactions().size shouldBe 0
        orchestrator.getStatus().metrics.totalFinanceExtracted shouldBe 0L
    }

    @Test
    fun test_apb_bank_notification_extracted_successfully() = runTest {
        orchestrator.start()

        val eventId = fakeStorage.addTestEvent(
            packageName = "com.apb.mobile",
            title = "Агропромбанк",
            text = "Покупка по карте **5576 на сумму 15,15 RUP Баланс 406,86 RUP",
            sourceId = SourceId.NOTIFICATION
        )

        val submitted = orchestrator.submit(eventId)
        submitted shouldBe true

        waitUntil { orchestrator.getStatus().metrics.totalCompleted == 1L }

        val stored = fakeStorage.getStoredEvent(eventId)
        stored shouldNotBe null
        stored!!.category shouldBe Category.FINANCE.name

        val transactions = fakeStorage.getAllTransactions()
        transactions.size shouldBe 1
        val tx = transactions.first()
        tx.eventId shouldBe eventId
        tx.amount.minor shouldBe 1515L
        tx.amount.currency shouldBe CurrencyCode.RUP
        tx.type shouldBe TransactionType.DEBIT
        orchestrator.getStatus().metrics.totalFinanceExtracted shouldBe 1L
    }

    @Test
    fun test_maib_declined_transaction_parsed() = runTest {
        orchestrator.start()

        val eventId = fakeStorage.addTestEvent(
            packageName = "md.maib.maibank",
            title = "Транзакция отклонена",
            text = "Платеж с карты ***6159 на сумму 664 MDL в Temu.com ОТКЛОНЕН из-за недостаточности средств. Пополните карту и попробуйте снова.",
            sourceId = SourceId.NOTIFICATION
        )

        val submitted = orchestrator.submit(eventId)
        submitted shouldBe true

        waitUntil { orchestrator.getStatus().metrics.totalCompleted == 1L }

        val stored = fakeStorage.getStoredEvent(eventId)
        stored shouldNotBe null
        stored!!.category shouldBe Category.FINANCE.name

        val transactions = fakeStorage.getAllTransactions()
        transactions.size shouldBe 1
        val tx = transactions.first()
        tx.status shouldBe TransactionStatus.DECLINED
        tx.amount.currency shouldBe CurrencyCode.MDL
        tx.amount.minor shouldBe 66400L
        orchestrator.getStatus().metrics.totalDeclinedTransactions shouldBe 1L
    }

    @Test
    fun test_parallel_submit_idempotency() = runTest {
        orchestrator.start()

        val eventId = fakeStorage.addTestEvent(
            packageName = "com.apb.mobile",
            title = "Агропромбанк",
            text = "Покупка по карте **5576 на сумму 15,15 RUP Баланс 406,86 RUP",
            sourceId = SourceId.NOTIFICATION
        )

        // 20 параллельных submit одного и того же eventId
        coroutineScope {
            repeat(20) {
                launch(Dispatchers.Default) {
                    orchestrator.submit(eventId)
                }
            }
        }

        waitUntil {
            val metrics = orchestrator.getStatus().metrics
            (metrics.totalCompleted + metrics.totalSkipped) == 20L
        }

        val metrics = orchestrator.getStatus().metrics
        metrics.totalCompleted shouldBe 1L
        metrics.totalSkipped shouldBe 19L
        fakeStorage.getAllTransactions().size shouldBe 1
    }

    @Test
    fun test_recovery_sweep_picks_up_unclassified_events() = runTest {
        fakeStorage.addTestEvent("com.apb.mobile", "Агропромбанк", "Покупка по карте **5576 на сумму 10,00 RUP")
        fakeStorage.addTestEvent("com.radolyn.ayugram", "InTour", "Горящие туры в Турцию 399 евро")
        fakeStorage.addTestEvent("md.maib.maibank", "maibank", "Оплата 100 MDL в Linella")

        orchestrator.start()
        // start() автоматически вызывает triggerRecoverySweep()

        waitUntil {
            orchestrator.getStatus().metrics.totalCompleted >= 3L
        }

        val pending = fakeStorage.getPendingUnprocessedEventIds(100)
        pending.size shouldBe 0
    }

    @Test
    fun test_graceful_shutdown() = runTest {
        orchestrator.start()

        repeat(10) { i ->
            fakeStorage.addTestEvent("com.radolyn.ayugram", "Spam $i", "Тестовый спам $i")
            orchestrator.submit((i + 1).toLong())
        }

        orchestrator.stop()

        waitUntil(5000L) {
            orchestrator.getStatus().state == OrchestratorState.STOPPED
        }

        orchestrator.getStatus().state shouldBe OrchestratorState.STOPPED
        orchestrator.getStatus().metrics.currentQueueDepth shouldBe 0
    }

    @Test
    fun test_isUpdateOf_event_does_not_duplicate_financial_transaction() = runTest {
        orchestrator.start()

        val eventId1 = fakeStorage.addTestEvent(
            packageName = "com.apb.mobile",
            title = "Агропромбанк",
            text = "Перевод на карту *5576 от Виталий С. зачислен, 222,00 RUP",
            sourceId = SourceId.NOTIFICATION
        )
        val submitted1 = orchestrator.submit(eventId1)
        submitted1 shouldBe true

        waitUntil { orchestrator.getStatus().metrics.totalCompleted == 1L }

        fakeStorage.getAllTransactions().size shouldBe 1
        val tx1 = fakeStorage.getAllTransactions().first()
        tx1.amount.minor shouldBe 22200L
        tx1.merchant shouldBe "Виталий С."

        // Update of the first event arrives
        val eventId2 = fakeStorage.addTestEvent(
            packageName = "com.apb.mobile",
            title = "Агропромбанк",
            text = "Перевод на карту *5576 от Виталий С. зачислен, 222,00 RUP",
            sourceId = SourceId.NOTIFICATION,
            isUpdateOf = eventId1
        )
        val submitted2 = orchestrator.submit(eventId2)
        submitted2 shouldBe true

        waitUntil { orchestrator.getStatus().metrics.totalCompleted == 2L }

        // Must still have exactly 1 transaction, not duplicated!
        fakeStorage.getAllTransactions().size shouldBe 1
        val txAfter = fakeStorage.getAllTransactions().first()
        txAfter.amount.minor shouldBe 22200L
        txAfter.merchant shouldBe "Виталий С."
    }

    @Test
    fun test_cross_notification_apb_p2p_and_processing_deduplicated_and_enriched() = runTest {
        orchestrator.start()

        val baseTime = Instant.now()

        // Push 1: P2P transfer with sender name
        val eventId1 = fakeStorage.addTestEvent(
            packageName = "com.apb.mobile",
            title = "Агропромбанк",
            text = "Перевод на карту *5576 от Виталий С. зачислен, 222,00 RUP",
            sourceId = SourceId.NOTIFICATION,
            ts = baseTime
        )
        val submitted1 = orchestrator.submit(eventId1)
        submitted1 shouldBe true

        waitUntil { orchestrator.getStatus().metrics.totalCompleted == 1L }
        fakeStorage.getAllTransactions().size shouldBe 1

        // Push 2: Processing push 4 minutes later with card mask
        val eventId2 = fakeStorage.addTestEvent(
            packageName = "com.apb.mobile",
            title = "Агропромбанк",
            text = "Пополнение счета по карте Клевер 910401******5576, 222.00 RUP",
            sourceId = SourceId.NOTIFICATION,
            ts = baseTime.plusSeconds(240) // 4 minutes later
        )
        val submitted2 = orchestrator.submit(eventId2)
        submitted2 shouldBe true

        waitUntil { orchestrator.getStatus().metrics.totalCompleted == 2L }

        // Deduplicated into exactly 1 transaction with merged data
        val txns = fakeStorage.getAllTransactions()
        txns.size shouldBe 1
        val tx = txns.first()
        tx.amount.minor shouldBe 22200L // 222.00 RUP, not doubled!
        tx.amount.currency shouldBe CurrencyCode.RUP
        tx.merchant shouldBe "Виталий С." // from P2P push
        tx.accountMask shouldBe "910401******5576" // from processing push
        tx.type shouldBe TransactionType.CREDIT
    }
}

data class FakeStoredEvent(
    val event: Event,
    var category: String = "UNCLASSIFIED",
    var confidence: Double = 0.0,
    var engineUsed: String = "NONE",
    var contentFingerprint: String? = null
)

class FakeStorageGateway : StorageGateway {
    private val idGen = AtomicLong(0L)
    private val rawEvents = ConcurrentHashMap<Long, RawEvent>()
    private val storedEvents = ConcurrentHashMap<Long, FakeStoredEvent>()
    private val eventPackages = ConcurrentHashMap<Long, String>()
    private val eventSources = ConcurrentHashMap<Long, SourceId>()
    private val transactions = ConcurrentHashMap<Long, FinancialTransaction>()
    private val prototypes = ConcurrentHashMap<String, UserPrototype>()
    private val deduplicator = FinancialTransactionDeduplicator()

    fun addTestEvent(
        packageName: String,
        title: String,
        text: String,
        sourceId: SourceId = SourceId.NOTIFICATION,
        category: String = "UNCLASSIFIED",
        isUpdateOf: Long? = null,
        ts: Instant = Instant.now()
    ): Long {
        val id = idGen.incrementAndGet()
        val raw = RawEvent(
            id = id,
            seq = id,
            source = sourceId,
            packageName = packageName,
            receivedAt = ts,
            payloadJson = "{}",
            hash = DeduplicationKey(String.format("%064x", id))
        )
        val event = Event(
            id = id,
            rawId = id,
            ts = ts,
            title = title,
            text = text,
            normalizedText = text,
            lang = Lang.RU,
            isUpdateOf = isUpdateOf
        )
        rawEvents[id] = raw
        storedEvents[id] = FakeStoredEvent(event = event, category = category)
        eventPackages[id] = packageName
        eventSources[id] = sourceId
        return id
    }

    fun getStoredEvent(id: Long): FakeStoredEvent? = storedEvents[id]

    fun getAllTransactions(): List<FinancialTransaction> = transactions.values.toList()

    override suspend fun tryClaimEvent(eventId: Long): Boolean {
        val stored = storedEvents[eventId] ?: return false
        synchronized(stored) {
            if (stored.category == "UNCLASSIFIED" || stored.category == "PROCESSING") {
                stored.category = "PROCESSING"
                return true
            }
            return false
        }
    }

    override suspend fun getEventWithPackage(eventId: Long): EventProcessingTarget? {
        val stored = storedEvents[eventId] ?: return null
        val pkg = eventPackages[eventId] ?: return null
        val src = eventSources[eventId] ?: SourceId.NOTIFICATION
        return EventProcessingTarget(
            event = stored.event,
            packageName = pkg,
            sourceId = src,
            rawPayloadJson = "{}"
        )
    }

    override suspend fun completeEventProcessing(
        eventId: Long,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Boolean {
        val stored = storedEvents[eventId] ?: return false
        synchronized(stored) {
            stored.category = classification.category.name
            stored.confidence = classification.confidence
            stored.engineUsed = classification.engine.name
            stored.contentFingerprint = classification.contentFingerprint
        }
        if (transaction != null) {
            val isUpdateOf = stored.event.isUpdateOf
            if (isUpdateOf != null && transactions.containsKey(isUpdateOf)) {
                val existing = transactions[isUpdateOf]!!
                val merged = deduplicator.merge(existing, transaction)
                transactions[isUpdateOf] = merged
                return true
            }

            val duplicate = deduplicator.findDuplicate(transaction, transactions.values.toList())
            if (duplicate != null) {
                val targetKey = duplicate.eventId ?: duplicate.id
                val merged = deduplicator.merge(duplicate, transaction)
                transactions[targetKey] = merged
                storedEvents[eventId] = stored.copy(
                    event = stored.event.copy(isUpdateOf = duplicate.eventId)
                )
            } else {
                transactions[eventId] = transaction.copy(eventId = eventId)
            }
        }
        return true
    }

    override suspend fun markEventFailed(eventId: Long, reason: String): Boolean {
        val stored = storedEvents[eventId] ?: return false
        synchronized(stored) {
            stored.category = Category.OTHER.name
        }
        return true
    }

    override suspend fun getPendingUnprocessedEventIds(limit: Int): List<Long> {
        return storedEvents.values
            .filter { it.category == "UNCLASSIFIED" || it.category == "PROCESSING" }
            .map { it.event.id }
            .sorted()
            .take(limit)
    }

    override suspend fun findMatchingPrototype(packageName: String, fingerprint: String): UserPrototype? {
        return prototypes["$packageName:$fingerprint"]
    }

    override suspend fun insertRawEvent(event: RawEvent): Long {
        val id = if (event.id > 0) event.id else idGen.incrementAndGet()
        rawEvents[id] = event.copy(id = id)
        return id
    }

    override suspend fun insertEvent(event: Event): Long {
        val id = if (event.id > 0) event.id else idGen.incrementAndGet()
        val e = event.copy(id = id)
        storedEvents[id] = FakeStoredEvent(event = e)
        return id
    }

    override suspend fun upsertSourceHealth(health: SourceHealth) {}
    override suspend fun findDuplicate(key: DeduplicationKey): Long? = null
    override suspend fun getRawEvent(id: Long): RawEvent? = rawEvents[id]
    override suspend fun getRawEventByEventId(eventId: Long): RawEvent? {
        val stored = storedEvents[eventId] ?: return null
        return rawEvents[stored.event.rawId]
    }
    override suspend fun insertTransaction(transaction: FinancialTransaction): Long {
        val id = if (transaction.id > 0) transaction.id else idGen.incrementAndGet()
        val withId = transaction.copy(id = id)
        transactions[transaction.eventId ?: id] = withId
        return id
    }
    override suspend fun saveProcessedEvent(
        event: Event,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Long {
        insertEvent(event)
        completeEventProcessing(event.id, classification, transaction)
        return event.id
    }
    override fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>> = emptyFlow()
    override fun observeTransactionsByPeriod(from: Instant, to: Instant): Flow<List<FinancialTransaction>> = emptyFlow()
    override suspend fun getTransactionByEventId(eventId: Long): FinancialTransaction? = transactions[eventId]
    override suspend fun getAggregatedTotals(direction: TransactionType, from: Instant, to: Instant): Map<CurrencyCode, Long> = emptyMap()
    override fun observeAggregatedTotals(
        from: java.time.Instant,
        to: java.time.Instant,
        direction: com.example.npc.core.model.finance.TransactionType?,
        reduceExpenseByRefund: Boolean,
        includeSuggested: Boolean
    ): kotlinx.coroutines.flow.Flow<Map<com.example.npc.core.model.finance.CurrencyCode, com.example.npc.core.model.finance.AggregatedSums>> = kotlinx.coroutines.flow.flowOf(emptyMap())
    override suspend fun recordUserCorrection(eventId: Long, category: Category) {}
    override suspend fun recordUserCorrection(
        eventId: Long,
        packageName: String,
        contentFingerprint: String,
        newCategory: Category,
        correctedAt: Instant
    ) {
        val fp = if (contentFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            contentFingerprint
        } else {
            contentFingerprint.padEnd(64, '0').take(64)
        }
        prototypes["$packageName:$contentFingerprint"] = UserPrototype(
            id = idGen.incrementAndGet(),
            packageName = packageName,
            fingerprint = fp,
            category = newCategory,
            supportCount = 2,
            createdAt = correctedAt,
            lastSeenAt = correctedAt
        )
    }
    override fun observeEvents(limit: Int): Flow<List<Event>> = emptyFlow()
    override fun observeSourceHealth(): Flow<List<SourceHealth>> = emptyFlow()
    override suspend fun exportAllToJson(): String = "{}"
    override suspend fun clearAllData() {
        rawEvents.clear()
        storedEvents.clear()
        eventPackages.clear()
        eventSources.clear()
        transactions.clear()
        prototypes.clear()
    }
}
