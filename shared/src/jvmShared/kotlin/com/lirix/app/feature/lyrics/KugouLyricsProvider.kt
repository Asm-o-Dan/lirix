package com.lirix.app.feature.lyrics

import com.lirix.app.translations.TranslationMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlin.math.abs

/** Public lyric search/download. Search-issued accesskeys are neither stored nor logged. */
class KugouLyricsProvider(
    private val fetch: suspend (String) -> String? = { LyricsSourceHttp.get(it, setOf("lyrics.kugou.com")) }
) {
    suspend fun find(title: String, artist: String, durationMs: Long? = null): AdditionalLyricsResult? = withTimeoutOrNull(12_000) {
        if (title.isBlank() || artist.isBlank() || artist.equals("Unknown Artist", true)) return@withTimeoutOrNull null
        val search = searchUrl(title, artist, durationMs)
        try {
            val candidates = candidates(fetch(search) ?: return@withTimeoutOrNull null, title, artist, durationMs)
            for (candidate in candidates.take(2)) {
                currentCoroutineContext().ensureActive()
                val body = fetch(downloadUrl(candidate)) ?: continue
                val lrc = parseDownload(body) ?: continue
                val plain = LrcSyncEngine.parseLrcLinesStrict(lrc).map { it.text }.filter { it.isNotBlank() }.joinToString("\n")
                return@withTimeoutOrNull AdditionalLyricsResult(SOURCE_ID, "KuGou", plain, lrc, sourceUrl = search)
            }
            null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    companion object {
        const val SOURCE_ID = "builtin:kugou"
        private val json = Json { ignoreUnknownKeys = true }
        class Candidate(val id: String, internal val accesskey: String, val durationMs: Long)
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        fun searchUrl(title: String, artist: String, durationMs: Long?): String =
            "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${encode("$artist $title")}" +
                (durationMs?.takeIf { it > 0 }?.let { "&duration=$it" } ?: "")
        private fun downloadUrl(c: Candidate) = "https://lyrics.kugou.com/download?ver=1&client=pc&id=${encode(c.id)}&accesskey=${encode(c.accesskey)}&fmt=lrc&charset=utf8"
        private fun titleKey(raw: String, artist: String): String {
            var title = raw.trim().replace(Regex("(?i)\\s*[\\(\\[](?:official(?: music)? (?:video|audio)|lyrics?|audio|hd|4k)[\\)\\]]"), " ")
            val parts = title.split(Regex("\\s+[-–—]\\s+"), limit = 2)
            if (parts.size == 2 && TranslationMetadata.artistKey(parts[0]) == TranslationMetadata.artistKey(artist)) title = parts[1]
            return Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "")
        }
        fun candidates(body: String, title: String, artist: String, durationMs: Long?): List<Candidate> {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
            if (root["status"]?.jsonPrimitive?.intOrNull != 200) return emptyList()
            val expectedDuration = durationMs?.takeIf { it > 0 }
            val exact = root["candidates"]?.jsonArray.orEmpty().take(30).mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                fun value(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (titleKey(value("song"), artist) != titleKey(title, artist) || TranslationMetadata.artistKey(value("singer")) != TranslationMetadata.artistKey(artist)) return@mapNotNull null
                val duration = value("duration").toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                if (expectedDuration != null && abs(duration - expectedDuration) > maxOf(3_000L, expectedDuration / 50)) return@mapNotNull null
                val id = value("id"); val accesskey = value("accesskey")
                if (!id.matches(Regex("[0-9]{1,30}")) || !accesskey.matches(Regex("[A-Za-z0-9_-]{1,200}"))) return@mapNotNull null
                Candidate(id, accesskey, duration)
            }.distinctBy { it.id }
            // Unknown duration cannot disambiguate substantially different recordings.
            if (expectedDuration == null && exact.isNotEmpty() && exact.maxOf { it.durationMs } - exact.minOf { it.durationMs } > 3_000) return emptyList()
            return exact.sortedBy { expectedDuration?.let { expected -> abs(it.durationMs - expected) } ?: it.durationMs }.take(2)
        }
        fun parseDownload(body: String): String? = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            if (root["status"]?.jsonPrimitive?.intOrNull != 200 || root["fmt"]?.jsonPrimitive?.content != "lrc") return null
            val encoded = root["content"]?.jsonPrimitive?.content ?: return null
            if (encoded.length > 700_000) return null
            val bytes = Base64.getDecoder().decode(encoded)
            val lrc = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            val lines = LrcSyncEngine.parseLrcLinesStrict(lrc).filter { it.text.isNotBlank() }
            if (lines.size < 2 || lines.none { it.timestampMs > 0 } || lrc.contains("<html", true)) null else lrc
        }.getOrNull()
    }
}
