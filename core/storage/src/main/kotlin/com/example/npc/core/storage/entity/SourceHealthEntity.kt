package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_health")
data class SourceHealthEntity(
    @PrimaryKey
    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "last_event_at")
    val lastEventAt: Long?,

    @ColumnInfo(name = "events_24h")
    val events24h: Int,

    @ColumnInfo(name = "last_error")
    val lastError: String?,

    @ColumnInfo(name = "queue_depth")
    val queueDepth: Int
)
