package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.compiler.diagnostic.SourceLocation
import com.example.npc.pipeline.compiler.diagnostic.Target
import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.TextFieldTarget
import com.example.npc.pipeline.dsl.TransformDefinition
import com.example.npc.pipeline.nodes.api.frame.FrameLayout

enum class DataType {
    STRING,
    LONG,
    DOUBLE,
    BOOLEAN,
    OBJECT_TRANSACTION,
    POISON
}

data class VariableSymbol(
    val name: String,
    val type: DataType,
    val definedAtStageId: String,
    val slotIndex: Int
)

data class DataflowAnalysisResult(
    val diagnostics: List<CompilationDiagnostic>,
    val symbolTable: Map<String, VariableSymbol>,
    val requiredInputMask: Long
)

interface Pass3DataflowAnalyzer {
    fun analyze(definition: PipelineDefinition): DataflowAnalysisResult

    companion object {
        fun create(): Pass3DataflowAnalyzer = Pass3DataflowAnalyzerImpl()
    }
}

class Pass3DataflowAnalyzerImpl : Pass3DataflowAnalyzer {

    override fun analyze(definition: PipelineDefinition): DataflowAnalysisResult {
        val diagnostics = mutableListOf<CompilationDiagnostic>()
        var inputMask = 0L

        // Built-in read-only inputs
        val systemInputs = mapOf(
            "input.title" to (DataType.STRING to FrameLayout.MASK_INPUT_TITLE),
            "input.text" to (DataType.STRING to FrameLayout.MASK_INPUT_TEXT),
            "input.sender" to (DataType.STRING to FrameLayout.MASK_INPUT_SENDER),
            "input.postTime" to (DataType.LONG to FrameLayout.MASK_INPUT_POST_TIME),
            "input.packageName" to (DataType.STRING to FrameLayout.MASK_INPUT_PACKAGE),
            "input.channelId" to (DataType.LONG to FrameLayout.MASK_INPUT_CHANNEL_ID)
        )

        // 1. Calculate input mask from triggers & conditions & default transforms
        definition.stages.forEach { stage ->
            stage.condition?.let { cond ->
                inputMask = inputMask or computeConditionInputMask(cond)
            }
        }

        // Variable tracking
        val symbolTable = mutableMapOf<String, VariableSymbol>()
        val alwaysDefinedVars = mutableSetOf<String>()
        val conditionallyDefinedVars = mutableSetOf<String>()
        val readVars = mutableSetOf<String>()
        val actionConsumedVars = mutableSetOf<String>()
        var nextSlotIndex = 0

        definition.stages.forEachIndexed { stageIndex, stage ->
            val stagePath = "$['stages'][$stageIndex]"
            val isConditional = stage.condition != null

            // Process transforms in this stage
            stage.transforms.forEachIndexed { tIndex, transform ->
                val transformPath = "$stagePath['transforms'][$tIndex]"

                when (transform) {
                    is TransformDefinition.RegionalTextSanitize -> {
                        inputMask = inputMask or FrameLayout.MASK_INPUT_TEXT
                        val target = transform.targetVar
                        checkReadOnlyWrite(target, transformPath, stage.id, diagnostics)
                        registerVariable(
                            name = target,
                            type = DataType.STRING,
                            stageId = stage.id,
                            transformPath = transformPath,
                            symbolTable = symbolTable,
                            alwaysDefinedVars = alwaysDefinedVars,
                            conditionallyDefinedVars = conditionallyDefinedVars,
                            isConditional = isConditional,
                            diagnostics = diagnostics,
                            allocSlot = { nextSlotIndex++ }
                        )
                    }
                    is TransformDefinition.FingerprintCompute -> {
                        inputMask = inputMask or FrameLayout.MASK_INPUT_TEXT
                        val target = transform.targetVar
                        checkReadOnlyWrite(target, transformPath, stage.id, diagnostics)
                        registerVariable(
                            name = target,
                            type = DataType.STRING,
                            stageId = stage.id,
                            transformPath = transformPath,
                            symbolTable = symbolTable,
                            alwaysDefinedVars = alwaysDefinedVars,
                            conditionallyDefinedVars = conditionallyDefinedVars,
                            isConditional = isConditional,
                            diagnostics = diagnostics,
                            allocSlot = { nextSlotIndex++ }
                        )
                    }
                    is TransformDefinition.AmountParse -> {
                        val src = transform.sourceVar
                        readVars.add(src)
                        validateReadVar(
                            varName = src,
                            expectedType = DataType.STRING,
                            path = "$transformPath['sourceVar']",
                            stageId = stage.id,
                            symbolTable = symbolTable,
                            systemInputs = systemInputs,
                            alwaysDefined = alwaysDefinedVars,
                            condDefined = conditionallyDefinedVars,
                            diagnostics = diagnostics
                        )

                        val target = transform.targetVar
                        checkReadOnlyWrite(target, transformPath, stage.id, diagnostics)
                        registerVariable(
                            name = target,
                            type = DataType.LONG,
                            stageId = stage.id,
                            transformPath = transformPath,
                            symbolTable = symbolTable,
                            alwaysDefinedVars = alwaysDefinedVars,
                            conditionallyDefinedVars = conditionallyDefinedVars,
                            isConditional = isConditional,
                            diagnostics = diagnostics,
                            allocSlot = { nextSlotIndex++ }
                        )
                    }
                    is TransformDefinition.CurrencyResolve -> {
                        val src = transform.sourceVar
                        readVars.add(src)
                        validateReadVar(
                            varName = src,
                            expectedType = DataType.STRING,
                            path = "$transformPath['sourceVar']",
                            stageId = stage.id,
                            symbolTable = symbolTable,
                            systemInputs = systemInputs,
                            alwaysDefined = alwaysDefinedVars,
                            condDefined = conditionallyDefinedVars,
                            diagnostics = diagnostics
                        )

                        val target = transform.targetVar
                        checkReadOnlyWrite(target, transformPath, stage.id, diagnostics)
                        registerVariable(
                            name = target,
                            type = DataType.STRING,
                            stageId = stage.id,
                            transformPath = transformPath,
                            symbolTable = symbolTable,
                            alwaysDefinedVars = alwaysDefinedVars,
                            conditionallyDefinedVars = conditionallyDefinedVars,
                            isConditional = isConditional,
                            diagnostics = diagnostics,
                            allocSlot = { nextSlotIndex++ }
                        )
                    }
                    is TransformDefinition.FinanceExtract -> {
                        inputMask = inputMask or FrameLayout.MASK_INPUT_TEXT or FrameLayout.MASK_INPUT_PACKAGE or FrameLayout.MASK_INPUT_SENDER
                        val target = transform.targetVar
                        checkReadOnlyWrite(target, transformPath, stage.id, diagnostics)
                        registerVariable(
                            name = target,
                            type = DataType.OBJECT_TRANSACTION,
                            stageId = stage.id,
                            transformPath = transformPath,
                            symbolTable = symbolTable,
                            alwaysDefinedVars = alwaysDefinedVars,
                            conditionallyDefinedVars = conditionallyDefinedVars,
                            isConditional = isConditional,
                            diagnostics = diagnostics,
                            allocSlot = { nextSlotIndex++ }
                        )
                    }
                }
            }

            // Process actions in this stage
            stage.actions.forEachIndexed { aIndex, action ->
                val actionPath = "$stagePath['actions'][$aIndex]"
                when (action) {
                    is ActionDefinition.CreateTransaction -> {
                        val amtVar = action.amountVar
                        readVars.add(amtVar)
                        actionConsumedVars.add(amtVar)
                        validateReadVar(
                            varName = amtVar,
                            expectedType = DataType.LONG,
                            path = "$actionPath['amountVar']",
                            stageId = stage.id,
                            symbolTable = symbolTable,
                            systemInputs = systemInputs,
                            alwaysDefined = alwaysDefinedVars,
                            condDefined = conditionallyDefinedVars,
                            diagnostics = diagnostics
                        )

                        val curVar = action.currencyVar
                        readVars.add(curVar)
                        actionConsumedVars.add(curVar)
                        validateReadVar(
                            varName = curVar,
                            expectedType = DataType.STRING,
                            path = "$actionPath['currencyVar']",
                            stageId = stage.id,
                            symbolTable = symbolTable,
                            systemInputs = systemInputs,
                            alwaysDefined = alwaysDefinedVars,
                            condDefined = conditionallyDefinedVars,
                            diagnostics = diagnostics
                        )
                    }
                    is ActionDefinition.SetCategory -> {
                        if (action.confidence !in 0.0..1.0) {
                            diagnostics.add(
                                CompilationDiagnostic(
                                    code = "P3005",
                                    severity = DiagnosticSeverity.ERROR,
                                    message = "Node contract violation: confidence must be between 0.0 and 1.0, got ${action.confidence}",
                                    messageKey = "error.contract.violation",
                                    messageArgs = listOf(action.confidence.toString()),
                                    location = SourceLocation(jsonPath = "$actionPath['confidence']", target = Target.VALUE, stageId = stage.id)
                                )
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        // Liveness analysis / Dead stores & Unused variables warnings
        symbolTable.forEach { (name, sym) ->
            if (name !in readVars) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P3101",
                        severity = DiagnosticSeverity.WARNING,
                        message = "Value assigned to variable '$name' is never read (Dead Store)",
                        messageKey = "warning.variable.dead_store",
                        messageArgs = listOf(name),
                        location = SourceLocation(jsonPath = "$['stages']", target = Target.VALUE, stageId = sym.definedAtStageId)
                    )
                )
            } else if (name !in actionConsumedVars) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P3102",
                        severity = DiagnosticSeverity.WARNING,
                        message = "Variable '$name' is declared but never consumed in actions (Unused Variable)",
                        messageKey = "warning.variable.unused_in_actions",
                        messageArgs = listOf(name),
                        location = SourceLocation(jsonPath = "$['stages']", target = Target.VALUE, stageId = sym.definedAtStageId)
                    )
                )
            }
        }

        return DataflowAnalysisResult(
            diagnostics = diagnostics,
            symbolTable = symbolTable,
            requiredInputMask = inputMask
        )
    }

