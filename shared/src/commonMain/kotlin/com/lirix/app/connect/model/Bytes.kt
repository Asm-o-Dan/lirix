package com.lirix.app.connect.model

import kotlinx.serialization.Serializable

/**
 * Сериализуемая обёртка над ByteArray с корректными equals/hashCode по содержимому.
 */
@Serializable
data class Bytes(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bytes) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()

    val size: Int get() = data.size

    fun toHex(): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(data.size * 2)
        for (b in data) {
            val octet = b.toInt() and 0xFF
            sb.append(hexChars[octet ushr 4])
            sb.append(hexChars[octet and 0x0F])
        }
        return sb.toString()
    }

    override fun toString(): String = "Bytes(${toHex()})"

    companion object {
        val EMPTY = Bytes(ByteArray(0))

        fun fromHex(hex: String): Bytes {
            val clean = hex.trim()
            require(clean.length % 2 == 0) { "Hex string length must be even" }
            val bytes = ByteArray(clean.length / 2)
            for (i in bytes.indices) {
                val hi = clean[i * 2].digitToInt(16)
                val lo = clean[i * 2 + 1].digitToInt(16)
                bytes[i] = ((hi shl 4) or lo).toByte()
            }
            return Bytes(bytes)
        }
    }
}

fun ByteArray.asBytes(): Bytes = Bytes(this)
