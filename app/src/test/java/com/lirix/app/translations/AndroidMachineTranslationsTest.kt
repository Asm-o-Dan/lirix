package com.lirix.app.translations

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidMachineTranslationsTest {
    private lateinit var context: Context
    private class Backend : MachineTranslationBackend {
        var source: String? = "en"
        var available = false
        var translated = 0
        var downloaded = 0
        var detected = 0
        var waitForCancellation = false
        val entered = CompletableDeferred<Unit>()
        override suspend fun identify(text: String): String? { detected++; return source }
        override suspend fun modelsAvailable(source: String, target: String) = available
        override suspend fun download(source: String, target: String) { downloaded++; available = true }
        override suspend fun translate(lines: List<String>, source: String, target: String): List<String> {
            translated++; entered.complete(Unit)
            if (waitForCancellation) awaitCancellation()
            return lines.map { if (it.isBlank()) "" else "$target:$it" }
        }
    }
    private val request = MachineTranslationRequest("stable-A", "Synthetic song", "Artist", "First line\n\nSecond line\nFirst line", "ru")
    private fun files() = File(context.cacheDir, "machine-translations-v1").listFiles().orEmpty()
    @Before fun resetPrivateRobolectricSandbox() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lirix_translations", Context.MODE_PRIVATE).edit().clear().commit()
        files().forEach { check(it.delete()) }
    }
    @Test fun defaultOffModelsNeedExplicitDownloadAndCachePreservesExactLines() = runBlocking {
        val backend = Backend()
        val service = AndroidMachineTranslations(context, backend)
        assertFalse(service.enabled.value)
        service.setEnabled(true)
        assertTrue(service.translate(request) is MachineTranslationResult.ModelRequired)
        assertEquals(0, backend.downloaded); assertEquals(0, backend.translated)
        val doc = (service.translate(request, true) as MachineTranslationResult.Translated).document
        assertEquals(listOf("First line", "", "Second line", "First line"), doc.blocks.map { it.original })
        assertEquals(listOf("ru:First line", "", "ru:Second line", "ru:First line"), doc.blocks.map { it.translated })
        assertEquals("", doc.sourceUrl); assertEquals("Google Translate", doc.sourceName)
        assertEquals(1, backend.downloaded); assertEquals(1, backend.translated)
        backend.available = false
        assertEquals(doc, (service.translate(request) as MachineTranslationResult.Translated).document)
        assertEquals(1, backend.translated)
        assertTrue(AndroidMachineTranslations(context, backend).enabled.value)
    }
    @Test fun cacheSeparatesTrackOriginalTargetSourceAndIntegrationVersion() = runBlocking {
        val backend = Backend().apply { available = true }
        val service = AndroidMachineTranslations(context, backend, "v1")
        service.setEnabled(true)
        service.translate(request)
        service.translate(request.copy(trackKey = "stable-B"))
        service.translate(request.copy(original = "Other words"))
        service.translate(request.copy(targetLanguage = "es"))
        backend.source = "de"
        service.translate(request)
        AndroidMachineTranslations(context, backend, "v2").translate(request)
        assertEquals(6, backend.translated)
    }
    @Test fun corruptCacheIsRegeneratedWithoutAnyPublishedTranslationWrite() = runBlocking {
        val backend = Backend().apply { available = true }
        val service = AndroidMachineTranslations(context, backend)
        service.setEnabled(true); service.translate(request)
        files().single().writeText("{broken")
        val doc = (service.translate(request) as MachineTranslationResult.Translated).document
        assertEquals(2, backend.translated)
        assertEquals("First line", doc.blocks.first().original)
        assertFalse(context.databaseList().any { it == "event_engine.db" })
    }
    @Test fun unknownSameLanguageAndInvalidInputNeverDownloadOrTranslate() = runBlocking {
        val backend = Backend()
        val service = AndroidMachineTranslations(context, backend)
        service.setEnabled(true)
        backend.source = null
        assertEquals(MachineTranslationResult.UnknownLanguage, service.translate(request))
        backend.source = "ru"
        assertEquals(MachineTranslationResult.SameLanguage, service.translate(request))
        for (invalid in listOf(request.copy(original = ""), request.copy(original = "x".repeat(50_001)), request.copy(original = "a\n".repeat(1_001)))) {
            try { service.translate(invalid); fail("Oversized/empty input accepted") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, backend.translated); assertEquals(0, backend.downloaded)
    }
    @Test fun cancellationDoesNotPublishOrWritePartialDocument() = runBlocking {
        val backend = Backend().apply { available = true; waitForCancellation = true }
        val service = AndroidMachineTranslations(context, backend)
        service.setEnabled(true)
        val operation = launch { service.translate(request); fail("Cancelled translation returned a result") }
        withTimeout(5_000) { backend.entered.await() }
        operation.cancelAndJoin()
        assertTrue(operation.isCancelled)
        assertTrue(files().isEmpty())
    }
    @Test fun disposableCacheIsBounded() = runBlocking {
        val backend = Backend().apply { available = true }
        val service = AndroidMachineTranslations(context, backend)
        service.setEnabled(true)
        repeat(130) { service.translate(request.copy(trackKey = "track-$it")) }
        assertTrue(files().size <= 128)
        assertTrue(files().sumOf { it.length() } <= 20 * 1024 * 1024)
    }
}
