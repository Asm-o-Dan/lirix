package com.lirix.app.feature

/**
 * Scraped content from MyChords.net containing plain lyrics and formatted chords.
 */
data class MyChordsScrapeResult(
    val plainLyrics: String,
    val chords: String?
)
