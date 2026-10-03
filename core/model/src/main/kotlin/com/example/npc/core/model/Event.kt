package com.example.npc.core.model

import java.time.Instant
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine

data class Event(
    val id: Long = 0L,
    val rawId: Long,
    val ts: Instant,
    val title: String,
    val text: String,
    val normalizedText: String,
    val lang: Lang,
    val threadKey: ThreadKey? = null,
    val isUpdateOf: Long? = null,
    val category: Category = Category.UNCLASSIFIED,
    val confidence: Float = 0.0f,
    val engineUsed: Engine = Engine.NONE,
    val isUserCorrected: Boolean = false,
    val contentFingerprint: String? = null,
    val pipelineRevisionId: Long? = null
) {
    init {
        require(id >= 0L) { "id must be >= 0" }
        require(rawId >= 0L) { "rawId must be >= 0" }
        require(isUpdateOf == null || isUpdateOf > 0L) { "isUpdateOf must be > 0 if specified" }
        require(confidence in 0.0f..1.0f) { "confidence must be between 0.0 and 1.0" }
    }
}
