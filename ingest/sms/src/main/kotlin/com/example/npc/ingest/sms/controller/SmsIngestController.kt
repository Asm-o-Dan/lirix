package com.example.npc.ingest.sms.controller

import com.example.npc.ingest.sms.SmsContract
import com.example.npc.ingest.sms.model.SmsRawPayload

interface SmsIngestController {
    val queueDepth: Int
    suspend fun enqueueSms(contract: SmsContract, subId: Int? = null): Long
    suspend fun processPayload(payload: SmsRawPayload): Long
    fun nextSeq(): Long
    fun start()
    fun stop()
}
