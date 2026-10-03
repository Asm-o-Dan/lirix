package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "extraction_feedback",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["created_at"])
    ]
)
data class ExtractionFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "event_id")
    val eventId: Long,

    @ColumnInfo(name = "features_json")
    val featuresJson: String,

    @ColumnInfo(name = "verdict")
    val verdict: String,

    @ColumnInfo(name = "extractor_kind")
    val extractorKind: String,

    @ColumnInfo(name = "user_action")
    val userAction: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
