package com.example.npc.pipeline.runtime.execution

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout

class ExecutionContext(
    val maxRefSlots: Int = 128,
    val maxPrimSlots: Int = 64,
    val maxMatcherSlots: Int = 32,
    val maxTextSlots: Int = 8,
    val maxTextCapacity: Int = 2048,
    val effectCapacity: Int = 32
) {
    val layout: FrameLayout = FrameLayout(
        longSlots = maxPrimSlots,
        doubleSlots = 16,
        refSlots = maxRefSlots,
        textSlots = maxTextSlots,
        textCapacity = maxTextCapacity
    )

    @JvmField val frame: Frame = Frame(layout)

    @JvmField val effectBuffer: EffectBuffer = EffectBuffer(
        maxEffects = effectCapacity,
        maxArgs = 64,
        maxChars = 2048
    )

    @JvmField var inUse: Boolean = false

    fun reset() {
        frame.resetRefs()
        frame.longs.fill(0L)
        frame.doubles.fill(0.0)
        effectBuffer.reset()
        for (i in frame.texts.indices) {
            frame.texts[i].clear()
        }
        inUse = false
    }
}

class ExecutionContextPool(
    val capacity: Int = 4
) {
    init {
        require(capacity > 0) { "Capacity must be positive: $capacity" }
    }

    private val items: Array<ExecutionContext> = Array(capacity) { ExecutionContext() }
    private var top: Int = capacity

    var overflowAllocationsCount: Int = 0
        private set

    fun acquire(): ExecutionContext {
        return if (top > 0) {
            val ctx = items[--top]
            ctx.inUse = true
            ctx
        } else {
            overflowAllocationsCount++
            val ctx = ExecutionContext()
            ctx.inUse = true
            ctx
        }
    }

    fun release(context: ExecutionContext) {
        check(context.inUse) { "Attempt to release context that is not marked inUse" }
        context.reset()
        if (top < capacity) {
            items[top++] = context
        }
    }
}
