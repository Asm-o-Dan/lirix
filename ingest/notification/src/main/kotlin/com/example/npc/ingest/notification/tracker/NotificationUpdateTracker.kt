package com.example.npc.ingest.notification.tracker

import com.example.npc.core.model.ThreadKey
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.notification.model.NotificationExtrasData
import java.util.LinkedHashMap

class NotificationUpdateTracker(
    @Suppress("unused")
    private val storageGateway: StorageGateway,
    private val maxCacheSize: Int = 1000
) {
    private val lock = Any()
    private val memoryCache = object : LinkedHashMap<String, Long>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > maxCacheSize
        }
    }

    fun computeThreadKey(
        packageName: String,
        id: Int,
        tag: String?,
        extras: NotificationExtrasData
    ): ThreadKey {
        val messaging = extras.messagingStyle
        val rawKey = when {
            messaging != null && !messaging.conversationTitle.isNullOrBlank() -> {
                "$packageName:conv:${messaging.conversationTitle.trim()}"
            }
            !extras.conversationTitle.isNullOrBlank() -> {
                "$packageName:conv:${extras.conversationTitle.trim()}"
            }
            messaging != null && !extras.title.isNullOrBlank() -> {
                "$packageName:conv:${extras.title.trim()}"
            }
            else -> {
                val cleanTag = tag ?: ""
                "$packageName:notif:$cleanTag:$id"
            }
        }
        val safeKey = if (rawKey.length > 256) rawKey.take(256) else rawKey
        return ThreadKey(safeKey)
    }

    suspend fun resolvePreviousEventId(threadKey: ThreadKey, notificationKey: String): Long? {
        if (notificationKey.isBlank()) return null
        synchronized(lock) {
            return memoryCache[notificationKey]
        }
    }

    fun recordEventMapping(threadKey: ThreadKey, notificationKey: String, eventId: Long) {
        if (notificationKey.isBlank() || eventId <= 0L) return
        synchronized(lock) {
            memoryCache[notificationKey] = eventId
        }
    }

    fun evictNotification(notificationKey: String) {
        if (notificationKey.isBlank()) return
        synchronized(lock) {
            memoryCache.remove(notificationKey)
        }
    }
}
