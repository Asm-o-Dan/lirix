package com.lirix.app.ui.components

import androidx.compose.runtime.*
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.feature.lyrics.romanization.*

/** One document request feeds karaoke and text, including kanji-only rows. No conversion in composition. */
@Composable
fun rememberDocumentReadings(trackKey: String?, lrc: String?, plain: String?, mode: RomanizationDisplayMode,
                             language: RomanizationLanguage): Map<String, ReadingResult> {
    val service = remember { ReadingDocumentService() }
    var readings by remember(trackKey, lrc, plain, mode, language) { mutableStateOf<Map<String, ReadingResult>>(emptyMap()) }
    LaunchedEffect(trackKey, lrc, plain, mode, language) {
        if (trackKey == null || mode == RomanizationDisplayMode.ORIGINAL_ONLY) return@LaunchedEffect
        val synced = lrc?.let { LrcSyncEngine.parseLrcLinesStrict(it) }.orEmpty()
        val source = synced.mapIndexed { index, line -> ReadingSourceLine(index, line.timestampMs, line.text) } +
            LrcSyncEngine.sanitizePlainText(plain.orEmpty()).lines().mapIndexed { index, text -> ReadingSourceLine(synced.size + index, 0L, text) }
        readings = service.romanizeDocument(source, language).associate { it.source.text to it.result }
    }
    return readings
}
