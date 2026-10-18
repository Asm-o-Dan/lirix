package com.lirix.app.desktop

import com.lirix.app.desktop.library.*
import com.lirix.app.storage.TrackEntity
import org.junit.Assert.*
import org.junit.Test

class DesktopLibraryViewStateTest {
    private fun track(key: String, title: String = key, artist: String = "Artist", album: String = "",
                      plain: String? = null, lrc: String? = null, favorite: Boolean = false) =
        TrackEntity(key, title, artist, album, "test", plainLyrics = plain, syncedLyrics = lrc, isFavorite = favorite)
    private fun view(tracks: List<TrackEntity>, query: String = "", filter: DesktopLibraryFilter = DesktopLibraryFilter.ALL,
                     artist: String? = null, selected: String? = null) =
        buildDesktopLibraryViewState(tracks, query, filter, artist, selected)

    @Test fun emptyDatabaseHasHistoryExplanationRegardlessOfRestrictionsAndNoSelection() {
        val result = view(emptyList(), "missing", DesktopLibraryFilter.FAVORITES, "Ghost", "old")
        assertEquals(DesktopLibraryEmpty.NO_HISTORY, result.empty)
        assertTrue(result.visibleTracks.isEmpty())
        assertNull(result.selectedTrack)
        assertTrue(result.hasRestrictions)
        assertFalse(view(emptyList()).hasRestrictions)
    }

    @Test fun whitespaceQueryDoesNotRestrictOrChangeInputOrder() {
        val tracks = listOf(track("z"), track("a"), track("m"))
        val result = view(tracks, " \n\t ")
        assertFalse(result.hasRestrictions)
        assertEquals(DesktopLibraryEmpty.NONE, result.empty)
        assertEquals(tracks, result.visibleTracks)
        assertEquals(tracks.first(), result.selectedTrack)
    }

    @Test fun querySearchesOnlySpecifiedFieldsWithTrimAndCaseInsensitiveLiteralSubstring() {
        val tracks = listOf(track("title", title = "Свет ЗВЕЗДЫ"), track("artist", artist = "Звезды"),
            track("album", album = "звезды ночи"), track("plain", plain = "сияют звезды"),
            track("timed-only", lrc = "[00:01.00] звезды"), track("hidden-key-звезды", title = "Unrelated title"))
        val result = view(tracks, "  ЗвЕзДы  ")
        assertEquals(listOf("title", "artist", "album", "plain"), result.visibleTracks.map { it.trackKey })
        assertTrue(result.hasRestrictions)
        val punctuation = track("literal", title = "[a.*](song)")
        assertEquals(listOf(punctuation), view(listOf(punctuation), "[a.*]").visibleTracks)
        assertEquals(DesktopLibraryEmpty.NO_MATCHES, view(listOf(punctuation), ".+").empty)
    }

    @Test fun favoritesIntersectSearchAndRetainSourceOrder() {
        val tracks = listOf(track("z", title = "Song Z", favorite = true), track("a", title = "Song A"),
            track("m", title = "Song M", favorite = true), track("other", title = "Other", favorite = true))
        val result = view(tracks, "song", DesktopLibraryFilter.FAVORITES)
        assertEquals(listOf("z", "m"), result.visibleTracks.map { it.trackKey })
        assertTrue(result.hasRestrictions)
    }

    @Test fun karaokeRequiresRealParsedTimestampWhileMalformedTextRemainsSaved() {
        val tracks = listOf(track("valid", lrc = "[00:01.25] Real line"),
            track("malformed", lrc = "[no-time] Some line"), track("blank", plain = " \t", lrc = "\n "),
            track("plain", plain = "Local text"), track("none"))
        assertEquals(listOf("valid"), view(tracks, filter = DesktopLibraryFilter.KARAOKE).visibleTracks.map { it.trackKey })
        assertEquals(listOf("valid", "malformed", "plain"), view(tracks, filter = DesktopLibraryFilter.SAVED_TEXT).visibleTracks.map { it.trackKey })
        assertEquals(4, DesktopLibraryFilter.entries.size)
        assertEquals(listOf("Все", "Избранное", "Караоке", "С сохранённым текстом"), DesktopLibraryFilter.entries.map { it.label })
    }

    @Test fun artistUsesExactIdentityAndIntersectsTextAndFilter() {
        val tracks = listOf(track("a", title = "Needle", artist = "Artist", favorite = true),
            track("b", title = "Needle", artist = "artist", favorite = true),
            track("c", title = "Other", artist = "Artist", favorite = true),
            track("d", title = "Needle", artist = "Artist"))
        val result = view(tracks, "needle", DesktopLibraryFilter.FAVORITES, "Artist")
        assertEquals(listOf("a"), result.visibleTracks.map { it.trackKey })
        assertTrue(view(tracks, artist = "Artist").hasRestrictions)
    }

    @Test fun validSelectionIsRetainedButStaleHiddenAndMissingSelectionFallBackToFirstVisible() {
        val tracks = listOf(track("first", title = "Match one"), track("second", title = "Match two"), track("hidden", title = "Other"))
        assertEquals("second", view(tracks, "match", selected = "second").selectedTrack?.trackKey)
        for (selected in listOf(null, "hidden", "deleted")) {
            assertEquals("first", view(tracks, "match", selected = selected).selectedTrack?.trackKey)
        }
        val refreshed = tracks[1].copy(userNotes = "Fresh persisted notes")
        assertEquals(refreshed, view(listOf(tracks[0], refreshed), selected = "second").selectedTrack)
    }

    @Test fun nonemptyDatabaseWithNoResultsNeverExposesStaleDetailsAndClearingRestoresAll() {
        val tracks = listOf(track("first"), track("second"))
        val missing = view(tracks, "missing", DesktopLibraryFilter.FAVORITES, "Ghost", "first")
        assertEquals(DesktopLibraryEmpty.NO_MATCHES, missing.empty)
        assertTrue(missing.visibleTracks.isEmpty())
        assertNull(missing.selectedTrack)
        assertTrue(missing.hasRestrictions)
        val reset = view(tracks)
        assertEquals(DesktopLibraryEmpty.NONE, reset.empty)
        assertFalse(reset.hasRestrictions)
        assertEquals(tracks, reset.visibleTracks)
        assertEquals(tracks.first(), reset.selectedTrack)
    }
}
