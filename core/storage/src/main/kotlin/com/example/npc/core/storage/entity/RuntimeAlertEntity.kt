package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "runtime_alert",
    indices = [
        Index(value = ["pipeline_id", "timestamp"], unique = false),
        Index(value = ["level"], unique = false),
        Index(value = ["is_dismissed", "timestamp"], unique = false)
    ]
)
data class RuntimeAlertEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "pipeline_id")
    val pipelineId: String,

    @ColumnInfo(name = "node_id")
    val nodeId: String? = null,

    @ColumnInfo(name = "level")
    val level: String,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_dismissed", defaultValue = "0")
    val isDismissed: Boolean = false
)
