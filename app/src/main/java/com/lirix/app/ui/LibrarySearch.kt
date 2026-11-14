package com.lirix.app.ui

import com.lirix.app.feature.lyrics.ChordTransposer
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.TrackEntity

internal enum class LibrarySearchField { TITLE, ARTIST, TEXT, CHORDS }
internal data class LibrarySearchMatch(val field: LibrarySearchField, val excerpt: String, val rank: Int)

private val searchWhitespace = Regex("\\s+")
private val remainingLrcTags = Regex("\\[\\d+:\\d{2}(?:[.:]\\d{1,3})?]")
private fun normalizedSearch(value: String): String = value.lowercase().replace('ё', 'е')
    .replace(searchWhitespace, " ").trim()

internal fun librarySearchMatch(
    track: TrackEntity,
    cache: LyricsCacheEntity?,
    query: String,
    fields: Set<LibrarySearchField>,
    unknownArtistLabel: String
): LibrarySearchMatch? {
    val needle = normalizedSearch(query)
    if (needle.isEmpty()) return LibrarySearchMatch(LibrarySearchField.TITLE, "", 0)
    fun textMatch(field: LibrarySearchField, text: String, rank: Int): LibrarySearchMatch? {
        if (field !in fields) return null
        val clean = text.replace(searchWhitespace, " ").trim()
        val value = normalizedSearch(clean)
        val start = value.indexOf(needle)
        if (start < 0) return null
        val excerptStart = (start - 30).coerceAtLeast(0)
        val excerptEnd = (start + needle.length + 60).coerceAtMost(clean.length)
        val excerpt = clean.substring(excerptStart.coerceAtMost(excerptEnd), excerptEnd)
        return LibrarySearchMatch(field, excerpt, rank)
    }
    val titleRank = when {
        normalizedSearch(track.title) == needle -> 0
        normalizedSearch(track.title).startsWith(needle) -> 1
        else -> 2
    }
    textMatch(LibrarySearchField.TITLE, track.title, titleRank)?.let { return it }
    textMatch(LibrarySearchField.ARTIST, track.artist, 3)?.let { return it }
    textMatch(LibrarySearchField.ARTIST, displayArtist(track.artist, unknownArtistLabel), 3)?.let { return it }
    if (fields.size == LibrarySearchField.entries.size) {
        val album = normalizedSearch(track.album)
        if (album.contains(needle)) return LibrarySearchMatch(LibrarySearchField.TITLE, track.album, 4)
    }
    val plain = cache?.plainLyrics?.takeIf { it.isNotBlank() } ?: track.plainLyrics.orEmpty()
    val synced = cache?.syncedLyricsLrc?.takeIf { it.isNotBlank() } ?: track.syncedLyrics.orEmpty()
    val words = plain.ifBlank { LrcSyncEngine.parseLrcLinesStrict(synced).joinToString(" ") { it.text.replace(remainingLrcTags, "") } }
    textMatch(LibrarySearchField.TEXT, words, 5)?.let { return it }
    if (LibrarySearchField.CHORDS in fields) {
        val queryTokens = query.trim().split(searchWhitespace).map { ChordTransposer.searchToken(it) ?: return null }
        val chords = ChordTransposer.searchTokens(cache?.chordsAmDm.orEmpty())
        if (queryTokens.isNotEmpty() && chords.windowed(queryTokens.size).any { it == queryTokens }) {
            return LibrarySearchMatch(LibrarySearchField.CHORDS, queryTokens.joinToString(" "), 6)
        }
    }
    return null
}
