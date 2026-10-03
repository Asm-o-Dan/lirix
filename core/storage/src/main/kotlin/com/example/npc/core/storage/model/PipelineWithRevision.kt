package com.example.npc.core.storage.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.entity.PipelineRevisionEntity

data class PipelineWithRevision(
    @Embedded
    val definition: PipelineDefinitionEntity,

    @Relation(
        parentColumn = "active_revision_id",
        entityColumn = "id"
    )
    val activeRevision: PipelineRevisionEntity?
)
