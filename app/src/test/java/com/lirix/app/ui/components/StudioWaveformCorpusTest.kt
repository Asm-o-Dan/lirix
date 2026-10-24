package com.lirix.app.ui.components

import android.media.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StudioWaveformCorpusTest {
    @Test fun fullStereoFilePreservesSilentRegionsAndOppositePhasePeaks() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/fixtures/audio/studio-envelope.wav")).use { it.readBytes() }
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, header.getShort(22).toInt())
        assertEquals(48000, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        val samples = ByteBuffer.wrap(bytes, 44, bytes.size - 44).slice().order(ByteOrder.LITTLE_ENDIAN)
        val peaks = FloatArray(200)
        val frames = accumulateWaveformPcm(samples, 2, 48000, AudioFormat.ENCODING_PCM_16BIT, 0, 2000000, peaks)
        assertEquals(96000, frames)
        assertTrue(peaks.take(50).all { it == 0f })
        assertTrue(peaks.drop(150).all { it == 0f })
        assertTrue(peaks.slice(50..99).all { it in .198f.. .201f })
        assertTrue(peaks.slice(100..149).all { it in .798f.. .801f })
        assertTrue(peaks.all { it.isFinite() && it in 0f..1f })
    }
}
