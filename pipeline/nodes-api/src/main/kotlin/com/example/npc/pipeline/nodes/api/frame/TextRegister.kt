package com.example.npc.pipeline.nodes.api.frame

/**
 * Мутабельный строковый регистр фиксированной емкости.
 * Реализует [CharSequence] для передачи в RE2/J Matcher.reset() без аллокаций памяти.
 */
class TextRegister(val capacity: Int = 1024) : CharSequence {

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    @JvmField val chars: CharArray = CharArray(capacity)
    @JvmField var len: Int = 0
    @JvmField var isTruncated: Boolean = false

    override val length: Int get() = len

    override fun get(index: Int): Char {
        if (index < 0 || index >= len) throw IndexOutOfBoundsException("Index: $index, Length: $len")
        return chars[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        throw UnsupportedOperationException("subSequence allocates memory; inspect chars in-place")
    }

    fun set(source: CharSequence) {
        val srcLen = source.length
        val copyLen = if (srcLen > capacity) {
            isTruncated = true
            capacity
        } else {
            isTruncated = false
            srcLen
        }
        for (i in 0 until copyLen) {
            chars[i] = source[i]
        }
        len = copyLen
    }

    fun clear() {
        len = 0
        isTruncated = false
    }

    fun copyTo(destination: TextRegister) {
        val copyLen = minOf(this.len, destination.capacity)
        System.arraycopy(this.chars, 0, destination.chars, 0, copyLen)
        destination.len = copyLen
        destination.isTruncated = this.isTruncated || (this.len > destination.capacity)
    }

    fun materializeString(): String = String(chars, 0, len)
}
