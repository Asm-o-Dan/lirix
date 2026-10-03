package com.example.npc.ui.timeline.model

enum class SourceFilter(val sourceId: String?) {
    ALL(null),
    NOTIFICATION("notification"),
    SMS("sms"),
    MEDIA("media");

    companion object {
        fun fromSourceId(sourceId: String?): SourceFilter = when (sourceId?.lowercase()) {
            "notification" -> NOTIFICATION
            "sms" -> SMS
            "media" -> MEDIA
            else -> ALL
        }
    }
}
