package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pipeline_revision",
    foreignKeys = [
        ForeignKey(
            entity = PipelineDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["pipeline_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pipeline_id", "revision_number"], unique = true),
        Index(value = ["pipeline_id", "created_at"], unique = false),
        Index(value = ["canonical_sha256"], unique = false)
    ]
)
data class PipelineRevisionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "pipeline_id")
    val pipelineId: String,

    @ColumnInfo(name = "revision_number")
    val revisionNumber: Long,

    @ColumnInfo(name = "definition_json")
    val definitionJson: String,

    @ColumnInfo(name = "canonical_sha256")
    val canonicalSha256: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "commit_message")
    val commitMessage: String? = null
)
