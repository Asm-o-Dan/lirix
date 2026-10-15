package com.lirix.app.domain

import java.util.UUID

/**
 * Immutable domain model representing a unified on-device event.
 */
data class Event(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: EventSource,
    val sourcePackage: String,
    val appName: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val eventType: EventType = EventType.UNKNOWN,
    val category: Category = Category.UNKNOWN,
    val confidence: Float = 1.0f,
    val isOngoing: Boolean = false,
    val mediaTrack: String? = null,
    val mediaArtist: String? = null,
    val mediaPlaybackState: String? = null,
    val contentFingerprint: String = "",
    val rawPayload: String = "",
    val normalizedText: String = "",
    val structuredData: String = "{}",
    val isUserCorrected: Boolean = false,
    val embedding: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Event
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
