package com.example.npc.ui.timeline.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

@Immutable
data class SourceHealthUiModel(
    val sourceId: String,
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val status: SourceHealthStatus,
    val events24h: Int,
    val events24hLabel: String,
    val lastEventTimeLabel: String?,
    val queueDepth: Int,
    val lastError: String?
) {
    init {
        require(events24h >= 0) { "events24h must be >= 0" }
        require(queueDepth >= 0) { "queueDepth must be >= 0" }
    }

    val source: String get() = sourceId
    val events24hText: String get() = events24hLabel
    val lastEventText: String? get() = lastEventTimeLabel
    val lastErrorText: String? get() = lastError
}
