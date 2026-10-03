package com.example.npc.pipeline.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StageDefinition(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("enabled")
    val enabled: Boolean = true,

    @SerialName("condition")
    val condition: ConditionDefinition? = null,

    @SerialName("transforms")
    val transforms: List<TransformDefinition> = emptyList(),

    @SerialName("actions")
    val actions: List<ActionDefinition> = emptyList(),

    @SerialName("terminateOnMatch")
    val terminateOnMatch: Boolean = false
) {
    init {
        require(id.matches(PipelineDefinition.ID_REGEX)) {
            "Stage ID '$id' is invalid. Must match pattern ^[a-z0-9_.-]{3,64}$"
        }
        require(name.isNotBlank() && name.length <= PipelineDefinition.MAX_NAME_LENGTH) {
            "Stage name must be between 1 and ${PipelineDefinition.MAX_NAME_LENGTH} characters, but was: '$name'"
        }
        require(transforms.isNotEmpty() || actions.isNotEmpty()) {
            "Stage '$id' must declare at least one transform or action"
        }
    }
}
