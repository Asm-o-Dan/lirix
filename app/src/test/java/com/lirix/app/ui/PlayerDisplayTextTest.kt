package com.lirix.app.ui

import com.lirix.app.storage.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlayerDisplayTextTest {
    private val unknown = "Неизвестный исполнитель"
    private fun track(key: String, artist: String, title: String = key, favorite: Boolean = false) =
        TrackEntity(trackKey = key, title = title, artist = artist, sourcePackage = "test", isFavorite = favorite)

    @Test fun storedPlaceholderAndBlankArtistAreLocalizedOnlyForDisplay() {
        assertEquals(unknown, displayArtist("Unknown Artist", unknown))
        assertEquals(unknown, displayArtist(" unknown artist ", unknown))
        assertEquals(unknown, displayArtist("", unknown))
        assertEquals(unknown, displayArtist("   ", unknown))
        assertEquals("Кино", displayArtist("Кино", unknown))
        // A real artist that merely contains the words keeps its name.
        assertEquals("Unknown Artist Band", displayArtist("Unknown Artist Band", unknown))
    }

    @Test fun artistRowsDescribeExactlyTheGivenSelection() {
        val favorites = listOf(
            track("a1", "Кино", favorite = true),
            track("a2", "Кино", favorite = true),
            track("b1", "Unknown Artist", favorite = true),
            track("b2", "", favorite = true),
            track("c1", "Ария", favorite = true)
        )
        val rows = libraryArtistRows(favorites, unknown)
        assertEquals(
            listOf(LibraryArtistRow("Кино", 2), LibraryArtistRow(unknown, 2), LibraryArtistRow("Ария", 1)),
            rows
        )
        // Same list: never more artists than tracks, and per-artist counts add up to the track count.
        assertTrue(rows.size <= favorites.size)
        assertEquals(favorites.size, rows.sumOf { it.trackCount })
        assertEquals(emptyList<LibraryArtistRow>(), libraryArtistRows(emptyList(), unknown))
    }

    @Test fun searchFindsPlaceholderTracksByLocalizedLabelAndKeepsOtherFields() {
        val placeholder = track("p", "Unknown Artist", title = "09")
        val named = track("n", "Кино", title = "Группа крови").copy(album = "Альбом", plainLyrics = "тёплое место")
        assertTrue(libraryTrackMatches(placeholder, "неизвестный", unknown))
        assertTrue(libraryTrackMatches(placeholder, "unknown", unknown))
        assertFalse(libraryTrackMatches(named, "неизвестный", unknown))
        assertTrue(libraryTrackMatches(named, " КРОВИ ", unknown))
        assertTrue(libraryTrackMatches(named, "альбом", unknown))
        assertTrue(libraryTrackMatches(named, "тёплое", unknown))
        assertTrue(libraryTrackMatches(named, "  ", unknown))
        assertFalse(libraryTrackMatches(named, "metallica", unknown))
    }

    @Test fun minutesUseTheLocaleGroupingSeparator() {
        assertTrue(formatGroupedNumber(9363, Locale.forLanguageTag("ru")).matches(Regex("9[\\u00A0\\u202F ]363")))
        assertEquals("9,363", formatGroupedNumber(9363, Locale.ENGLISH))
        assertEquals("999", formatGroupedNumber(999, Locale.forLanguageTag("ru")))
        assertEquals("0", formatGroupedNumber(0, Locale.ENGLISH))
        assertEquals("1,000,000", formatGroupedNumber(1_000_000, Locale.ENGLISH))
    }

    @Test fun inlineActionsFollowWhatTheKaraokeTextAreaShows() {
        val lrc = "[00:01.00]Строка"
        val plain = "Строка"
        val karaoke = NowPlayingMode.KARAOKE
        // Plain text only: the reading view shows edit, studio and the unsynced badge.
        val plainOnly = karaokeInlineLyricsActions(karaoke, false, null, plain, false)
        assertEquals(InlineLyricsActions(edit = true, studio = true), plainOnly)
        assertTrue(plainOnly.unsyncedBadge)
        // Timed lyrics: karaoke view, no inline buttons, menu keeps both items.
        assertEquals(InlineLyricsActions.NONE, karaokeInlineLyricsActions(karaoke, false, lrc, plain, true))
        // LRC text without timed lines falls back to plain reading with studio only.
        assertEquals(InlineLyricsActions(edit = false, studio = true), karaokeInlineLyricsActions(karaoke, false, "garbage", plain, false))
        assertEquals(InlineLyricsActions.NONE, karaokeInlineLyricsActions(karaoke, false, "garbage", null, false))
        // Loading, no lyrics and other tabs show no inline buttons.
        assertEquals(InlineLyricsActions.NONE, karaokeInlineLyricsActions(karaoke, true, null, plain, false))
        assertEquals(InlineLyricsActions.NONE, karaokeInlineLyricsActions(karaoke, false, null, "  ", false))
        assertEquals(InlineLyricsActions.NONE, karaokeInlineLyricsActions(NowPlayingMode.LYRICS, false, null, plain, false))
        assertEquals(InlineLyricsActions.NONE, karaokeInlineLyricsActions(NowPlayingMode.CHORDS, false, null, plain, false))
    }
}
