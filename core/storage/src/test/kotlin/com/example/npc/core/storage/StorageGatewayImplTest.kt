package com.example.npc.core.storage

import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.storage.dao.EventDao
import com.example.npc.core.storage.dao.RawEventDao
import com.example.npc.core.storage.dao.SourceHealthDao
import com.example.npc.core.storage.entity.EventEntity
import com.example.npc.core.storage.entity.RawEventEntity
import com.example.npc.core.storage.entity.SourceHealthEntity
import com.example.npc.core.storage.mapper.FinancialTransactionMapper
import com.example.npc.core.storage.mapper.RawEventMapper
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TxStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.dao.FinancialTransactionDao
import com.example.npc.core.storage.dao.UserPrototypeDao
import com.example.npc.core.storage.dedup.FinancialTransactionDeduplicator
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StorageGatewayImplTest {

    private val database: AppDatabase = mockk(relaxed = true)
    private val rawEventDao: RawEventDao = mockk(relaxed = true)
    private val eventDao: EventDao = mockk(relaxed = true)
    private val sourceHealthDao: SourceHealthDao = mockk(relaxed = true)
    private val transactionDao: FinancialTransactionDao = mockk(relaxed = true)
    private val prototypeDao: UserPrototypeDao = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var gateway: StorageGatewayImpl

    @BeforeEach
    fun setUp() {
        gateway = StorageGatewayImpl(
            database = database,
            rawEventDao = rawEventDao,
            eventDao = eventDao,
            sourceHealthDao = sourceHealthDao,
            transactionDao = transactionDao,
            prototypeDao = prototypeDao,
            deduplicator = FinancialTransactionDeduplicator(),
            ioDispatcher = testDispatcher,
            transactionRunner = { it.invoke() }
        )
    }

    @Test
    fun `insertRawEvent successfully inserts and returns id`() = runTest(testDispatcher) {
        val rawEvent = RawEvent(
            id = 0L,
            seq = 1L,
            source = SourceId("notification"),
            packageName = "com.test",
            receivedAt = Instant.ofEpochMilli(1_000L),
            payloadJson = """{"k":"v"}""",
            hash = DeduplicationKey("a".repeat(64))
        )

        coEvery { rawEventDao.insert(any()) } returns 101L

        val result = gateway.insertRawEvent(rawEvent)

        result shouldBe 101L
        coVerify(exactly = 1) { rawEventDao.insert(any()) }
    }

    @Test
    fun `insertRawEvent catches SQLiteConstraintException and returns existing id`() = runTest(testDispatcher) {
        val rawEvent = RawEvent(
            id = 0L,
            seq = 2L,
            source = SourceId("notification"),
            packageName = "com.test",
            receivedAt = Instant.ofEpochMilli(2_000L),
            payloadJson = """{"k":"v2"}""",
            hash = DeduplicationKey("d".repeat(64))
        )

        coEvery { rawEventDao.insert(any()) } throws SQLiteConstraintException("UNIQUE constraint failed: raw_event.hash")
        coEvery { rawEventDao.findIdByHash("d".repeat(64)) } returns 55L

        val result = gateway.insertRawEvent(rawEvent)

        result shouldBe 55L
        coVerify(exactly = 1) { rawEventDao.findIdByHash("d".repeat(64)) }
    }

    @Test
    fun `insertRawEvent catches SQLiteConstraintException and returns -1 when existing id not found`() = runTest(testDispatcher) {
        val rawEvent = RawEvent(
            id = 0L,
            seq = 3L,
            source = SourceId("sms"),
            packageName = "com.test",
            receivedAt = Instant.ofEpochMilli(3_000L),
            payloadJson = """{}""",
            hash = DeduplicationKey("e".repeat(64))
        )

        coEvery { rawEventDao.insert(any()) } throws SQLiteConstraintException("UNIQUE constraint failed")
        coEvery { rawEventDao.findIdByHash("e".repeat(64)) } returns null

        val result = gateway.insertRawEvent(rawEvent)

        result shouldBe -1L
    }

    @Test
    fun `insertEvent delegates to eventDao and returns id`() = runTest(testDispatcher) {
        val event = Event(
            id = 0L,
            rawId = 101L,
            ts = Instant.ofEpochMilli(5_000L),
            title = "Title",
            text = "Text",
            normalizedText = "Text",
            lang = Lang.EN,
            threadKey = ThreadKey("t1"),
            isUpdateOf = null
        )

        coEvery { eventDao.insert(any()) } returns 202L

        val result = gateway.insertEvent(event)

        result shouldBe 202L
        coVerify(exactly = 1) { eventDao.insert(any()) }
    }

    @Test
    fun `upsertSourceHealth delegates to sourceHealthDao`() = runTest(testDispatcher) {
        val health = SourceHealth(
            source = SourceId("notification"),
            lastEventAt = Instant.ofEpochMilli(6_000L),
            events24h = 10,
            lastError = null,
            queueDepth = 0
        )

        gateway.upsertSourceHealth(health)

        coVerify(exactly = 1) { sourceHealthDao.upsert(any()) }
    }

    @Test
    fun `findDuplicate delegates to rawEventDao findIdByHash`() = runTest(testDispatcher) {
        val key = DeduplicationKey("f".repeat(64))
        coEvery { rawEventDao.findIdByHash("f".repeat(64)) } returns 77L

        val result = gateway.findDuplicate(key)

        result shouldBe 77L
        coVerify(exactly = 1) { rawEventDao.findIdByHash("f".repeat(64)) }
    }

    @Test
    fun `findDuplicate returns null when not found`() = runTest(testDispatcher) {
        val key = DeduplicationKey("0".repeat(64))
        coEvery { rawEventDao.findIdByHash("0".repeat(64)) } returns null

        val result = gateway.findDuplicate(key)

        result shouldBe null
    }

    @Test
    fun `observeEvents throws IllegalArgumentException when limit is 0 or negative`() {
        assertThrows<IllegalArgumentException> {
            gateway.observeEvents(0)
        }
        assertThrows<IllegalArgumentException> {
            gateway.observeEvents(-5)
        }
    }

    @Test
    fun `observeEvents emits mapped domain events`() = runTest(testDispatcher) {
        val entity = EventEntity(
            id = 1L,
            rawId = 10L,
            ts = 1_000L,
            title = "Title",
            text = "Body",
            normalizedText = "Body",
            lang = "EN",
            threadKey = null,
            isUpdateOf = null
        )
        every { eventDao.observeLatest(10) } returns flowOf(listOf(entity))

        gateway.observeEvents(10).test {
            val list = awaitItem()
            list.size shouldBe 1
            list[0].id shouldBe 1L
            list[0].lang shouldBe Lang.EN
            awaitComplete()
        }
    }

    @Test
    fun `observeSourceHealth emits mapped domain source healths`() = runTest(testDispatcher) {
        val entity = SourceHealthEntity(
            source = "notification",
            lastEventAt = 2_000L,
            events24h = 5,
            lastError = null,
            queueDepth = 0
        )
        every { sourceHealthDao.observeAll() } returns flowOf(listOf(entity))

        gateway.observeSourceHealth().test {
            val list = awaitItem()
            list.size shouldBe 1
            list[0].source shouldBe SourceId("notification")
            list[0].events24h shouldBe 5
            awaitComplete()
        }
    }

    @Test
    fun `exportAllToJson returns valid JSON with all tables`() = runTest(testDispatcher) {
        coEvery { rawEventDao.getAll() } returns listOf(
            RawEventEntity(1L, 1L, "sms", "com.sms", 1000L, "{}", "hash1")
        )
        coEvery { eventDao.getAll() } returns listOf(
            EventEntity(1L, 1L, 1000L, "T", "B", "B", "EN", null, null)
        )
        coEvery { sourceHealthDao.getAll() } returns listOf(
            SourceHealthEntity("sms", 1000L, 1, null, 0)
        )

        val json = gateway.exportAllToJson()

        json shouldContain """"version":1"""
        json shouldContain """"exportedAt":"""
        json shouldContain """"rawEvents":"""
        json shouldContain """"events":"""
        json shouldContain """"sourceHealth":"""
    }

    @Test
    fun `deleteAllData invokes deleteAll on all three DAOs`() = runTest(testDispatcher) {
        coEvery { rawEventDao.deleteAll() } returns 1
        coEvery { eventDao.deleteAll() } returns 1
        coEvery { sourceHealthDao.deleteAll() } returns 1

        gateway.deleteAllData()

        coVerify(exactly = 1) { eventDao.deleteAll() }
        coVerify(exactly = 1) { rawEventDao.deleteAll() }
        coVerify(exactly = 1) { sourceHealthDao.deleteAll() }
    }

    @Test
    fun `getRawEvent returns mapped domain when found`() = runTest(testDispatcher) {
        val entity = RawEventEntity(
            id = 10L,
            seq = 1L,
            source = "notification",
            packageName = "com.test",
            receivedAt = 1000L,
            payloadJson = "{}",
            hash = "a".repeat(64)
        )
        coEvery { rawEventDao.getById(10L) } returns entity

        val result = gateway.getRawEvent(10L)
        result shouldBe RawEventMapper.toDomain(entity)
        coVerify(exactly = 1) { rawEventDao.getById(10L) }
    }

    @Test
    fun `getRawEvent returns null when not found`() = runTest(testDispatcher) {
        coEvery { rawEventDao.getById(999L) } returns null

        val result = gateway.getRawEvent(999L)
        result shouldBe null
    }

    @Test
    fun `getRawEventByEventId returns raw event when event and raw exist`() = runTest(testDispatcher) {
        val eventEntity = EventEntity(
            id = 50L,
            rawId = 10L,
            ts = 1000L,
            title = "Title",
            text = "Text",
            normalizedText = "Text",
            lang = "EN",
            threadKey = null,
            isUpdateOf = null
        )
        val rawEntity = RawEventEntity(
            id = 10L,
            seq = 1L,
            source = "notification",
            packageName = "com.test",
            receivedAt = 1000L,
            payloadJson = """{"data":"payload"}""",
            hash = "a".repeat(64)
        )
        coEvery { eventDao.getById(50L) } returns eventEntity
        coEvery { rawEventDao.getById(10L) } returns rawEntity

        val result = gateway.getRawEventByEventId(50L)
        result shouldBe RawEventMapper.toDomain(rawEntity)
    }

    @Test
    fun `getRawEventByEventId returns null when event not found`() = runTest(testDispatcher) {
        coEvery { eventDao.getById(404L) } returns null

        val result = gateway.getRawEventByEventId(404L)
        result shouldBe null
    }

    @Test
    fun `saveProcessedEvent with isUpdateOf enriches existing transaction and does not insert new transaction`() = runTest(testDispatcher) {
        val initialEventId = 10L
        val existingTxnEntity = FinancialTransactionEntity(
            id = 1L,
            eventId = initialEventId,
            bank = "APB",
            direction = "CREDIT",
            amountMinor = 22200L,
            currency = "RUP",
            balanceMinor = null,
            balanceCurrency = null,
            merchant = "Виталий С.",
            accountMask = "*5576",
            occurredAt = 1_000_000L,
            extractorId = "apb.push",
            extractorVersion = 1,
            createdAt = 1_000_000L
        )

        coEvery { eventDao.insert(any()) } returns 11L
        coEvery { transactionDao.getByEventId(11L) } returns null
        coEvery { transactionDao.getByEventId(initialEventId) } returns existingTxnEntity
        coEvery { transactionDao.getByPeriod(any(), any()) } returns emptyList()

        val updateEvent = Event(
            id = 0L,
            rawId = 2L,
            ts = Instant.ofEpochMilli(1_050_000L),
            title = "Агропромбанк",
            text = "Пополнение счета по карте Клевер 910401******5576, 222.00 RUP",
            normalizedText = "Пополнение счета по карте Клевер 910401******5576, 222.00 RUP",
            lang = Lang.RU,
            isUpdateOf = initialEventId
        )

        val updatedTxn = FinancialTransaction(
            id = 0L,
            eventId = null,
            bank = "APB",
            type = TransactionType.CREDIT,
            amount = Money(22200L, CurrencyCode.RUP),
            balance = null,
            merchant = null,
            accountMask = "910401******5576",
            status = TransactionStatus.COMPLETED,
            occurredAt = Instant.ofEpochMilli(1_050_000L),
            extractorId = "apb.push",
            extractorVersion = 1,
            rawText = updateEvent.text
        )

        val slot = slot<FinancialTransactionEntity>()
        coEvery { transactionDao.insert(capture(slot)) } returns 1L

        val classification = ClassificationResult(
            category = Category.FINANCE,
            confidence = 0.99,
            engine = Engine.RULES,
            contentFingerprint = "a".repeat(64)
        )

        val eventId = gateway.saveProcessedEvent(updateEvent, classification, updatedTxn)
        eventId shouldBe 11L

        coVerify(exactly = 1) { transactionDao.insert(any()) }
        slot.captured.id shouldBe 1L
        slot.captured.eventId shouldBe initialEventId
        slot.captured.amountMinor shouldBe 22200L
        slot.captured.merchant shouldBe "Виталий С."
        slot.captured.accountMask shouldBe "910401******5576"
    }

    @Test
    fun `saveProcessedEvent with 5-minute APB duplicate merges and does not insert duplicate financial transaction`() = runTest(testDispatcher) {
        val baseMs = 1_000_000L
        val p2pTxnEntity = FinancialTransactionEntity(
            id = 1L,
            eventId = 101L,
            bank = "APB",
            direction = "CREDIT",
            amountMinor = 22200L,
            currency = "RUP",
            balanceMinor = null,
            balanceCurrency = null,
            merchant = "Виталий С.",
            accountMask = "*5576",
            occurredAt = baseMs,
            extractorId = "apb.push",
            extractorVersion = 1,
            createdAt = baseMs
        )

        val processingEvent = Event(
            id = 0L,
            rawId = 2L,
            ts = Instant.ofEpochMilli(baseMs + 240_000L),
            title = "Агропромбанк",
            text = "Пополнение счета по карте Клевер 910401******5576, 222.00 RUP",
            normalizedText = "Пополнение счета по карте Клевер 910401******5576, 222.00 RUP",
            lang = Lang.RU,
            isUpdateOf = null
        )

        val processingTxn = FinancialTransaction(
            id = 0L,
            eventId = null,
            bank = "APB",
            type = TransactionType.CREDIT,
            amount = Money(22200L, CurrencyCode.RUP),
            balance = null,
            merchant = null,
            accountMask = "910401******5576",
            status = TransactionStatus.COMPLETED,
            occurredAt = Instant.ofEpochMilli(baseMs + 240_000L),
            extractorId = "apb.push",
            extractorVersion = 1,
            rawText = processingEvent.text
        )

        coEvery { eventDao.insert(any()) } returns 102L
        coEvery { transactionDao.getByEventId(102L) } returns null
        coEvery { transactionDao.getByPeriod(any(), any()) } returns listOf(p2pTxnEntity)
        val slot = slot<FinancialTransactionEntity>()
        coEvery { transactionDao.insert(capture(slot)) } returns 1L
        coEvery { eventDao.updateIsUpdateOf(102L, 101L) } returns 1

        val classification = ClassificationResult(
            category = Category.FINANCE,
            confidence = 0.99,
            engine = Engine.RULES,
            contentFingerprint = "b".repeat(64)
        )

        val eventId = gateway.saveProcessedEvent(processingEvent, classification, processingTxn)
        eventId shouldBe 102L

        coVerify(exactly = 1) { transactionDao.insert(any()) }
        slot.captured.id shouldBe 1L
        slot.captured.eventId shouldBe 101L
        slot.captured.amountMinor shouldBe 22200L
        slot.captured.merchant shouldBe "Виталий С."
        slot.captured.accountMask shouldBe "910401******5576"
        coVerify(exactly = 1) { eventDao.updateIsUpdateOf(102L, 101L) }
    }

    @Test
    fun `insertTransaction with semantic duplicate within 5 minutes enriches existing transaction`() = runTest(testDispatcher) {
        val baseMs = 1_000_000L
        val existingEntity = FinancialTransactionEntity(
            id = 5L,
            eventId = 201L,
            bank = "APB",
            direction = "DEBIT",
            amountMinor = 15000L,
            currency = "RUP",
            balanceMinor = null,
            balanceCurrency = null,
            merchant = null,
            accountMask = "*5576",
            occurredAt = baseMs,
            extractorId = "apb.sms",
            extractorVersion = 1,
            createdAt = baseMs
        )

        val incomingTxn = FinancialTransaction(
            id = 0L,
            eventId = 202L,
            bank = "com.apb.mobile",
            type = TransactionType.DEBIT,
            amount = Money(15000L, CurrencyCode.RUP),
            balance = null,
            merchant = "Sheriff-15",
            accountMask = "910401******5576",
            status = TransactionStatus.COMPLETED,
            occurredAt = Instant.ofEpochMilli(baseMs + 60_000L),
            extractorId = "apb.push",
            extractorVersion = 1,
            rawText = "Покупка Sheriff-15 150.00 RUP"
        )

        coEvery { transactionDao.getByEventId(202L) } returns null
        coEvery { eventDao.getById(202L) } returns null
        coEvery { transactionDao.getByPeriod(any(), any()) } returns listOf(existingEntity)
        val slot = slot<FinancialTransactionEntity>()
        coEvery { transactionDao.insert(capture(slot)) } returns 5L
        coEvery { eventDao.updateIsUpdateOf(202L, 201L) } returns 1

        val resultId = gateway.insertTransaction(incomingTxn)
        resultId shouldBe 5L

        coVerify(exactly = 1) { transactionDao.insert(any()) }
        slot.captured.id shouldBe 5L
        slot.captured.merchant shouldBe "Sheriff-15"
        slot.captured.accountMask shouldBe "910401******5576"
    }

    @Test
    fun `collapseExistingDuplicates retroactively collapses duplicates and deletes duplicate records`() = runTest(testDispatcher) {
        val baseMs = 1_000_000L
        val txn1 = FinancialTransactionEntity(
            id = 1L,
            eventId = 10L,
            bank = "APB",
            direction = "DEBIT",
            amountMinor = 5000L,
            currency = "RUP",
            balanceMinor = null,
            balanceCurrency = null,
            merchant = "Apteka",
            accountMask = "*1234",
            occurredAt = baseMs,
            extractorId = "apb.sms",
            extractorVersion = 1,
            createdAt = baseMs
        )
        val txn2Duplicate = FinancialTransactionEntity(
            id = 2L,
            eventId = 11L,
            bank = "com.apb.mobile",
            direction = "DEBIT",
            amountMinor = 5000L,
            currency = "RUP",
            balanceMinor = null,
            balanceCurrency = null,
            merchant = null,
            accountMask = "910401******1234",
            occurredAt = baseMs + 120_000L, // +2 min
            extractorId = "apb.push",
            extractorVersion = 1,
            createdAt = baseMs + 120_000L
        )

        coEvery { transactionDao.getAll() } returns listOf(txn1, txn2Duplicate)
        coEvery { eventDao.getById(any()) } returns null
        coEvery { transactionDao.insert(any()) } returns 1L
        coEvery { transactionDao.deleteById(2L) } returns 1
        coEvery { eventDao.updateIsUpdateOf(11L, 10L) } returns 1

        val collapsed = gateway.collapseExistingDuplicates()
        collapsed shouldBe 1

        coVerify(exactly = 1) { transactionDao.deleteById(2L) }
        coVerify(exactly = 1) { eventDao.updateIsUpdateOf(11L, 10L) }
    }

    @Test
    fun `reprocessing same event repairs automatic direction amount and status without a duplicate`() = runTest(testDispatcher) {
        val existing = correctionFixture()
        coEvery { eventDao.getById(201L) } returns null
        coEvery { transactionDao.getByEventId(201L) } returns existing
        val captured = slot<FinancialTransactionEntity>()
        coEvery { transactionDao.insert(capture(captured)) } returns existing.id
        val corrected = FinancialTransactionMapper.toDomain(existing).copy(
            type = TransactionType.CREDIT,
            amount = Money(2500, CurrencyCode.USD),
            status = TransactionStatus.DECLINED,
            txStatus = TxStatus.SUGGESTED,
            isRefund = false
        )

        gateway.insertTransaction(corrected) shouldBe existing.id

        captured.captured.direction shouldBe "CREDIT"
        captured.captured.amountMinor shouldBe 2500L
        captured.captured.status shouldBe "DECLINED"
        captured.captured.txStatus shouldBe "SUGGESTED"
        captured.captured.id shouldBe existing.id
        captured.captured.eventId shouldBe existing.eventId
        captured.captured.bankVersion shouldBe existing.bankVersion
        captured.captured.isRefund shouldBe false
        coVerify(exactly = 0) { transactionDao.getByPeriod(any(), any()) }
    }

    @Test
    fun `reprocessing cannot overwrite persisted user direction or amount`() = runTest(testDispatcher) {
        for (confirmation in listOf(TxStatus.USER_CONFIRMED, TxStatus.USER_EDITED)) {
            val existing = correctionFixture().copy(txStatus = confirmation.name)
            coEvery { eventDao.getById(201L) } returns null
            coEvery { transactionDao.getByEventId(201L) } returns existing
            val captured = slot<FinancialTransactionEntity>()
            coEvery { transactionDao.insert(capture(captured)) } returns existing.id
            val incoming = FinancialTransactionMapper.toDomain(existing).copy(
                type = TransactionType.CREDIT,
                amount = Money(2500, CurrencyCode.USD),
                status = TransactionStatus.DECLINED,
                txStatus = TxStatus.CONFIRMED_AUTO
            )

            gateway.insertTransaction(incoming) shouldBe existing.id
            captured.captured shouldBe existing
        }
    }

    @Test
    fun `two explicit manual edits of same event persist latest direction amount status and refund`() = runTest(testDispatcher) {
        var stored = correctionFixture().copy(txStatus = "USER_CONFIRMED", isRefund = false)
        coEvery { eventDao.getById(201L) } returns null
        coEvery { transactionDao.getByEventId(201L) } answers { stored }
        coEvery { transactionDao.insert(any()) } answers {
            stored = firstArg<FinancialTransactionEntity>()
            stored.id
        }
        val firstEdit = FinancialTransactionMapper.toDomain(stored).copy(
            type = TransactionType.CREDIT,
            amount = Money(2500, CurrencyCode.USD),
            status = TransactionStatus.DECLINED,
            txStatus = TxStatus.USER_EDITED,
            isRefund = true
        )
        gateway.insertTransaction(firstEdit) shouldBe 5L
        stored.direction shouldBe "CREDIT"
        stored.amountMinor shouldBe 2500L
        stored.status shouldBe "DECLINED"
        stored.txStatus shouldBe "USER_EDITED"
        stored.isRefund shouldBe true

        val secondEdit = firstEdit.copy(
            type = TransactionType.DEBIT,
            amount = Money(3000, CurrencyCode.USD),
            status = TransactionStatus.COMPLETED,
            isRefund = false
        )
        gateway.insertTransaction(secondEdit) shouldBe 5L
        stored.direction shouldBe "DEBIT"
        stored.amountMinor shouldBe 3000L
        stored.status shouldBe "COMPLETED"
        stored.txStatus shouldBe "USER_EDITED"
        stored.isRefund shouldBe false
        stored.bankVersion shouldBe 7L
        stored.eventId shouldBe 201L
    }

    private fun correctionFixture() = FinancialTransactionEntity(
        id = 5L,
        eventId = 201L,
        bank = "AnyBank",
        direction = "DEBIT",
        amountMinor = 9000L,
        currency = "USD",
        balanceMinor = null,
        balanceCurrency = null,
        merchant = "Shop",
        accountMask = "*1234",
        occurredAt = 1000L,
        extractorId = "universal",
        extractorVersion = 1,
        createdAt = 2000L,
        bankVersion = 7L,
        isRefund = true
    )
}
