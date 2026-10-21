package com.lirix.app

import android.media.AudioFormat
import com.lirix.app.ui.components.accumulateWaveformPcm
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KaraokeWaveformPcmTest {
    @Test fun stereoUsesActualPeakOfEitherChannelAndPlacesItByTimestamp() {
        val data = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        listOf<Short>(0, 0, 16384, -32768, 8192, 0).forEach { data.putShort(it) }
        data.flip()
        val peaks = FloatArray(8)
        val count = accumulateWaveformPcm(data, 2, 4, AudioFormat.ENCODING_PCM_16BIT, 1_000_000, 2_000_000, peaks)
        assertEquals(3, count)
        assertEquals(0f, peaks[0], 0f)
        assertEquals(0f, peaks[4], 0f)
        assertEquals(1f, peaks[5], 0f)
        assertEquals(0.25f, peaks[6], 0f)
    }
    @Test fun floatPcmClampsAndRejectsNanWithoutFakePeaks() {
        val data = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        listOf(Float.NaN, 0f, -0.5f, 2f).forEach { data.putFloat(it) }
        data.flip()
        val peaks = FloatArray(4)
        accumulateWaveformPcm(data, 1, 4, AudioFormat.ENCODING_PCM_FLOAT, 0, 1_000_000, peaks)
        assertArrayEquals(floatArrayOf(0f, 0f, 0.5f, 1f), peaks, 0f)
    }
    @Test fun silenceIsZeroAndOutOfRangeSamplesAreNotInvented() {
        val peaks = FloatArray(5)
        val data = ByteBuffer.wrap(ByteArray(100)).order(ByteOrder.LITTLE_ENDIAN)
        accumulateWaveformPcm(data, 1, 50, AudioFormat.ENCODING_PCM_16BIT, 2_000_000, 1_000_000, peaks)
        assertTrue(peaks.all { it == 0f })
    }

    @Test fun unsigned8BitPcmUses128AsSilence() {
        val peaks = FloatArray(4)
        val bytes = ByteBuffer.wrap(byteArrayOf(0, 128.toByte(), 255.toByte(), 64))
        accumulateWaveformPcm(bytes, 1, 4, AudioFormat.ENCODING_PCM_8BIT, 0, 1_000_000, peaks)
        assertArrayEquals(floatArrayOf(1f, 0f, 127f / 128, 0.5f), peaks, 0f)
    }

    @Test fun signed24And32BitPcmKeepNegativeFullScale() {
        val packed = ByteBuffer.allocate(12)
        listOf(-8388608, 0, 4194304, 8388607).forEach {
            packed.put(it.toByte()); packed.put((it shr 8).toByte()); packed.put((it shr 16).toByte())
        }
        packed.flip()
        val peaks24 = FloatArray(4)
        accumulateWaveformPcm(packed, 1, 4, AudioFormat.ENCODING_PCM_24BIT_PACKED, 0, 1_000_000, peaks24)
        assertArrayEquals(floatArrayOf(1f, 0f, 0.5f, 8388607f / 8388608), peaks24, 0.000001f)
        val integers = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        listOf(Int.MIN_VALUE, 0, 1073741824, -1073741824).forEach { integers.putInt(it) }
        integers.flip()
        val peaks32 = FloatArray(4)
        accumulateWaveformPcm(integers, 1, 4, AudioFormat.ENCODING_PCM_32BIT, 0, 1_000_000, peaks32)
        assertArrayEquals(floatArrayOf(1f, 0f, 0.5f, 0.5f), peaks32, 0f)
    }
}
