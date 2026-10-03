package com.example.npc.feature.replay.backfill

import app.cash.turbine.test
import com.example.npc.core.model.backfill.BackfillCriteria
import com.example.npc.core.model.backfill.BackfillTargetTemplate
import com.example.npc.core.model.Category
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.ExtractorKind
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TxStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.dao.EventDao
import com.example.npc.core.storage.dao.FinancialTransactionDao
import com.example.npc.core.storage.dao.ReplayEventSourceDao
import com.example.npc.core.storage.dao.ReplayHistoricalEvent
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import com.example.npc.feature.replay.engine.VirtualEffectEvaluator
import com.example.npc.pipeline.runtime.hotswap.CompiledTemplate
import com.google.re2j.Pattern
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class TemplateBackfillEngineTest {

    private lateinit var eventSourceDao: ReplayEventSourceDao
    private lateinit var transactionDao: FinancialTransactionDao
    private lateinit var eventDao: EventDao
    private lateinit var engine: TemplateBackfillEngine

    private val targetPackage = "md.maib.maibank"
    private val templateId = "tmpl-maib-pay-001"

    // RE2/J pattern with named capture groups for MAIB
    private val maibPattern = """(?i)Plata cu cardul (?P<amount>\d+(?:\.\d{2})?)\s*(?P<curr>MDL|USD|EUR)\s+la\s+(?P<merchant>[A-Za-z0-9._ -]+)\s+sold:\s*(?P<bal>\d+(?:\.\d{2})?)"""

    private val compiledTemplate = CompiledTemplate(
        id = templateId,
        sourceKey = targetPackage,
        priority = 100,
        pattern = Pattern.compile(maibPattern),
        requiredLiterals = listOf("Plata cu cardul", "sold:"),
        constants = mapOf("opType" to "DEBIT")
    )

    @BeforeEach
    fun setUp() {
        eventSourceDao = mockk(relaxed = true)
        transactionDao = mockk(relaxed = true)
        eventDao = mockk(relaxed = true)

        engine = TemplateBackfillEngine(
            eventSourceDao = eventSourceDao,
            transactionDao = transactionDao,
            eventDao = eventDao,
            effectEvaluator = VirtualEffectEvaluator()
        )
    }

    @Test
    @DisplayName("Critical Safety Gate: События со статусом USER_EDITED и USER_CONFIRMED ни при каких обстоятельствах не перезаписываются")
    fun `critical safety gate - user edited and user confirmed events are NEVER overwritten`() = runTest {
        val userEditedEvent = ReplayHistoricalEvent(
            eventId = 1L,
            rawId = 101L,
            packageName = targetPackage,
            title = "MAIB",
            text = "Plata cu cardul 150.00 MDL la Linella sold: 500.00",
            postTime = 1_000_000L,
            historicalCategory = "USER_EDITED",
            historicalConfidence = 1.0,
            historicalTransactionJson = "15000"
        )

        val userConfirmedEvent = ReplayHistoricalEvent(
            eventId = 2L,
            rawId = 102L,
            packageName = targetPackage,
            title = "MAIB",
            text = "Plata cu cardul 200.00 MDL la Rogob sold: 300.00",
            postTime = 1_000_100L,
            historicalCategory = "USER_CONFIRMED",
            historicalConfidence = 1.0,
            historicalTransactionJson = "20000"
        )

        val manualTxEntity = FinancialTransactionEntity(
            id = 50L,
            eventId = 3L,
            bank = targetPackage,
            direction = "DEBIT",
            amountMinor = 30000L,
            currency = "MDL",
            balanceMinor = 20000L,
            balanceCurrency = "MDL",
            merchant = "Custom User Merchant",
            accountMask = null,
            occurredAt = 1_000_200L,
            extractorId = "user.manual",
            extractorVersion = 1,
            createdAt = 1_000_200L,
            extractorKind = "MANUAL",
            templateId = null
        )

        val manualEvent = ReplayHistoricalEvent(
            eventId = 3L,
            rawId = 103L,
            packageName = targetPackage,
            title = "MAIB",
            text = "Plata cu cardul 300.00 MDL la Metro sold: 200.00",
            postTime = 1_000_200L,
            historicalCategory = "FINANCE",
            historicalConfidence = 1.0,
            historicalTransactionJson = "30000"
        )

        val normalEventWithoutTx = ReplayHistoricalEvent(
            eventId = 4L,
            rawId = 104L,
            packageName = targetPackage,
            title = "MAIB",
            text = "Plata cu cardul 99.50 MDL la Farmacia sold: 100.50",
            postTime = 1_000_300L,
            historicalCategory = "UNCLASSIFIED",
            historicalConfidence = 0.0,
            historicalTransactionJson = null
        )

        val events = listOf(userEditedEvent, userConfirmedEvent, manualEvent, normalEventWithoutTx)

        coEvery {
            eventSourceDao.getEventsByPackageAfterId(targetPackage, any(), any(), any(), any())
        } returns events andThen emptyList()

        coEvery { transactionDao.getByEventId(1L) } returns null
        coEvery { transactionDao.getByEventId(2L) } returns null
        coEvery { transactionDao.getByEventId(3L) } returns manualTxEntity
        coEvery { transactionDao.getByEventId(4L) } returns null

        val report = engine.executeBackfill(compiledTemplate)

        report.totalAnalyzedEvents shouldBe 4
        report.skippedManualEditsCount shouldBe 3 // event 1 (USER_EDITED), event 2 (USER_CONFIRMED), event 3 (extractorKind=MANUAL)
        report.extractedTransactionsCount shouldBe 1 // only event 4 was backfilled

        // Verify transactionDao.insert was NEVER called for events 1, 2, 3
        coVerify(exactly = 0) { transactionDao.insert(match { it.eventId == 1L }) }
        coVerify(exactly = 0) { transactionDao.insert(match { it.eventId == 2L }) }
        coVerify(exactly = 0) { transactionDao.insert(match { it.eventId == 3L }) }

        // Verify event 4 was upserted with new templateId and TEMPLATE kind
        val slot = slot<FinancialTransactionEntity>()
        coVerify(exactly = 1) { transactionDao.insert(capture(slot)) }
        slot.captured.eventId shouldBe 4L
        slot.captured.amountMinor shouldBe 9950L
        slot.captured.currency shouldBe "MDL"
        slot.captured.templateId shouldBe templateId
        slot.captured.extractorKind shouldBe ExtractorKind.TEMPLATE.name
    }

    @Test
    @DisplayName("DoD: Корректность бэкфилла по 100 историческим событиям")
    fun `dod acceptance criteria - correct backfill across 100 historical events`() = runTest {
        // Construct 100 historical events:
        // - 50 eligible events without transactions (match pattern) -> should be extracted
        // - 20 eligible events with existing SUGGESTED transactions -> should be updated
        // - 15 protected user-edited events (USER_EDITED) -> must be skipped
        // - 15 non-matching notifications (OTP, general text) -> should not match, not extracted
        val allEvents = mutableListOf<ReplayHistoricalEvent>()
        val existingTxMap = mutableMapOf<Long, FinancialTransactionEntity>()

        for (i in 1L..50L) {
            allEvents.add(
                ReplayHistoricalEvent(
                    eventId = i,
                    rawId = 1000L + i,
                    packageName = targetPackage,
                    title = "MAIB Info",
                    text = "Plata cu cardul ${i}.00 MDL la Store$i sold: 1000.00",
                    postTime = 1_600_000_000L + i * 1000,
                    historicalCategory = "UNCLASSIFIED",
                    historicalConfidence = 0.0,
                    historicalTransactionJson = null
                )
            )
        }

        for (i in 51L..70L) {
            allEvents.add(
                ReplayHistoricalEvent(
                    eventId = i,
                    rawId = 1000L + i,
                    packageName = targetPackage,
                    title = "MAIB Info",
                    text = "Plata cu cardul ${i}.00 MDL la Cafe$i sold: 2000.00",
                    postTime = 1_600_000_000L + i * 1000,
                    historicalCategory = "SUGGESTED",
                    historicalConfidence = 0.65,
                    historicalTransactionJson = "${i * 100}"
                )
            )
            existingTxMap[i] = FinancialTransactionEntity(
                id = 500L + i,
                eventId = i,
                bank = targetPackage,
                direction = "DEBIT",
                amountMinor = i * 100,
                currency = "MDL",
                balanceMinor = 200000L,
                balanceCurrency = "MDL",
                merchant = "Cafe$i",
                accountMask = null,
                occurredAt = 1_600_000_000L + i * 1000,
                extractorId = "universal.extractor",
                extractorVersion = 1,
                createdAt = 1_600_000_000L,
                extractorKind = "UNIVERSAL",
                templateId = null
            )
        }

        for (i in 71L..85L) {
            allEvents.add(
                ReplayHistoricalEvent(
                    eventId = i,
                    rawId = 1000L + i,
                    packageName = targetPackage,
                    title = "MAIB Info",
                    text = "Plata cu cardul ${i}.00 MDL la UserStore$i sold: 500.00",
                    postTime = 1_600_000_000L + i * 1000,
                    historicalCategory = "USER_EDITED",
                    historicalConfidence = 1.0,
                    historicalTransactionJson = "${i * 100}"
                )
            )
            existingTxMap[i] = FinancialTransactionEntity(
                id = 700L + i,
                eventId = i,
                bank = targetPackage,
                direction = "DEBIT",
                amountMinor = i * 100,
                currency = "MDL",
                balanceMinor = 50000L,
                balanceCurrency = "MDL",
                merchant = "UserStore$i",
                accountMask = null,
                occurredAt = 1_600_000_000L + i * 1000,
                extractorId = "user.manual",
                extractorVersion = 1,
                createdAt = 1_600_000_000L,
                extractorKind = "MANUAL",
                templateId = null
            )
        }

        for (i in 86L..100L) {
            allEvents.add(
                ReplayHistoricalEvent(
                    eventId = i,
                    rawId = 1000L + i,
                    packageName = targetPackage,
                    title = "MAIB Info",
                    text = "Codul de securitate OTP este ${1000 + i}. Nu divulgati nimanui.",
                    postTime = 1_600_000_000L + i * 1000,
                    historicalCategory = "OTHER",
                    historicalConfidence = 0.95,
                    historicalTransactionJson = null
                )
            )
        }

        allEvents.size shouldBe 100

        // Mock DAO responses
        coEvery {
            eventSourceDao.getEventsByPackageAfterId(targetPackage, 0L, any(), any(), any())
        } returns allEvents

        coEvery {
            eventSourceDao.getEventsByPackageAfterId(targetPackage, 100L, any(), any(), any())
        } returns emptyList()

        coEvery { transactionDao.getByEventId(any()) } answers {
            val id = firstArg<Long>()
            existingTxMap[id]
        }

        val upsertedEntities = mutableListOf<FinancialTransactionEntity>()
        coEvery { transactionDao.insert(capture(upsertedEntities)) } returns 1L

        val report = engine.executeBackfill(
            template = compiledTemplate,
            criteria = BackfillCriteria(maxEventsLimit = 100, chunkSize = 150)
        )

        // Verifications
        report.totalAnalyzedEvents shouldBe 100
        report.skippedManualEditsCount shouldBe 15
        report.extractedTransactionsCount shouldBe 70 // 50 new + 20 updated from SUGGESTED

        // Verify that none of the 15 manual edits were touched
        for (i in 71L..85L) {
            val touched = upsertedEntities.any { it.eventId == i }
            touched shouldBe false
        }

        // Verify that all 20 SUGGESTED transactions maintained their original primary keys
        for (i in 51L..70L) {
            val updated = upsertedEntities.firstOrNull { it.eventId == i }
            updated shouldBe updated
            updated?.id shouldBe (500L + i) // Retained existing primary key
            updated?.templateId shouldBe templateId
            updated?.extractorKind shouldBe ExtractorKind.TEMPLATE.name
        }

        // Verify that 50 new transactions were inserted with id = 0 (new autoincrement)
        val newEntities = upsertedEntities.filter { (it.eventId ?: 0L) in 1L..50L }
        newEntities.size shouldBe 50
        newEntities.all { it.id == 0L } shouldBe true
        newEntities.all { it.templateId == templateId } shouldBe true
        newEntities.all { it.extractorKind == ExtractorKind.TEMPLATE.name } shouldBe true
    }

    @Test
    @DisplayName("Обновление SUGGESTED транзакций: замена на CONFIRMED_AUTO с новым templateId")
    fun `suggested transaction is successfully upgraded to confirmed auto with new templateId`() = runTest {
        val event = ReplayHistoricalEvent(
            eventId = 42L,
            rawId = 142L,
            packageName = targetPackage,
            title = "MAIB",
            text = "Plata cu cardul 55.00 MDL la Andy sold: 450.00",
            postTime = 1_234_567L,
            historicalCategory = "SUGGESTED",
            historicalConfidence = 0.6,
            historicalTransactionJson = "5500"
        )

        val existingSuggestedTx = FinancialTransactionEntity(
            id = 999L,
            eventId = 42L,
            bank = targetPackage,
            direction = "DEBIT",
            amountMinor = 5500L,
            currency = "MDL",
            balanceMinor = 45000L,
            balanceCurrency = "MDL",
            merchant = "Andy",
            accountMask = null,
            occurredAt = 1_234_567L,
            extractorId = "universal.extractor",
            extractorVersion = 1,
            createdAt = 1_000_000L,
            extractorKind = "UNIVERSAL",
            templateId = null
        )

        coEvery {
            eventSourceDao.getEventsByPackageAfterId(targetPackage, any(), any(), any(), any())
        } returns listOf(event) andThen emptyList()

        coEvery { transactionDao.getByEventId(42L) } returns existingSuggestedTx

        val slot = slot<FinancialTransactionEntity>()
        coEvery { transactionDao.insert(capture(slot)) } returns 999L

        val report = engine.executeBackfill(compiledTemplate)

        report.totalAnalyzedEvents shouldBe 1
        report.extractedTransactionsCount shouldBe 1
        report.skippedManualEditsCount shouldBe 0

        slot.captured.id shouldBe 999L // Kept original ID
        slot.captured.eventId shouldBe 42L
        slot.captured.extractorKind shouldBe "TEMPLATE"
        slot.captured.templateId shouldBe templateId
        slot.captured.amountMinor shouldBe 5500L
        slot.captured.merchant shouldBe "Andy"

        coVerify(exactly = 1) {
            eventDao.updateClassification(42L, Category.FINANCE.name, 1.0, "TEMPLATE", null)
        }
    }

    @Test
    @DisplayName("Исполнение через интерфейс домена с BackfillTargetTemplate")
    fun `domain execution via BackfillTargetTemplate contract`() = runTest {
        val event = ReplayHistoricalEvent(
            eventId = 10L,
            rawId = 110L,
            packageName = targetPackage,
            title = "MAIB",
            text = "Plata cu cardul 12.34 MDL la Pegas sold: 100.00",
            postTime = 1_500_000L,
            historicalCategory = "UNCLASSIFIED",
            historicalConfidence = 0.0,
            historicalTransactionJson = null
        )

        coEvery {
            eventSourceDao.getEventsByPackageAfterId(targetPackage, any(), any(), any(), any())
        } returns listOf(event) andThen emptyList()

        coEvery { transactionDao.getByEventId(10L) } returns null

        val targetTemplate = BackfillTargetTemplate(
            id = "domain-tmpl-01",
            sourcePackage = targetPackage,
            pattern = maibPattern,
            requiredLiterals = listOf("Plata cu cardul", "sold:")
        )

        val report = engine.executeBackfill(targetTemplate)

        report.templateId shouldBe "domain-tmpl-01"
        report.totalAnalyzedEvents shouldBe 1
        report.extractedTransactionsCount shouldBe 1
        report.skippedManualEditsCount shouldBe 0
    }

    @Test
    @DisplayName("Реактивный backfillFlow транслирует прогресс и завершение")
    fun `backfillFlow emits progress and completed state`() = runTest {
        val event1 = ReplayHistoricalEvent(
            eventId = 1L,
            rawId = 101L,
            packageName = targetPackage,
            title = "MAIB",
            text = "Plata cu cardul 10.00 MDL la Pegas sold: 90.00",
            postTime = 1_000L,
            historicalCategory = "UNCLASSIFIED",
            historicalConfidence = 0.0,
            historicalTransactionJson = null
        )

        coEvery { eventSourceDao.countEventsByPackage(targetPackage, any(), any()) } returns 1
        coEvery {
            eventSourceDao.getEventsByPackageAfterId(targetPackage, any(), any(), any(), any())
        } returns listOf(event1) andThen emptyList()

        coEvery { transactionDao.getByEventId(1L) } returns null

        engine.backfillFlow(compiledTemplate).test {
            val progress = awaitItem()
            (progress is BackfillProgress.InProgress) shouldBe true
            (progress as BackfillProgress.InProgress).analyzedCount shouldBe 1

            val completed = awaitItem()
            (completed is BackfillProgress.Completed) shouldBe true
            (completed as BackfillProgress.Completed).report.extractedTransactionsCount shouldBe 1

            awaitComplete()
        }
    }

    @Test
    fun `backfill uses title direction and repairs an automatic transaction`() = runTest {
        val event = directionEvent(title = "Зачислено", text = "100 MDL")
        val existing = directionEntity().copy(direction = "DEBIT", amountMinor = 5000L)
        coEvery { eventSourceDao.getEventsByPackageAfterId(targetPackage, any(), any(), any(), any()) } returns listOf(event) andThen emptyList()
        coEvery { transactionDao.getByEventId(event.eventId) } returns existing
        val saved = slot<FinancialTransactionEntity>()
        coEvery { transactionDao.insert(capture(saved)) } returns existing.id

        engine.executeBackfill(directionTemplate()).extractedTransactionsCount shouldBe 1
        saved.captured.id shouldBe existing.id
        saved.captured.createdAt shouldBe existing.createdAt
        saved.captured.direction shouldBe "CREDIT"
        saved.captured.amountMinor shouldBe 10000L
        saved.captured.txStatus shouldBe "CONFIRMED_AUTO"
    }

    @Test
    fun `backfill stores unresolved direction as suggested instead of debit`() = runTest {
        val event = directionEvent(title = "Bank", text = "Операция 100 MDL")
        coEvery { eventSourceDao.getEventsByPackageAfterId(targetPackage, any(), any(), any(), any()) } returns listOf(event) andThen emptyList()
        coEvery { transactionDao.getByEventId(event.eventId) } returns null
        val saved = slot<FinancialTransactionEntity>()
        coEvery { transactionDao.insert(capture(saved)) } returns 1L

        engine.executeBackfill(directionTemplate()).extractedTransactionsCount shouldBe 1
        saved.captured.direction shouldBe "UNKNOWN"
        saved.captured.txStatus shouldBe "SUGGESTED"
    }

    @Test
    fun `backfill preserves persisted user confirmation even for static extractor`() = runTest {
        val event = directionEvent(title = "Зачислено", text = "100 MDL")
        coEvery { eventSourceDao.getEventsByPackageAfterId(targetPackage, any(), any(), any(), any()) } returns listOf(event) andThen emptyList()
        coEvery { transactionDao.getByEventId(event.eventId) } returns directionEntity().copy(txStatus = TxStatus.USER_CONFIRMED.name)

        engine.executeBackfill(directionTemplate()).skippedManualEditsCount shouldBe 1
        coVerify(exactly = 0) { transactionDao.insert(any()) }
    }

    @Test
    fun `backfill cannot turn OTP with a payment amount into a transaction`() = runTest {
        val event = directionEvent(title = "OTP code", text = "Payment 100 MDL. Code 123456")
        coEvery { eventSourceDao.getEventsByPackageAfterId(targetPackage, any(), any(), any(), any()) } returns listOf(event) andThen emptyList()
        coEvery { transactionDao.getByEventId(event.eventId) } returns null

        engine.executeBackfill(directionTemplate()).extractedTransactionsCount shouldBe 0
        coVerify(exactly = 0) { transactionDao.insert(any()) }
    }

    private fun directionTemplate() = CompiledTemplate(
        id = "auto-direction", sourceKey = targetPackage, priority = 100,
        pattern = Pattern.compile("""(?P<amount>\d+) (?P<curr>MDL)"""), constants = emptyMap()
    )

    private fun directionEvent(title: String, text: String) = ReplayHistoricalEvent(
        eventId = 500L, rawId = 600L, packageName = targetPackage, title = title, text = text,
        postTime = 10000L, historicalCategory = "FINANCE", historicalConfidence = 1.0,
        historicalTransactionJson = null
    )

    private fun directionEntity() = FinancialTransactionEntity(
        id = 700L, eventId = 500L, bank = targetPackage, direction = "DEBIT", amountMinor = 10000L,
        currency = "MDL", balanceMinor = null, balanceCurrency = null, merchant = null, accountMask = null,
        occurredAt = 10000L, extractorId = "bank.static", extractorVersion = 1, createdAt = 5000L
    )
}
