package com.eventengine.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import com.eventengine.app.service.FloatingLyricsService
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventengine.app.feature.AggregatedLyricsProvider
import com.eventengine.app.feature.MusicFeatureEngine
import com.eventengine.app.feature.lyrics.AmDmChordParser
import com.eventengine.app.feature.lyrics.ChordAutoscrollCalculator
import com.eventengine.app.feature.lyrics.ChordTransposer
import com.eventengine.app.feature.lyrics.GuitarChordDictionary
import com.eventengine.app.feature.lyrics.GuitarChordDialog
import com.eventengine.app.feature.share.ShareCardGenerator
import com.eventengine.app.feature.share.ShareManager
import com.eventengine.app.ui.components.AnalogTurntable
import com.eventengine.app.ui.components.LrcTapSyncStudioDialog
import com.eventengine.app.ui.components.ShareFormatDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.net.URLEncoder
import com.eventengine.app.feature.FallbackLyricsScraper
import com.eventengine.app.feature.LrcLibLyricsProvider
import com.eventengine.app.feature.LyricsSourceIds
import com.eventengine.app.feature.music.ParsedTrackInfo
import com.eventengine.app.ui.components.rememberAlbumArtBitmap
import com.eventengine.app.ingestion.MediaSessionCollector
import com.eventengine.app.ingestion.NotificationListener
import com.eventengine.app.ingestion.SyncOffsetStore
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.LyricsCacheEntity
import com.eventengine.app.storage.TrackEntity
import com.eventengine.app.ui.components.NotificationPermissionDialog
import com.eventengine.app.ui.theme.AppColors
import androidx.compose.material.icons.filled.Security
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NowPlayingMode(val label: String) {
    KARAOKE("Караоке"),
    LYRICS("Текст"),
    CHORDS("Аккорды"),
    NOTES("Заметки")
}

typealias KaraokeLine = com.eventengine.app.feature.lyrics.KaraokeLine

fun parseLrcLinesStrict(rawLrc: String): List<KaraokeLine> =
    com.eventengine.app.feature.lyrics.LrcSyncEngine.parseLrcLinesStrict(rawLrc)


fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

