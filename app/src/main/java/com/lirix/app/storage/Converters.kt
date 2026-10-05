package com.lirix.app.storage

import androidx.room.TypeConverter
import com.lirix.app.domain.Category
import com.lirix.app.domain.EventSource
import com.lirix.app.domain.EventType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Type converters for Room database persistence.
 * Music & Lyrics Hub edition: Finance/Study/Prototype converters removed.
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

    /**
     * Converts FloatArray (e.g. audio fingerprint embedding) to raw Little-Endian BLOB.
     */
    @TypeConverter
    fun fromFloatArray(vector: FloatArray?): ByteArray? {
        if (vector == null) return null
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in vector) buffer.putFloat(f)
        return buffer.array()
    }

    /**
     * Converts raw Little-Endian BLOB back into FloatArray.
     */
    @TypeConverter
    fun toFloatArray(blob: ByteArray?): FloatArray? {
        if (blob == null || blob.isEmpty()) return null
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        val floatCount = blob.size / 4
        val result = FloatArray(floatCount)
        for (i in 0 until floatCount) result[i] = buffer.float
        return result
    }
}
