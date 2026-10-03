package com.example.npc.pipeline.nodes.api.spec

import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.frame.Bank

enum class NodeCategory {
    CONDITION,
    TRANSFORM,
    ACTION
}

data class NodeTraits(
    val isPure: Boolean = true,
    val isDeterministic: Boolean = true,
    val costScore: Int = 1
)

data class PortDef(
    val name: String,
    val bank: Bank,
    val isRequired: Boolean = true,
    val failureDefaultLong: Long = 0L,
    val failureDefaultDouble: Double = 0.0,
    val failureDefaultRef: Any? = null
)

class PortSchema(
    val inputs: List<PortDef> = emptyList(),
    val outputs: List<PortDef> = emptyList(),
    val branches: List<String> = emptyList()
)

data class ParamDef<T>(
    val name: String,
    val defaultValue: T? = null,
    val isRequired: Boolean = true
)

class ParamSchema(val params: List<ParamDef<*>> = emptyList()) {
    constructor(vararg defs: ParamDef<*>) : this(defs.toList())
}

interface BindContext {
    fun getParam(name: String): Any?
    fun getInputSlot(portName: String): Int
    fun getOutputSlot(portName: String): Int
    fun getBank(portName: String): Bank
    fun allocateScratchSlot(): Int
}

interface NodeSpec {
    val id: String
    val category: NodeCategory
    val traits: NodeTraits
    val paramSchema: ParamSchema
    val portSchema: PortSchema

    /**
     * Вызывается компилятором во время фазы Lowering.
     * Связывает логические порты и параметры со статическими слотами FrameLayout.
     */
    fun bind(context: BindContext): NodeExecutor
}
