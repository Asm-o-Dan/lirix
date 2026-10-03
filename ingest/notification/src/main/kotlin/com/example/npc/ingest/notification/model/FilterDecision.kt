package com.example.npc.ingest.notification.model

data class FilterDecision(
    val isAccepted: Boolean,
    val rejectionReason: FilterRejectionReason?
)
