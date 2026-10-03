package com.example.npc.pipeline.nodes.api.effect

import com.example.npc.pipeline.nodes.api.frame.TextRegister

object EffectKindId {
    const val SET_CATEGORY: Int = 1
    const val CREATE_FINANCIAL_TRANSACTION: Int = 2
    const val SAVE_TO_STORAGE: Int = 3
    const val DROP_EVENT: Int = 4
    const val STOP_PROCESSING: Int = 5
}

/**
 * Высокопроизводительный Struct-of-Arrays (SoA) буфер отложенных эффектов на плоских массивах.
 * Поддерживает транзакционность через [mark] и [rollback] для изоляции сбоев узлов.
 */
class EffectBuffer(
    val maxEffects: Int = 32,
    val maxArgs: Int = 64,
    val maxChars: Int = 2048
) {
    @JvmField internal val kindCodes: IntArray = IntArray(maxEffects)
    @JvmField internal val originPcs: IntArray = IntArray(maxEffects)
    @JvmField internal val argStarts: IntArray = IntArray(maxEffects + 1)
    @JvmField internal val longArgs: LongArray = LongArray(maxArgs)
    @JvmField internal val refArgs: Array<Any?> = arrayOfNulls(maxArgs)
    @JvmField internal val charBuffer: CharArray = CharArray(maxChars)

    @JvmField var effectCount: Int = 0
    @JvmField var argCount: Int = 0
    @JvmField var charCount: Int = 0

    @JvmField var currentPc: Int = 0
    @JvmField var allowMask: Long = -1L

    init {
        argStarts[0] = 0
    }

    fun begin(kindCode: Int): Boolean {
        if ((allowMask and (1L shl kindCode)) == 0L) return false
        if (effectCount >= maxEffects) return false
        kindCodes[effectCount] = kindCode
        originPcs[effectCount] = currentPc
        argStarts[effectCount] = argCount
        return true
    }

    fun putLong(value: Long): Boolean {
        if (argCount >= maxArgs) return false
        longArgs[argCount++] = value
        return true
    }

    fun putRef(value: Any?): Boolean {
        if (argCount >= maxArgs) return false
        refArgs[argCount++] = value
        return true
    }

    fun putText(text: TextRegister): Boolean {
        val len = text.len
        if (argCount >= maxArgs || charCount + len > maxChars) return false
        System.arraycopy(text.chars, 0, charBuffer, charCount, len)
        val packed = (charCount.toLong() shl 32) or (len.toLong() and 0xFFFF_FFFFL)
        longArgs[argCount++] = packed
        charCount += len
        return true
    }

    fun end() {
        if (effectCount < maxEffects) {
            effectCount++
            argStarts[effectCount] = argCount
        }
    }

    fun mark(): Int = effectCount

    fun rollback(mark: Int) {
        if (mark < 0 || mark > effectCount) return
        val startArgToClear = argStarts[mark]
        for (i in startArgToClear until argCount) {
            refArgs[i] = null
        }
        argCount = startArgToClear
        effectCount = mark
    }

    fun reset() {
        for (i in 0 until argCount) {
            refArgs[i] = null
        }
        effectCount = 0
        argCount = 0
        charCount = 0
        argStarts[0] = 0
        currentPc = 0
        allowMask = -1L
    }

    fun view(): EffectView = EffectView(this)
}

class EffectView internal constructor(private val buf: EffectBuffer) {
    private var cursor: Int = -1

    val size: Int get() = buf.effectCount

    fun hasNext(): Boolean = cursor + 1 < buf.effectCount

    fun next(): Boolean {
        if (hasNext()) {
            cursor++
            return true
        }
        return false
    }

    val kindId: Int
        get() {
            check(cursor in 0 until buf.effectCount)
            return buf.kindCodes[cursor]
        }

    val originPc: Int
        get() {
            check(cursor in 0 until buf.effectCount)
            return buf.originPcs[cursor]
        }

    fun getKind(index: Int): Int {
        checkIndex(index)
        return buf.kindCodes[index]
    }

    fun getOriginPc(index: Int): Int {
        checkIndex(index)
        return buf.originPcs[index]
    }

    fun getLongArg(effectIndex: Int, argOffset: Int): Long {
        checkIndex(effectIndex)
        val argIdx = buf.argStarts[effectIndex] + argOffset
        if (argIdx >= buf.argStarts[effectIndex + 1]) throw IndexOutOfBoundsException("Arg offset $argOffset out of bounds")
        return buf.longArgs[argIdx]
    }

    fun getLongArg(argOffset: Int): Long {
        check(cursor in 0 until buf.effectCount)
        return getLongArg(cursor, argOffset)
    }

    fun getIntArg(argOffset: Int): Int {
        check(cursor in 0 until buf.effectCount)
        return getLongArg(cursor, argOffset).toInt()
    }

    fun getDoubleArg(argOffset: Int): Double {
        check(cursor in 0 until buf.effectCount)
        return Double.fromBits(getLongArg(cursor, argOffset))
    }

    fun getRefArg(effectIndex: Int, argOffset: Int): Any? {
        checkIndex(effectIndex)
        val argIdx = buf.argStarts[effectIndex] + argOffset
        if (argIdx >= buf.argStarts[effectIndex + 1]) throw IndexOutOfBoundsException("Arg offset $argOffset out of bounds")
        return buf.refArgs[argIdx]
    }

    fun getRefArg(argOffset: Int): Any? {
        check(cursor in 0 until buf.effectCount)
        return getRefArg(cursor, argOffset)
    }

    fun getTextArg(effectIndex: Int, argOffset: Int): String {
        checkIndex(effectIndex)
        val packed = getLongArg(effectIndex, argOffset)
        val offset = (packed ushr 32).toInt()
        val len = packed.toInt()
        return String(buf.charBuffer, offset, len)
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= buf.effectCount) {
            throw IndexOutOfBoundsException("Index: $index, Size: ${buf.effectCount}")
        }
    }
}