    private fun checkReadOnlyWrite(
        varName: String,
        path: String,
        stageId: String,
        diagnostics: MutableList<CompilationDiagnostic>
    ) {
        if (varName.startsWith("input.")) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P3003",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Cannot write to read-only input field '$varName'",
                    messageKey = "error.variable.readonly_write",
                    messageArgs = listOf(varName),
                    location = SourceLocation(jsonPath = path, target = Target.VALUE, stageId = stageId)
                )
            )
        }
    }

    private fun registerVariable(
        name: String,
        type: DataType,
        stageId: String,
        transformPath: String,
        symbolTable: MutableMap<String, VariableSymbol>,
        alwaysDefinedVars: MutableSet<String>,
        conditionallyDefinedVars: MutableSet<String>,
        isConditional: Boolean,
        diagnostics: MutableList<CompilationDiagnostic>,
        allocSlot: () -> Int
    ) {
        val existing = symbolTable[name]
        if (existing != null) {
            if (existing.type != type && existing.type != DataType.POISON) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P3004",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Type mismatch for variable '$name'. Expected ${existing.type}, but got $type",
                        messageKey = "error.variable.type_mismatch",
                        messageArgs = listOf(name, existing.type.name, type.name),
                        location = SourceLocation(jsonPath = transformPath, target = Target.VALUE, stageId = stageId)
                    )
                )
            }
        } else {
            val slot = allocSlot()
            symbolTable[name] = VariableSymbol(name = name, type = type, definedAtStageId = stageId, slotIndex = slot)
        }

        if (isConditional) {
            conditionallyDefinedVars.add(name)
        } else {
            alwaysDefinedVars.add(name)
        }
    }

    private fun validateReadVar(
        varName: String,
        expectedType: DataType,
        path: String,
        stageId: String,
        symbolTable: Map<String, VariableSymbol>,
        systemInputs: Map<String, Pair<DataType, Long>>,
        alwaysDefined: Set<String>,
        condDefined: Set<String>,
        diagnostics: MutableList<CompilationDiagnostic>
    ) {
        if (varName in systemInputs) {
            val (sysType, _) = systemInputs[varName]!!
            if (sysType != expectedType) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P3004",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Type mismatch for variable '$varName'. Expected $expectedType, but got $sysType",
                        messageKey = "error.variable.type_mismatch",
                        messageArgs = listOf(varName, expectedType.name, sysType.name),
                        location = SourceLocation(jsonPath = path, target = Target.VALUE, stageId = stageId)
                    )
                )
            }
            return
        }

        if (varName !in alwaysDefined && varName !in condDefined) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P3001",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Variable '$varName' is used before being defined",
                    messageKey = "error.variable.use_before_def",
                    messageArgs = listOf(varName),
                    location = SourceLocation(jsonPath = path, target = Target.VALUE, stageId = stageId)
                )
            )
            return
        }

        if (varName in condDefined && varName !in alwaysDefined) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P3002",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Variable '$varName' is not defined on all execution paths reaching this stage",
                    messageKey = "error.variable.not_defined_on_all_paths",
                    messageArgs = listOf(varName),
                    location = SourceLocation(jsonPath = path, target = Target.VALUE, stageId = stageId)
                )
            )
            return
        }

        val sym = symbolTable[varName]
        if (sym != null && sym.type != expectedType && sym.type != DataType.POISON) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P3004",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Type mismatch for variable '$varName'. Expected $expectedType, but got ${sym.type}",
                    messageKey = "error.variable.type_mismatch",
                    messageArgs = listOf(varName, expectedType.name, sym.type.name),
                    location = SourceLocation(jsonPath = path, target = Target.VALUE, stageId = stageId)
                )
            )
        }
    }

    private fun computeConditionInputMask(condition: ConditionDefinition): Long {
        return when (condition) {
            is ConditionDefinition.PackageMatch -> FrameLayout.MASK_INPUT_PACKAGE
            is ConditionDefinition.SenderMatch -> FrameLayout.MASK_INPUT_SENDER
            is ConditionDefinition.TextRegexMatch -> when (condition.targetField) {
                TextFieldTarget.TITLE -> FrameLayout.MASK_INPUT_TITLE
                TextFieldTarget.TEXT -> FrameLayout.MASK_INPUT_TEXT
                TextFieldTarget.TITLE_OR_TEXT -> FrameLayout.MASK_INPUT_TITLE or FrameLayout.MASK_INPUT_TEXT
                TextFieldTarget.SENDER -> FrameLayout.MASK_INPUT_SENDER
                TextFieldTarget.PACKAGE_NAME -> FrameLayout.MASK_INPUT_PACKAGE
            }
            is ConditionDefinition.LogicalAnd -> condition.conditions.fold(0L) { acc, c -> acc or computeConditionInputMask(c) }
            is ConditionDefinition.LogicalOr -> condition.conditions.fold(0L) { acc, c -> acc or computeConditionInputMask(c) }
            is ConditionDefinition.LogicalNot -> computeConditionInputMask(condition.condition)
            else -> 0L
        }
    }
}
