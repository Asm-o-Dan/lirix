package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pipeline_definition",
    indices = [
        Index(value = ["enabled", "priority"], unique = false),
        Index(value = ["active_revision_id"], unique = false)
    ]
)
data class PipelineDefinitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "schema_version", defaultValue = "1")
    val schemaVersion: Int = 1,

    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Boolean = true,

    @ColumnInfo(name = "priority", defaultValue = "100")
    val priority: Int = 100,

    @ColumnInfo(name = "package_whitelist", defaultValue = "'[]'")
    val packageWhitelist: String = "[]",

    @ColumnInfo(name = "active_revision_id", defaultValue = "NULL")
    val activeRevisionId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
