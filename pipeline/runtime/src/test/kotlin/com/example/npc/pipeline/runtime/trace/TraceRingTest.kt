package com.example.npc.pipeline.runtime.trace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TraceRingTest {

    @Test
    fun constructor_validatesPowerOfTwo() {
        TraceRing(128)
        TraceRing(16)
        TraceRing(2)

        assertThrows(IllegalArgumentException::class.java) {
            TraceRing(100) // Not power of two
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceRing(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceRing(-8)
        }
    }

    @Test
    fun recordAndDecode_preservesExactFields() {
        val ring = TraceRing(capacity = 8)

        ring.record(
            type = TraceRing.TYPE_STAGE_PASS,
            stageId = 42,
            revision = 3,
            arg = 100500,
            timestampNanos = 123456789L
        )

        val records = ring.getDecodedSnapshot()
        assertEquals(1, records.size)
        val rec = records[0]
        assertEquals(123456789L, rec.timestampNanos)
        assertEquals(TraceRing.TYPE_STAGE_PASS, rec.type)
        assertEquals(42, rec.stageId)
        assertEquals(3, rec.revision)
        assertEquals(100500, rec.arg)
    }

    @Test
    fun overflow_evictsOldestAndMaintainsFifoOrder() {
        val ring = TraceRing(capacity = 4)

        // Write 6 records (overflowing 4)
        for (i in 1..6) {
            ring.record(
                type = TraceRing.TYPE_EVENT_START,
                stageId = i,
                revision = 1,
                arg = i * 10,
                timestampNanos = i * 1000L
            )
        }

        val snapshot = ring.getDecodedSnapshot()
        assertEquals(4, snapshot.size) // Capacity is 4

        // The oldest 2 (i=1, 2) were overwritten; remaining are 3, 4, 5, 6
        assertEquals(3, snapshot[0].stageId)
        assertEquals(4, snapshot[1].stageId)
        assertEquals(5, snapshot[2].stageId)
        assertEquals(6, snapshot[3].stageId)
    }

    @Test
    fun dumpSnapshot_copiesArray() {
        val ring = TraceRing(capacity = 4)
        ring.record(1, 1, 1, 1, 100L)

        val dest = LongArray(8)
        val head = ring.dumpSnapshot(dest)
        assertEquals(1L, head)
        assertEquals(100L, dest[0])
    }
}
