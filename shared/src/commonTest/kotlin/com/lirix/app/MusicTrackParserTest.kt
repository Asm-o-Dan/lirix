package com.lirix.app

import com.lirix.app.feature.music.MusicTrackParser
import com.lirix.app.feature.music.ParsedTrackInfo
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

    @Test
    fun test_parseTrackMetadata_realWorldYouTubeAndBrowserFormats() {
        // 1. Reversed Track - Artist format with Official Music Video in brackets
        val r1 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Given Up [Official Music Video] - Linkin Park",
            mediaArtist = "Linkin Park",
            title = "Given Up [Official Music Video] - Linkin Park",
            text = "Linkin Park"
        )
        assertEquals("Linkin Park", r1.artist)
        assertEquals("Given Up", r1.title)

        // 2. Official Music Video in parentheses
        val r2 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "CHAOS (Official Music Video)",
            mediaArtist = "Hollywood Undead",
            title = "CHAOS (Official Music Video)",
            text = "Hollywood Undead"
        )
        assertEquals("Hollywood Undead", r2.artist)
        assertEquals("CHAOS", r2.title)

        // 3. Official Video starring @Channel in parentheses
        val r3 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "ARROW OF LOVE (OFFICIAL VIDEO starring @Kalle)",
            mediaArtist = "Electric Callboy",
            title = "ARROW OF LOVE (OFFICIAL VIDEO starring @Kalle)",
            text = "Electric Callboy"
        )
        assertEquals("Electric Callboy", r3.artist)
        assertEquals("ARROW OF LOVE", r3.title)

        // 4. Visual / Visualizer tag
        val r4 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Time Bomb (Visual)",
            mediaArtist = "Hollywood Undead",
            title = "Time Bomb (Visual)",
            text = "Hollywood Undead"
        )
        assertEquals("Hollywood Undead", r4.artist)
        assertEquals("Time Bomb", r4.title)

        // 5. YouTube channel with VEVO suffix where true artist is in title
        val r5 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Five Finger Death Punch - I Apologize",
            mediaArtist = "5FDPVEVO",
            title = "Five Finger Death Punch - I Apologize",
            text = "5FDPVEVO"
        )
        assertEquals("Five Finger Death Punch", r5.artist)
        assertEquals("I Apologize", r5.title)

        // 6. YouTube title with - YouTube suffix and acronym channel (5FDPVEVO)
        val r6 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Five Finger Death Punch - Wash It All Away (Official Video) - YouTube",
            mediaArtist = "5FDPVEVO",
            title = "Five Finger Death Punch - Wash It All Away (Official Video) - YouTube",
            text = "5FDPVEVO"
        )
        assertEquals("Five Finger Death Punch", r6.artist)
        assertEquals("Wash It All Away", r6.title)

        // 7. Reversed Track - Artist with acronym channel (5FDPVEVO)
        val r7 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Wash It All Away - Five Finger Death Punch",
            mediaArtist = "5FDPVEVO",
            title = "Wash It All Away - Five Finger Death Punch",
            text = "5FDPVEVO"
        )
        assertEquals("Five Finger Death Punch", r7.artist)
        assertEquals("Wash It All Away", r7.title)

        // 8. Label / uploader channel (Century Media Records) with Artist - Track
        val r8 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Arch Enemy - Deceiver, Deceiver (OFFICIAL VIDEO)",
            mediaArtist = "Century Media Records",
            title = "Arch Enemy - Deceiver, Deceiver (OFFICIAL VIDEO)",
            text = "Century Media Records"
        )
        assertEquals("Arch Enemy", r8.artist)
        assertEquals("Deceiver, Deceiver", r8.title)

        // 9. Legitimate artist with title containing Part 2 (must not falsely split)
        val r9 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Another Brick in the Wall - Part 2",
            mediaArtist = "Pink Floyd",
            title = "Another Brick in the Wall - Part 2",
            text = "Pink Floyd"
        )
        assertEquals("Pink Floyd", r9.artist)
        assertEquals("Another Brick in the Wall - Part 2", r9.title)

        // 10. Title without dash and VEVO channel (strips channel suffix)
        val r10 = MusicTrackParser.parseTrackMetadata(
            mediaTrack = "Wash It All Away",
            mediaArtist = "5FDPVEVO",
            title = "Wash It All Away",
            text = "5FDPVEVO"
        )
        assertEquals("5FDP", r10.artist)
        assertEquals("Wash It All Away", r10.title)
    }
}
