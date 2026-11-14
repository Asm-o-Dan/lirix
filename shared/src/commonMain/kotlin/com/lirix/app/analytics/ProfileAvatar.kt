package com.lirix.app.analytics

import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.TrackMetadataEntity

/**
 * Local abstract profile picture: four independent layers, nothing personal and nothing sent anywhere.
 * Spec: .sdd/specs/media-core/track_metadata_and_avatar_v1.md, section "Аватар".
 */
data class ProfileAvatar(val shape: Int, val pattern: Int, val palette: Int, val accent: Int) {
    init {
        require(shape in 0 until SHAPES && pattern in 0 until PATTERNS && palette in 0 until PALETTES && accent in 0 until ACCENTS)
    }

    fun encode(): String = "$shape-$pattern-$palette-$accent"

    companion object {
        const val SHAPES = 5     // circle, rounded square, hexagon, diamond, octagon
        const val PATTERNS = 5   // rings, waves, rays, equalizer, dots
        const val PALETTES = 8   // see ProfileAvatarPalettes
        const val ACCENTS = 4    // none, note, sun, moon
        val Neutral = ProfileAvatar(0, 0, 7, 0)

        fun decode(value: String?): ProfileAvatar? = runCatching {
            val parts = value.orEmpty().split('-').map { it.toInt() }
            require(parts.size == 4)
            ProfileAvatar(parts[0], parts[1], parts[2], parts[3])
        }.getOrNull()
    }
}

/** Listening facts the automatic avatar is built from. Null means "not known", never zero. */
data class AvatarInput(
    val topGenre: String?,
    val energy: Double?,
    val valence: Double?,
    val uniqueArtists: Int,
    /** Stable fallback when nothing is known about genres: the most listened artist. */
    val topArtist: String?
)

object ProfileAvatarGenerator {
    /** Genre family -> palette. Checked in order; the first family whose word occurs in the genre wins. */
    private val families: List<Pair<Int, List<String>>> = listOf(
        0 to listOf("metal", "rock", "punk", "grunge", "hardcore", "alternative", "indie", "рок", "метал", "панк"),
        1 to listOf("hip-hop", "hip hop", "rap", "trap", "r&b", "хип-хоп", "рэп"),
        2 to listOf("electro", "dance", "house", "techno", "trance", "edm", "drum", "dubstep", "synth", "электрон"),
        3 to listOf("pop", "поп", "disco", "k-pop", "j-pop"),
        4 to listOf("jazz", "blues", "soul", "funk", "swing", "джаз", "блюз"),
        5 to listOf("classical", "orchestra", "opera", "soundtrack", "ambient", "instrumental", "классик", "саундтрек"),
        6 to listOf("folk", "country", "acoustic", "singer", "bard", "world", "reggae", "latin", "chanson", "фолк", "шансон", "авторская", "бард")
    )

    fun paletteForGenre(genre: String?): Int? {
        val value = genre?.lowercase()?.trim().orEmpty()
        if (value.isEmpty()) return null
        return families.firstOrNull { (_, words) -> words.any { it in value } }?.first
    }

    /**
     * The same input always gives the same avatar. Each layer has one documented meaning:
     * palette = main genre family, pattern = average energy, accent = average positivity,
     * shape = how many different artists are listened to. Unknown facts fall back to a stable
     * hash of the top artist, or to the neutral avatar when the history is empty.
     */
    fun fromListening(input: AvatarInput): ProfileAvatar {
        val seed = stableHash(input.topArtist?.lowercase()?.trim().orEmpty())
        if (input.topGenre == null && input.energy == null && input.valence == null && input.topArtist.isNullOrBlank()) return ProfileAvatar.Neutral
        val palette = paletteForGenre(input.topGenre) ?: if (input.topGenre != null) 7 else seed % ProfileAvatar.PALETTES
        val pattern = when (val energy = input.energy) {
            null -> (seed / 8) % ProfileAvatar.PATTERNS
            else -> when {
                energy >= 0.75 -> 2   // rays
                energy >= 0.55 -> 3   // equalizer
                energy >= 0.35 -> 1   // waves
                else -> 0             // rings
            }
        }
        val accent = when (val valence = input.valence) {
            null -> 0
            else -> when {
                valence >= 0.6 -> 2   // sun
                valence <= 0.35 -> 3  // moon
                else -> 1             // note
            }
        }
        val shape = when {
            input.uniqueArtists <= 10 -> 0
            input.uniqueArtists <= 40 -> 1
            input.uniqueArtists <= 120 -> 2
            input.uniqueArtists <= 300 -> 3
            else -> 4
        }
        return ProfileAvatar(shape, pattern, palette, accent)
    }

    private fun stableHash(value: String): Int = value.fold(7) { acc, char -> (acc * 31 + char.code) and 0x7fffffff }
}

/** Genre share by registered listening time, among tracks whose genre is known. */
data class GenreShare(val genre: String, val percent: Int)

data class ListeningProfile(
    val topGenres: List<GenreShare>,
    /** Tracks with a known genre / all listened tracks: shown so the shares are not read as the whole library. */
    val tracksWithGenre: Int,
    val tracksTotal: Int,
    val avatarInput: AvatarInput
)

object ListeningProfileEngine {
    /** Fewer tracks with known data than this and no comparison is shown (too few observations). */
    const val MIN_TRACKS = 5

    fun build(tracks: List<TrackEntity>, metadata: List<TrackMetadataEntity>): ListeningProfile {
        val listened = tracks.filter { it.totalDurationMs > 0 && !WrappedStatsEngine.isUnknownArtist(it.artist) }
        val byKey = metadata.associateBy { it.trackKey }
        val genreTime = LinkedHashMap<String, Long>()
        var withGenre = 0
        var featureWeight = 0L
        var energy = 0.0
        var valenceWeight = 0L
        var valence = 0.0
        listened.sortedBy { it.trackKey }.forEach { track ->
            val meta = byKey[track.trackKey] ?: return@forEach
            meta.genreList().firstOrNull()?.let { genre -> withGenre++; genreTime[genre] = (genreTime[genre] ?: 0L) + track.totalDurationMs }
            meta.energy?.let { energy += it * track.totalDurationMs; featureWeight += track.totalDurationMs }
            meta.valence?.let { valence += it * track.totalDurationMs; valenceWeight += track.totalDurationMs }
        }
        val total = genreTime.values.sum()
        val enough = withGenre >= MIN_TRACKS && total > 0
        // Ties are broken by name so the result does not depend on database order.
        val ranked = genreTime.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        val top = if (enough) ranked.take(3).map { GenreShare(it.key, ((it.value * 100 + total / 2) / total).toInt()) } else emptyList()
        val artistTime = listened.groupBy { it.artist.trim() }.mapValues { entry -> entry.value.sumOf { it.totalDurationMs } }
        val topArtist = artistTime.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key }).firstOrNull()?.key
        return ListeningProfile(top, withGenre, listened.size, AvatarInput(
            topGenre = top.firstOrNull()?.genre,
            energy = if (enough && featureWeight > 0) energy / featureWeight else null,
            valence = if (enough && valenceWeight > 0) valence / valenceWeight else null,
            uniqueArtists = artistTime.size, topArtist = topArtist
        ))
    }
}
