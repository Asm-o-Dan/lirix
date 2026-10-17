package com.lirix.app.desktop.ui.components

import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.ui.player.LrcLine

enum class DesktopLyricsMode { TEXT, KARAOKE, CHORDS, ABOUT }
enum class DesktopLyricsContent { IDLE, LOADING, TEXT, KARAOKE, UNSYNCED, UNAVAILABLE, CHORDS, ABOUT }

fun desktopPlainLyrics(lyrics: DesktopLyricsState): String =
    lyrics.plainLyrics?.takeIf { it.isNotBlank() } ?: lyrics.lrcLines.joinToString("\n") { it.text }

fun resolveDesktopLyricsContent(mode: DesktopLyricsMode, hasPlayback: Boolean, lyrics: DesktopLyricsState): DesktopLyricsContent = when {
    mode == DesktopLyricsMode.CHORDS -> DesktopLyricsContent.CHORDS
    mode == DesktopLyricsMode.ABOUT -> DesktopLyricsContent.ABOUT
    !hasPlayback -> DesktopLyricsContent.IDLE
    lyrics.isLoading -> DesktopLyricsContent.LOADING
    mode == DesktopLyricsMode.TEXT -> if (desktopPlainLyrics(lyrics).isNotBlank()) DesktopLyricsContent.TEXT else DesktopLyricsContent.UNAVAILABLE
    lyrics.lrcLines.isNotEmpty() -> DesktopLyricsContent.KARAOKE
    desktopPlainLyrics(lyrics).isNotBlank() || !lyrics.chords.isNullOrBlank() -> DesktopLyricsContent.UNSYNCED
    else -> DesktopLyricsContent.UNAVAILABLE
}

fun activeDesktopLyricIndex(lines: List<LrcLine>, positionMs: Long): Int =
    lines.indexOfLast { it.timestampMs <= positionMs }

fun desktopLyricSeekPosition(timestampMs: Long, syncOffsetMs: Long, durationMs: Long): Long {
    val position = when {
        syncOffsetMs > 0 && timestampMs > Long.MAX_VALUE - syncOffsetMs -> Long.MAX_VALUE
        syncOffsetMs < 0 && timestampMs < Long.MIN_VALUE - syncOffsetMs -> Long.MIN_VALUE
        else -> timestampMs + syncOffsetMs
    }.coerceAtLeast(0)
    return if (durationMs > 0) position.coerceAtMost(durationMs) else position
}
