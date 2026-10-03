package com.example.npc.ingest.notification.model

data class MessagingMessageData(
    val text: String,
    val timestampMillis: Long,
    val senderName: String?
)
