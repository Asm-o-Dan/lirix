package com.lirix.app.translations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PrivateModelStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val names = listOf("encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx", "source.spm", "target.spm", "vocab.json")
    private val abcDigest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private fun catalog(): PrivateModelCatalog {
        val files = JSONArray()
        names.forEach { files.put(JSONObject().put("name", it).put("bytes", 3).put("sha256", abcDigest).put("url", "https://huggingface.co/pinned/$it")) }
        val model = JSONObject().put("pair", "en-ru").put("eos", 0).put("pad", 62517).put("vocabSize", 62518).put("files", files)
        return PrivateModelCatalog(JSONObject().put("packages", JSONArray().put(model)).toString().toByteArray())
    }
    private fun connection(uri: URI, payload: String = "abc", redirect: String? = null, cancelled: Boolean = false) = object : HttpURLConnection(uri.toURL()) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = if (redirect == null) 200 else 302
        override fun getContentLengthLong() = payload.toByteArray().size.toLong()
        override fun getHeaderField(name: String?) = if (name == "Location") redirect else null
        override fun getInputStream() = if (cancelled) object : ByteArrayInputStream(payload.toByteArray()) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int { throw CancellationException("Fixture cancelled") }
        } else ByteArrayInputStream(payload.toByteArray())
    }
    @Test fun downloadsOnlyPinnedBytesThenAtomicallyMarksPackageReady() = runBlocking {
        val catalog = catalog(); val model = catalog.packages.getValue("en-ru")
        val root = temporary.newFolder(); val requests = mutableListOf<URI>()
        val store = PrivateModelStore(root, catalog, { true }, { requests += it; connection(it) })
        assertFalse(store.ready(model)); store.download(model)
        assertEquals(6, requests.size); assertTrue(store.ready(model))
        val verified = store.verify(model)
        names.forEach { assertEquals("abc", File(verified, it).readText()) }
        assertFalse(File(root, ".download-en-ru").exists())
        assertNull(store.progress.value)
    }
    @Test fun truncatedOversizedAndSameLengthCorruptedFilesNeverBecomeInstalled() = runBlocking {
        for (payload in listOf("ab", "abcd", "abd")) {
            val catalog = catalog(); val model = catalog.packages.getValue("en-ru"); val root = temporary.newFolder()
            val store = PrivateModelStore(root, catalog, { true }, { connection(it, payload) })
            try { store.download(model); fail("Invalid model installed") } catch (_: IllegalStateException) { }
            assertFalse(store.ready(model)); assertFalse(File(root, ".download-en-ru").exists())
        }
    }
    @Test fun foreignRedirectAndOfflineModeFailBeforeOpeningUntrustedConnection() = runBlocking {
        val catalog = catalog(); val model = catalog.packages.getValue("en-ru"); val requests = mutableListOf<URI>()
        val offline = PrivateModelStore(temporary.newFolder(), catalog, { false }, { error("No network may be opened") })
        try { offline.download(model); fail("Offline download started") } catch (_: IllegalStateException) { }
        val store = PrivateModelStore(temporary.newFolder(), catalog, { true }, {
            requests += it; connection(it, redirect = "https://evil.example/model")
        })
        try { store.download(model); fail("Foreign destination accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(1, requests.size); assertEquals("huggingface.co", requests.single().host)
        assertFalse(store.ready(model))
    }
    @Test fun cancellationLeavesNoReadyMarkerOrPartialPackage() = runBlocking {
        val catalog = catalog(); val model = catalog.packages.getValue("en-ru"); val root = temporary.newFolder()
        val store = PrivateModelStore(root, catalog, { true }, { connection(it, cancelled = true) })
        try { store.download(model); fail("Cancellation swallowed") } catch (_: CancellationException) { }
        assertFalse(store.ready(model)); assertFalse(File(root, ".download-en-ru").exists())
        assertNull(store.progress.value)
    }
    @Test fun corruptionOfInstalledModelInvalidatesMarkerAndAllowsExplicitRedownload() = runBlocking {
        val catalog = catalog(); val model = catalog.packages.getValue("en-ru"); val store = PrivateModelStore(temporary.newFolder(), catalog, { true }, { connection(it) })
        store.download(model)
        File(store.directory(model), "encoder_model.onnx").writeText("abd")
        try { store.verify(model); fail("Corrupt installed model accepted") } catch (_: IllegalStateException) { }
        assertFalse(store.ready(model))
        store.download(model); store.verify(model); assertTrue(store.ready(model))
    }
}
