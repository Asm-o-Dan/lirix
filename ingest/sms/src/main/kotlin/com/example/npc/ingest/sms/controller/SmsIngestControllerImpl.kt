package com.example.npc.ingest.sms.controller

import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.SmsContract
import com.example.npc.ingest.sms.mapper.SmsMapper
import com.example.npc.ingest.sms.model.SmsRawPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import com.example.npc.core.model.pipeline.EventProcessingOrchestrator
import java.util.concurrent.atomic.AtomicLong

class SmsIngestControllerImpl(
    private val storageGateway: StorageGateway,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val orchestrator: EventProcessingOrchestrator? = null
) : SmsIngestController {

    private val seqGenerator = AtomicLong(0L)
    private val queueDepthCounter = AtomicInteger(0)
    private val channel = Channel<SmsRawPayload>(Channel.UNLIMITED)
    private var consumerJob: Job? = null

    override val queueDepth: Int
        get() = queueDepthCounter.get()

    override fun nextSeq(): Long = seqGenerator.incrementAndGet()

    override suspend fun enqueueSms(contract: SmsContract, subId: Int?): Long {
        val seq = seqGenerator.incrementAndGet()
        val payload = SmsRawPayload(
            seq = seq,
            originAddress = contract.originAddress,
            body = contract.body,
            timestampMillis = contract.timestampMillis,
            receivedAt = Instant.now(),
            subId = subId
        )
        queueDepthCounter.incrementAndGet()
        channel.send(payload)
        return seq
    }

    override suspend fun processPayload(payload: SmsRawPayload): Long {
        val payloadJson = SmsMapper.toPayloadJson(
            originAddress = payload.originAddress,
            body = payload.body,
            timestampMillis = payload.timestampMillis,
            subId = payload.subId
        )
        val dedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.SMS,
            "android.telephony.sms",
            payloadJson
        )

        val existingId = storageGateway.findDuplicate(dedupKey)
        if (existingId != null) {
            val health = SourceHealth(
                source = SourceId.SMS,
                lastEventAt = payload.receivedAt,
                events24h = 0,
                lastError = null,
                queueDepth = queueDepth
            )
            storageGateway.upsertSourceHealth(health)
            return existingId
        }

        val rawEvent = SmsMapper.toRawEvent(payload, dedupKey)
        val rawId = storageGateway.insertRawEvent(rawEvent)

        val contract = SmsContract(
            originAddress = payload.originAddress,
            body = payload.body,
            timestampMillis = payload.timestampMillis
        )
        val event = SmsMapper.toEvent(rawEvent.copy(id = rawId), contract)
        val savedEventId = storageGateway.insertEvent(event)
        orchestrator?.submit(savedEventId)

        val health = SourceHealth(
            source = SourceId.SMS,
            lastEventAt = payload.receivedAt,
            events24h = 0,
            lastError = null,
            queueDepth = queueDepth
        )
        storageGateway.upsertSourceHealth(health)

        return rawId
    }

    override fun start() {
        if (consumerJob != null) return
        consumerJob = scope.launch(ioDispatcher) {
            while (isActive) {
                val payload = channel.receive()
                queueDepthCounter.decrementAndGet()
                try {
                    processPayload(payload)
                } catch (t: Throwable) {
                    val health = SourceHealth(
                        source = SourceId.SMS,
                        lastEventAt = payload.receivedAt,
                        events24h = 0,
                        lastError = t.message ?: "Unknown error",
                        queueDepth = queueDepth
                    )
                    storageGateway.upsertSourceHealth(health)
                }
            }
        }
    }

    override fun stop() {
        consumerJob?.cancel()
        consumerJob = null
    }
}
