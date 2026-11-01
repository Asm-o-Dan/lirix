package com.lirix.app.feature.lyrics

/** Provider data only. Callers decide priority, rejection, caching and manual-edit protection. */
data class AdditionalLyricsResult(
    val sourceId: String,
    val sourceName: String,
    val plainLyrics: String? = null,
    val syncedLyricsLrc: String? = null,
    val chordsText: String? = null,
    val sourceUrl: String? = null,
    val author: String? = null
)
