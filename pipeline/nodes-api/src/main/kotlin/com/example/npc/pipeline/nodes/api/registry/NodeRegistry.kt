package com.example.npc.pipeline.nodes.api.registry

import com.example.npc.pipeline.nodes.api.spec.NodeSpec
import java.util.concurrent.ConcurrentHashMap

/**
 * Потокобезопасный каталог зарегистрированных спецификаций узлов конвейера.
 */
interface NodeRegistry {
    fun register(spec: NodeSpec)
    fun find(id: String): NodeSpec?
    fun getAll(): Collection<NodeSpec>

    companion object {
        fun create(): NodeRegistry = NodeRegistryImpl()
    }
}

/**
 * Стандартная потокобезопасная реализация каталога на ConcurrentHashMap.
 */
class NodeRegistryImpl : NodeRegistry {
    private val specs = ConcurrentHashMap<String, NodeSpec>()

    override fun register(spec: NodeSpec) {
        require(spec.id.isNotBlank()) { "NodeSpec id cannot be blank" }
        val prev = specs.putIfAbsent(spec.id, spec)
        require(prev == null) { "Node with id '${spec.id}' is already registered" }
    }

    override fun find(id: String): NodeSpec? = specs[id]

    override fun getAll(): Collection<NodeSpec> = java.util.Collections.unmodifiableCollection(specs.values)
}
