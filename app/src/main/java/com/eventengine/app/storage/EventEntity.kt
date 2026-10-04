package com.eventengine.app.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eventengine.app.domain.Category
import com.eventengine.app.domain.Event
import com.eventengine.app.domain.EventSource
import com.eventengine.app.domain.EventType

/**
 * Unified persistence entity for all OS events ingested by the engine.
 * Indexed for fast time-series queries, category filtering, and deduplication lookup.
 */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["source_package", "timestamp"], name = "idx_event_pkg_time"),
        Index(value = ["category", "timestamp"], name = "idx_event_cat_time"),
        Index(value = ["event_type", "timestamp"], name = "idx_event_type_time"),
        Index(value = ["content_fingerprint"], name = "idx_event_fingerprint"),
        Index(value = ["confidence"], name = "idx_event_confidence"),
        Index(value = ["is_user_corrected"], name = "idx_event_corrected")
    ]
)
data class EventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "source")
    val source: EventSource,

    @ColumnInfo(name = "source_package")
    val sourcePackage: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "text")
    val text: String? = null,

    @ColumnInfo(name = "sub_text")
    val subText: String? = null,

    @ColumnInfo(name = "event_type")
    val eventType: EventType,

    @ColumnInfo(name = "category")
    val category: Category,

    @ColumnInfo(name = "confidence")
    val confidence: Float = 1.0f,

    @ColumnInfo(name = "is_ongoing", defaultValue = "0")
    val isOngoing: Boolean = false,

    @ColumnInfo(name = "media_track")
    val mediaTrack: String? = null,

    @ColumnInfo(name = "media_artist")
    val mediaArtist: String? = null,

    @ColumnInfo(name = "media_playback_state")
    val mediaPlaybackState: String? = null,

    @ColumnInfo(name = "content_fingerprint")
    val contentFingerprint: String = "",

    @ColumnInfo(name = "raw_payload")
    val rawPayload: String = "",

    @ColumnInfo(name = "normalized_text")
    val normalizedText: String = "",

    @ColumnInfo(name = "structured_data")
    val structuredData: String = "{}",

    @ColumnInfo(name = "is_user_corrected", defaultValue = "0")
    val isUserCorrected: Boolean = false,

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EventEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

fun EventEntity.toDomain(): Event = Event(
    id = id,
    timestamp = timestamp,
    source = source,
    sourcePackage = sourcePackage,
    appName = appName,
    title = title,
    text = text,
    subText = subText,
    eventType = eventType,
    category = category,
    confidence = confidence,
    isOngoing = isOngoing,
    mediaTrack = mediaTrack,
    mediaArtist = mediaArtist,
    mediaPlaybackState = mediaPlaybackState,
    contentFingerprint = contentFingerprint,
    rawPayload = rawPayload,
    normalizedText = normalizedText,
    structuredData = structuredData,
    isUserCorrected = isUserCorrected,
    embedding = embedding
)

fun Event.toEntity(): EventEntity = EventEntity(
    id = id,
    timestamp = timestamp,
    source = source,
    sourcePackage = sourcePackage,
    appName = appName,
    title = title,
    text = text,
    subText = subText,
    eventType = eventType,
    category = category,
    confidence = confidence,
    isOngoing = isOngoing,
    mediaTrack = mediaTrack,
    mediaArtist = mediaArtist,
    mediaPlaybackState = mediaPlaybackState,
    contentFingerprint = contentFingerprint,
    rawPayload = rawPayload,
    normalizedText = normalizedText,
    structuredData = structuredData,
    isUserCorrected = isUserCorrected,
    embedding = embedding
)
