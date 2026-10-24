package com.lirix.app.feature.share.animation

/** Snapshot labels at export start, independent of locale changes while rendering. */
data class CounterRollLabels(
    val header: String,
    val minutes: String,
    val tracks: String,
    val artists: String,
    val topTracks: String,
    val plays: String
) {
    companion object {
        val ENGLISH = CounterRollLabels("Wrapped", "minutes", "tracks", "artists", "Top tracks", "plays")
    }
}
