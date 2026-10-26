package com.lirix.app.storage

import kotlinx.serialization.json.*

/** Actual origin of a newly acquired field; notes and unknown origins are not lyrics. */
data class AutomaticLookupField(val text: String, val sourceId: String, val sourceUrl: String? = null, val author: String? = null) {
    fun authorized(rejected: Set<String>): Boolean = text.isNotBlank() && sourceId.isNotBlank() &&
        sourceId !in rejected && sourceId !in setOf("none", "note:user", "cache:room")
}
data class AutomaticLookupCandidate(
    val plain: AutomaticLookupField? = null,
    val synced: AutomaticLookupField? = null,
    val chords: AutomaticLookupField? = null
)

/** Only a note-origin row plus an exact duplicate proves that a track field is legacy note pollution.
 * Independent track words remain authoritative even while a note-only cache row exists. */
fun automaticLookupAcceptedTrackField(text: String?, track: TrackEntity?, cache: LyricsCacheEntity?): String? =
    text?.takeIf { it.isNotBlank() }?.takeUnless { value ->
        cache?.provider == "note:user" &&
            (value == cache.plainLyrics || value == cache.syncedLyricsLrc ||
                value == cache.userNotes || value == track?.userNotes)
    }

/** Preserve unknown JSON members and untouched field provenance as well as known members. */
internal fun mergeAutomaticLookupAttribution(old: String?, lyric: AutomaticLookupField?, chord: AutomaticLookupField?): String? {
    if (lyric == null && chord == null) return old
    val fields = old?.let { runCatching { Json.parseToJsonElement(it).jsonObject.toMutableMap() }.getOrNull() } ?: mutableMapOf()
    fun origin(value: AutomaticLookupField) = buildJsonObject {
        put("sourceId", value.sourceId)
        value.sourceUrl?.let { put("sourceUrl", it) }
        value.author?.let { put("author", it) }
    }
    lyric?.let { fields["lyrics"] = origin(it) }
    chord?.let { fields["chords"] = origin(it) }
    return JsonObject(fields).toString()
}
