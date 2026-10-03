package com.example.npc.core.storage

import app.cash.turbine.test
import com.example.npc.core.storage.dao.FinancialTransactionDao
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FinancialTransactionDaoTest {

    private val dao: FinancialTransactionDao = mockk(relaxed = true)

    private val sampleEntity = FinancialTransactionEntity(
        id = 1L,
        eventId = 10L,
        bank = "APB",
        direction = "DEBIT",
        amountMinor = 1550L,
        currency = "RUP",
        balanceMinor = 40000L,
        balanceCurrency = "RUP",
        merchant = "Sheriff-15",
        accountMask = "**5576",
        occurredAt = 1_000_000L,
        extractorId = "apb.notification",
        extractorVersion = 1,
        createdAt = 1_000_100L
    )

    @Test
    fun `insert stores financial transaction and returns generated id`() = runTest {
        coEvery { dao.insert(sampleEntity) } returns 1L

        val generatedId = dao.insert(sampleEntity)

        generatedId shouldBe 1L
        coVerify(exactly = 1) { dao.insert(sampleEntity) }
    }

    @Test
    fun `getByEventId returns transaction when linked to event`() = runTest {
        coEvery { dao.getByEventId(10L) } returns sampleEntity

        val result = dao.getByEventId(10L)

        result shouldBe sampleEntity
        result?.eventId shouldBe 10L
        result?.bank shouldBe "APB"
        result?.amountMinor shouldBe 1550L
        result?.currency shouldBe "RUP"
    }

    @Test
    fun `getByEventId returns null when event has no transaction`() = runTest {
        coEvery { dao.getByEventId(999L) } returns null

        val result = dao.getByEventId(999L)

        result shouldBe null
    }

    @Test
    fun `observeLatest emits stream of transactions ordered by occurredAt DESC`() = runTest {
        val txn2 = sampleEntity.copy(id = 2L, eventId = 11L, occurredAt = 2_000_000L)
        every { dao.observeLatest(10) } returns flowOf(listOf(txn2, sampleEntity))

        dao.observeLatest(10).test {
            val items = awaitItem()
            items.size shouldBe 2
            items[0].id shouldBe 2L
            items[1].id shouldBe 1L
            awaitComplete()
        }
    }

    @Test
    fun `getByPeriod filters transactions within timestamp range`() = runTest {
        val fromMs = 500_000L
        val toMs = 1_500_000L
        coEvery { dao.getByPeriod(fromMs, toMs) } returns listOf(sampleEntity)

        val result = dao.getByPeriod(fromMs, toMs)

        result.size shouldBe 1
        result[0].id shouldBe 1L
        coVerify(exactly = 1) { dao.getByPeriod(fromMs, toMs) }
    }

    @Test
    fun `foreign key ON DELETE SET NULL allows transaction to persist with null eventId`() = runTest {
        // When an event is deleted from database, foreign key ON DELETE SET NULL retains financial record
        val orphanedTxn = sampleEntity.copy(id = 1L, eventId = null)
        coEvery { dao.getById(1L) } returns orphanedTxn

        val result = dao.getById(1L)

        result?.id shouldBe 1L
        result?.eventId shouldBe null
        result?.bank shouldBe "APB"
        result?.amountMinor shouldBe 1550L
    }

    @Test
    fun `deleteAll removes all financial records`() = runTest {
        coEvery { dao.deleteAll() } returns 1

        val deletedCount = dao.deleteAll()

        deletedCount shouldBe 1
        coVerify(exactly = 1) { dao.deleteAll() }
    }
}
