package com.example.npc.pipeline.nodes.api.frame

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer

/**
 * Рабочий контекст потока исполнения.
 * Поля представляют собой открытые (@JvmField) массивы для мономорфного доступа JIT.
 */
class Frame(val layout: FrameLayout) {
    @JvmField val longs: LongArray = LongArray(layout.longSlots)
    @JvmField val doubles: DoubleArray = DoubleArray(layout.doubleSlots)
    @JvmField val refs: Array<Any?> = arrayOfNulls(layout.refSlots)
    @JvmField val texts: Array<TextRegister> = Array(layout.textSlots) { TextRegister(layout.textCapacity) }

    @JvmField val effects: EffectBuffer = EffectBuffer(maxEffects = 32, maxArgs = 64, maxChars = 2048)
    @JvmField var stepBudget: Int = 1000

    fun resetRefs() {
        java.util.Arrays.fill(refs, null)
        for (i in texts.indices) {
            texts[i].clear()
        }
        effects.reset()
        stepBudget = 1000
    }
}
