package com.lirix.app.desktop

import com.lirix.app.metadata.TrackMatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matching rules of .sdd/specs/media-core/track_metadata_and_avatar_v1.md, section "Сопоставление".
 * Expected answers come from the rules, not from running the matcher.
 */
class TrackMatchTest {
    @Test fun sameSongIgnoresCaseDecorationsAndYo() {
        assertTrue(TrackMatch.sameTitle("Nightmare", "nightmare"))
        assertTrue(TrackMatch.sameTitle("Nightmare (Remastered 2011)", "Nightmare"))
        assertTrue(TrackMatch.sameTitle("Nightmare - Remastered 2011", "Nightmare"))
        assertTrue(TrackMatch.sameTitle("Famous (feat. Gadjet)", "Famous"))
        assertTrue(TrackMatch.sameTitle("Ёлка", "елка"))
        assertTrue(TrackMatch.sameTitle("Кукла колдуна (Из сериала «Король и Шут»)", "Кукла колдуна"))
    }

    @Test fun anotherVersionIsAnotherRecording() {
        assertFalse(TrackMatch.sameTitle("Nightmare (Live)", "Nightmare"))
        assertFalse(TrackMatch.sameTitle("Nightmare - Live at Wembley", "Nightmare"))
        assertFalse(TrackMatch.sameTitle("Song (Remix)", "Song"))
        assertFalse(TrackMatch.sameTitle("Song (Acoustic)", "Song (Live)"))
        assertFalse(TrackMatch.sameTitle("Песня (Акустика)", "Песня"))
        assertTrue("the same version on both sides is the same recording", TrackMatch.sameTitle("Song (Live)", "Song [live]"))
    }

    @Test fun differentOrEmptyTitlesDoNotMatch() {
        assertFalse(TrackMatch.sameTitle("Sweet Nightmare", "Nightmare"))
        assertFalse(TrackMatch.sameTitle("Artist - Song", "Song"))
        assertFalse(TrackMatch.sameTitle("", ""))
        assertFalse(TrackMatch.sameTitle("(Live)", "(Live)"))
    }

    @Test fun artistMatchesAcrossScriptsAndCollaborations() {
        assertTrue(TrackMatch.sameArtist("Deuce", "DEUCE"))
        assertTrue(TrackMatch.sameArtist("Король и Шут", "Korol I Shut"))
        assertTrue(TrackMatch.sameArtist("Кино", "Kino"))
        assertTrue(TrackMatch.sameArtist("Hollywood Undead feat. Deuce", "Deuce"))
        assertTrue(TrackMatch.sameArtist("Artist A & Artist B", "Artist B, Artist C"))
    }

    @Test fun otherArtistsDoNotMatch() {
        assertFalse("a longer name is another artist", TrackMatch.sameArtist("Deuce", "Deuce Blake"))
        assertFalse(TrackMatch.sameArtist("Deuce", "LilDeuceDeuce"))
        assertFalse(TrackMatch.sameArtist("Metallica", "Megadeth"))
        assertFalse(TrackMatch.sameArtist("Король и Шут", "Тайм Сквер"))
        assertFalse(TrackMatch.sameArtist("", "Deuce"))
        assertFalse(TrackMatch.sameArtist("Deuce", "   "))
    }

    @Test fun durationToleranceIsThreeSeconds() {
        assertTrue(TrackMatch.sameDuration(261_813, 261_000))
        assertTrue(TrackMatch.sameDuration(200_000, 203_000))
        assertFalse(TrackMatch.sameDuration(200_000, 203_001))
        assertFalse(TrackMatch.sameDuration(261_813, 183_907))
        assertFalse("an unknown length never confirms a match", TrackMatch.sameDuration(null, 261_000))
        assertFalse(TrackMatch.sameDuration(0, 0))
    }
}
