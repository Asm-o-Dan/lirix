package com.example.npc.pipeline.nodes.api.effect

import com.example.npc.pipeline.nodes.api.frame.TextRegister
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EffectBufferTest {

    @Test
    fun `basic begin put and view effects`() {
        val buffer = EffectBuffer(maxEffects = 8, maxArgs = 16, maxChars = 256)
        buffer.currentPc = 12

        assertTrue(buffer.begin(EffectKindId.SET_CATEGORY))
        assertTrue(buffer.putLong(1L)) // category ordinal
        assertTrue(buffer.putLong(Double.fromBits(0x3FE0000000000000L).toBits())) // confidence
        buffer.end()

        assertEquals(1, buffer.effectCount)
        val view = buffer.view()
        assertEquals(1, view.size)
        assertEquals(EffectKindId.SET_CATEGORY, view.getKind(0))
        assertEquals(12, view.getOriginPc(0))
        assertEquals(1L, view.getLongArg(0, 0))
    }

    @Test
    fun `text arg and ref arg support`() {
        val buffer = EffectBuffer(maxEffects = 4, maxArgs = 8, maxChars = 128)
        val textReg = TextRegister(32)
        textReg.set("Sample text")

        assertTrue(buffer.begin(EffectKindId.SAVE_TO_STORAGE))
        assertTrue(buffer.putText(textReg))
        val sampleObj = "RefObject"
        assertTrue(buffer.putRef(sampleObj))
        buffer.end()

        val view = buffer.view()
        assertEquals("Sample text", view.getTextArg(0, 0))
        assertSame(sampleObj, view.getRefArg(0, 1))
    }

    @Test
    fun `allowMask filters disallowed effects`() {
        val buffer = EffectBuffer()
        // Only allow SET_CATEGORY (bit 1)
        buffer.allowMask = 1L shl EffectKindId.SET_CATEGORY

        assertTrue(buffer.begin(EffectKindId.SET_CATEGORY))
        buffer.end()

        // Disallowed DROP_EVENT (bit 4)
        assertFalse(buffer.begin(EffectKindId.DROP_EVENT))
    }

    @Test
    fun `mark and rollback clears refs and resets counts without leak`() {
        val buffer = EffectBuffer(maxEffects = 10, maxArgs = 20, maxChars = 256)

        // Effect 1
        buffer.begin(1)
        buffer.putLong(100L)
        buffer.putRef("Ref1")
        buffer.end()

        val mark1 = buffer.mark()
        assertEquals(1, mark1)

        // Effect 2
        buffer.begin(2)
        buffer.putRef("Ref2")
        buffer.putLong(200L)
        buffer.end()

        assertEquals(2, buffer.effectCount)
        assertEquals(4, buffer.argCount)
        assertNotNull(buffer.refArgs[2]) // Ref2

        // Rollback to mark1
        buffer.rollback(mark1)
        assertEquals(1, buffer.effectCount)
        assertEquals(2, buffer.argCount)
        assertNull(buffer.refArgs[2]) // Ref2 cleared (no memory leak)
    }

    @Test
    fun `reset clears all state`() {
        val buffer = EffectBuffer()
        buffer.begin(1)
        buffer.putRef("Ref")
        buffer.putLong(42L)
        buffer.end()

        buffer.reset()
        assertEquals(0, buffer.effectCount)
        assertEquals(0, buffer.argCount)
        assertEquals(0, buffer.charCount)
        assertNull(buffer.refArgs[0])
    }
}
