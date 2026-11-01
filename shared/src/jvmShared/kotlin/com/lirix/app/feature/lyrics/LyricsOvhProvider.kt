package com.lirix.app.feature.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

/** Exact artist/title plain-text fallback. This API does not provide timing or identity metadata. */
class LyricsOvhProvider(
    private val fetch: suspend (String) -> String? = {
        LyricsSourceHttp.get(it, setOf("api.lyrics.ovh"), HTTP_POLICY)
    }
) {
    companion object {
        const val SOURCE_ID = "builtin:lyrics-ovh"
        private val HTTP_POLICY = LyricsSourceHttp.Policy(
            maxBytes = 256 * 1024,
            totalTimeoutMs = 4_000L,
            connectTimeoutMs = 1_500,
            readTimeoutMs = 2_500,
            maxRedirects = 0
        )

        private fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    suspend fun find(title: String, artist: String): AdditionalLyricsResult? {
        currentCoroutineContext().ensureActive()
        if (title.isBlank() || artist.isBlank()) return null
        val url = "https://api.lyrics.ovh/v1/${segment(artist.trim())}/${segment(title.trim())}"
        return try {
            val response = fetch(url) ?: return null
            currentCoroutineContext().ensureActive()
            val root = Json.parseToJsonElement(response) as? JsonObject ?: return null
            if ("error" in root) return null
            val value = root["lyrics"] as? JsonPrimitive ?: return null
            if (!value.isString) return null
            val plain = value.content.replace("\r\n", "\n").replace('\r', '\n').trim()
            val html = Regex("<[/!]?(?:doctype|html|head|body|script|div|p|br)(?:[\\s>])", RegexOption.IGNORE_CASE)
            if (plain.isBlank() || html.containsMatchIn(plain)) return null
            AdditionalLyricsResult(SOURCE_ID, "lyrics.ovh", plainLyrics = plain, sourceUrl = url)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}
