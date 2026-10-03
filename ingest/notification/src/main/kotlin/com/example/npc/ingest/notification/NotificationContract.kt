package com.example.npc.ingest.notification

import java.time.Instant

data class NotificationContract(
    val statusBarNotification: Any,
    val receivedAt: Instant
)
