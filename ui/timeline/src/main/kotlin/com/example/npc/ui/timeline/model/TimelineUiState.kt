package com.example.npc.ui.timeline.model

import androidx.compose.runtime.Immutable

@Immutable
data class TimelineUiState(
    val events: List<EventUiModel> = emptyList(),
    val sourceHealth: List<SourceHealthUiModel> = emptyList(),
    val selectedSourceFilter: SourceFilter = SourceFilter.ALL,
    val selectedCategoryFilter: CategoryFilter = CategoryFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedEventForDetails: EventUiModel? = null,
    val eventForCategoryCorrection: EventUiModel? = null,
    val isDeleteConfirmationVisible: Boolean = false,
    val isExporting: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedFilter: SourceFilter get() = selectedSourceFilter
    val sourceHealthList: List<SourceHealthUiModel> get() = sourceHealth
    val isSuccess: Boolean get() = !isLoading && errorMessage == null && events.isNotEmpty()
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && events.isEmpty()
    val isError: Boolean get() = errorMessage != null
    val isCorrectionDialogOpen: Boolean get() = eventForCategoryCorrection != null

    constructor(
        events: List<EventUiModel> = emptyList(),
        sourceHealth: List<SourceHealthUiModel> = emptyList(),
        selectedFilter: SourceFilter = SourceFilter.ALL,
        searchQuery: String = "",
        isLoading: Boolean = false,
        selectedEventForDetails: EventUiModel? = null,
        isDeleteConfirmationVisible: Boolean = false,
        isExporting: Boolean = false,
        errorMessage: String? = null
    ) : this(
        events = events,
        sourceHealth = sourceHealth,
        selectedSourceFilter = selectedFilter,
        selectedCategoryFilter = CategoryFilter.ALL,
        searchQuery = searchQuery,
        isLoading = isLoading,
        selectedEventForDetails = selectedEventForDetails,
        eventForCategoryCorrection = null,
        isDeleteConfirmationVisible = isDeleteConfirmationVisible,
        isExporting = isExporting,
        errorMessage = errorMessage
    )
}
