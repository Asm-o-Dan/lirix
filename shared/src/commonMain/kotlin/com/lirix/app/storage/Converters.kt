package com.lirix.app.storage

import androidx.room.TypeConverter
import com.lirix.app.domain.Category
import com.lirix.app.domain.EventSource
import com.lirix.app.domain.EventType
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleType

/**
 * Type converters for Room database persistence.
 * Music & Lyrics Hub edition: Pure KMP implementation without java.nio.
 */
class Converters {

    @TypeConverter
    fun fromSource(value: EventSource): String = value.name

    @TypeConverter
    fun toSource(value: String): EventSource = runCatching {
        EventSource.valueOf(value)
    }.getOrDefault(EventSource.NOTIFICATION)

    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): EventType = runCatching {
        EventType.valueOf(value)
    }.getOrDefault(EventType.UNKNOWN)

    @TypeConverter
    fun fromCategory(value: Category): String = value.key

    @TypeConverter
    fun toCategory(value: String): Category = Category.fromKey(value)

    @TypeConverter
    fun fromBlacklistRuleType(value: BlacklistRuleType): String = value.name

    @TypeConverter
    fun toBlacklistRuleType(value: String): BlacklistRuleType = runCatching {
        BlacklistRuleType.valueOf(value)
    }.getOrDefault(BlacklistRuleType.TITLE)

    @TypeConverter
    fun fromBlacklistMatchMode(value: BlacklistMatchMode): String = value.name

    @TypeConverter
    fun toBlacklistMatchMode(value: String): BlacklistMatchMode = runCatching {
        BlacklistMatchMode.valueOf(value)
    }.getOrDefault(BlacklistMatchMode.CONTAINS_IGNORE_CASE)

    /**
     * Converts FloatArray (e.g. audio fingerprint embedding) to raw Little-Endian BLOB.
     */
    @TypeConverter
    fun fromFloatArray(vector: FloatArray?): ByteArray? {
        if (vector == null) return null
        val bytes = ByteArray(vector.size * 4)
        for (i in vector.indices) {
            val bits = vector[i].toRawBits()
            val offset = i * 4
            bytes[offset] = (bits and 0xFF).toByte()
            bytes[offset + 1] = ((bits ushr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((bits ushr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Converts raw Little-Endian BLOB back into FloatArray.
     */
    @TypeConverter
    fun toFloatArray(blob: ByteArray?): FloatArray? {
        if (blob == null || blob.isEmpty()) return null
        val floatCount = blob.size / 4
        val result = FloatArray(floatCount)
        for (i in 0 until floatCount) {
            val offset = i * 4
            val b0 = blob[offset].toInt() and 0xFF
            val b1 = blob[offset + 1].toInt() and 0xFF
            val b2 = blob[offset + 2].toInt() and 0xFF
            val b3 = blob[offset + 3].toInt() and 0xFF
            val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            result[i] = Float.fromBits(bits)
        }
        return result
    }
}
