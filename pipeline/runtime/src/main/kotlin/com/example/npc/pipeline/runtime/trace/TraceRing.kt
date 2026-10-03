package com.example.npc.pipeline.runtime.trace

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

data class TraceRecord(
    val timestampNanos: Long,
    val type: Int,
    val stageId: Int,
    val revision: Int,
    val arg: Int
)

class TraceRing(val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0 && (capacity and (capacity - 1)) == 0) {
            "Capacity must be a positive power of two, got $capacity"
        }
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 128

        const val TYPE_EVENT_START: Int = 1
        const val TYPE_STAGE_PASS: Int = 2
        const val TYPE_STAGE_FAIL: Int = 3
        const val TYPE_STAGE_BYPASS: Int = 4
        const val TYPE_EVENT_COMMIT: Int = 5
        const val TYPE_EVENT_DROP: Int = 6
        const val TYPE_BREAKER_TRIP: Int = 7
    }

    private val buffer = LongArray(capacity * 2)
    private val mask = (capacity - 1).toLong()
    private val head = AtomicLong(0L)
    private val slotSeq = AtomicLongArray(capacity)
    private val writeSeq = AtomicLong(0L)

    fun record(type: Int, stageId: Int, revision: Int, arg: Int, timestampNanos: Long) {
        val packed = (type.toLong() shl 56) or
                ((stageId.toLong() and 0xFFFL) shl 44) or
                ((revision.toLong() and 0xFFFL) shl 32) or
                (arg.toLong() and 0xFFFF_FFFFL)

        val currentHead = head.getAndIncrement()
        val slot = (currentHead and mask).toInt()
        val idx = slot shl 1

        slotSeq.incrementAndGet(slot)
        writeSeq.incrementAndGet()

        buffer[idx] = timestampNanos
        buffer[idx + 1] = packed

        slotSeq.incrementAndGet(slot)
        writeSeq.incrementAndGet()
    }

    fun getSlotSeq(slot: Int): Long = slotSeq.get(slot)

    fun getWriteSeq(): Long = writeSeq.get()

    fun getHead(): Long = head.get()

    fun getBufferValue(index: Int): Long = buffer[index]

    fun dumpSnapshot(destination: LongArray): Long {
        require(destination.size >= capacity * 2) {
            "Destination size ${destination.size} must be at least ${capacity * 2}"
        }
        System.arraycopy(buffer, 0, destination, 0, buffer.size)
        return head.get()
    }

    fun getDecodedSnapshot(): List<TraceRecord> {
        val currentHead = head.get()
        val count = minOf(currentHead, capacity.toLong()).toInt()
        val result = ArrayList<TraceRecord>(count)

        val start = currentHead - count
        for (i in 0 until count) {
            val slot = ((start + i) and mask).toInt()
            val idx = slot shl 1
            val ts = buffer[idx]
            val packed = buffer[idx + 1]

            val type = (packed ushr 56).toInt() and 0xFF
            val stageId = ((packed ushr 44) and 0xFFFL).toInt()
            val revision = ((packed ushr 32) and 0xFFFL).toInt()
            val arg = packed.toInt()

            result.add(TraceRecord(ts, type, stageId, revision, arg))
        }

        return result
    }
}