@Composable
fun NowPlayingScreen(
    initialTrack: ParsedTrackInfo? = null,
    selectedTrack: TrackEntity? = null,
    onTrackConsumed: () -> Unit = {},
    onOpenTeachMode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val engine = remember { MusicFeatureEngine(musicDao = db.musicDao(), lyricsDao = db.lyricsDao()) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isRejectingLyrics by remember { mutableStateOf(false) }
    var hasRejections by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val livePlayback by MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle()
    val isOverlayActive by FloatingLyricsService.isRunning.collectAsStateWithLifecycle()
    val syncOffsetStore = remember { SyncOffsetStore(context) }

    val recentTracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val isLiveActive = livePlayback != null && livePlayback!!.isPlaying

    val currentTrack: TrackEntity? = remember(recentTracks, initialTrack, livePlayback, selectedTrack, isLiveActive) {
        if (isLiveActive) {
            val cleanArtist = livePlayback!!.artist.ifBlank { "Unknown Artist" }
            val cleanAlbum = livePlayback!!.album
            val trackKey = MusicFeatureEngine.computeTrackKey(livePlayback!!.title, cleanArtist, cleanAlbum)
            TrackEntity(
                trackKey = trackKey,
                title = livePlayback!!.title,
                artist = cleanArtist,
                album = cleanAlbum,
                sourcePackage = livePlayback!!.packageName,
                albumArtUri = livePlayback!!.albumArtUri,
                syncedLyrics = recentTracks.find { it.trackKey == trackKey }?.syncedLyrics,
                plainLyrics = recentTracks.find { it.trackKey == trackKey }?.plainLyrics,
                userNotes = recentTracks.find { it.trackKey == trackKey }?.userNotes.orEmpty()
            )
        } else if (selectedTrack != null) {
            selectedTrack
        } else if (livePlayback != null && livePlayback!!.title.isNotBlank()) {
            val cleanArtist = livePlayback!!.artist.ifBlank { "Unknown Artist" }
            val cleanAlbum = livePlayback!!.album
            val trackKey = MusicFeatureEngine.computeTrackKey(livePlayback!!.title, cleanArtist, cleanAlbum)
            recentTracks.find { it.trackKey == trackKey } ?: TrackEntity(
                trackKey = trackKey,
                title = livePlayback!!.title,
                artist = cleanArtist,
                album = cleanAlbum,
                sourcePackage = livePlayback!!.packageName,
                albumArtUri = livePlayback!!.albumArtUri
            )
        } else if (initialTrack != null && initialTrack.title.isNotBlank()) {
            val cleanArtist = initialTrack.artist.ifBlank { "Unknown Artist" }
            val cleanAlbum = initialTrack.album ?: ""
            val trackKey = MusicFeatureEngine.computeTrackKey(initialTrack.title, cleanArtist, cleanAlbum)
            recentTracks.find { it.trackKey == trackKey } ?: TrackEntity(
                trackKey = trackKey,
                title = initialTrack.title,
                artist = cleanArtist,
                album = cleanAlbum,
                sourcePackage = "com.spotify.music"
            )
        } else {
            recentTracks.firstOrNull()
        }
    }

    LaunchedEffect(livePlayback?.isPlaying) {
        if (livePlayback?.isPlaying == true && selectedTrack != null) {
            onTrackConsumed()
        }
    }

    val effectiveAlbumArtUri = remember(livePlayback?.albumArtUri, currentTrack?.albumArtUri, isLiveActive) {
        if (isLiveActive) {
            livePlayback?.albumArtUri?.takeIf { it.isNotBlank() }
                ?: currentTrack?.albumArtUri?.takeIf { it.isNotBlank() }
        } else {
            currentTrack?.albumArtUri?.takeIf { it.isNotBlank() }
                ?: livePlayback?.albumArtUri?.takeIf { it.isNotBlank() }
        }
    }
    val vinylCenterArt = rememberAlbumArtBitmap(effectiveAlbumArtUri)

    var syncOffsetMs by remember(currentTrack?.trackKey) {
        mutableLongStateOf(syncOffsetStore.getOffset(currentTrack?.trackKey.orEmpty()))
    }

    var selectedMode by remember { mutableStateOf(NowPlayingMode.KARAOKE) }
    var lyricsCache by remember { mutableStateOf<LyricsCacheEntity?>(null) }
    var isLoadingLyrics by remember { mutableStateOf(false) }
    var isLoadingChords by remember { mutableStateOf(false) }

    var isEditingNotes by remember { mutableStateOf(false) }
    var notesInputText by remember(currentTrack?.trackKey) {
        mutableStateOf(currentTrack?.userNotes.orEmpty())
    }

    var isAutoscrollRunning by rememberSaveable { mutableStateOf(false) }
    var autoscrollMultiplier by rememberSaveable { mutableFloatStateOf(1.0f) }
    var isTouchPaused by remember { mutableStateOf(false) }
    val chordsScrollState = rememberScrollState()
    var transposeSemitones by rememberSaveable(currentTrack?.trackKey) { mutableIntStateOf(0) }
    var chordTextZoom by rememberSaveable { mutableFloatStateOf(1.0f) }
    var selectedChordDiagram by remember { mutableStateOf<String?>(null) }
    var isVinylCollapsed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentTrack?.trackKey) {
        isEditingNotes = false
        isAutoscrollRunning = false
        isTouchPaused = false
        transposeSemitones = 0
        selectedChordDiagram = null
    }

    var localPlaybackPositionMs by remember { mutableLongStateOf(0L) }
    var localIsPlaying by remember { mutableStateOf(false) }

    val isPlaying = livePlayback?.isPlaying ?: localIsPlaying
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showTapSyncStudio by remember { mutableStateOf(false) }

    var isNotificationPermissionGranted by remember {
        mutableStateOf(NotificationListener.isPermissionGranted(context))
    }
    var showPermissionDialog by rememberSaveable {
        mutableStateOf(!NotificationListener.isPermissionGranted(context))
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = NotificationListener.isPermissionGranted(context)
                isNotificationPermissionGranted = granted
                if (granted) {
                    showPermissionDialog = false
                    try {
                        android.service.notification.NotificationListenerService.requestRebind(
                            android.content.ComponentName(context, NotificationListener::class.java)
                        )
                    } catch (_: Exception) {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showPermissionDialog) {
        NotificationPermissionDialog(
            onDismissRequest = { showPermissionDialog = false },
            onPermissionGrantedCheck = {
                val granted = NotificationListener.isPermissionGranted(context)
                isNotificationPermissionGranted = granted
                if (granted) showPermissionDialog = false
            }
        )
    }

    if (showTapSyncStudio && currentTrack != null) {
        val plainLyricsText = lyricsCache?.plainLyrics ?: currentTrack.plainLyrics.orEmpty()
        LrcTapSyncStudioDialog(
            trackTitle = currentTrack.title,
            artist = currentTrack.artist,
            initialPlainText = plainLyricsText,
            onDismissRequest = { showTapSyncStudio = false },
            onSaveLrc = { generatedLrc ->
                showTapSyncStudio = false
                scope.launch(Dispatchers.IO) {
                    val updatedEntity = LyricsCacheEntity(
                        trackKey = currentTrack.trackKey,
                        plainLyrics = plainLyricsText,
                        syncedLyricsLrc = generatedLrc,
                        chordsAmDm = lyricsCache?.chordsAmDm,
                        userNotes = lyricsCache?.userNotes,
                        provider = "USER:TAP_SYNC",
                        updatedAt = System.currentTimeMillis()
                    )
                    // 1. Dual-Write to lyrics_cache
                    db.lyricsDao().saveLyrics(updatedEntity)
                    // 2. Dual-Write to music_tracks
                    db.musicDao().updateLyrics(currentTrack.trackKey, plainLyricsText, generatedLrc)
                    withContext(Dispatchers.Main) {
                        lyricsCache = updatedEntity
                        selectedMode = NowPlayingMode.KARAOKE
                        snackbarHostState.showSnackbar(
                            message = "Караоке синхронизировано и сохранено!",
                            duration = androidx.compose.material3.SnackbarDuration.Short
                        )
                    }
                }
            }
        )
    }

    if (showShareDialog && currentTrack != null) {
        val trackToShare = currentTrack
        ShareFormatDialog(
            onDismissRequest = { showShareDialog = false },
            onFormatSelected = { format ->
                val artBmp = effectiveAlbumArtUri?.let { uri ->
                    try {
                        val path = uri.removePrefix("file://")
                        val file = File(path)
                        if (file.exists() && file.length() > 0L) BitmapFactory.decodeFile(file.absolutePath) else null
                    } catch (e: Exception) {
                        null
                    }
                }
                val activeLyricLine = run {
                    val lrc = lyricsCache?.syncedLyricsLrc
                    if (!lrc.isNullOrBlank()) {
                        val parsed = parseLrcLinesStrict(lrc)
                        val idx = parsed.indexOfLast { it.timestampMs <= playbackPositionMs }
                        if (idx != -1) parsed[idx].text else parsed.firstOrNull()?.text
                    } else {
                        lyricsCache?.plainLyrics?.lineSequence()?.firstOrNull { it.isNotBlank() }
                    }
                }
                val cardBitmap = ShareCardGenerator.generateNowPlayingCard(
                    context = context,
                    track = trackToShare,
                    currentLyricLine = activeLyricLine,
                    format = format,
                    albumArtBitmap = artBmp
                )
                ShareManager.shareBitmap(
                    context = context,
                    bitmap = cardBitmap,
                    chooserTitle = "Поделиться треком ${trackToShare.title}"
                )
            }
        )
    }

    if (selectedChordDiagram != null) {
        val transposedText = remember(lyricsCache?.chordsAmDm, transposeSemitones) {
            ChordTransposer.transposeText(lyricsCache?.chordsAmDm.orEmpty(), transposeSemitones)
        }
        val currentSongChords = remember(transposedText) {
            GuitarChordDictionary.extractChordsFromText(transposedText)
        }
        GuitarChordDialog(
            initialChordName = selectedChordDiagram!!,
            availableChords = currentSongChords,
            onDismissRequest = { selectedChordDiagram = null }
        )
    }

    // Reset local playback position on track switch if livePlayback is null
    LaunchedEffect(currentTrack?.trackKey) {
        if (livePlayback == null || !livePlayback!!.isPlaying) {
            localPlaybackPositionMs = 0L
            playbackPositionMs = 0L
        }
    }

    // High-precision 60-120 fps interpolation cycle using withFrameMillis
    LaunchedEffect(livePlayback?.isPlaying, livePlayback?.lastPositionUpdateTimeMs, livePlayback?.basePositionMs, syncOffsetMs, localIsPlaying) {
        val snapshot = livePlayback
        if (snapshot != null) {
            while (isActive) {
                withFrameMillis {
                    val raw = snapshot.currentPositionMs()
                    playbackPositionMs = (raw + syncOffsetMs).coerceAtLeast(0L)
                }
            }
        } else if (localIsPlaying) {
            var lastTick = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                withFrameMillis {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val delta = now - lastTick
                    lastTick = now
                    localPlaybackPositionMs += delta
                    playbackPositionMs = (localPlaybackPositionMs + syncOffsetMs).coerceAtLeast(0L)
                }
            }
        }
    }

    // Autoscroll loop for CHORDS tab (TASK-CHR-01)
    LaunchedEffect(isAutoscrollRunning, isTouchPaused, autoscrollMultiplier, livePlayback?.durationMs, chordsScrollState.maxValue) {
        if (!isAutoscrollRunning || isTouchPaused) return@LaunchedEffect

        val durationMs = livePlayback?.durationMs ?: 0L
        val linesCount = lyricsCache?.chordsAmDm?.lines()?.count { it.isNotBlank() } ?: 0
        val totalScrollPx = chordsScrollState.maxValue.toFloat()

        val baseSpeed = ChordAutoscrollCalculator.calculateBaseSpeedPxPerSec(
            durationMs = durationMs,
            linesCount = linesCount,
            totalScrollPx = totalScrollPx
        )
        val effectiveSpeed = ChordAutoscrollCalculator.clampMultiplier(autoscrollMultiplier) * baseSpeed

        var lastFrameNanos = 0L
        while (isActive && isAutoscrollRunning && !isTouchPaused) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val deltaMs = (frameNanos - lastFrameNanos) / 1_000_000L
                    if (deltaMs > 0) {
                        val stepDelta = ChordAutoscrollCalculator.calculateStepDelta(effectiveSpeed, deltaMs)
                        if (chordsScrollState.value >= chordsScrollState.maxValue) {
                            isAutoscrollRunning = false
                        } else {
                            chordsScrollState.dispatchRawDelta(stepDelta)
                        }
                    }
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    // Touch interruption detection for CHORDS autoscroll (TASK-CHR-01)
    LaunchedEffect(chordsScrollState.isScrollInProgress) {
        if (chordsScrollState.isScrollInProgress && isAutoscrollRunning) {
            isTouchPaused = true
        } else if (!chordsScrollState.isScrollInProgress && isTouchPaused && isAutoscrollRunning) {
            delay(2500L)
            isTouchPaused = false
        }
    }

    // Load or fetch lyrics when current track changes
    LaunchedEffect(currentTrack?.trackKey) {
        val track = currentTrack ?: return@LaunchedEffect
        isLoadingLyrics = true
        withContext(Dispatchers.IO) {
            if (db.musicDao().getTrackByKey(track.trackKey) == null) {
                db.musicDao().insertOrUpdateTrack(track)
            }
            val cached = db.lyricsDao().getLyrics(track.trackKey)
            if (cached != null) {
                lyricsCache = cached
                // Синхронизируем с music_tracks, если там было пусто
                if (track.plainLyrics == null && track.syncedLyrics == null) {
                    db.musicDao().updateLyrics(track.trackKey, cached.plainLyrics, cached.syncedLyricsLrc)
                }
                hasRejections = db.lyricsDao().getRejectionsCountForTrack(track.trackKey) > 0
                isLoadingLyrics = false
            } else {
                val rejected = db.lyricsDao().getRejectedSourceIds(track.trackKey).toSet()
                val fetched = AggregatedLyricsProvider().getLyrics(track, rejectedSourceIds = rejected)
                if (fetched.hasLyrics && fetched.sourceId !in rejected) {
                    val plain = fetched.plainLyrics ?: fetched.lyricsText
                    val synced = fetched.syncedLyrics
                    val entity = LyricsCacheEntity(
                        trackKey = track.trackKey,
                        plainLyrics = plain,
                        syncedLyricsLrc = synced,
                        chordsAmDm = fetched.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                        userNotes = track.userNotes,
                        provider = fetched.sourceId.ifBlank { fetched.source }
                    )
                    // 1. Сохраняем в lyrics_cache
                    db.lyricsDao().saveLyrics(entity)
                    // 2. Dual-Write: сохраняем в music_tracks!
                    db.musicDao().updateLyrics(track.trackKey, plain, synced)
                    lyricsCache = entity
                } else {
                    lyricsCache = null
                }
                hasRejections = db.lyricsDao().getRejectionsCountForTrack(track.trackKey) > 0
                isLoadingLyrics = false
            }
        }
    }

    // On-demand async fetch for AmDm chords when switching to CHORDS tab
    LaunchedEffect(selectedMode, currentTrack?.trackKey, lyricsCache?.chordsAmDm) {
        if (selectedMode == NowPlayingMode.CHORDS && currentTrack != null && lyricsCache?.chordsAmDm.isNullOrBlank()) {
            val track = currentTrack ?: return@LaunchedEffect
            isLoadingChords = true
            withContext(Dispatchers.IO) {
                val scraper = FallbackLyricsScraper()
                val cleanArtist = LrcLibLyricsProvider.cleanArtistName(track.artist)
                val cleanTitle = LrcLibLyricsProvider.cleanTrackName(track.title)
                val amdmDetails = scraper.scrapeAmDmDetails(cleanArtist, cleanTitle)
                val parsedChords = amdmDetails?.chords ?: AmDmChordParser.parseAmDmHtml(amdmDetails?.plainLyrics)
                if (!parsedChords.isNullOrBlank()) {
                    val current = lyricsCache
                    val updatedEntity = if (current != null) {
                        current.copy(chordsAmDm = parsedChords)
                    } else {
                        LyricsCacheEntity(
                            trackKey = track.trackKey,
                            plainLyrics = amdmDetails?.plainLyrics ?: track.plainLyrics,
                            syncedLyricsLrc = track.syncedLyrics,
                            chordsAmDm = parsedChords,
                            userNotes = track.userNotes,
                            provider = LyricsSourceIds.AMDM
                        )
                    }
                    db.lyricsDao().saveLyrics(updatedEntity)
                    withContext(Dispatchers.Main) {
                        lyricsCache = updatedEntity
                        isLoadingChords = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoadingChords = false
                    }
                }
            }
        }
    }

    val onSearchWeb: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (onOpenTeachMode != null) {
            onOpenTeachMode()
        } else {
            val query = "${currentTrack?.title.orEmpty()} ${currentTrack?.artist.orEmpty()} текст песни".trim()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"))).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    val onResetRejections: () -> Unit = {
        val trackKey = currentTrack?.trackKey
        if (trackKey != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch(Dispatchers.IO) {
                isLoadingLyrics = true
                val result = engine.clearAllRejections(trackKey)
                lyricsCache = if (result.hasLyrics) {
                    db.lyricsDao().getLyrics(trackKey)
                } else null
                hasRejections = db.lyricsDao().getRejectionsCountForTrack(trackKey) > 0
                isLoadingLyrics = false
                snackbarHostState.showSnackbar(
                    message = if (result.hasLyrics) "Источники сброшены. Загружен ${result.source}" else "Источники сброшены",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val onRetryDefault: () -> Unit = {
        currentTrack?.let { track ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch(Dispatchers.IO) {
                isLoadingLyrics = true
                if (db.musicDao().getTrackByKey(track.trackKey) == null) {
                    db.musicDao().insertOrUpdateTrack(track)
                }
                val rejected = db.lyricsDao().getRejectedSourceIds(track.trackKey).toSet()
                val fetched = AggregatedLyricsProvider().getLyrics(track, rejectedSourceIds = rejected)
                if (fetched.hasLyrics && fetched.sourceId !in rejected) {
                    val plain = fetched.plainLyrics ?: fetched.lyricsText
                    val synced = fetched.syncedLyrics
                    val entity = LyricsCacheEntity(
                        trackKey = track.trackKey,
                        plainLyrics = plain,
                        syncedLyricsLrc = synced,
                        chordsAmDm = fetched.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                        userNotes = track.userNotes,
                        provider = fetched.sourceId.ifBlank { fetched.source }
                    )
                    db.lyricsDao().saveLyrics(entity)
                    db.musicDao().updateLyrics(track.trackKey, plain, synced)
                    lyricsCache = entity
                }
                hasRejections = db.lyricsDao().getRejectionsCountForTrack(track.trackKey) > 0
                isLoadingLyrics = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Track Header & Analog Vinyl Disk
        if (currentTrack != null) {
            Spacer(modifier = Modifier.height(2.dp))

            // Collapsible Analog Turntable with Kinetic Tonearm
            AnalogTurntable(
                isPlaying = isPlaying,
                albumArtBitmap = vinylCenterArt,
                isCollapsed = isVinylCollapsed,
                onToggleCollapse = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isVinylCollapsed = !isVinylCollapsed
                },
                onTogglePlayPause = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val newLocal = !isPlaying
                    localIsPlaying = newLocal
                    MediaSessionCollector.togglePlayPause()
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Track metadata & Favorite toggle (with 38dp mini-disc when vinyl is collapsed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Компактный вращающийся мини-диск (38dp), когда винил свёрнут
                    if (isVinylCollapsed) {
                        val infiniteTransition = rememberInfiniteTransition(label = "MiniVinylSpin")
                        val spinAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 16000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "MiniVinylSpinAngle"
                        )

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AppColors.SurfaceLevel2)
                                .border(
                                    1.2.dp,
                                    Brush.sweepGradient(
                                        listOf(
                                            AppColors.HyperViolet,
                                            AppColors.CyberCyan,
                                            AppColors.HyperVioletDark,
                                            AppColors.HyperViolet
                                        )
                                    ),
                                    CircleShape
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isVinylCollapsed = false
                                }
                                .rotate(if (isPlaying) spinAngle else 0f),
                            contentAlignment = Alignment.Center
                        ) {
                            // Mini Vinyl Grooves
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .border(0.8.dp, AppColors.BorderSubtle.copy(alpha = 0.5f), CircleShape)
                            )
                            // Center label / Art
                            if (vinylCenterArt != null) {
                                Image(
                                    bitmap = vinylCenterArt,
                                    contentDescription = "Развернуть винил",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.AmoledBlack)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Развернуть винил",
                                    tint = AppColors.CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentTrack.artist.ifBlank { "Unknown Artist" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = AppColors.HyperViolet,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isNotificationPermissionGranted) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showPermissionDialog = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AppColors.AmberGold.copy(alpha = 0.18f))
                                .border(1.dp, AppColors.AmberGold, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Настроить доступ к уведомлениям",
                                tint = AppColors.AmberGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Floating Lyrics Overlay Toggle (Track C)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!FloatingLyricsService.isPermissionGranted(context)) {
                                FloatingLyricsService.requestPermission(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Предоставьте разрешение «Поверх других приложений»",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } else {
                                FloatingLyricsService.toggle(context)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isOverlayActive) AppColors.CyberCyan.copy(alpha = 0.18f) else AppColors.SurfaceLevel1)
                            .border(
                                1.dp,
                                if (isOverlayActive) AppColors.CyberCyan else AppColors.BorderSubtle,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Плавающий оверлей",
                            tint = if (isOverlayActive) AppColors.CyberCyan else AppColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { showShareDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AppColors.SurfaceLevel1)
                            .border(1.dp, AppColors.BorderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться треком",
                            tint = AppColors.HyperViolet,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch(Dispatchers.IO) {
                                db.musicDao().setFavorite(currentTrack.trackKey, !currentTrack.isFavorite)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (currentTrack.isFavorite) AppColors.ExpenseContainer else AppColors.SurfaceLevel1)
                            .border(1.dp, if (currentTrack.isFavorite) AppColors.ExpenseRed.copy(alpha = 0.5f) else AppColors.BorderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (currentTrack.isFavorite) AppColors.ExpenseRed else AppColors.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Transport Bar (Time + Scrub bar + Controls, >=48dp touch targets)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.SurfaceLevel1)
                    .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Time elapsed
                Text(
                    text = formatMs(playbackPositionMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = AppColors.CyberCyan
                )

                // Skip -10s (>=48dp touch target)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val targetMs = (playbackPositionMs - 10000L).coerceAtLeast(0L)
                        val audioTargetMs = (targetMs - syncOffsetMs).coerceAtLeast(0L)
                        playbackPositionMs = targetMs
                        localPlaybackPositionMs = targetMs
                        MediaSessionCollector.seekTo(audioTargetMs)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "-10s",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause toggle (52dp focal button)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        localIsPlaying = !isPlaying
                        MediaSessionCollector.togglePlayPause()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AppColors.HyperViolet)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = AppColors.AmoledBlack,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Skip +10s (>=48dp touch target)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val duration = currentTrack.totalDurationMs.takeIf { it > 0L }
                            ?: (livePlayback?.durationMs ?: 0L)
                        val targetMs = if (duration > 0L) {
                            (playbackPositionMs + 10000L).coerceAtMost(duration)
                        } else {
                            playbackPositionMs + 10000L
                        }
                        val audioTargetMs = (targetMs - syncOffsetMs).coerceAtLeast(0L)
                        playbackPositionMs = targetMs
                        localPlaybackPositionMs = targetMs
                        MediaSessionCollector.seekTo(audioTargetMs)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "+10s",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Provider badge & rejection button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Provider badge
                    Surface(
                        color = AppColors.SurfaceLevel2,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AppColors.BorderSubtle)
                    ) {
                        Text(
                            text = lyricsCache?.provider?.uppercase() ?: "AUDIO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Кнопка отклонения "Не тот текст" (>=44dp touch target)
                    val isUserNote = lyricsCache?.provider == LyricsSourceIds.USER_NOTE || lyricsCache?.provider?.startsWith("note:") == true
                    if (lyricsCache != null && !isUserNote && (!lyricsCache!!.plainLyrics.isNullOrBlank() || !lyricsCache!!.syncedLyricsLrc.isNullOrBlank())) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.SurfaceLevel2)
                                .border(BorderStroke(1.dp, AppColors.BorderSubtle), RoundedCornerShape(8.dp))
                                .clickable(enabled = !isRejectingLyrics) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val trackKey = currentTrack.trackKey
                                    val oldProvider = lyricsCache?.provider.orEmpty()
                                    isRejectingLyrics = true

                                    scope.launch(Dispatchers.IO) {
                                        val nextResult = engine.rejectCurrentLyrics(trackKey, oldProvider)

                                        lyricsCache = if (nextResult.hasLyrics) {
                                            db.lyricsDao().getLyrics(trackKey)
                                        } else null
                                        hasRejections = db.lyricsDao().getRejectionsCountForTrack(trackKey) > 0
                                        isRejectingLyrics = false

                                        val snackbarResult = snackbarHostState.showSnackbar(
                                            message = if (nextResult.hasLyrics) {
                                                "Текст заменён на ${nextResult.source}"
                                            } else {
                                                "Источник отклонён. Других текстов нет"
                                            },
                                            actionLabel = "Отменить",
                                            duration = SnackbarDuration.Short
                                        )

                                        if (snackbarResult == SnackbarResult.ActionPerformed) {
                                            val restored = engine.undoLyricsRejection(trackKey, oldProvider)
                                            lyricsCache = if (restored.hasLyrics) {
                                                db.lyricsDao().getLyrics(trackKey)
                                            } else null
                                            hasRejections = db.lyricsDao().getRejectionsCountForTrack(trackKey) > 0
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isRejectingLyrics) {
                                    CircularProgressIndicator(
                                        color = AppColors.HyperViolet,
                                        strokeWidth = 1.5.dp,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Text(
                                    text = "✕ Не тот текст",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AppColors.HyperViolet
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Offset Calibration Bar: [-50мс] | [Тайминг: ±Xмс] | [+50мс] (>=44dp Height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.SurfaceLevel1)
                    .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Button -50мс
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            syncOffsetMs = (syncOffsetMs - SyncOffsetStore.STEP_OFFSET_MS).coerceIn(
                                SyncOffsetStore.MIN_OFFSET_MS,
                                SyncOffsetStore.MAX_OFFSET_MS
                            )
                            currentTrack?.let { syncOffsetStore.setOffset(it.trackKey, syncOffsetMs) }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[-50мс]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = AppColors.CyberCyan
                    )
                }

                // Current offset indicator (Click to reset)
                val offsetPrefix = if (syncOffsetMs > 0) "+" else ""
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            syncOffsetMs = 0L
                            currentTrack?.let { syncOffsetStore.setOffset(it.trackKey, 0L) }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Тайминг: $offsetPrefix${syncOffsetMs}мс",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = if (syncOffsetMs == 0L) AppColors.TextSecondary else AppColors.HyperViolet
                    )
                }

                // Button +50мс
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            syncOffsetMs = (syncOffsetMs + SyncOffsetStore.STEP_OFFSET_MS).coerceIn(
                                SyncOffsetStore.MIN_OFFSET_MS,
                                SyncOffsetStore.MAX_OFFSET_MS
                            )
                            currentTrack?.let { syncOffsetStore.setOffset(it.trackKey, syncOffsetMs) }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[+50мс]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = AppColors.CyberCyan
                    )
                }
            }
        } else {
            if (!isNotificationPermissionGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppColors.SurfaceLevel1)
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        AppColors.AmberGold.copy(alpha = 0.8f),
                                        AppColors.HyperViolet.copy(alpha = 0.8f)
                                    )
                                )
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AppColors.AmberGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AppColors.AmberGold,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Требуется доступ к плееру",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AppColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Чтобы Lirix автоматически подхватывал треки из Spotify, VK или Яндекс Музыки, включите доступ к уведомлениям.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showPermissionDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.HyperViolet),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Настроить доступ к музыке",
                                color = AppColors.AmoledBlack,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Музыка не играет\nВключите трек в любимом плеере",
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mode Switcher Segmented Control (Караоке / Текст / Аккорды / Заметки)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.SurfaceLevel1)
                .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NowPlayingMode.entries.forEach { mode ->
                val isSelected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AppColors.HyperViolet else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedMode = mode
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        ),
                        color = if (isSelected) AppColors.AmoledBlack else AppColors.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mode Content Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.SurfaceLevel1)
                .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingLyrics) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AppColors.HyperViolet,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Загрузка текста и аккордов...",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            } else {
                Crossfade(targetState = selectedMode, label = "ModeContent") { mode ->
                    when (mode) {
                        NowPlayingMode.KARAOKE -> {
                            val hasAnyLyrics = !lyricsCache?.syncedLyricsLrc.isNullOrBlank() || !lyricsCache?.plainLyrics.isNullOrBlank()
                            if (hasAnyLyrics) {
                                HighFidelityKaraokePlayer(
                                    lrcText = lyricsCache?.syncedLyricsLrc,
                                    plainText = lyricsCache?.plainLyrics,
                                    playbackPositionMs = playbackPositionMs,
                                    isPlaying = isPlaying,
                                    onSeekTo = { targetMs ->
                                        val audioTargetMs = (targetMs - syncOffsetMs).coerceAtLeast(0L)
                                        // Мгновенный перенос фокуса и тайминга в UI
                                        playbackPositionMs = targetMs
                                        localPlaybackPositionMs = targetMs
                                        // Перемотка реального трека в плеере
                                        MediaSessionCollector.seekTo(audioTargetMs)
                                    },
                                    onStartTapSync = { showTapSyncStudio = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                ExhaustedLyricsPlaceholder(
                                    onSearchWeb = onSearchWeb,
                                    onResetRejections = onResetRejections,
                                    onRetryDefault = onRetryDefault,
                                    hasRejections = hasRejections
                                )
                            }
                        }
                        NowPlayingMode.LYRICS -> {
                            val text = lyricsCache?.plainLyrics ?: currentTrack?.plainLyrics
                            if (!text.isNullOrBlank()) {
                                PlainLyricsReadingView(
                                    lyricsText = text,
                                    onStartTapSync = { showTapSyncStudio = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                ExhaustedLyricsPlaceholder(
                                    onSearchWeb = onSearchWeb,
                                    onResetRejections = onResetRejections,
                                    onRetryDefault = onRetryDefault,
                                    hasRejections = hasRejections
                                )
                            }
                        }
                        NowPlayingMode.CHORDS -> {
                            val chords = lyricsCache?.chordsAmDm
                            if (!chords.isNullOrBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val displayedChords = remember(chords, transposeSemitones) {
                                        if (transposeSemitones == 0) chords else ChordTransposer.transposeText(chords, transposeSemitones)
                                    }

                                    // Header bar with Autoscroll, Transpose and Zoom controls (TASK-CHR-01, TASK-CHR-02)
                                    Surface(
                                        color = AppColors.SurfaceLevel2,
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Row 1: Title badge + Autoscroll
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Title badge
                                                Text(
                                                    text = "🎸 Аккорды AmDm",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = AppColors.CyberCyan
                                                )

                                                // Autoscroll Toolbar Widget
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    // Play/Pause button [ ▶ Старт / ⏸ Пауза ]
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (isAutoscrollRunning && !isTouchPaused) AppColors.HyperViolet.copy(alpha = 0.2f) else AppColors.SurfaceLevel3,
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            1.dp,
                                                            if (isAutoscrollRunning && !isTouchPaused) AppColors.HyperViolet else AppColors.BorderSubtle
                                                        ),
                                                        modifier = Modifier.clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            isAutoscrollRunning = !isAutoscrollRunning
                                                            if (isAutoscrollRunning) isTouchPaused = false
                                                        }
                                                    ) {
                                                        Text(
                                                            text = if (isAutoscrollRunning && !isTouchPaused) "⏸ Пауза" else if (isAutoscrollRunning && isTouchPaused) "⏳ Пауза" else "▶ Старт",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = if (isAutoscrollRunning) AppColors.HyperViolet else AppColors.CyberCyan,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }

                                                    // Tempo controller: [-] multiplier [+]
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(AppColors.SurfaceLevel3)
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "−",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = if (autoscrollMultiplier > ChordAutoscrollCalculator.MIN_MULTIPLIER) AppColors.TextPrimary else AppColors.TextTertiary,
                                                            modifier = Modifier
                                                                .clickable(enabled = autoscrollMultiplier > ChordAutoscrollCalculator.MIN_MULTIPLIER) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    autoscrollMultiplier = ChordAutoscrollCalculator.nextMultiplier(autoscrollMultiplier, increase = false)
                                                                }
                                                                .padding(horizontal = 4.dp)
                                                        )

                                                        Text(
                                                            text = String.format(java.util.Locale.US, "%.2fx", autoscrollMultiplier),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                                            color = AppColors.ElectricMint,
                                                            modifier = Modifier.padding(horizontal = 4.dp)
                                                        )

                                                        Text(
                                                            text = "+",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = if (autoscrollMultiplier < ChordAutoscrollCalculator.MAX_MULTIPLIER) AppColors.TextPrimary else AppColors.TextTertiary,
                                                            modifier = Modifier
                                                                .clickable(enabled = autoscrollMultiplier < ChordAutoscrollCalculator.MAX_MULTIPLIER) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    autoscrollMultiplier = ChordAutoscrollCalculator.nextMultiplier(autoscrollMultiplier, increase = true)
                                                                }
                                                                .padding(horizontal = 4.dp)
                                                        )
                                                    }

                                                    // Reset to top button [ ⤾ ]
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AppColors.SurfaceLevel3,
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle),
                                                        modifier = Modifier.clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            scope.launch {
                                                                chordsScrollState.animateScrollTo(0)
                                                            }
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "⤾",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = AppColors.TextSecondary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Row 2: Transposition [ ♭ -1 ] [ Тон: ±N ] [ ♯ +1 ] and Zoom [ A- ] [ 100% ] [ A+ ]
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Transpose controller
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(AppColors.SurfaceLevel3)
                                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "♭ -1",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (transposeSemitones > -11) AppColors.TextPrimary else AppColors.TextTertiary,
                                                        modifier = Modifier
                                                            .clickable(enabled = transposeSemitones > -11) {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                transposeSemitones--
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )

                                                    val toneLabel = when {
                                                        transposeSemitones == 0 -> "Тон: 0"
                                                        transposeSemitones > 0 -> "+$transposeSemitones"
                                                        else -> "$transposeSemitones (Капо: ${-transposeSemitones})"
                                                    }
                                                    Text(
                                                        text = toneLabel,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = if (transposeSemitones != 0) AppColors.HyperViolet else AppColors.TextSecondary,
                                                        modifier = Modifier
                                                            .clickable {
                                                                if (transposeSemitones != 0) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    transposeSemitones = 0
                                                                }
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )

                                                    Text(
                                                        text = "♯ +1",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (transposeSemitones < 11) AppColors.TextPrimary else AppColors.TextTertiary,
                                                        modifier = Modifier
                                                            .clickable(enabled = transposeSemitones < 11) {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                transposeSemitones++
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                // Font zoom controller
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(AppColors.SurfaceLevel3)
                                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "A−",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (chordTextZoom > 0.85f) AppColors.TextPrimary else AppColors.TextTertiary,
                                                        modifier = Modifier
                                                            .clickable(enabled = chordTextZoom > 0.85f) {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                chordTextZoom = (chordTextZoom - 0.15f).coerceAtLeast(0.85f)
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )

                                                    Text(
                                                        text = "${(chordTextZoom * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = AppColors.CyberCyan,
                                                        modifier = Modifier
                                                            .clickable {
                                                                if (chordTextZoom != 1.0f) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    chordTextZoom = 1.0f
                                                                }
                                                            }
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )

                                                    Text(
                                                        text = "A+",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (chordTextZoom < 1.6f) AppColors.TextPrimary else AppColors.TextTertiary,
                                                        modifier = Modifier
                                                            .clickable(enabled = chordTextZoom < 1.6f) {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                chordTextZoom = (chordTextZoom + 0.15f).coerceAtMost(1.6f)
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            // Row 3: Song Chords Ribbon (Interactive guitar fingerings)
                                            val songChords = remember(displayedChords) {
                                                GuitarChordDictionary.extractChordsFromText(displayedChords)
                                            }
                                            if (songChords.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    item {
                                                        Text(
                                                            text = "Аппликатуры:",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = AppColors.TextTertiary,
                                                            modifier = Modifier.padding(end = 2.dp)
                                                        )
                                                    }
                                                    items(songChords.size) { idx ->
                                                        val chordName = songChords[idx]
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = AppColors.SurfaceLevel3,
                                                            border = BorderStroke(1.dp, AppColors.BorderSubtle),
                                                            modifier = Modifier.clickable {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                selectedChordDiagram = chordName
                                                            }
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "🎸 ",
                                                                    fontSize = 9.sp
                                                                )
                                                                Text(
                                                                    text = chordName,
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontFamily = FontFamily.Monospace,
                                                                        fontWeight = FontWeight.Bold
                                                                    ),
                                                                    color = AppColors.HyperViolet
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Scrollable chords body
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .weight(1f)
                                            .verticalScroll(chordsScrollState)
                                    ) {
                                        Text(
                                            text = displayedChords,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = (13 * chordTextZoom).sp,
                                                lineHeight = (22 * chordTextZoom).sp
                                            ),
                                            color = AppColors.CyberCyan,
                                            modifier = Modifier.padding(bottom = 32.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (isLoadingChords) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(
                                                color = AppColors.CyberCyan,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Поиск подбора на AmDm...",
                                                color = AppColors.TextSecondary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Аккорды AmDm не найдены для этого трека",
                                            color = AppColors.TextSecondary,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                        NowPlayingMode.NOTES -> {
                            val activeNotes = recentTracks.find { it.trackKey == currentTrack?.trackKey }?.userNotes
                                ?: currentTrack?.userNotes.orEmpty()

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp)
                            ) {
                                // Top Action Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = AppColors.SurfaceLevel2,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
                                    ) {
                                        Text(
                                            text = "📝 Персональные заметки",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = AppColors.ElectricMint,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    if (currentTrack != null) {
                                        if (isEditingNotes) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Cancel button
                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        notesInputText = activeNotes
                                                        isEditingNotes = false
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Отмена",
                                                        tint = AppColors.TextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                // Save button
                                                Surface(
                                                    color = AppColors.ElectricMint.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.ElectricMint),
                                                    modifier = Modifier.clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        val trackKey = currentTrack.trackKey
                                                        val newNotes = notesInputText.trim()
                                                        scope.launch(Dispatchers.IO) {
                                                            db.musicDao().updateUserNotes(trackKey, newNotes)
                                                        }
                                                        isEditingNotes = false
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Сохранить",
                                                            tint = AppColors.ElectricMint,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Сохранить",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = AppColors.ElectricMint
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // Edit button
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    notesInputText = activeNotes
                                                    isEditingNotes = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Редактировать",
                                                    tint = AppColors.ElectricMint,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isEditingNotes) {
                                    OutlinedTextField(
                                        value = notesInputText,
                                        onValueChange = { notesInputText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text(
                                                text = "Запишите гитарный строй, каподастр, любимые строчки или мысли об этой песне...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = AppColors.TextTertiary
                                            )
                                        },
                                        minLines = 6,
                                        maxLines = 15,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = AppColors.SurfaceLevel1,
                                            unfocusedContainerColor = AppColors.SurfaceLevel1,
                                            focusedBorderColor = AppColors.ElectricMint,
                                            unfocusedBorderColor = AppColors.BorderSubtle,
                                            focusedTextColor = AppColors.TextPrimary,
                                            unfocusedTextColor = AppColors.TextPrimary,
                                            cursorColor = AppColors.ElectricMint
                                        )
                                    )
                                } else {
                                    if (activeNotes.isNotBlank()) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    notesInputText = activeNotes
                                                    isEditingNotes = true
                                                },
                                            color = AppColors.SurfaceLevel2,
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
                                        ) {
                                            Text(
                                                text = activeNotes,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    lineHeight = 24.sp,
                                                    fontSize = 14.sp
                                                ),
                                                color = AppColors.TextPrimary,
                                                modifier = Modifier.padding(14.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "Заметок пока нет...",
                                                    color = AppColors.TextSecondary,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Surface(
                                                    color = AppColors.SurfaceLevel2,
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.ElectricMint.copy(alpha = 0.5f)),
                                                    modifier = Modifier.clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        notesInputText = ""
                                                        isEditingNotes = true
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = null,
                                                            tint = AppColors.ElectricMint,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Добавить заметку",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = AppColors.ElectricMint
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * 120Hz-ready High-Fidelity Karaoke Player with centered auto-scrolling,
 * tactile haptic response, neon glow active line, and graceful plain-text fallback.
 */
@Composable
fun HighFidelityKaraokePlayer(
    lrcText: String?,
    plainText: String?,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onStartTapSync: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lines = remember(lrcText) {
        if (!lrcText.isNullOrBlank()) parseLrcLinesStrict(lrcText) else emptyList()
    }

    if (lines.isEmpty()) {
        if (!plainText.isNullOrBlank()) {
            PlainLyricsReadingView(
                lyricsText = plainText,
                onStartTapSync = onStartTapSync,
                modifier = modifier
            )
        } else {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Синхронизированные строки LRC недоступны для этого трека",
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    val activeIndex = remember(lines, playbackPositionMs) {
        val idx = lines.indexOfLast { it.timestampMs <= playbackPositionMs }
        if (idx == -1) 0 else idx
    }

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // Precision centered auto-scrolling
    LaunchedEffect(activeIndex, isPlaying) {
        if (lines.isNotEmpty() && activeIndex in lines.indices) {
            lazyListState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = 0
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Calculate vertical padding to ensure the active line lands directly at the vertical viewport center
        val centerPadding = (maxHeight / 2) - 34.dp

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = centerPadding, bottom = centerPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines, key = { _, line -> line.timestampMs }) { index, line ->
                val isActive = index == activeIndex

                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1.0f else 0.35f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "karaokeLineAlpha"
                )

                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.04f else 1.0f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "karaokeLineScale"
                )

                val itemShape = RoundedCornerShape(16.dp)
                val activeBorderAndBg = if (isActive) {
                    Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    AppColors.HyperVioletGlow,
                                    AppColors.CyberCyanGlow.copy(alpha = 0.18f),
                                    AppColors.HyperVioletGlow
                                )
                            ),
                            shape = itemShape
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(AppColors.HyperViolet, AppColors.CyberCyan)
                            ),
                            shape = itemShape
                        )
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .clip(itemShape)
                        .then(activeBorderAndBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSeekTo(line.timestampMs)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isActive) {
                            Text(
                                text = formatMs(line.timestampMs),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = AppColors.CyberCyan,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Text(
                            text = line.text,
                            style = if (isActive) {
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 30.sp,
                                    letterSpacing = (-0.2).sp
                                )
                            } else {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 24.sp
                                )
                            },
                            color = if (isActive) AppColors.HyperViolet else AppColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(alpha)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium reading sheet for plain text lyrics without LRC timestamps.
 */
@Composable
private fun PlainLyricsReadingView(
    lyricsText: String,
    onStartTapSync: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = AppColors.SurfaceLevel2,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AppColors.CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Режим чтения • LRC таймкоды отсутствуют",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.TextSecondary
                    )
                }
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboard.setText(AnnotatedString(lyricsText))
                    isCopied = true
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy lyrics",
                    tint = if (isCopied) AppColors.ElectricMint else AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (onStartTapSync != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onStartTapSync()
                    },
                color = AppColors.SurfaceLevel2,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AppColors.CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⏱ Создать караоке (Tap-to-Sync)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = AppColors.HyperViolet
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = lyricsText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.2.sp
            ),
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun ExhaustedLyricsPlaceholder(
    onSearchWeb: () -> Unit,
    onResetRejections: () -> Unit,
    onRetryDefault: () -> Unit,
    hasRejections: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = AppColors.TextTertiary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Текст не найден",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = AppColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasRejections) {
                "Все доступные источники были опрошены или отклонены"
            } else {
                "Текст отсутствует в стандартных базах"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка перехода к режиму поиска / обучения
        Button(
            onClick = onSearchWeb,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.HyperViolet),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("🌐 Найти в интернете", color = AppColors.AmoledBlack, fontWeight = FontWeight.Bold)
        }

        if (hasRejections) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onResetRejections,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.CyberCyan)
            ) {
                Text("🔄 Сбросить отклонённые источники", color = AppColors.CyberCyan)
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRetryDefault,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = AppColors.HyperViolet,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Повторить поиск", color = AppColors.TextPrimary)
            }
        }
    }
}
