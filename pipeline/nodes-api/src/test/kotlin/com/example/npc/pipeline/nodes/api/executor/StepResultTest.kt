package com.example.npc.pipeline.nodes.api.executor

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepResultTest {

    @Test
    fun `next constant properties`() {
        val next = StepResult.NEXT
        assertTrue(next.isNext)
        assertFalse(next.isJump)
        assertFalse(next.isHalt)
        assertFalse(next.isFail)
        assertEquals(StepResult.OP_NEXT, next.op)
        assertEquals(0, next.arg)
    }

    @Test
    fun `jump packing and unpacking`() {
        val jump0 = StepResult.jump(0)
        assertTrue(jump0.isJump)
        assertEquals(StepResult.OP_JUMP, jump0.op)
        assertEquals(0, jump0.arg)

        val jump42 = StepResult.jump(42)
        assertTrue(jump42.isJump)
        assertEquals(42, jump42.arg)

        assertThrows<IllegalArgumentException> {
            StepResult.jump(-1)
        }
    }

    @Test
    fun `halt packing and unpacking`() {
        val haltDefault = StepResult.halt()
        assertTrue(haltDefault.isHalt)
        assertEquals(StepResult.OP_HALT, haltDefault.op)
        assertEquals(0, haltDefault.arg)

        val haltReason = StepResult.halt(7)
        assertTrue(haltReason.isHalt)
        assertEquals(7, haltReason.arg)
    }

    @Test
    fun `fail packing and unpacking including negative errorCode`() {
        val fail1 = StepResult.fail(101)
        assertTrue(fail1.isFail)
        assertEquals(StepResult.OP_FAIL, fail1.op)
        assertEquals(101, fail1.arg)

        // Negative errorCode bitmask unpacking
        val failNeg = StepResult.fail(-5)
        assertTrue(failNeg.isFail)
        assertEquals(-5, failNeg.arg)
    }
}
