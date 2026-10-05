package com.lirix.app.service

/**
 * State contract for the Floating Lyrics Overlay view.
 * Spec: TASK-FLT-01, TASK-FLT-02
 */
data class FloatingLyricsState(
    val isExpanded: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUri: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeLineText: String = "",
    val previousLineText: String? = null,
    val nextLineText: String? = null,
    val currentChord: String? = null,
    val hasLyrics: Boolean = false,
    val isSynced: Boolean = false
)
