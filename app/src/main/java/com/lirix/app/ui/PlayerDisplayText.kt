package com.lirix.app.ui

import com.lirix.app.storage.TrackEntity
import java.text.NumberFormat
import java.util.Locale

/**
 * Placeholder that ingestion stores when a player reports no artist. Track keys are computed
 * from it, so it stays in the database; only the displayed text is localized.
 */
internal const val STORED_UNKNOWN_ARTIST = "Unknown Artist"

/** Shows [unknownLabel] for a blank artist or the stored English placeholder. */
internal fun displayArtist(stored: String, unknownLabel: String): String =
    if (stored.isBlank() || stored.trim().equals(STORED_UNKNOWN_ARTIST, ignoreCase = true)) unknownLabel else stored

internal data class LibraryArtistRow(val name: String, val trackCount: Int)

/**
 * Artists of exactly the tracks the library currently lists, grouped by displayed name and kept
 * in the order of first appearance. Both segment counters come from the same [tracks].
 */
internal fun libraryArtistRows(tracks: List<TrackEntity>, unknownLabel: String): List<LibraryArtistRow> =
    tracks.groupingBy { displayArtist(it.artist, unknownLabel) }.eachCount()
        .map { (name, count) -> LibraryArtistRow(name, count) }

/** Search match for the library: the localized unknown-artist label also finds placeholder tracks. */
internal fun libraryTrackMatches(track: TrackEntity, query: String, unknownLabel: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return track.title.lowercase().contains(q) ||
        track.artist.lowercase().contains(q) ||
        displayArtist(track.artist, unknownLabel).lowercase().contains(q) ||
        track.album.lowercase().contains(q) ||
        track.plainLyrics?.lowercase()?.contains(q) == true ||
        track.syncedLyrics?.lowercase()?.contains(q) == true
}

/** Whole number with the locale's grouping separator, e.g. 9363 → "9 363" (ru) or "9,363" (en). */
internal fun formatGroupedNumber(value: Long, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = true }.format(value)

/** Edit and studio buttons that the karaoke text area itself shows for the presented lyrics. */
internal data class InlineLyricsActions(val edit: Boolean, val studio: Boolean) {
    /** The "lyrics without timing" badge is drawn together with the inline studio button. */
    val unsyncedBadge: Boolean get() = studio
    companion object { val NONE = InlineLyricsActions(edit = false, studio = false) }
}

/**
 * Mirrors the karaoke branch of the teleprompter: plain-only lyrics show edit and studio
 * buttons; a non-blank LRC without timed lines falls back to plain text with studio only.
 */
internal fun karaokeInlineLyricsActions(
    mode: NowPlayingMode,
    isLoading: Boolean,
    synced: String?,
    plain: String?,
    hasTimedLines: Boolean
): InlineLyricsActions {
    if (mode != NowPlayingMode.KARAOKE || isLoading) return InlineLyricsActions.NONE
    val hasPlain = !plain.isNullOrBlank()
    return when {
        !synced.isNullOrBlank() -> InlineLyricsActions(edit = false, studio = !hasTimedLines && hasPlain)
        hasPlain -> InlineLyricsActions(edit = true, studio = true)
        else -> InlineLyricsActions.NONE
    }
}
