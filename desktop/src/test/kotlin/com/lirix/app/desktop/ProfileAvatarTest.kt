package com.lirix.app.desktop

import com.lirix.app.analytics.AvatarInput
import com.lirix.app.analytics.GenreShare
import com.lirix.app.analytics.ListeningProfileEngine
import com.lirix.app.analytics.ProfileAvatar
import com.lirix.app.analytics.ProfileAvatarGenerator
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.TrackMetadataEntity
import com.lirix.app.storage.TrackMetadataStatus
import com.lirix.app.ui.components.ProfileAvatarPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Avatar and genre summary rules of .sdd/specs/media-core/track_metadata_and_avatar_v1.md, section "Аватар".
 * Expected layers and percentages are computed by hand from the tables in the spec.
 */
class ProfileAvatarTest {
    private fun track(key: String, artist: String, ms: Long) = TrackEntity(key, "Title $key", artist, sourcePackage = "player", totalDurationMs = ms)
    private fun meta(key: String, genres: String?, energy: Double? = null, valence: Double? = null) =
        TrackMetadataEntity(key, TrackMetadataStatus.FOUND.name, genres = genres, energy = energy, valence = valence, fetchedAt = 1)

    @Test fun storedFormRoundTripsAndRejectsDamage() {
        val avatar = ProfileAvatar(4, 3, 6, 2)
        assertEquals("4-3-6-2", avatar.encode())
        assertEquals(avatar, ProfileAvatar.decode("4-3-6-2"))
        listOf(null, "", "1-2", "1-2-3-4-5", "a-b-c-d", "9-0-0-0", "0-0-8-0", "0-0-0--1").forEach { assertNull(it, ProfileAvatar.decode(it)) }
        assertEquals("every palette index has colours", ProfileAvatar.PALETTES, ProfileAvatarPalettes.all.size)
    }

    @Test fun eachLayerFollowsItsDocumentedFact() {
        fun avatar(genre: String?, energy: Double?, valence: Double?, artists: Int) =
            ProfileAvatarGenerator.fromListening(AvatarInput(genre, energy, valence, artists, "Band"))
        // shape, pattern, palette, accent
        assertEquals(ProfileAvatar(0, 2, 0, 3), avatar("Rock", 0.9, 0.2, 5))
        assertEquals(ProfileAvatar(2, 3, 1, 2), avatar("Hip-Hop/Rap", 0.6, 0.7, 50))
        assertEquals(ProfileAvatar(1, 1, 3, 1), avatar("Pop", 0.4, 0.5, 20))
        assertEquals(ProfileAvatar(4, 0, 4, 0), avatar("Jazz", 0.1, null, 500))
        assertEquals(ProfileAvatar(3, 0, 2, 3), avatar("Electronic", 0.0, 0.0, 200))
        assertEquals("a genre outside every family is neutral, not guessed", 7, avatar("Comedy", 0.5, 0.5, 1).palette)
    }

