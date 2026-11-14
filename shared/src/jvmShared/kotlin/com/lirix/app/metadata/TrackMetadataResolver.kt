package com.lirix.app.metadata

import com.lirix.app.feature.lyrics.LyricsSourceHttp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

/** What the player knows about the song. [durationMs] is 0 when the player does not publish it. */
data class TrackQuery(val title: String, val artist: String, val durationMs: Long = 0L)

data class AudioFeatures(
    val bpm: Double? = null, val musicalKey: Int? = null, val musicalMode: Int? = null, val energy: Double? = null,
    val valence: Double? = null, val danceability: Double? = null, val acousticness: Double? = null,
    val instrumentalness: Double? = null, val liveness: Double? = null, val speechiness: Double? = null,
    val loudnessDb: Double? = null
)

data class ResolvedMetadata(
    val genres: List<String> = emptyList(), val releaseYear: Int? = null, val durationMs: Long? = null,
    val isrc: String? = null, val features: AudioFeatures? = null, val tags: List<String> = emptyList(),
    /** Group -> source id, e.g. "genre" -> "itunes". */
    val sources: Map<String, String> = emptyMap()
) {
    val isEmpty: Boolean get() = genres.isEmpty() && releaseYear == null && durationMs == null && features == null && tags.isEmpty()
}

sealed interface MetadataOutcome {
    data class Found(val metadata: ResolvedMetadata) : MetadataOutcome
    /** The catalogue answered and has no such song. */
    data object NotFound : MetadataOutcome
    /** Several different recordings fit; nothing was taken. */
    data object Ambiguous : MetadataOutcome
    /** The first catalogue did not answer; nothing is known and nothing should be stored. */
    data object Unavailable : MetadataOutcome
}

/**
 * Finds extra facts about a song in open catalogues. Nothing but the title and artist reported by
 * the player leaves the device. Spec: .sdd/specs/media-core/track_metadata_and_avatar_v1.md
 *
 * Order: iTunes Search (genre, year, length) -> MusicBrainz (ISRC of the recording with the same
 * length) -> ReccoBeats (audio features by ISRC) and Deezer (tempo and album genres by ISRC) ->
 * Last.fm listener tags when the user supplied their own key. Every later step is optional: a
 * source that does not answer only leaves its fields empty.
 *
 * @param fetch returns the body of a successful HTTPS GET or null for any failure.
 */
