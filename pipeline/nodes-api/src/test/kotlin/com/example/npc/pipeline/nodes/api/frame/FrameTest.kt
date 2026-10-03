package com.example.npc.pipeline.nodes.api.frame

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FrameTest {

    @Test
    fun `frame allocates arrays strictly according to layout`() {
        val layout = FrameLayout(
            longSlots = 8,
            doubleSlots = 4,
            refSlots = 6,
            textSlots = 3,
            textCapacity = 512
        )
        val frame = Frame(layout)

        assertEquals(8, frame.longs.size)
        assertEquals(4, frame.doubles.size)
        assertEquals(6, frame.refs.size)
        assertEquals(3, frame.texts.size)
        assertEquals(512, frame.texts[0].capacity)
        assertEquals(1000, frame.stepBudget)
    }

    @Test
    fun `resetRefs clears references, text registers and effects without touching primitives`() {
        val layout = FrameLayout(longSlots = 2, doubleSlots = 2, refSlots = 2, textSlots = 2)
        val frame = Frame(layout)

        frame.longs[0] = 42L
        frame.doubles[0] = 3.14
        frame.refs[0] = "ActiveRef"
        frame.texts[0].set("ActiveText")
        frame.effects.begin(1)
        frame.effects.putLong(99L)
        frame.effects.end()
        frame.stepBudget = 500

        frame.resetRefs()

        // Primitives are untouched (deterministic overwrite by compiler)
        assertEquals(42L, frame.longs[0])
        assertEquals(3.14, frame.doubles[0])

        // References and text are cleared
        assertNull(frame.refs[0])
        assertEquals(0, frame.texts[0].length)
        assertEquals(0, frame.effects.effectCount)
        assertEquals(1000, frame.stepBudget)
    }
}
