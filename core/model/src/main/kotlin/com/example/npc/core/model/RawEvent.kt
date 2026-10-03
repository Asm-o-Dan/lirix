package com.example.npc.core.model

import java.time.Instant

data class RawEvent(
    val id: Long = 0L,
    val seq: Long,
    val source: SourceId,
    val packageName: String,
    val receivedAt: Instant,
    val payloadJson: String,
    val hash: DeduplicationKey
) {
    init {
        require(id >= 0L) { "id must be >= 0" }
        require(seq >= 0L) { "seq must be >= 0" }
        require(packageName.isNotBlank() && packageName.length in 1..255) { "packageName must not be blank" }
        require(payloadJson.isNotBlank()) { "payloadJson must not be blank" }
    }
}
