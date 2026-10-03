package com.example.npc.ui.timeline.model

sealed interface TimelineUiEffect {
    data class ShowSnackbar(val message: String) : TimelineUiEffect
    data class ShareJsonFile(val jsonContent: String, val filename: String) : TimelineUiEffect
    data class ShareJsonExport(val json: String, val filename: String = "npc_export_${System.currentTimeMillis()}.json") : TimelineUiEffect
    data class CopyToClipboard(val text: String, val label: String) : TimelineUiEffect
}
