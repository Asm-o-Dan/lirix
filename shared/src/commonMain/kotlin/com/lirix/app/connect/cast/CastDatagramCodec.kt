package com.lirix.app.connect.cast

/**
 * Модель сетевой QUIC-датаграммы для Audio Cast (SPEC-CONNECT-01 §4.7, TASK-CON-CAST-09).
 *
 * @param seq 16-битный монотонный порядковый номер фрейма (0..65535)
 * @param timestamp48k 32-битная временная метка аудио (дискретизация 48 кГц)
 * @param flags 8-битные флаги (bit 0 = FEC present, bit 1 = DTX silence)
 * @param opusPayload wire payload: Opus bytes or complete PCM16 stereo frames,
 * according to the codec negotiated by SetAudioCast (total datagram <= 1200 bytes).
 */
data class CastDatagram(
    val seq: Int,
    val timestamp48k: Long,
    val flags: Int = 0,
    val opusPayload: ByteArray
) {
    val hasFec: Boolean get() = (flags and FLAG_FEC) != 0
    val isDtx: Boolean get() = (flags and FLAG_DTX) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CastDatagram) return false
        if (seq != other.seq) return false
        if (timestamp48k != other.timestamp48k) return false
        if (flags != other.flags) return false
        return opusPayload.contentEquals(other.opusPayload)
    }

    override fun hashCode(): Int {
        var result = seq
        result = 31 * result + timestamp48k.hashCode()
        result = 31 * result + flags
        result = 31 * result + opusPayload.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_SIZE = 7
        const val MAX_DATAGRAM_SIZE = 1200

        const val FLAG_FEC = 0x01
        const val FLAG_DTX = 0x02
    }
}

/**
 * Кодек QUIC-датаграмм Live Audio Cast.
 *
 * Формат заголовка (7 байт, Big Endian):
 * `seq (u16) | timestamp48k (u32) | flags (u8) | opusPayload...`
 */
object CastDatagramCodec {

    fun encode(datagram: CastDatagram): ByteArray {
        val totalSize = CastDatagram.HEADER_SIZE + datagram.opusPayload.size
        require(totalSize <= CastDatagram.MAX_DATAGRAM_SIZE) {
            "Datagram size $totalSize exceeds maximum allowed limit ${CastDatagram.MAX_DATAGRAM_SIZE} bytes"
        }

        val result = ByteArray(totalSize)

        // seq (u16)
        result[0] = ((datagram.seq ushr 8) and 0xFF).toByte()
        result[1] = (datagram.seq and 0xFF).toByte()

        // timestamp48k (u32)
        result[2] = ((datagram.timestamp48k ushr 24) and 0xFF).toByte()
        result[3] = ((datagram.timestamp48k ushr 16) and 0xFF).toByte()
        result[4] = ((datagram.timestamp48k ushr 8) and 0xFF).toByte()
        result[5] = (datagram.timestamp48k and 0xFF).toByte()

        // flags (u8)
        result[6] = (datagram.flags and 0xFF).toByte()

        // opusPayload
        datagram.opusPayload.copyInto(result, destinationOffset = CastDatagram.HEADER_SIZE)

        return result
    }

    fun decode(bytes: ByteArray): CastDatagram {
        require(bytes.size >= CastDatagram.HEADER_SIZE) {
            "Datagram size ${bytes.size} is less than header size ${CastDatagram.HEADER_SIZE}"
        }
        require(bytes.size <= CastDatagram.MAX_DATAGRAM_SIZE) {
            "Datagram size ${bytes.size} exceeds maximum allowed limit ${CastDatagram.MAX_DATAGRAM_SIZE}"
        }

        val seq = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        val timestamp48k = ((bytes[2].toLong() and 0xFFL) shl 24) or
                ((bytes[3].toLong() and 0xFFL) shl 16) or
                ((bytes[4].toLong() and 0xFFL) shl 8) or
                (bytes[5].toLong() and 0xFFL)
        val flags = bytes[6].toInt() and 0xFF

        val payload = bytes.copyOfRange(CastDatagram.HEADER_SIZE, bytes.size)

        return CastDatagram(
            seq = seq,
            timestamp48k = timestamp48k,
            flags = flags,
            opusPayload = payload
        )
    }
}
