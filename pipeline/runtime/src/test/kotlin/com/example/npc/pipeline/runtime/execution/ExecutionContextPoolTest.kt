package com.example.npc.pipeline.runtime.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExecutionContextPoolTest {

    @Test
    fun acquire_marksContextInUse() {
        val pool = ExecutionContextPool(capacity = 2)
        val ctx = pool.acquire()

        assertTrue(ctx.inUse)
        assertEquals(0, pool.overflowAllocationsCount)
        pool.release(ctx)
    }

    @Test
    fun release_clearsStateAndResetsInUse() {
        val pool = ExecutionContextPool(capacity = 2)
        val ctx = pool.acquire()

        ctx.frame.refs[0] = "SampleRef"
        ctx.frame.longs[0] = 9999L
        ctx.effectBuffer.begin(1)
        ctx.effectBuffer.putLong(42L)
        ctx.effectBuffer.end()
        ctx.frame.texts[0].set("SampleText")

        pool.release(ctx)
        assertFalse(ctx.inUse)
        assertNull(ctx.frame.refs[0])
        assertEquals(0L, ctx.frame.longs[0])
        assertEquals(0, ctx.effectBuffer.effectCount)
        assertEquals("", ctx.frame.texts[0].materializeString())
    }

    @Test
    fun doubleRelease_throwsIllegalStateException() {
        val pool = ExecutionContextPool(capacity = 2)
        val ctx = pool.acquire()
        pool.release(ctx)

        assertThrows(IllegalStateException::class.java) {
            pool.release(ctx)
        }
    }

    @Test
    fun overflow_createsExtraContextsAndTracksMetric() {
        val pool = ExecutionContextPool(capacity = 2)
        val c1 = pool.acquire()
        val c2 = pool.acquire()
        assertEquals(0, pool.overflowAllocationsCount)

        val c3 = pool.acquire() // Overflow
        assertEquals(1, pool.overflowAllocationsCount)
        assertTrue(c3.inUse)

        pool.release(c1)
        pool.release(c2)
        pool.release(c3)
    }
}
