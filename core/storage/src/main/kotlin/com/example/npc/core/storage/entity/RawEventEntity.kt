package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_event",
    indices = [
        Index(value = ["hash"], unique = true),
        Index(value = ["seq"], unique = false)
    ]
)
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "seq")
    val seq: Long,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "hash")
    val hash: String
)
