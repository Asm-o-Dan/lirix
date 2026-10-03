package com.example.npc.core.text

/**
 * Диапазон символов в тексте [start, end).
 */
data class TextSpan(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end >= start) { "Invalid span: [$start, $end)" }
    }
    val length: Int get() = end - start
}

/**
 * Двунаправленная проекция позиций символов между оригиналом и нормализованной строкой.
 */
interface OffsetMap {
    fun toOriginal(normalizedOffset: Int): Int
    fun toNormalized(originalOffset: Int): Int
    fun toOriginalSpan(normalizedStart: Int, normalizedEnd: Int): TextSpan
}

/**
 * Высокопроизводительная двунаправленная карта смещений на основе целочисленных массивов.
 */
class ArrayOffsetMap(
    private val originalLength: Int,
    private val normalizedLength: Int,
    private val charOrigStart: IntArray,
    private val charOrigEnd: IntArray,
    private val origToNorm: IntArray
) : OffsetMap {

    override fun toOriginal(normalizedOffset: Int): Int {
        if (normalizedLength == 0) return 0
        if (normalizedOffset <= 0) return 0
        if (normalizedOffset >= normalizedLength) return originalLength
        return charOrigStart[normalizedOffset]
    }

    override fun toNormalized(originalOffset: Int): Int {
        if (originalLength == 0) return 0
        if (originalOffset <= 0) return 0
        if (originalOffset >= originalLength) return normalizedLength
        return origToNorm[originalOffset]
    }

    override fun toOriginalSpan(normalizedStart: Int, normalizedEnd: Int): TextSpan {
        require(normalizedStart >= 0 && normalizedEnd >= normalizedStart) {
            "Invalid span bounds: start=$normalizedStart, end=$normalizedEnd"
        }
        if (normalizedLength == 0) {
            return TextSpan(0, 0)
        }

        val clampedStart = normalizedStart.coerceIn(0, normalizedLength)
        val clampedEnd = normalizedEnd.coerceIn(0, normalizedLength)

        if (clampedStart == clampedEnd) {
            val pos = if (clampedStart < normalizedLength) {
                charOrigStart[clampedStart]
            } else {
                originalLength
            }
            return TextSpan(pos, pos)
        }

        val origStart = charOrigStart[clampedStart]
        val origEnd = charOrigEnd[clampedEnd - 1]

        val finalStart = origStart.coerceIn(0, originalLength)
        val finalEnd = maxOf(finalStart, origEnd.coerceIn(0, originalLength))

        return TextSpan(finalStart, finalEnd)
    }
}
