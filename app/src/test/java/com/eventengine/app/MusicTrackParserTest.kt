package com.eventengine.app

import com.eventengine.app.feature.music.MusicTrackParser
import com.eventengine.app.feature.music.ParsedTrackInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD Unit-tests for MusicTrackParser (TASK-ING-02).
 * Validates extraction of artist, title, cleaning of player suffixes,
 * remix/official tags, and fallback behaviors.
 */
class MusicTrackParserTest {

    @Test
    fun test_parseTrackMetadata_withCleanFields() {
        val result = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Numb",
            mediaArtist = "Linkin Park",
            title = null,
            text = null
        )
        assertEquals("Linkin Park", result.artist)
        assertEquals("Numb", result.title)
    }

    @Test
    fun test_parseTrackMetadata_cleansOfficialVideoAndRemixSuffixes() {
        val result = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Linkin Park - Numb (Official Video)",
            mediaArtist = "Linkin Park",
            title = "Numb (Official Video)",
            text = "Linkin Park"
        )
        assertEquals("Linkin Park", result.artist)
        assertEquals("Numb", result.title)

        val remixResult = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "In The End [Remix]",
            mediaArtist = "Linkin Park",
            title = null,
            text = null
        )
        assertEquals("Linkin Park", remixResult.artist)
        assertEquals("In The End", remixResult.title)
    }

    @Test
    fun test_parseTrackMetadata_cleansPlayerSuffixes() {
        val spotifySuffixResult = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Starboy - Spotify",
            mediaArtist = "The Weeknd",
            title = null,
            text = null
        )
        assertEquals("The Weeknd", spotifySuffixResult.artist)
        assertEquals("Starboy", spotifySuffixResult.title)

        val ytSuffixResult = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Blinding Lights - YouTube",
            mediaArtist = "The Weeknd",
            title = null,
            text = null
        )
        assertEquals("The Weeknd", ytSuffixResult.artist)
        assertEquals("Blinding Lights", ytSuffixResult.title)
    }

    @Test
    fun test_parseTrackMetadata_splitsArtistAndTrackWhenArtistIsBlank() {
        val result = MusicTrackParser.parseTrackMetadata(
            mediaTrack = null,
            mediaArtist = null,
            title = "Кино - Группа крови",
            text = "Яндекс Музыка"
        )
        assertEquals("Кино", result.artist)
        assertEquals("Группа крови", result.title)
    }

    @Test
    fun test_parseTrackMetadata_handlesTrackWithoutArtist() {
        val result = MusicTrackParser.parseTrackMetadata(
            mediaTrack = null,
            mediaArtist = null,
            title = "Track Without Artist",
            text = null
        )
        assertEquals("Unknown Artist", result.artist)
        assertEquals("Track Without Artist", result.title)
    }

    @Test
    fun test_parseTrackMetadata_handlesNullAndEmptyStringsSafely() {
        val result = MusicTrackParser.parseTrackMetadata(
            mediaTrack = null,
            mediaArtist = null,
            title = "",
            text = "   "
        )
        assertEquals("", result.artist)
        assertEquals("", result.title)
    }
}
