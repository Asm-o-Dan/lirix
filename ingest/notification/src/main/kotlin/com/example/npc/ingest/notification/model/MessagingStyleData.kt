package com.example.npc.ingest.notification.model

data class MessagingStyleData(
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val messages: List<MessagingMessageData>,
    val historicMessages: List<MessagingMessageData>
)
