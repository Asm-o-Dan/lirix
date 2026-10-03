package com.example.npc.pipeline.runtime.diagnostics

import com.example.npc.pipeline.runtime.trace.TraceRecord
import com.example.npc.pipeline.runtime.trace.TraceRing

class SeqlockTraceReader(private val ring: TraceRing) {

    fun readSnapshot(maxRetries: Int = 100): List<TraceRecord> {
        val currentHead = ring.getHead()
        val capacity = ring.capacity
        val count = minOf(currentHead, capacity.toLong()).toInt()
        if (count == 0) return emptyList()

        val result = ArrayList<TraceRecord>(count)
        val start = currentHead - count
        val mask = (capacity - 1).toLong()

        for (i in 0 until count) {
            val slot = ((start + i) and mask).toInt()
            val record = readSlotSeqlock(slot, maxRetries)
            if (record != null) {
                result.add(record)
            }
        }
        return result
    }

    fun readSlotSeqlock(slot: Int, maxRetries: Int = 100): TraceRecord? {
        var retries = 0
        while (retries < maxRetries) {
            val s1 = ring.getSlotSeq(slot)
            if ((s1 and 1L) != 0L) {
                retries++
                Thread.onSpinWait()
                continue
            }

            val idx = slot shl 1
            val ts = ring.getBufferValue(idx)
            val packed = ring.getBufferValue(idx + 1)

            val s2 = ring.getSlotSeq(slot)
            if (s1 == s2 && ((s1 and 1L) == 0L)) {
                val type = ((packed ushr 56) and 0xFFL).toInt()
                val stageId = ((packed ushr 44) and 0xFFFL).toInt()
                val revision = ((packed ushr 32) and 0xFFFL).toInt()
                val arg = (packed and 0xFFFF_FFFFL).toInt()
                return TraceRecord(ts, type, stageId, revision, arg)
            }
            retries++
            Thread.onSpinWait()
        }

        val idx = slot shl 1
        val ts = ring.getBufferValue(idx)
        val packed = ring.getBufferValue(idx + 1)
        val type = ((packed ushr 56) and 0xFFL).toInt()
        val stageId = ((packed ushr 44) and 0xFFFL).toInt()
        val revision = ((packed ushr 32) and 0xFFFL).toInt()
        val arg = (packed and 0xFFFF_FFFFL).toInt()
        return TraceRecord(ts, type, stageId, revision, arg)
    }
}
