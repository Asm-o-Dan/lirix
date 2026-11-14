package com.lirix.app.translations

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Private, disposable cache; deliberately independent of song_translations and backup archives. */
internal class AndroidMachineTranslations(
    context: Context,
    private val backend: MachineTranslationBackend,
    private val version: String = MachineTranslationEngineInfo.Compact.version,
    override val engineInfo: MachineTranslationEngineInfo = MachineTranslationEngineInfo.Compact
) : MachineTranslationFallback {
    private val preferences = context.applicationContext.getSharedPreferences("lirix_translations", Context.MODE_PRIVATE)
    private val preference = MutableStateFlow(preferences.getBoolean("machine_enabled", false))
    override val enabled = preference.asStateFlow()
    override val downloadProgress = backend.downloadProgress
    private val directory = File(context.applicationContext.cacheDir, "machine-translations-v1")
    private val gate = Mutex()
    override fun setEnabled(value: Boolean) {
        check(preferences.edit().putBoolean("machine_enabled", value).commit()) { "Could not store translation preference" }
        preference.value = value
    }
    override suspend fun translate(request: MachineTranslationRequest, downloadModels: Boolean): MachineTranslationResult = gate.withLock {
        check(enabled.value)
        require(request.trackKey.isNotBlank() && request.title.isNotBlank())
        require(request.targetLanguage in TranslationLanguages.labels)
        require(request.original.isNotBlank() && request.original.length <= 50_000)
        val lines = request.original.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        require(lines.size <= 1_000)
        withContext(Dispatchers.IO) { withTimeout(if (downloadModels) 15 * 60_000L else 180_000L) {
            val source = backend.identify(request.original) ?: return@withTimeout MachineTranslationResult.UnknownLanguage
            if (source == request.targetLanguage) return@withTimeout MachineTranslationResult.SameLanguage
            if (!backend.supports(source, request.targetLanguage)) return@withTimeout MachineTranslationResult.UnsupportedPair(source, request.targetLanguage)
            val originalHash = sha256(request.original)
            val key = sha256(listOf(request.trackKey, originalHash, source, request.targetLanguage, version).joinToString("\u0000"))
            val file = File(directory, "$key.json")
            read(file, request, source, originalHash)?.let { return@withTimeout MachineTranslationResult.Translated(it) }
            if (!backend.modelsAvailable(source, request.targetLanguage)) {
                if (!downloadModels) return@withTimeout MachineTranslationResult.ModelRequired(source)
                backend.download(source, request.targetLanguage)
            }
            currentCoroutineContext().ensureActive()
            check(enabled.value)
            val translated = backend.translate(lines, source, request.targetLanguage)
            require(translated.size == lines.size && lines.indices.all { lines[it].isBlank() || translated[it].isNotBlank() })
            val document = SongTranslation("", request.title, request.artist, "", "",
                lines.indices.map { TranslationBlock(lines[it], translated[it]) }, language = request.targetLanguage,
                sourceName = engineInfo.name, aligned = true)
            currentCoroutineContext().ensureActive()
            check(enabled.value)
            directory.mkdirs()
            val data = JSONObject().put("version", version).put("source", source).put("target", request.targetLanguage)
                .put("trackKey", request.trackKey).put("originalHash", originalHash)
                .put("document", TranslationDocuments.encode(document)).toString().toByteArray(Charsets.UTF_8)
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try { output.write(data); atomic.finishWrite(output) } catch (e: Exception) { atomic.failWrite(output); throw e }
            trimCache()
            MachineTranslationResult.Translated(document)
        } }
    }
    private fun read(file: File, request: MachineTranslationRequest, source: String, originalHash: String): SongTranslation? {
        if (!file.isFile || file.length() > 2_000_000) return null
        return runCatching {
            val json = JSONObject(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            require(json.getString("version") == version && json.getString("source") == source && json.getString("target") == request.targetLanguage)
            require(json.getString("trackKey") == request.trackKey && json.getString("originalHash") == originalHash)
            TranslationDocuments.decode(json.getString("document")).also {
                require(it.sourceUrl.isEmpty() && it.sourceName == engineInfo.name && it.language == request.targetLanguage)
                require(it.title == request.title && it.artist == request.artist)
                val expected = request.original.replace("\r\n", "\n").replace('\r', '\n').split('\n')
                require(it.blocks.map { block -> block.original } == expected)
            }
        }.getOrNull()
    }
    private fun trimCache() {
        val files = directory.listFiles { file -> file.name.matches(Regex("[0-9a-f]{64}\\.json")) }?.sortedBy { it.lastModified() }.orEmpty()
        var count = files.size
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (count <= 128 && bytes <= 20 * 1024 * 1024) break
            val length = file.length()
            if (file.delete()) { count--; bytes -= length }
        }
    }
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
}
