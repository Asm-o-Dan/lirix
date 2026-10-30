package com.lirix.app.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun isManualLyricsProvider(provider: String?): Boolean = provider.equals("USER", ignoreCase = true) ||
    provider?.startsWith("USER:", ignoreCase = true) == true

@Serializable
data class LyricsSourceAttribution(val sourceId: String, val sourceUrl: String? = null, val author: String? = null)

/** Original words and an independently selected chord arrangement may have different authors. */
@Serializable
data class LyricsAttribution(val lyrics: LyricsSourceAttribution? = null, val chords: LyricsSourceAttribution? = null) {
    fun encode(): String? = if (lyrics == null && chords == null) null else json.encodeToString(this)
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(value: String?): LyricsAttribution = value?.takeIf { it.length <= 32_000 }
            ?.let { runCatching { json.decodeFromString<LyricsAttribution>(it) }.getOrNull() } ?: LyricsAttribution()
    }
}
