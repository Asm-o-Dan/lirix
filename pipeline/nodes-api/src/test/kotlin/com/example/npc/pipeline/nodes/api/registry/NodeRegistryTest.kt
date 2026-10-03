package com.example.npc.pipeline.nodes.api.registry

import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.executor.StepResult
import com.example.npc.pipeline.nodes.api.spec.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NodeRegistryTest {

    private fun createDummySpec(nodeId: String): NodeSpec {
        return object : NodeSpec {
            override val id: String = nodeId
            override val category: NodeCategory = NodeCategory.CONDITION
            override val traits: NodeTraits = NodeTraits()
            override val paramSchema: ParamSchema = ParamSchema()
            override val portSchema: PortSchema = PortSchema()
            override fun bind(context: BindContext): NodeExecutor {
                return NodeExecutor { _, _ -> StepResult.NEXT }
            }
        }
    }

    @Test
    fun `register and find node spec`() {
        val registry = NodeRegistry.create()
        val spec = createDummySpec("test-condition")
        registry.register(spec)

        val found = registry.find("test-condition")
        assertNotNull(found)
        assertEquals("test-condition", found!!.id)
    }

    @Test
    fun `register duplicate id throws IllegalArgumentException`() {
        val registry = NodeRegistry.create()
        registry.register(createDummySpec("node-1"))

        val ex = assertThrows<IllegalArgumentException> {
            registry.register(createDummySpec("node-1"))
        }
        assertTrue(ex.message!!.contains("already registered"))
    }

    @Test
    fun `find non-existent returns null`() {
        val registry = NodeRegistry.create()
        assertNull(registry.find("unknown"))
    }

    @Test
    fun `getAll returns all registered specs`() {
        val registry = NodeRegistry.create()
        registry.register(createDummySpec("n1"))
        registry.register(createDummySpec("n2"))

        val all = registry.getAll()
        assertEquals(2, all.size)
        assertTrue(all.any { it.id == "n1" })
        assertTrue(all.any { it.id == "n2" })
    }
}
