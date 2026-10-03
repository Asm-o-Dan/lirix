package com.example.npc.core.model

import java.time.Instant

data class SourceHealth(
    val source: SourceId,
    val lastEventAt: Instant?,
    val events24h: Int,
    val lastError: String?,
    val queueDepth: Int
) {
    init {
        require(events24h >= 0) { "events24h must be >= 0" }
        require(queueDepth >= 0) { "queueDepth must be >= 0" }
        require(lastError == null || (lastError.isNotBlank() && lastError.length <= 1000)) { "lastError length must be <= 1000" }
    }
}
