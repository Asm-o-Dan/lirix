package com.example.npc.ingest.notification.model

import java.time.Instant

data class NotificationRawPayload(
    val seq: Long,
    val packageName: String,
    val id: Int,
    val tag: String?,
    val key: String,
    val groupKey: String?,
    val postTimeEpochMs: Long,
    val flags: Int,
    val channelId: String?,
    val extras: NotificationExtrasData,
    val receivedAt: Instant
)
