package com.example.npc.core.model.pipeline

import com.example.npc.core.model.Event
import com.example.npc.core.model.SourceId

data class EventProcessingTarget(
    val event: Event,
    val packageName: String,
    val sourceId: SourceId,
    val rawPayloadJson: String
) {
    init {
        require(event.id > 0L) { "Event ID must be positive (got ${event.id})" }
        require(packageName.isNotBlank()) { "Package name must not be blank" }
    }
}
