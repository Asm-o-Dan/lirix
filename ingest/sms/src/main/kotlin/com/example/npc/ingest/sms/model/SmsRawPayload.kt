package com.example.npc.ingest.sms.model

import java.time.Instant

data class SmsRawPayload(
    val seq: Long,
    val originAddress: String,
    val body: String,
    val timestampMillis: Long,
    val receivedAt: Instant,
    val subId: Int? = null
) {
    init {
        require(seq > 0L) { "seq must be > 0" }
        require(originAddress.isNotBlank()) { "originAddress must not be blank" }
        require(timestampMillis >= 0L) { "timestampMillis must be >= 0" }
    }
}
