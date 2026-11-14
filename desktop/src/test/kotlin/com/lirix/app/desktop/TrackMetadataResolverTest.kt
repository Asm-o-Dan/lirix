package com.lirix.app.desktop

import com.lirix.app.metadata.MetadataOutcome
import com.lirix.app.metadata.ResolvedMetadata
import com.lirix.app.metadata.TrackMetadataResolver
import com.lirix.app.metadata.TrackQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * The real resolver over answers recorded from the catalogues on 2026-11-14 (trimmed, no lyrics).
 * Expected values are read from the catalogue pages for "Nightmare" by Deuce and from the spec
 * .sdd/specs/media-core/track_metadata_and_avatar_v1.md; only the network transport is replaced.
 */
class TrackMetadataResolverTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/metadata/$name")) { name }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    private class Net(private val routes: (String) -> String?) {
        val urls = mutableListOf<String>()
        val fetch: suspend (String) -> String? = { url -> urls += url; routes(url) }
        fun asked(host: String) = urls.any { URI(it).host == host }
    }

    private val recco = "https://api.reccobeats.com/v1/audio-features?ids=USDPK1700195"
    private val deezerTrack = "https://api.deezer.com/track/isrc:USDPK1700195"
    private val deezerAlbum = "https://api.deezer.com/album/459632385"

    private fun nightmare(override: (String) -> String? = { "" }): (String) -> String? = { url ->
        val custom = override(url)
        when {
            custom == null -> null
            custom.isNotEmpty() -> custom
            url.startsWith("https://itunes.apple.com/search?") -> fixture("itunes-nightmare.json")
            url.startsWith("https://musicbrainz.org/ws/2/recording?") -> fixture("musicbrainz-nightmare.json")
            url == recco -> fixture("reccobeats-USDPK1700195.json")
            url == deezerTrack -> fixture("deezer-track-USDPK1700195.json")
            url == deezerAlbum -> fixture("deezer-album-459632385.json")
            else -> null
        }
    }

    private fun resolve(net: Net, query: TrackQuery, key: String? = null) = runBlocking { TrackMetadataResolver(net.fetch).resolve(query, key) }
    private fun found(outcome: MetadataOutcome): ResolvedMetadata = (outcome as MetadataOutcome.Found).metadata

    @Test fun fullChainCollectsGenreYearAndAudioFeatures() {
        val net = Net(nightmare())
        val data = found(resolve(net, TrackQuery("Nightmare", "Deuce", 261_000)))

        assertEquals(listOf("Rock", "Metal"), data.genres)
        assertEquals(2018, data.releaseYear)
        assertEquals("the player's own length is kept", 261_000L, data.durationMs)
        assertEquals("USDPK1700195", data.isrc)
        val features = requireNotNull(data.features)
        assertEquals(165.887, features.bpm!!, 1e-9)
        assertEquals(7, features.musicalKey)
        assertEquals(1, features.musicalMode)
        assertEquals(0.948, features.energy!!, 1e-9)
        assertEquals(0.145, features.valence!!, 1e-9)
        assertEquals(0.275, features.danceability!!, 1e-9)
        assertEquals(6.25e-05, features.acousticness!!, 1e-12)
        assertEquals(0.0, features.instrumentalness!!, 0.0)
        assertEquals(0.647, features.liveness!!, 1e-9)
        assertEquals(0.24, features.speechiness!!, 1e-9)
        assertEquals(-3.785, features.loudnessDb!!, 1e-9)
        assertEquals(mapOf("genre" to "itunes+deezer", "release" to "itunes", "features" to "reccobeats"), data.sources)
        assertTrue(data.tags.isEmpty())

        // Only HTTPS requests to the documented hosts; no Last.fm request without a key.
        assertTrue(net.urls.all { URI(it).scheme == "https" && URI(it).host in TrackMetadataResolver.HOSTS })
        assertFalse(net.asked("ws.audioscrobbler.com"))
    }

    @Test fun lengthComesFromTheCatalogueWhenThePlayerHidesIt() {
        val data = found(resolve(Net(nightmare()), TrackQuery("Nightmare", "Deuce")))
        assertEquals(261_813L, data.durationMs)
        assertEquals("USDPK1700195", data.isrc)
    }

    @Test fun cyrillicArtistMatchesTransliteratedCatalogueEntryAndSkipsOtherVersions() {
        val net = Net { url ->
            when {
                url.startsWith("https://itunes.apple.com/") -> fixture("itunes-kukla.json")
                url.startsWith("https://musicbrainz.org/") -> fixture("musicbrainz-kukla.json")
                else -> null   // audio sources know nothing about this recording
            }
        }
        val data = found(resolve(net, TrackQuery("Кукла колдуна", "Король и Шут", 203_920)))
        assertEquals(listOf("Rock"), data.genres)
        assertEquals("the live release of 2000 and covers by other artists are not this recording", 1998, data.releaseYear)
        assertNull(data.features)
        assertNull("an ISRC nobody confirmed is not stored", data.isrc)
        assertEquals(mapOf("genre" to "itunes", "release" to "itunes"), data.sources)
    }

    @Test fun liveVersionIsMatchedOnlyWithTheLiveEntry() {
        val net = Net { url -> if (url.startsWith("https://itunes.apple.com/")) fixture("itunes-kukla.json") else null }
        val data = found(resolve(net, TrackQuery("Кукла колдуна (Live)", "Король и Шут")))
        assertEquals(2000, data.releaseYear)
        assertEquals(183_907L, data.durationMs)
    }

    @Test fun silentFirstCatalogueMeansUnavailableNotNotFound() {
        assertEquals(MetadataOutcome.Unavailable, resolve(Net { null }, TrackQuery("Nightmare", "Deuce", 261_000)))
        assertEquals(MetadataOutcome.Unavailable, resolve(Net { "<html>Service unavailable</html>" }, TrackQuery("Nightmare", "Deuce", 261_000)))
        assertEquals(MetadataOutcome.Unavailable, resolve(Net { throw java.io.IOException("offline") }, TrackQuery("Nightmare", "Deuce")))
    }

    @Test fun songOfAnotherArtistIsNotFoundAndNothingElseIsAsked() {
        val net = Net(nightmare())
        assertEquals(MetadataOutcome.NotFound, resolve(net, TrackQuery("Nightmare", "Avenged Sevenfold", 374_000)))
        assertEquals(MetadataOutcome.NotFound, resolve(Net { """{"resultCount":0,"results":[]}""" }, TrackQuery("Unseen Song", "Unseen Band")))
        assertFalse(net.asked("api.reccobeats.com"))
        assertFalse(net.asked("api.deezer.com"))
    }

    private fun itunes(vararg entries: Triple<Long, String, String>) = """{"results":[${entries.joinToString(",") { (ms, genre, date) ->
        """{"artistName":"Band","trackName":"Song","trackTimeMillis":$ms,"primaryGenreName":"$genre","releaseDate":"$date"}"""
    }}]}"""

    @Test fun differentRecordingsWithDifferentGenresAreAmbiguous() {
        val body = itunes(Triple(200_000, "Rock", "2001-01-01T00:00:00Z"), Triple(300_000, "Pop", "2010-01-01T00:00:00Z"))
        assertEquals(MetadataOutcome.Ambiguous, resolve(Net { if ("itunes" in it) body else null }, TrackQuery("Song", "Band")))
        // The player's length tells them apart.
        val data = found(resolve(Net { if ("itunes" in it) body else null }, TrackQuery("Song", "Band", 299_000)))
        assertEquals(listOf("Pop"), data.genres)
        assertEquals(2010, data.releaseYear)
    }

    @Test fun differentRecordingsThatAgreeOnGenreGiveOnlyTheGenre() {
        val body = itunes(Triple(200_000, "Rock", "2001-01-01T00:00:00Z"), Triple(300_000, "Rock", "2010-01-01T00:00:00Z"))
        val data = found(resolve(Net { if ("itunes" in it) body else null }, TrackQuery("Song", "Band")))
        assertEquals(listOf("Rock"), data.genres)
        assertNull("the year of an unknown recording is not guessed", data.releaseYear)
        assertNull(data.durationMs)
    }

    @Test fun failingOptionalSourcesOnlyLeaveTheirFieldsEmpty() {
        val data = found(resolve(Net(nightmare { if ("musicbrainz" in it) null else "" }), TrackQuery("Nightmare", "Deuce", 261_000)))
        assertEquals(listOf("Rock"), data.genres)
        assertEquals(2018, data.releaseYear)
        assertNull(data.isrc)
        assertNull(data.features)
    }

    @Test fun answerAboutAnotherRecordingIsRejected() {
        val foreignIsrc = fixture("reccobeats-USDPK1700195.json").replace("USDPK1700195", "GBAAA0000001")
        val otherSong = fixture("deezer-track-USDPK1700195.json").replace("\"Nightmare\"", "\"Other Song\"")
        val data = found(resolve(Net(nightmare { url -> when (url) { recco -> foreignIsrc; deezerTrack -> otherSong; else -> "" } }),
            TrackQuery("Nightmare", "Deuce", 261_000)))
        assertNull(data.features)
        assertNull(data.isrc)
        assertEquals(listOf("Rock"), data.genres)

        val otherLength = fixture("deezer-track-USDPK1700195.json").replace("\"duration\": 261", "\"duration\": 190")
        val second = found(resolve(Net(nightmare { url -> when (url) { recco -> null; deezerTrack -> otherLength; else -> "" } }),
            TrackQuery("Nightmare", "Deuce", 261_000)))
        assertNull(second.features)
        assertEquals(listOf("Rock"), second.genres)
    }

    @Test fun deezerTempoIsUsedOnlyWhenTheFeatureSourceHasNone() {
        val data = found(resolve(Net(nightmare { if (it == recco) null else "" }), TrackQuery("Nightmare", "Deuce", 261_000)))
        assertEquals(166.04, data.features!!.bpm!!, 1e-9)
        assertNull(data.features!!.energy)
        assertEquals("deezer", data.sources["tempo"])
        assertNull(data.sources["features"])
        assertEquals(listOf("Rock", "Metal"), data.genres)
    }

    @Test fun impossibleValuesAreDropped() {
        val broken = fixture("reccobeats-USDPK1700195.json").replace("\"energy\": 0.948", "\"energy\": 1.7")
            .replace("\"tempo\": 165.887", "\"tempo\": 900").replace("\"key\": 7", "\"key\": 12")
        val features = found(resolve(Net(nightmare { if (it == recco) broken else "" }), TrackQuery("Nightmare", "Deuce", 261_000))).features!!
        assertNull(features.energy)
        assertNull(features.musicalKey)
        assertEquals("the out-of-range tempo is replaced by the other source", 166.04, features.bpm!!, 1e-9)
        assertEquals(0.145, features.valence!!, 1e-9)
    }

    @Test fun listenerTagsNeedAValidKeyAndDropNamesAndRareTags() {
        val tags = """{"toptags":{"tag":[{"name":"Rock","count":100},{"name":"deuce","count":90},{"name":"nu metal","count":50},
            {"name":"Nightmare","count":40},{"name":"seen once","count":3},{"name":"rock","count":20}]}}"""
        val key = "0123456789abcdef0123456789abcdef" // synthetic, not a real key
        val net = Net(nightmare { if ("audioscrobbler" in it) tags else "" })
        val data = found(resolve(net, TrackQuery("Nightmare", "Deuce", 261_000), key))
        assertEquals(listOf("rock", "nu metal"), data.tags)
        assertEquals("lastfm", data.sources["tags"])

        val withoutKey = Net(nightmare { if ("audioscrobbler" in it) tags else "" })
        assertTrue(found(resolve(withoutKey, TrackQuery("Nightmare", "Deuce", 261_000), "not-a-key&artist=x")).tags.isEmpty())
        assertFalse("a malformed key is never put into a URL", withoutKey.asked("ws.audioscrobbler.com"))
    }
}
