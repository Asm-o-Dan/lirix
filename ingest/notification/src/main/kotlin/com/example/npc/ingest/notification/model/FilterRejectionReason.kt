package com.example.npc.ingest.notification.model

enum class FilterRejectionReason {
    OWN_PACKAGE,
    ONGOING_EVENT,
    FOREGROUND_SERVICE,
    PROGRESS_BAR,
    GROUP_SUMMARY,
    EMPTY_CONTENT
}
