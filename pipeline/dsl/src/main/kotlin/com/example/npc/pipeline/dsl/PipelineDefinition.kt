package com.example.npc.pipeline.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PipelineDefinition(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("description")
    val description: String? = null,

    @SerialName("schemaVersion")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,

    @SerialName("revision")
    val revision: Long = 1L,

    @SerialName("enabled")
    val enabled: Boolean = true,

    @SerialName("priority")
    val priority: Int = 100,

    @SerialName("packageWhitelist")
    val packageWhitelist: List<String> = emptyList(),

    @SerialName("triggers")
    val triggers: List<TriggerDefinition>,

    @SerialName("stages")
    val stages: List<StageDefinition>,

    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.matches(ID_REGEX)) {
            "Pipeline ID '$id' is invalid. Must match pattern ^[a-z0-9_.-]{3,64}$"
        }
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) {
            "Pipeline name must be between 1 and $MAX_NAME_LENGTH characters, but was: '${name.take(30)}...'"
        }
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported schemaVersion: $schemaVersion. Expected: $CURRENT_SCHEMA_VERSION"
        }
        require(revision >= 1L) {
            "Revision must be positive (>= 1), but was: $revision"
        }
        require(priority in 0..MAX_PRIORITY) {
            "Priority must be between 0 and $MAX_PRIORITY, but was: $priority"
        }
        require(triggers.isNotEmpty()) {
            "Pipeline must declare at least one trigger"
        }
        require(stages.isNotEmpty()) {
            "Pipeline must contain at least one stage"
        }
        require(stages.size <= MAX_STAGES_COUNT) {
            "Pipeline cannot exceed $MAX_STAGES_COUNT stages, but has: ${stages.size}"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_NAME_LENGTH = 128
        const val MAX_PRIORITY = 1000
        const val MAX_STAGES_COUNT = 50
        val ID_REGEX = Regex("^[a-z0-9_.-]{3,64}$")
    }
}
