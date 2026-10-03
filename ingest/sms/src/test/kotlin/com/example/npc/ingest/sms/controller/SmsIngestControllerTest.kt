package com.example.npc.ingest.sms.controller

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.SmsContract
import com.example.npc.ingest.sms.mapper.SmsMapper
import com.example.npc.ingest.sms.model.SmsRawPayload
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import com.example.npc.core.model.pipeline.EventProcessingOrchestrator
import com.example.npc.core.model.pipeline.OrchestratorStatus
import io.kotest.matchers.collections.shouldContain
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SmsIngestControllerTest {

    private val storageGateway: StorageGateway = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var controller: SmsIngestControllerImpl

    @BeforeEach
    fun setUp() {
        controller = SmsIngestControllerImpl(
            storageGateway = storageGateway,
            scope = testScope,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `enqueueSms increments seq via AtomicLong and returns monotonically increasing id greater than zero`() = runTest {
        val contract1 = SmsContract("+79991112233", "Сообщение 1", 1_774_567_890_000L)
        val contract2 = SmsContract("+79991112233", "Сообщение 2", 1_774_567_891_000L)

        val seq1 = controller.enqueueSms(contract1)
        val seq2 = controller.enqueueSms(contract2, subId = 1)

        seq1 shouldBeGreaterThan 0L
        seq2 shouldBeGreaterThan seq1
        seq2 shouldBe seq1 + 1L
    }

    @Test
    fun `processPayload computes dedupKey and returns existingId without inserting when duplicate found`() = runTest {
        val payload = SmsRawPayload(
            seq = 1L,
            originAddress = "Tinkoff",
            body = "Код: 1234",
            timestampMillis = 1_774_567_890_000L,
            receivedAt = Instant.parse("2026-09-26T06:30:00Z"),
            subId = null
        )
        val expectedPayloadJson = SmsMapper.toPayloadJson(
            originAddress = payload.originAddress,
            body = payload.body,
            timestampMillis = payload.timestampMillis,
            subId = payload.subId
        )
        val expectedDedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.SMS,
            "android.telephony.sms",
            expectedPayloadJson
        )

        coEvery { storageGateway.findDuplicate(expectedDedupKey) } returns 42L
        coEvery { storageGateway.upsertSourceHealth(any()) } returns Unit

        val resultId = controller.processPayload(payload)

        // Step 1: DedupKey queried
        coVerify(exactly = 1) { storageGateway.findDuplicate(expectedDedupKey) }

        // Step 2: Return existing ID without inserting rawEvent or event
        resultId shouldBe 42L
        coVerify(exactly = 0) { storageGateway.insertRawEvent(any()) }
        coVerify(exactly = 0) { storageGateway.insertEvent(any()) }

        // Step 5: Upsert health with current queueDepth
        coVerify(atLeast = 1) {
            storageGateway.upsertSourceHealth(
                match { health ->
                    health.source == SourceId.SMS &&
                        health.lastError == null &&
                        health.queueDepth >= 0
                }
            )
        }
    }

    @Test
    fun `processPayload executes immediate insertRawEvent before normalization and insertEvent when new message`() = runTest {
        val payload = SmsRawPayload(
            seq = 5L,
            originAddress = "AlfaBank",
            body = "Списание 500р",
            timestampMillis = 1_774_567_890_000L,
            receivedAt = Instant.parse("2026-09-26T06:35:00Z"),
            subId = null
        )
        val expectedPayloadJson = SmsMapper.toPayloadJson(
            originAddress = payload.originAddress,
            body = payload.body,
            timestampMillis = payload.timestampMillis,
            subId = payload.subId
        )
        val expectedDedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.SMS,
            "android.telephony.sms",
            expectedPayloadJson
        )

        coEvery { storageGateway.findDuplicate(expectedDedupKey) } returns null
        coEvery { storageGateway.insertRawEvent(any()) } returns 105L
        coEvery { storageGateway.insertEvent(any()) } returns 205L
        coEvery { storageGateway.upsertSourceHealth(any()) } returns Unit

        val resultId = controller.processPayload(payload)

        resultId shouldBe 105L

        // Order of steps: findDuplicate -> insertRawEvent (before normalization) -> insertEvent -> upsertSourceHealth
        coVerifyOrder {
            storageGateway.findDuplicate(expectedDedupKey)
            storageGateway.insertRawEvent(
                match { rawEvent ->
                    rawEvent.seq == 5L &&
                        rawEvent.source == SourceId.SMS &&
                        rawEvent.packageName == "android.telephony.sms" &&
                        rawEvent.hash == expectedDedupKey &&
                        rawEvent.receivedAt == payload.receivedAt
                }
            )
            storageGateway.insertEvent(
                match { event ->
                    event.rawId == 105L &&
                        event.title == "AlfaBank" &&
                        event.text == "Списание 500р" &&
                        event.threadKey == ThreadKey("AlfaBank") &&
                        event.isUpdateOf == null
                }
            )
            storageGateway.upsertSourceHealth(
                match { health ->
                    health.source == SourceId.SMS &&
                        health.lastEventAt == payload.receivedAt &&
                        health.lastError == null &&
                        health.queueDepth >= 0
                }
            )
        }
    }

    @Test
    fun `processPayload updates SourceHealth with actual queueDepth and records lastEventAt`() = runTest {
        val payload = SmsRawPayload(
            seq = 10L,
            originAddress = "+79990001122",
            body = "Привет",
            timestampMillis = 1_774_567_899_000L,
            receivedAt = Instant.parse("2026-09-26T06:40:00Z"),
            subId = 2
        )
        val expectedPayloadJson = SmsMapper.toPayloadJson(
            originAddress = payload.originAddress,
            body = payload.body,
            timestampMillis = payload.timestampMillis,
            subId = payload.subId
        )
        val expectedDedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.SMS,
            "android.telephony.sms",
            expectedPayloadJson
        )
        coEvery { storageGateway.findDuplicate(expectedDedupKey) } returns null
        coEvery { storageGateway.insertRawEvent(any()) } returns 200L
        coEvery { storageGateway.insertEvent(any()) } returns 300L
        val healthSlot = slot<SourceHealth>()
        coEvery { storageGateway.upsertSourceHealth(capture(healthSlot)) } returns Unit

        controller.processPayload(payload)

        healthSlot.isCaptured shouldBe true
        val health = healthSlot.captured
        health.source shouldBe SourceId.SMS
        health.lastEventAt shouldBe payload.receivedAt
        health.lastError shouldBe null
        health.queueDepth shouldBe controller.queueDepth
    }

    @Test
    fun `processPayload submits saved event id to EventProcessingOrchestrator when new message`() = runTest {
        val submittedIds = mutableListOf<Long>()
        val fakeOrchestrator = object : EventProcessingOrchestrator {
            override fun start() {}
            override fun submit(eventId: Long): Boolean { submittedIds.add(eventId); return true }
            override fun stop() {}
            override fun getStatus(): OrchestratorStatus = error("not needed")
            override fun triggerRecoverySweep(): kotlinx.coroutines.Job = kotlinx.coroutines.Job().apply { complete() }
        }

        val orchestratorController = SmsIngestControllerImpl(
            storageGateway = storageGateway,
            scope = testScope,
            ioDispatcher = testDispatcher,
            orchestrator = fakeOrchestrator
        )

        val payload = SmsRawPayload(
            seq = 7L,
            originAddress = "Bank",
            body = "Пополнение +1000",
            timestampMillis = 1_774_567_890_000L,
            receivedAt = Instant.parse("2026-09-26T06:36:00Z"),
            subId = null
        )

        val expectedPayloadJson = SmsMapper.toPayloadJson(
            originAddress = payload.originAddress,
            body = payload.body,
            timestampMillis = payload.timestampMillis,
            subId = payload.subId
        )
        val expectedDedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.SMS,
            "android.telephony.sms",
            expectedPayloadJson
        )

        coEvery { storageGateway.findDuplicate(expectedDedupKey) } returns null
        coEvery { storageGateway.insertRawEvent(match { it.seq == 7L }) } returns 110L
        coEvery { storageGateway.insertEvent(match { it.rawId == 110L }) } returns 777L
        coEvery { storageGateway.upsertSourceHealth(any()) } returns Unit

        orchestratorController.processPayload(payload)

        submittedIds shouldContain 777L
    }
}
