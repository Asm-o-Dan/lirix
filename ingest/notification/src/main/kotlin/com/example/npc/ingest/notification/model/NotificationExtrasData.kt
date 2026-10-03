package com.example.npc.ingest.notification.model

data class NotificationExtrasData(
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String>,
    val subText: String?,
    val infoText: String?,
    val progressMax: Int,
    val progressCurrent: Int,
    val isProgressIndeterminate: Boolean,
    val conversationTitle: String?,
    val messagingStyle: MessagingStyleData?
)
