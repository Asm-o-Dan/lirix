package com.lirix.app.storage

/**
 * What a "wrong lyrics" / "wrong chords" action removed, kept so the action can be undone.
 * [storedSourceId] is the id written to `lyrics_rejections`; for chords it carries
 * [ContentRejection.CHORDS_PREFIX] so words and chords of one source are rejected separately.
 */
data class RejectedContent(
    val trackKey: String,
    val chords: Boolean,
    val sourceId: String,
    val storedSourceId: String,
    val previousCache: LyricsCacheEntity,
    val previousTrackPlain: String?,
    val previousTrackSynced: String?
)

/**
 * Rules for rejecting automatically found content (AGENTS 7.1, 7.4): only content with a known
 * automatic source can be rejected; anything the user typed, edited, timed in the studio or
 * bound in Teach Mode is never offered for rejection and never removed.
 */
object ContentRejection {
    const val CHORDS_PREFIX = "chords:"
    private val NOT_A_SOURCE = setOf("", "none", "note:user", "cache:room")

    /** Source of the stored words when they may be rejected, otherwise null. */
    fun lyricsSource(cache: LyricsCacheEntity?): String? {
        if (cache == null || isManualLyricsProvider(cache.provider) || cache.provider == "note:user") return null
        if (cache.plainLyrics.isNullOrBlank() && cache.syncedLyricsLrc.isNullOrBlank()) return null
        val source = LyricsAttribution.decode(cache.sourceAttributionJson).lyrics?.sourceId ?: cache.provider
        return source.takeUnless { it in NOT_A_SOURCE || isManualLyricsProvider(it) }
    }

    /** Source of the stored chords when they may be rejected, otherwise null. */
    fun chordsSource(cache: LyricsCacheEntity?): String? {
        if (cache == null || cache.chordsAmDm.isNullOrBlank()) return null
        val source = LyricsAttribution.decode(cache.sourceAttributionJson).chords?.sourceId ?: cache.provider
        return source.takeUnless { it in NOT_A_SOURCE || isManualLyricsProvider(it) }
    }

    /**
     * Sources that must not supply chords for a track: everything rejected for it, plus the
     * sources rejected for chords only.
     */
    fun forChords(rejected: Set<String>): Set<String> =
        rejected + rejected.filter { it.startsWith(CHORDS_PREFIX) }.map { it.removePrefix(CHORDS_PREFIX) }
}
