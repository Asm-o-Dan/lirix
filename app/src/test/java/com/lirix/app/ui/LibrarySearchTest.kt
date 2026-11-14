package com.lirix.app.ui

import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.LyricsCacheEntity
import org.junit.Assert.*
import org.junit.Test

class LibrarySearchTest {
    private val track = TrackEntity("id", "Тёплый ветер", "Исполнитель", album = "Альбом", sourcePackage = "test", plainLyrics = "Устаревшие слова")
    private val cache = LyricsCacheEntity("id", "Мы увидим\nновый берег", "[00:01.00] Мы увидим", "Am G F C\nТекст куплета\n[Dm]Другие слова [G/B]", null, "USER")
    private fun match(query: String, vararg fields: LibrarySearchField) = librarySearchMatch(track, cache, query, fields.toSet(), "Неизвестный")

    @Test fun selectedFieldsUseUnionAndNormalizeText() {
        assertNotNull(match("теплый", LibrarySearchField.TITLE))
        assertNull(match("теплый", LibrarySearchField.ARTIST))
        assertNotNull(match("ИСПОЛНИТЕЛЬ", LibrarySearchField.TITLE, LibrarySearchField.ARTIST))
        assertNotNull(match("увидим новый", LibrarySearchField.TEXT))
        assertNull(match("Устаревшие", LibrarySearchField.TEXT))
        assertNull(match("00:01", LibrarySearchField.TEXT))
    }
    @Test fun chordSearchPreservesSuffixBassAndSequence() {
        assertNotNull(match("Am G", LibrarySearchField.CHORDS))
        assertNotNull(match("Dm G/B", LibrarySearchField.CHORDS))
        assertNull(match("Am7 G", LibrarySearchField.CHORDS))
        assertNull(match("G Dm", LibrarySearchField.CHORDS))
        assertNull(match("Другие", LibrarySearchField.CHORDS))
        assertNull(match("G/Bb", LibrarySearchField.CHORDS))
    }
    @Test fun timedLyricsSearchUsesWordsWithoutTimestampTags() {
        val timed = cache.copy(plainLyrics = null, syncedLyricsLrc = "[00:01.00] Мы увидим\n[00:02.00] новый берег")
        val withoutPlain = track.copy(plainLyrics = null)
        assertNotNull(librarySearchMatch(withoutPlain, timed, "увидим новый", setOf(LibrarySearchField.TEXT), "Unknown"))
        assertNull(librarySearchMatch(withoutPlain, timed, "00:01", setOf(LibrarySearchField.TEXT), "Unknown"))
    }
    @Test fun exactTitleRanksAheadAndAlbumRemainsInAllFields() {
        assertEquals(0, match("Теплый ветер", LibrarySearchField.TITLE)!!.rank)
        assertNull(match("альбом", LibrarySearchField.TITLE))
        assertNotNull(librarySearchMatch(track, cache, "альбом", LibrarySearchField.entries.toSet(), "Неизвестный"))
    }
}
