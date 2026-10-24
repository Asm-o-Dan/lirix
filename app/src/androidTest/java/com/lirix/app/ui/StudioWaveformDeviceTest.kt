package com.lirix.app.ui

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.lirix.app.ui.components.decodeKaraokeWaveform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StudioWaveformDeviceTest {
    @Test fun actualExtractorAndDecoderPreserveFileTimelineAndEnvelope() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File.createTempFile("studio-fixture-", ".wav", context.cacheDir)
        try {
            instrumentation.context.assets.open("audio/studio-envelope.wav").use { input -> file.outputStream().use { input.copyTo(it) } }
            val progress = mutableListOf<Float>()
            val waveform = decodeKaraokeWaveform(context, Uri.fromFile(file)) { progress += it }
            assertEquals(2000L, waveform.durationMs)
            assertEquals(200, waveform.peaks.size)
            assertTrue(waveform.peaks.take(45).all { it < .002f })
            assertTrue(waveform.peaks.slice(55..94).all { it in .19f.. .21f })
            assertTrue(waveform.peaks.slice(105..144).all { it in .79f.. .81f })
            assertTrue(waveform.peaks.drop(155).all { it < .002f })
            assertTrue(waveform.peaks.all { it.isFinite() && it in 0f..1f })
            assertEquals(1f, progress.last())
        } finally { file.delete() }
    }
}
