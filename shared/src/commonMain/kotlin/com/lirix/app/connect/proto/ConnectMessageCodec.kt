package com.lirix.app.connect.proto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Кодек фреймов сообщений Lirix Connect (SPEC-CONNECT-01 §4.1, TASK-CON-PROTO-03).
 *
 * Формат фрейма:
 * - 4 байта u32 LE префикс длины полезной нагрузки (максимум 64 КБ = 65 536 байт);
 * - байты сериализованного Envelope.
 */
object ConnectMessageCodec {

    const val MAX_PAYLOAD_SIZE_BYTES: Long = 64 * 1024 // 65 536 байт (64 КБ)
    const val LENGTH_HEADER_BYTES: Int = 4

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Кодирует Envelope в бинарный фрейм [4 байта u32 LE длины] + [payload].
     *
     * @throws IllegalArgumentException если размер payload превышает 64 КБ.
     */
    fun encode(envelope: Envelope): ByteArray {
        val jsonString = json.encodeToString(envelope)
        val payloadBytes = jsonString.encodeToByteArray()
        val payloadLength = payloadBytes.size.toLong()

        if (payloadLength > MAX_PAYLOAD_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Payload size $payloadLength bytes exceeds maximum allowed limit of $MAX_PAYLOAD_SIZE_BYTES bytes"
            )
        }

        val frame = ByteArray(LENGTH_HEADER_BYTES + payloadBytes.size)
        writeU32Le(payloadLength, frame, 0)
        payloadBytes.copyInto(frame, destinationOffset = LENGTH_HEADER_BYTES)

        return frame
    }

    /**
     * Декодирует бинарный фрейм с 4-байтным u32 LE заголовком в Envelope.
     *
     * @throws IllegalArgumentException если фрейм повреждён, неполон или размер превышает 64 КБ.
     */
    fun decode(data: ByteArray, offset: Int = 0): Envelope {
        return decodeFrame(data, offset).first
    }

    /**
     * Декодирует один фрейм из буфера и возвращает пару: (Envelope, количество прочитанных байт фрейма).
     */
    fun decodeFrame(data: ByteArray, offset: Int = 0): Pair<Envelope, Int> {
        val available = data.size - offset
        if (available < LENGTH_HEADER_BYTES) {
            throw IllegalArgumentException(
                "Buffer too small: expected at least $LENGTH_HEADER_BYTES bytes for length prefix, got $available"
            )
        }

        val payloadLength = readU32Le(data, offset)
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Payload length $payloadLength exceeds limit of $MAX_PAYLOAD_SIZE_BYTES bytes"
            )
        }

        val totalFrameSize = LENGTH_HEADER_BYTES + payloadLength.toInt()
        if (available < totalFrameSize) {
            throw IllegalArgumentException(
                "Incomplete frame: expected $totalFrameSize bytes, available: $available"
            )
        }

        val payloadBytes = data.copyOfRange(offset + LENGTH_HEADER_BYTES, offset + totalFrameSize)
        val jsonString = payloadBytes.decodeToString()

        val envelope = try {
            json.decodeFromString<Envelope>(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode Envelope: ${e.message}", e)
        }

        return Pair(envelope, totalFrameSize)
    }

    private fun writeU32Le(value: Long, dest: ByteArray, offset: Int) {
        dest[offset] = (value and 0xFF).toByte()
        dest[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        dest[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        dest[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun readU32Le(src: ByteArray, offset: Int): Long {
        val b0 = src[offset].toLong() and 0xFF
        val b1 = src[offset + 1].toLong() and 0xFF
        val b2 = src[offset + 2].toLong() and 0xFF
        val b3 = src[offset + 3].toLong() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
