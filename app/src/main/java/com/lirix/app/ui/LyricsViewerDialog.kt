package com.lirix.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.domain.Event
import com.lirix.app.feature.AggregatedLyricsProvider
import com.lirix.app.feature.LyricsResult
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.feature.MusicTrackParser
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One parsed line from a synced LRC file. */
private data class LrcLine(val timestamp: String, val text: String)

/** Parse raw LRC string into a list of [LrcLine]. Lines without [mm:ss.xx] prefix are skipped. */
private fun parseLrc(raw: String): List<LrcLine> {
    val lineRegex = Regex("""^\[(\d{2}:\d{2})\.\d{2,3}\]\s?(.*)""")
    return raw.lines()
        .mapNotNull { line ->
            val match = lineRegex.matchEntire(line.trim()) ?: return@mapNotNull null
            val ts = match.groupValues[1]   // "01:23"
            val text = match.groupValues[2].trim()
            if (text.isBlank()) null else LrcLine(ts, text)
        }
}

/**
 * Fetches and displays lyrics for a music [event].
 *
 * Displays synced LRC karaoke (with [mm:ss] badges) when available,
 * or plain scrollable text as fallback.
 * Provides "Скопировать" button and source attribution.
 */
@Composable
fun LyricsViewerDialog(
    event: Event,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var isLoading by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<LyricsResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsedInfo = remember(event) { MusicTrackParser.parse(event) }
    val trackTitle = parsedInfo.title
    val artist = parsedInfo.artist
    val album = parsedInfo.album

    LaunchedEffect(event.id) {
        isLoading = true
        errorMessage = null
        result = null

        if (trackTitle.isBlank()) {
            errorMessage = "Не удалось определить название трека"
            isLoading = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val musicDao = db.musicDao()

                val trackKey = MusicFeatureEngine.computeTrackKey(trackTitle, artist, album)

                // Try to load from Room first
                val cached = musicDao.getTrackByKey(trackKey)
                val track = cached ?: TrackEntity(
                    trackKey = trackKey,
                    title = trackTitle,
                    artist = artist,
                    album = album,
                    sourcePackage = event.sourcePackage.ifBlank { "unknown" }
                )

                val provider = AggregatedLyricsProvider(
                    onLyricsDiscovered = { key, plain, synced ->
                        musicDao.updateLyrics(key, plain, synced)
                    }
                )

                val fetchResult = provider.getLyrics(track)
                result = fetchResult
                if (!fetchResult.hasLyrics) {
                    errorMessage = "Текст не найден"
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка загрузки: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = trackTitle.ifBlank { "Текст песни" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    errorMessage != null -> {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }

                    result != null -> {
                        val lyricsResult = result!!

                        // Synced LRC karaoke view
                        if (!lyricsResult.syncedLyrics.isNullOrBlank()) {
                            val lrcLines = remember(lyricsResult.syncedLyrics) {
                                parseLrc(lyricsResult.syncedLyrics!!)
                            }
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(lrcLines) { line ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = line.timestamp,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        } else if (!lyricsResult.plainLyrics.isNullOrBlank()) {
                            // Plain text fallback
                            val scrollState = rememberScrollState()
                            Text(
                                text = lyricsResult.plainLyrics!!,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(scrollState)
                            )
                        } else if (!lyricsResult.lyricsText.isNullOrBlank()) {
                            // Generic lyricsText field fallback
                            val scrollState = rememberScrollState()
                            Text(
                                text = lyricsResult.lyricsText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(scrollState)
                            )
                        }

                        // Source attribution
                        if (!lyricsResult.source.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Источник: ${lyricsResult.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Copy button — only visible when lyrics are available
            val copyText = result?.let { res ->
                res.plainLyrics?.takeIf { it.isNotBlank() }
                    ?: res.syncedLyrics?.let { raw -> parseLrc(raw).joinToString("\n") { it.text } }
                    ?: res.lyricsText.takeIf { it.isNotBlank() }
            }
            if (!copyText.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setText(AnnotatedString(copyText))
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .height(16.dp)
                    )
                    Text("Скопировать")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Закрыть")
            }
        }
    )
}
