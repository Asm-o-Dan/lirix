package com.example.npc.ingest.media.model

/**
 * Immutable snapshot of audio track metadata extracted from [androidx.media.MediaMetadataCompat].
 *
 * Invariants:
 *  - [durationMs] >= -1L (−1 = unknown duration, e.g. live radio stream).
 *  - If [title] != null, then title.isNotBlank() (blank strings are mapped to null during extraction).
 *  - If [artist] != null, then artist.isNotBlank().
 *  - If [album] != null, then album.isNotBlank().
 */
data class MediaMetadataSnapshot(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long
) {
    init {
        require(durationMs >= -1L) { "durationMs must be >= -1L, got $durationMs" }
        require(title == null || title.isNotBlank()) { "title must be null or non-blank" }
        require(artist == null || artist.isNotBlank()) { "artist must be null or non-blank" }
        require(album == null || album.isNotBlank()) { "album must be null or non-blank" }
    }

    companion object {
        /** Sentinel value meaning duration is unknown (live stream, radio). */
        const val DURATION_UNKNOWN = -1L

        /** Empty snapshot for cases where no metadata is available yet. */
        val EMPTY = MediaMetadataSnapshot(
            title = null,
            artist = null,
            album = null,
            durationMs = DURATION_UNKNOWN
        )
    }
}
