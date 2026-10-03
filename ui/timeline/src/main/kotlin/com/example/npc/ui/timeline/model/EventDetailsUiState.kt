package com.example.npc.ui.timeline.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface EventDetailsUiState {

    @Immutable
    data object Hidden : EventDetailsUiState

    @Immutable
    data object Loading : EventDetailsUiState

    @Immutable
    data class Success(
        val event: EventUiModel,
        val rawPayloadJson: String,
        val formattedPayloadJson: String,
        val exactTimestampIso: String,
        val rawEventReceivedAtIso: String,
        val isUpdateOf: Long?,
        val deduplicationHash: String
    ) : EventDetailsUiState

    @Immutable
    data class Error(
        val message: String
    ) : EventDetailsUiState
}
