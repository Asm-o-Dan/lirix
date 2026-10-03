package com.example.npc.core.model

import com.example.npc.core.model.classify.Engine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EngineTest {

    @Test
    fun `enum contains expected engines`() {
        val expected = setOf("NONE", "PROTOTYPE", "RULES", "USER")
        val actual = Engine.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `fromStringOrDefault maps strings case-insensitively`() {
        assertEquals(Engine.NONE, Engine.fromStringOrDefault("NONE"))
        assertEquals(Engine.NONE, Engine.fromStringOrDefault("none"))
        assertEquals(Engine.PROTOTYPE, Engine.fromStringOrDefault("PROTOTYPE"))
        assertEquals(Engine.PROTOTYPE, Engine.fromStringOrDefault("prototype"))
        assertEquals(Engine.PROTOTYPE, Engine.fromStringOrDefault("  Prototype  "))
        assertEquals(Engine.RULES, Engine.fromStringOrDefault("RULES"))
        assertEquals(Engine.RULES, Engine.fromStringOrDefault("rules"))
        assertEquals(Engine.USER, Engine.fromStringOrDefault("USER"))
        assertEquals(Engine.USER, Engine.fromStringOrDefault("user"))
    }

    @Test
    fun `fromStringOrDefault returns default on null blank or unrecognized strings`() {
        assertEquals(Engine.NONE, Engine.fromStringOrDefault(null))
        assertEquals(Engine.NONE, Engine.fromStringOrDefault(""))
        assertEquals(Engine.NONE, Engine.fromStringOrDefault("   "))
        assertEquals(Engine.NONE, Engine.fromStringOrDefault("unknown"))

        assertEquals(Engine.RULES, Engine.fromStringOrDefault(null, default = Engine.RULES))
        assertEquals(Engine.RULES, Engine.fromStringOrDefault("garbage", default = Engine.RULES))
    }
}