    @Test fun thresholdsAreInclusiveAsDocumented() {
        fun of(energy: Double = 0.5, valence: Double = 0.5, artists: Int = 1) =
            ProfileAvatarGenerator.fromListening(AvatarInput("Rock", energy, valence, artists, "Band"))
        assertEquals(listOf(2, 3, 3, 1, 1, 0), listOf(0.75, 0.749, 0.55, 0.549, 0.35, 0.349).map { of(energy = it).pattern })
        assertEquals(listOf(2, 1, 1, 3), listOf(0.6, 0.599, 0.351, 0.35).map { of(valence = it).accent })
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4), listOf(10, 11, 40, 41, 120, 121, 300, 301).map { of(artists = it).shape })
    }

    @Test fun genreFamiliesCoverCatalogueNames() {
        val expected = mapOf("Metal" to 0, "Alternative" to 0, "Русский рок" to 0, "Hip-Hop/Rap" to 1, "R&B/Soul" to 1, "Dance" to 2,
            "Electronic" to 2, "K-Pop" to 3, "Blues" to 4, "Soundtrack" to 5, "Classical" to 5, "Singer/Songwriter" to 6, "Country" to 6)
        assertEquals(expected, expected.mapValues { ProfileAvatarGenerator.paletteForGenre(it.key) })
        assertNull(ProfileAvatarGenerator.paletteForGenre("Comedy"))
        assertNull(ProfileAvatarGenerator.paletteForGenre("  "))
        assertNull(ProfileAvatarGenerator.paletteForGenre(null))
    }

    @Test fun unknownFactsNeverInventAMoodAndStayStable() {
        assertEquals(ProfileAvatar.Neutral, ProfileAvatarGenerator.fromListening(AvatarInput(null, null, null, 0, null)))
        val input = AvatarInput(null, null, null, 12, "Unseen Band")
        val first = ProfileAvatarGenerator.fromListening(input)
        assertEquals(first, ProfileAvatarGenerator.fromListening(input.copy(topArtist = "  unseen band ")))
        assertEquals("no positivity data: no sun or moon", 0, first.accent)
        assertEquals(1, first.shape)
    }

    @Test fun genreSharesAreWeightedByListeningTimeAmongKnownTracks() {
        val tracks = listOf(
            track("r1", "A", 300_000), track("r2", "A", 200_000), track("r3", "A", 100_000),
            track("p1", "C", 200_000), track("p2", "C", 100_000),
            track("none", "B", 500_000),                 // listened, genre unknown
            track("unknown", "Unknown Artist", 900_000), // cannot identify a song
            track("zero", "D", 0)                        // never actually listened
        )
        val metadata = listOf(
            meta("r1", "Rock|Metal", energy = 0.8, valence = 0.2), meta("r2", "Rock", energy = 0.6), meta("r3", "Rock"),
            meta("p1", "Pop", energy = 1.0, valence = 0.6), meta("p2", "Pop"),
            meta("unknown", "Comedy", energy = 0.0), meta("gone", "Jazz")
        )
        val profile = ListeningProfileEngine.build(tracks, metadata)
        // Rock 600 s of 900 s, Pop 300 s of 900 s.
        assertEquals(listOf(GenreShare("Rock", 67), GenreShare("Pop", 33)), profile.topGenres)
        assertEquals(5, profile.tracksWithGenre)
        assertEquals(6, profile.tracksTotal)
        assertEquals("Rock", profile.avatarInput.topGenre)
        assertEquals((0.8 * 300 + 0.6 * 200 + 1.0 * 200) / 700, profile.avatarInput.energy!!, 1e-9)
        assertEquals((0.2 * 300 + 0.6 * 200) / 500, profile.avatarInput.valence!!, 1e-9)
        assertEquals(3, profile.avatarInput.uniqueArtists)
        assertEquals("A", profile.avatarInput.topArtist)

        assertEquals("database order must not change the result", profile, ListeningProfileEngine.build(tracks.reversed(), metadata.reversed()))
    }

    @Test fun tooFewKnownTracksGiveNoConclusions() {
        val tracks = (1..4).map { track("t$it", "A", 100_000) } + track("t5", "B", 100_000)
        val profile = ListeningProfileEngine.build(tracks, (1..4).map { meta("t$it", "Rock", energy = 0.9, valence = 0.9) })
        assertTrue(profile.topGenres.isEmpty())
        assertEquals(4, profile.tracksWithGenre)
        assertEquals(5, profile.tracksTotal)
        assertNull(profile.avatarInput.topGenre)
        assertNull(profile.avatarInput.energy)
        assertNull(profile.avatarInput.valence)

        val empty = ListeningProfileEngine.build(emptyList(), emptyList())
        assertEquals(0, empty.tracksTotal)
        assertEquals(ProfileAvatar.Neutral, ProfileAvatarGenerator.fromListening(empty.avatarInput))
    }

    @Test fun equalSharesAreOrderedByName() {
        val tracks = (1..6).map { track("t$it", "A", 100_000) }
        val metadata = (1..3).map { meta("t$it", "Pop") } + (4..6).map { meta("t$it", "Jazz") }
        assertEquals(listOf(GenreShare("Jazz", 50), GenreShare("Pop", 50)), ListeningProfileEngine.build(tracks, metadata).topGenres)
    }
}
