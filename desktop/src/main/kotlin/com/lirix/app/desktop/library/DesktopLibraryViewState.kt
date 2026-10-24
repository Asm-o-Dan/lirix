package com.lirix.app.desktop.library

import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.storage.TrackEntity

enum class DesktopLibraryFilter(private val sourceLabel: String) {
    ALL("Все"),
    FAVORITES("Избранное"),
    KARAOKE("Караоке"),
    SAVED_TEXT("С сохранённым текстом");
    val label get() = com.lirix.app.ui.i18n.LirixStrings.message(sourceLabel)
}

enum class DesktopLibraryEmpty { NONE, NO_HISTORY, NO_MATCHES }

data class DesktopLibraryViewState(
    val visibleTracks: List<TrackEntity>,
    val selectedTrack: TrackEntity?,
    val empty: DesktopLibraryEmpty,
    val hasRestrictions: Boolean
)

fun buildDesktopLibraryViewState(
    tracks: List<TrackEntity>,
    query: String,
    filter: DesktopLibraryFilter,
    artist: String?,
    selectedKey: String?
): DesktopLibraryViewState {
    val search = query.trim()
    val visible = tracks.filter { track ->
        val matchesFilter = when (filter) {
            DesktopLibraryFilter.ALL -> true
            DesktopLibraryFilter.FAVORITES -> track.isFavorite
            DesktopLibraryFilter.KARAOKE -> LrcSyncEngine.parseLrcLinesStrict(track.syncedLyrics.orEmpty()).isNotEmpty()
            DesktopLibraryFilter.SAVED_TEXT -> !track.plainLyrics.isNullOrBlank() || !track.syncedLyrics.isNullOrBlank()
        }
        matchesFilter && (artist == null || track.artist == artist) &&
            listOf(track.title, track.artist, track.album, track.plainLyrics.orEmpty())
                .any { it.contains(search, ignoreCase = true) }
    }
    return DesktopLibraryViewState(
        visibleTracks = visible,
        selectedTrack = visible.firstOrNull { it.trackKey == selectedKey } ?: visible.firstOrNull(),
        empty = when {
            tracks.isEmpty() -> DesktopLibraryEmpty.NO_HISTORY
            visible.isEmpty() -> DesktopLibraryEmpty.NO_MATCHES
            else -> DesktopLibraryEmpty.NONE
        },
        hasRestrictions = search.isNotEmpty() || filter != DesktopLibraryFilter.ALL || artist != null
    )
}