class TrackMetadataResolver(
    private val fetch: suspend (String) -> String? = { url -> LyricsSourceHttp.get(url, HOSTS, POLICY) }
) {
    suspend fun resolve(query: TrackQuery, lastFmKey: String? = null): MetadataOutcome {
        val known = query.durationMs.takeIf { it > 0 }
        val catalogue = when (val result = itunes(query, known)) {
            is Step.Failed -> return MetadataOutcome.Unavailable
            is Step.Done -> result.value
        }
        var metadata = catalogue.metadata
        val duration = known ?: metadata.durationMs
        if (duration != null) {
            for (isrc in optional { musicBrainzIsrcs(query, duration) }.orEmpty()) {
                val features = optional { reccoBeats(isrc) }
                val deezer = optional { deezer(query, isrc, duration) }
                if (features == null && deezer == null) continue
                val merged = (features ?: AudioFeatures()).let { if (it.bpm == null) it.copy(bpm = deezer?.bpm) else it }
                metadata = metadata.copy(
                    isrc = isrc,
                    features = merged.takeIf { it != AudioFeatures() },
                    genres = (metadata.genres + deezer?.genres.orEmpty()).distinctBy { it.lowercase() },
                    sources = metadata.sources +
                        listOfNotNull(
                            ("features" to "reccobeats").takeIf { features != null },
                            ("tempo" to "deezer").takeIf { features?.bpm == null && deezer?.bpm != null },
                            ("genre" to listOfNotNull(metadata.sources["genre"], "deezer".takeIf { deezer?.genres?.isNotEmpty() == true })
                                .joinToString("+")).takeIf { deezer?.genres?.isNotEmpty() == true }
                        )
                )
                break
            }
        }
        if (!lastFmKey.isNullOrBlank()) {
            val tags = optional { lastFm(query, lastFmKey.trim()) }.orEmpty()
            if (tags.isNotEmpty()) metadata = metadata.copy(tags = tags, sources = metadata.sources + ("tags" to "lastfm"))
        }
        return when {
            !metadata.isEmpty -> MetadataOutcome.Found(metadata)
            catalogue.ambiguous -> MetadataOutcome.Ambiguous
            else -> MetadataOutcome.NotFound
        }
    }

    private sealed interface Step<out T> {
        data class Done<T>(val value: T) : Step<T>
        data object Failed : Step<Nothing>
    }

    private data class Catalogue(val metadata: ResolvedMetadata, val ambiguous: Boolean)
    private data class Entry(val genre: String?, val year: Int?, val durationMs: Long?)
    private data class DeezerFacts(val bpm: Double?, val genres: List<String>)

    private suspend fun <T> optional(block: suspend () -> T?): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun json(url: String): JsonObject? =
        fetch(url)?.let { body -> runCatching { parser.parseToJsonElement(body) as? JsonObject }.getOrNull() }

    private suspend fun itunes(query: TrackQuery, known: Long?): Step<Catalogue> {
        val root = optional { json("https://itunes.apple.com/search?term=${enc(query.artist + " " + query.title)}&entity=song&limit=25") }
            ?: return Step.Failed
        val matches = root.array("results").mapNotNull { it as? JsonObject }.filter {
            TrackMatch.sameTitle(it.string("trackName").orEmpty(), query.title) && TrackMatch.sameArtist(it.string("artistName").orEmpty(), query.artist)
        }.map { Entry(it.string("primaryGenreName")?.takeIf(String::isNotBlank), it.string("releaseDate")?.take(4)?.toIntOrNull()?.takeIf { y -> y in 1860..2200 },
            it.long("trackTimeMillis")?.takeIf { ms -> ms > 0 }) }
        if (matches.isEmpty()) return Step.Done(Catalogue(ResolvedMetadata(), ambiguous = false))

        // The same recording appears on several albums; entries of the right length are that recording.
        val sameRecording = when {
            known != null -> matches.filter { TrackMatch.sameDuration(it.durationMs, known) }
            matches.all { TrackMatch.sameDuration(it.durationMs, matches.first().durationMs) } -> matches
            else -> emptyList()
        }
        if (sameRecording.isNotEmpty()) {
            val genre = sameRecording.mapNotNull { it.genre }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            return Step.Done(Catalogue(ResolvedMetadata(
                genres = listOfNotNull(genre), releaseYear = sameRecording.mapNotNull { it.year }.minOrNull(),
                durationMs = known ?: sameRecording.first().durationMs,
                sources = buildMap { if (genre != null) put("genre", "itunes"); put("release", "itunes") }
            ), ambiguous = false))
        }
        // Different recordings of one song: the genre is taken only when every one of them agrees on it.
        val genres = matches.mapNotNull { it.genre }.distinct()
        return if (genres.size == 1 && matches.all { it.genre != null }) {
            Step.Done(Catalogue(ResolvedMetadata(genres = genres, sources = mapOf("genre" to "itunes")), ambiguous = false))
        } else Step.Done(Catalogue(ResolvedMetadata(), ambiguous = true))
    }

    private suspend fun musicBrainzIsrcs(query: TrackQuery, durationMs: Long): List<String> {
        val lucene = "recording:\"${escape(query.title)}\" AND artist:\"${escape(TrackMatch.artists(query.artist).firstOrNull() ?: query.artist)}\""
        val root = json("https://musicbrainz.org/ws/2/recording?query=${enc(lucene)}&fmt=json&limit=25") ?: return emptyList()
        return root.array("recordings").mapNotNull { it as? JsonObject }.filter { recording ->
            TrackMatch.sameTitle(recording.string("title").orEmpty(), query.title) &&
                TrackMatch.sameDuration(recording.long("length"), durationMs) &&
                recording.array("artist-credit").any { credit ->
                    val name = (credit as? JsonObject)?.let { it.string("name") ?: (it["artist"] as? JsonObject)?.string("name") }
                    name != null && TrackMatch.sameArtist(name, query.artist)
                }
        }.flatMap { it.array("isrcs").mapNotNull { isrc -> (isrc as? JsonPrimitive)?.contentOrNull } }
            .map { it.trim().uppercase() }.filter { isrcPattern.matches(it) }.distinct().take(3)
    }

    private suspend fun reccoBeats(isrc: String): AudioFeatures? {
        val item = json("https://api.reccobeats.com/v1/audio-features?ids=$isrc")?.array("content")
            ?.mapNotNull { it as? JsonObject }?.firstOrNull { it.string("isrc")?.uppercase() == isrc } ?: return null
        fun unit(name: String) = item.double(name)?.takeIf { it in 0.0..1.0 }
        return AudioFeatures(
            bpm = item.double("tempo")?.takeIf { it in 30.0..300.0 },
            musicalKey = item.int("key")?.takeIf { it in 0..11 },
            musicalMode = item.int("mode")?.takeIf { it in 0..1 },
            energy = unit("energy"), valence = unit("valence"), danceability = unit("danceability"),
            acousticness = unit("acousticness"), instrumentalness = unit("instrumentalness"),
            liveness = unit("liveness"), speechiness = unit("speechiness"),
            loudnessDb = item.double("loudness")?.takeIf { it in -70.0..5.0 }
        ).takeIf { it != AudioFeatures() }
    }

    private suspend fun deezer(query: TrackQuery, isrc: String, durationMs: Long): DeezerFacts? {
        val track = json("https://api.deezer.com/track/isrc:$isrc")?.takeIf { it["error"] == null } ?: return null
        if (track.string("isrc")?.uppercase() != isrc || !TrackMatch.sameTitle(track.string("title").orEmpty(), query.title)) return null
        if (!TrackMatch.sameDuration(track.long("duration")?.times(1000), durationMs + 500)) return null
        val genres = (track["album"] as? JsonObject)?.long("id")?.let { albumId ->
            optional { json("https://api.deezer.com/album/$albumId") }?.takeIf { it["error"] == null }
                ?.let { album -> (album["genres"] as? JsonObject)?.array("data")?.mapNotNull { (it as? JsonObject)?.string("name")?.takeIf(String::isNotBlank) } }
        }.orEmpty()
        return DeezerFacts(track.double("bpm")?.takeIf { it in 30.0..300.0 }, genres.take(4))
    }

    private suspend fun lastFm(query: TrackQuery, key: String): List<String> {
        if (!lastFmKeyPattern.matches(key)) return emptyList()
        val artist = TrackMatch.artists(query.artist).firstOrNull() ?: return emptyList()
        val root = json("https://ws.audioscrobbler.com/2.0/?method=track.gettoptags&artist=${enc(query.artist.trim())}" +
            "&track=${enc(query.title.trim())}&autocorrect=0&format=json&api_key=$key") ?: return emptyList()
        return ((root["toptags"] as? JsonObject)?.array("tag")).orEmpty().mapNotNull { it as? JsonObject }
            .filter { (it.int("count") ?: 0) >= 10 }
            .mapNotNull { it.string("name")?.trim()?.lowercase()?.takeIf { name -> name.length in 2..32 } }
            .filterNot { TrackMatch.sameArtist(it, artist) || TrackMatch.sameTitle(it, query.title) }
            .distinct().take(6)
    }

    companion object {
        val HOSTS = setOf("itunes.apple.com", "musicbrainz.org", "api.reccobeats.com", "api.deezer.com", "ws.audioscrobbler.com")
        private val POLICY = LyricsSourceHttp.Policy(maxBytes = 512 * 1024, totalTimeoutMs = 10_000L)
        private val parser = Json { ignoreUnknownKeys = true; isLenient = true }
        private val isrcPattern = Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")
        val lastFmKeyPattern = Regex("[0-9a-fA-F]{32}")

        private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        private fun escape(value: String) = value.replace(Regex("""([+\-!(){}\[\]^"~*?:\\/]|&&|\|\|)"""), """\\$1""")

        private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.long(name: String) = (this[name] as? JsonPrimitive)?.longOrNull
        private fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.intOrNull
        private fun JsonObject.double(name: String) = (this[name] as? JsonPrimitive)?.doubleOrNull
        private fun JsonObject.array(name: String): List<JsonElement> = (this[name] as? JsonArray).orEmpty()
    }
}
