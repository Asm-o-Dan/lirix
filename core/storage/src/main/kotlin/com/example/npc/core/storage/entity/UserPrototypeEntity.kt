package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_prototype",
    indices = [
        Index(value = ["package_name", "fingerprint"], unique = true)
    ]
)
data class UserPrototypeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "fingerprint")
    val fingerprint: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "support_count", defaultValue = "1")
    val supportCount: Int = 1,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long
)
