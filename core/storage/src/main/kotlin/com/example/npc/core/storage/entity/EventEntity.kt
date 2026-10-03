package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(
            entity = RawEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["raw_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["is_update_of"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PipelineRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["pipeline_revision_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["raw_id"], unique = false),
        Index(value = ["ts"], unique = false),
        Index(value = ["thread_key"], unique = false),
        Index(value = ["is_update_of"], unique = false),
        Index(value = ["category"], unique = false),
        Index(value = ["content_fingerprint"], unique = false),
        Index(value = ["pipeline_revision_id"], unique = false)
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "raw_id")
    val rawId: Long,

    @ColumnInfo(name = "ts")
    val ts: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "normalized_text")
    val normalizedText: String,

    @ColumnInfo(name = "lang")
    val lang: String,

    @ColumnInfo(name = "thread_key")
    val threadKey: String?,

    @ColumnInfo(name = "is_update_of")
    val isUpdateOf: Long?,

    // Поля Фазы 1
    @ColumnInfo(name = "category", defaultValue = "'UNCLASSIFIED'")
    val category: String = "UNCLASSIFIED",

    @ColumnInfo(name = "confidence", defaultValue = "0.0")
    val confidence: Double = 0.0,

    @ColumnInfo(name = "engine_used", defaultValue = "'NONE'")
    val engineUsed: String = "NONE",

    @ColumnInfo(name = "is_user_corrected", defaultValue = "0")
    val isUserCorrected: Boolean = false,

    @ColumnInfo(name = "content_fingerprint", defaultValue = "NULL")
    val contentFingerprint: String? = null,

    // Новое поле Фазы 2 (Схема v3): связь с ревизией конвейера
    @ColumnInfo(name = "pipeline_revision_id", defaultValue = "NULL")
    val pipelineRevisionId: Long? = null
)
