package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.compiler.diagnostic.SourceLocation
import com.example.npc.pipeline.compiler.diagnostic.Target
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition

interface Pass1StructuralValidator {
    fun validate(definition: PipelineDefinition): List<CompilationDiagnostic>

    companion object {
        fun create(): Pass1StructuralValidator = Pass1StructuralValidatorImpl()
    }
}

class Pass1StructuralValidatorImpl : Pass1StructuralValidator {

    private val idRegex = Regex("^[a-z0-9_.-]{3,64}$")

    override fun validate(definition: PipelineDefinition): List<CompilationDiagnostic> {
        val diagnostics = mutableListOf<CompilationDiagnostic>()

        // 1. Schema version
        if (definition.schemaVersion != 1) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P1001",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Unsupported schemaVersion: ${definition.schemaVersion}. Expected: 1",
                    messageKey = "error.schema_version.unsupported",
                    messageArgs = listOf(definition.schemaVersion.toString()),
                    location = SourceLocation(jsonPath = "$['schemaVersion']", target = Target.VALUE)
                )
            )
        }

        // 2. Pipeline ID
        if (!idRegex.matches(definition.id)) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P1002",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Invalid ID format '${definition.id}'. Must match ^[a-z0-9_.-]{3,64}$",
                    messageKey = "error.id.invalid_format",
                    messageArgs = listOf(definition.id),
                    location = SourceLocation(jsonPath = "$['id']", target = Target.VALUE)
                )
            )
        }

        // 3. Pipeline Name
        if (definition.name.isBlank() || definition.name.length > 128) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P1008",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Name cannot be blank and must not exceed 128 chars",
                    messageKey = "error.name.invalid",
                    messageArgs = listOf(definition.name),
                    location = SourceLocation(jsonPath = "$['name']", target = Target.VALUE)
                )
            )
        }

        // 4. Triggers
        if (definition.triggers.isEmpty()) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P1004",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Pipeline must declare at least one trigger",
                    messageKey = "error.triggers.empty",
                    location = SourceLocation(jsonPath = "$['triggers']", target = Target.VALUE)
                )
            )
        }

        // 5. Stages empty / count
        if (definition.stages.isEmpty()) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P1005",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Pipeline must contain at least one stage",
                    messageKey = "error.stages.empty",
                    location = SourceLocation(jsonPath = "$['stages']", target = Target.VALUE)
                )
            )
        } else if (definition.stages.size > 50) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P1006",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Pipeline exceeds maximum allowed stages count (50), got: ${definition.stages.size}",
                    messageKey = "error.stages.limit_exceeded",
                    messageArgs = listOf(definition.stages.size.toString()),
                    location = SourceLocation(jsonPath = "$['stages']", target = Target.VALUE)
                )
            )
        }

        // 6. Stages validation
        val seenStageIds = mutableMapOf<String, Int>()
        definition.stages.forEachIndexed { index, stage ->
            val stagePath = "$['stages'][$index]"

            // Stage ID format
            if (!idRegex.matches(stage.id)) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P1002",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Invalid ID format '${stage.id}'. Must match ^[a-z0-9_.-]{3,64}$",
                        messageKey = "error.id.invalid_format",
                        messageArgs = listOf(stage.id),
                        location = SourceLocation(jsonPath = "$stagePath['id']", target = Target.VALUE, stageId = stage.id)
                    )
                )
            }

            // Duplicate stage ID
            if (seenStageIds.containsKey(stage.id)) {
                val firstIndex = seenStageIds[stage.id]!!
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P1003",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Duplicate stage ID '${stage.id}'. Stage IDs must be unique within pipeline",
                        messageKey = "error.stage.duplicate_id",
                        messageArgs = listOf(stage.id),
                        location = SourceLocation(jsonPath = "$stagePath['id']", target = Target.VALUE, stageId = stage.id),
                        relatedLocations = listOf(
                            SourceLocation(jsonPath = "$['stages'][$firstIndex]['id']", target = Target.VALUE, stageId = stage.id)
                        )
                    )
                )
            } else {
                seenStageIds[stage.id] = index
            }

            // Stage Name
            if (stage.name.isBlank() || stage.name.length > 128) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P1008",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Name cannot be blank and must not exceed 128 chars",
                        messageKey = "error.name.invalid",
                        messageArgs = listOf(stage.name),
                        location = SourceLocation(jsonPath = "$stagePath['name']", target = Target.VALUE, stageId = stage.id)
                    )
                )
            }

            // At least one transform or action
            if (stage.transforms.isEmpty() && stage.actions.isEmpty()) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P1007",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Stage '${stage.id}' must declare at least one transform or action",
                        messageKey = "error.stage.empty_body",
                        messageArgs = listOf(stage.id),
                        location = SourceLocation(jsonPath = stagePath, target = Target.VALUE, stageId = stage.id)
                    )
                )
            }

            // Validate conditions
            stage.condition?.let { cond ->
                validateCondition(cond, "$stagePath['condition']", stage.id, depth = 1, diagnostics)
            }
        }

        return diagnostics
    }

    private fun validateCondition(
        condition: ConditionDefinition,
        currentPath: String,
        stageId: String,
        depth: Int,
        diagnostics: MutableList<CompilationDiagnostic>
    ) {
        if (depth > ConditionDefinition.MAX_NESTING_DEPTH) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P4003",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Condition nesting depth exceeds maximum allowed limit (${ConditionDefinition.MAX_NESTING_DEPTH})",
                    messageKey = "error.condition.max_depth_exceeded",
                    messageArgs = listOf(depth.toString(), ConditionDefinition.MAX_NESTING_DEPTH.toString()),
                    location = SourceLocation(jsonPath = currentPath, target = Target.VALUE, stageId = stageId)
                )
            )
        }

        when (condition) {
            is ConditionDefinition.LogicalAnd -> {
                if (condition.conditions.size !in 2..ConditionDefinition.MAX_LOGICAL_ARITY) {
                    diagnostics.add(
                        CompilationDiagnostic(
                            code = "P4004",
                            severity = DiagnosticSeverity.ERROR,
                            message = "Logical operator 'LogicalAnd' requires between 2 and ${ConditionDefinition.MAX_LOGICAL_ARITY} operands, but got: ${condition.conditions.size}",
                            messageKey = "error.condition.invalid_arity",
                            messageArgs = listOf("LogicalAnd", condition.conditions.size.toString()),
                            location = SourceLocation(jsonPath = "$currentPath['conditions']", target = Target.VALUE, stageId = stageId)
                        )
                    )
                }
                condition.conditions.forEachIndexed { i, child ->
                    validateCondition(child, "$currentPath['conditions'][$i]", stageId, depth + 1, diagnostics)
                }
            }
            is ConditionDefinition.LogicalOr -> {
                if (condition.conditions.size !in 2..ConditionDefinition.MAX_LOGICAL_ARITY) {
                    diagnostics.add(
                        CompilationDiagnostic(
                            code = "P4004",
                            severity = DiagnosticSeverity.ERROR,
                            message = "Logical operator 'LogicalOr' requires between 2 and ${ConditionDefinition.MAX_LOGICAL_ARITY} operands, but got: ${condition.conditions.size}",
                            messageKey = "error.condition.invalid_arity",
                            messageArgs = listOf("LogicalOr", condition.conditions.size.toString()),
                            location = SourceLocation(jsonPath = "$currentPath['conditions']", target = Target.VALUE, stageId = stageId)
                        )
                    )
                }
                condition.conditions.forEachIndexed { i, child ->
                    validateCondition(child, "$currentPath['conditions'][$i]", stageId, depth + 1, diagnostics)
                }
            }
            is ConditionDefinition.LogicalNot -> {
                validateCondition(condition.condition, "$currentPath['condition']", stageId, depth + 1, diagnostics)
            }
            else -> {}
        }
    }
}
