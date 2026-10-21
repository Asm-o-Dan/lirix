package com.lirix.app.ui.components

import android.util.AtomicFile
import com.lirix.app.feature.lyrics.KaraokeStudioEngine
import com.lirix.app.feature.lyrics.KaraokeStudioState
import com.lirix.app.feature.lyrics.StudioLine
import com.lirix.app.feature.lyrics.StudioMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

data class KaraokeStudioDraft(
    val state: KaraokeStudioState, val expectedPlain: String, val expectedLrc: String?,
    val originalPlain: String = expectedPlain, val originalLrc: String? = expectedLrc,
    val audioUri: String? = null,
    /** Independent song evidence only; never infer this from the persisted effective bound. */
    val originalDurationMs: Long = 0
)

sealed interface StudioDraftLoad {
    data object Missing : StudioDraftLoad
    data class Found(val draft: KaraokeStudioDraft) : StudioDraftLoad
    data class Invalid(val message: String) : StudioDraftLoad
}

/** Independent of song Apply: an interrupted draft write must never erase the last good draft. */
class KaraokeStudioDraftStore(private val directory: File) {
    companion object {
        private val mutex = Mutex()
        private const val LIMIT = 2 * 1024 * 1024
    }

    suspend fun load(trackKey: String): StudioDraftLoad = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = atomic(trackKey)
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withLock StudioDraftLoad.Missing
            try {
                val bytes = file.openRead().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= LIMIT) { "Черновик слишком большой" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                StudioDraftLoad.Found(decode(trackKey, bytes.toString(Charsets.UTF_8)))
            } catch (failure: Exception) {
                StudioDraftLoad.Invalid("Не удалось прочитать черновик. Он сохранён для восстановления; рабочий текст не изменён.")
            }
        }
    }

    suspend fun save(draft: KaraokeStudioDraft) = withContext(Dispatchers.IO) {
        val payload = encode(draft).toByteArray(Charsets.UTF_8)
        require(payload.size <= LIMIT) { "Черновик слишком большой" }
        mutex.withLock {
            check(directory.isDirectory || directory.mkdirs()) { "Не удалось создать каталог черновиков" }
            val file = atomic(draft.state.trackKey)
            val output = file.startWrite()
            try {
                output.write(payload)
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }
    }

    /** Explicit recovery starts a new draft only after the damaged payload has been preserved. */
    suspend fun archive(trackKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val base = atomic(trackKey).baseFile
            val suffix = ".archive-" + java.util.UUID.randomUUID().toString()
            listOf(base, File(base.path + ".bak"), File(base.path + ".new")).filter { it.exists() }.forEach {
                check(it.renameTo(File(it.path + suffix))) { "Не удалось сохранить повреждённый черновик отдельно" }
            }
        }
    }

    private fun atomic(trackKey: String): AtomicFile {
        require(trackKey.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256").digest(trackKey.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it.toInt() and 255) }
        return AtomicFile(File(directory, "$name.json"))
    }

    private fun encode(draft: KaraokeStudioDraft): String {
        val state = draft.state
        fun lines(values: List<StudioLine>): JSONArray {
            require(values.size <= KaraokeStudioEngine.MAX_LINES)
            return JSONArray().apply { values.forEach { line ->
                put(JSONObject().put("id", line.id).put("text", line.text).put("time", line.timeMs ?: JSONObject.NULL))
            } }
        }
        return JSONObject().put("version", 1).put("key", state.trackKey)
            .put("expectedPlain", draft.expectedPlain).put("expectedLrc", draft.expectedLrc ?: JSONObject.NULL)
            .put("originalPlain", draft.originalPlain).put("originalLrc", draft.originalLrc ?: JSONObject.NULL)
            .put("audioUri", draft.audioUri ?: JSONObject.NULL)
            .put("originalDurationMs", draft.originalDurationMs.coerceAtLeast(0))
            .put("original", lines(state.originalLines)).put("lines", lines(state.lines))
            .put("duration", state.durationMs).put("selected", state.selectedIndex)
            .put("cursor", state.cursorMs).put("span", state.spanMs).put("mode", state.mode.name).toString()
    }

    private fun decode(trackKey: String, payload: String): KaraokeStudioDraft {
        val json = JSONObject(payload)
        require(json.getInt("version") == 1 && json.getString("key") == trackKey)
        fun lines(array: JSONArray): List<StudioLine> {
            require(array.length() <= KaraokeStudioEngine.MAX_LINES)
            val values = (0 until array.length()).map { index ->
                val line = array.getJSONObject(index)
                StudioLine(line.getInt("id"), line.getString("text"), if (line.isNull("time")) null else line.getLong("time"))
            }
            require(values.map { it.id }.distinct().size == values.size)
            require(values.all { it.id >= 0 && it.text.isNotBlank() && '\n' !in it.text && '\r' !in it.text && (it.timeMs?.let { time -> time >= 0 } != false) })
            return values
        }
        val current = lines(json.getJSONArray("lines"))
        val duration = json.getLong("duration")
        // Version-1 drafts without provenance can contain a stale file-derived bound.
        val originalDuration = if (json.has("originalDurationMs")) json.getLong("originalDurationMs") else 0L
        val selected = json.getInt("selected")
        val cursor = json.getLong("cursor")
        val span = json.getLong("span")
        require(duration >= 0 && originalDuration >= 0 && selected in 0..(current.size - 1).coerceAtLeast(0) && cursor >= 0 && span in 2000..120000)
        return KaraokeStudioDraft(
            KaraokeStudioState(trackKey, lines(json.getJSONArray("original")), current, originalDuration,
                selectedIndex = selected, cursorMs = cursor, spanMs = span, mode = StudioMode.valueOf(json.getString("mode"))),
            json.getString("expectedPlain"), if (json.isNull("expectedLrc")) null else json.getString("expectedLrc"),
            json.getString("originalPlain"), if (json.isNull("originalLrc")) null else json.getString("originalLrc"),
            if (json.isNull("audioUri")) null else json.getString("audioUri"), originalDuration
        )
    }
}
