package com.lirix.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.feature.lyrics.ChordAutoscrollCalculator
import com.lirix.app.feature.lyrics.ChordTransposer
import com.lirix.app.feature.lyrics.GuitarChordDictionary
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.ingestion.SyncOffsetStore
import com.lirix.app.feature.AggregatedLyricsProvider
import com.lirix.app.feature.lyrics.AmDmChordParser
import com.lirix.app.feature.FallbackLyricsScraper
import com.lirix.app.feature.LrcLibLyricsProvider
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.feature.music.ParsedTrackInfo
import com.lirix.app.feature.share.ShareCardGenerator
import com.lirix.app.feature.share.ShareManager
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.ingestion.NotificationListener
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.ThemePreferencesRepository
import com.lirix.app.connect.AndroidConnectManager
import com.lirix.app.service.FloatingLyricsService
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.connect.DeviceConnectBottomSheet
import com.lirix.app.ui.player.ChordsTabContent
import com.lirix.app.ui.components.AlbumArtImage
import com.lirix.app.ui.components.EditLyricsDialog
import com.lirix.app.ui.components.EmptyPlayerStateView
import com.lirix.app.ui.components.LrcTapSyncStudioDialog
import com.lirix.app.ui.components.NowPlayingOverflowMenu
import com.lirix.app.ui.components.OverlayPermissionDialog
import com.lirix.app.ui.components.PermissionRequestBanner
import com.lirix.app.ui.components.PlayerEmptyStateType
import com.lirix.app.ui.components.ShareFormatDialog
import com.lirix.app.ui.components.ThemeSelectionDialog
import com.lirix.app.ui.components.TimingCalibrationDialog
import com.lirix.app.ui.theme.FontFeature
import com.lirix.app.ui.theme.GolosTextFontFamily
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LiterataFontFamily
import com.lirix.app.ui.theme.toSentenceCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 4 modes of NowPlayingScreen deck (SPEC-UI-MOBILE-2.0 / ADR-023).
 */
enum class NowPlayingMode(val label: String) {
    KARAOKE("Караоке"),
    CHORDS("Аккорды"),
    LYRICS("Текст"),
    NOTES("Заметки")
}

typealias KaraokeLine = com.lirix.app.feature.lyrics.KaraokeLine

fun parseLrcLinesStrict(rawLrc: String): List<KaraokeLine> =
    LrcSyncEngine.parseLrcLinesStrict(rawLrc)

fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

/**
 * Mobile Teleprompter Deck (SPEC-UI-MOBILE-2.0 / ADR-023).
 *
 * Requirements:
 * 1. Fixed heights for control zones + lyrics column on Modifier.weight(1f).
 * 2. Auto-collapse metadata into header on screens < 700dp (360x640), keeping prompter >= 300dp.
 * 3. Focus mode via Double-Tap on prompter (gives up to 72% height / 540dp to text).
 * 4. TalkBack customAction("Скрыть метаданные / Переключить фокус").
 * 5. Interactive seek on long press (blocked if canSeek == false).
 * 6. FLAG_KEEP_SCREEN_ON active on player screen in portrait & landscape; cleared after 5 min pause.
 * 7. Reduced motion: ANIMATOR_DURATION_SCALE == 0 disables springs and animations.
 * 8. Font scale stepper 'Аа' (80%..150%) + delay shift ±0.1s + 4 tabs.
 * 9. Landscape prompter 800x360: 280dp left column with verticalScroll(), 520dp prompter right column.
 * 10. All 5 empty/error states implemented.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowPlayingScreen(
    initialTrack: ParsedTrackInfo? = null,
    selectedTrack: TrackEntity? = null,
    onTrackConsumed: () -> Unit = {},
    onOpenTeachMode: ((TrackEntity?) -> Unit)? = null,
    onOpenBlacklistSettings: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lirixColors = LirixThemeTokens.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    val livePlayback by MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle()
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

    var syncOffsetMs by remember(currentTrack?.trackKey) {
        mutableLongStateOf(syncOffsetStore.getOffset(currentTrack?.trackKey.orEmpty()))
    }

    var selectedMode by rememberSaveable { mutableStateOf(NowPlayingMode.KARAOKE) }
    val pagerState = rememberPagerState(initialPage = selectedMode.ordinal) { NowPlayingMode.entries.size }

    LaunchedEffect(pagerState.currentPage) {
        val newMode = NowPlayingMode.entries.getOrNull(pagerState.currentPage) ?: NowPlayingMode.KARAOKE
        if (selectedMode != newMode) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selectedMode = newMode
        }
    }

    LaunchedEffect(selectedMode) {
        if (pagerState.currentPage != selectedMode.ordinal) {
            pagerState.animateScrollToPage(selectedMode.ordinal)
        }
    }

    var lyricsCache by remember(currentTrack?.trackKey) { mutableStateOf<LyricsCacheEntity?>(null) }
    var isLoadingLyrics by remember(currentTrack?.trackKey) { mutableStateOf(currentTrack != null) }
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

    // Text Scale Stepper 'Аа' (0.8f to 1.5f)
    var textScaleFactor by rememberSaveable { mutableFloatStateOf(1.0f) }

    // Focus Mode (Double-Tap)
    var isFocusMode by rememberSaveable { mutableStateOf(false) }

    // Preferences for one-time hints
    val prefs = remember { context.getSharedPreferences("lirix_ui_prompter_prefs", Context.MODE_PRIVATE) }
    var hasShownFocusHint by remember { mutableStateOf(prefs.getBoolean("hint_focus_shown", false)) }
    var hasShownSeekHint by remember { mutableStateOf(prefs.getBoolean("hint_seek_shown", false)) }
    var keepScreenOnSetting by rememberSaveable { mutableStateOf(prefs.getBoolean("keep_screen_on", true)) }

    // Reduced motion check
    val isReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Playback state and seek availability
    var localPlaybackPositionMs by remember { mutableLongStateOf(0L) }
    var localIsPlaying by remember { mutableStateOf(false) }
    val isPlaying = livePlayback?.isPlaying ?: localIsPlaying
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    val canSeek = (livePlayback != null && livePlayback!!.durationMs > 0L) || localIsPlaying
    val totalTrackDuration = livePlayback?.durationMs ?: 0L

    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    var isNotesFocused by remember { mutableStateOf(false) }
    val isHideControlsForNotes = (selectedMode == NowPlayingMode.NOTES && (isImeVisible || isNotesFocused)) || isImeVisible
    val hasSyncedLyrics = (lyricsCache?.trackKey == currentTrack?.trackKey && !lyricsCache?.syncedLyricsLrc.isNullOrBlank()) || (!isLoadingLyrics && !currentTrack?.syncedLyrics.isNullOrBlank())
    val isKaraokeWithLyrics = selectedMode == NowPlayingMode.KARAOKE && hasSyncedLyrics

    val saveNotes: () -> Unit = {
        val trackKey = currentTrack?.trackKey
        if (trackKey != null) {
            scope.launch(Dispatchers.IO) {
                db.musicDao().updateUserNotes(trackKey, notesInputText)
                val cached = db.lyricsDao().getLyrics(trackKey)
                if (cached != null) {
                    db.lyricsDao().saveLyrics(cached.copy(userNotes = notesInputText, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    // Keep screen on management (FLAG_KEEP_SCREEN_ON) with 5-minute pause timeout
    var pausedDurationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            pausedDurationMs = 0L
            while (isActive && pausedDurationMs < 300_000L) {
                delay(1000L)
                pausedDurationMs += 1000L
            }
        } else {
            pausedDurationMs = 0L
        }
    }

    val shouldKeepScreenOn = keepScreenOnSetting && (isPlaying || pausedDurationMs < 300_000L)
    DisposableEffect(shouldKeepScreenOn) {
        val window = (context as? Activity)?.window
        if (shouldKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // First launch hints
    LaunchedEffect(currentTrack?.trackKey) {
        if (currentTrack != null) {
            if (!hasShownFocusHint) {
                delay(1000L)
                snackbarHostState.showSnackbar(
                    message = "Двойной тап по тексту — режим максимального фокуса",
                    duration = SnackbarDuration.Short
                )
                prefs.edit().putBoolean("hint_focus_shown", true).apply()
                hasShownFocusHint = true
            } else if (!hasShownSeekHint && canSeek) {
                delay(2000L)
                snackbarHostState.showSnackbar(
                    message = "Удерживайте строку для перемотки",
                    duration = SnackbarDuration.Short
                )
                prefs.edit().putBoolean("hint_seek_shown", true).apply()
                hasShownSeekHint = true
            }
        }
    }

    var showShareDialog by remember { mutableStateOf(false) }
    var showTapSyncStudio by remember { mutableStateOf(false) }
    var showEditLyricsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val connectManager = remember { AndroidConnectManager.getInstance(context) }
    var showConnectBottomSheet by remember { mutableStateOf(false) }
    val isLocalPlayback by connectManager.handoffStateMachine.isLocalPlayback.collectAsStateWithLifecycle()
    val isRemotePlaying = !isLocalPlayback
    val isOverlayRunning by FloatingLyricsService.isRunning.collectAsStateWithLifecycle()
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    val themeRepo = remember { ThemePreferencesRepository.getInstance(context) }
    val currentTheme by themeRepo.selectedTheme.collectAsStateWithLifecycle()

    var isNotificationPermissionGranted by remember {
        mutableStateOf(NotificationListener.isPermissionGranted(context))
    }

    // High-precision playback position updates
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

    // Load lyrics from database or online providers
    LaunchedEffect(currentTrack?.trackKey) {
        val track = currentTrack
        if (track == null) {
            lyricsCache = null
            isLoadingLyrics = false
            return@LaunchedEffect
        }
        isLoadingLyrics = true
        lyricsCache = null
        withContext(Dispatchers.IO) {
            try {
                if (db.musicDao().getTrackByKey(track.trackKey) == null) {
                    db.musicDao().insertOrUpdateTrack(track)
                }
                val cached = db.lyricsDao().getLyrics(track.trackKey)
                if (cached != null && (!cached.plainLyrics.isNullOrBlank() || !cached.syncedLyricsLrc.isNullOrBlank())) {
                    lyricsCache = cached
                    isLoadingLyrics = false
                } else {
                    val rejected = db.lyricsDao().getRejectedSourceIds(track.trackKey).toSet()
                    val fetched = AggregatedLyricsProvider(lyricsDao = db.lyricsDao()).getLyrics(track, rejectedSourceIds = rejected)
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
                    } else {
                        lyricsCache = null
                    }
                    isLoadingLyrics = false
                }
            } catch (e: Exception) {
                android.util.Log.w("NowPlayingScreen", "Failed to load lyrics for ${track.trackKey}", e)
                lyricsCache = null
                isLoadingLyrics = false
            }
        }
    }

    // Orientation and Screen arithmetic
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightDp = configuration.screenHeightDp
    val isCompactPortrait = !isLandscape && screenHeightDp < 700

    val sourceLabel = remember(currentTrack?.sourcePackage) {
        when {
            currentTrack?.sourcePackage?.contains("yandex") == true -> "Яндекс Музыке"
            currentTrack?.sourcePackage?.contains("spotify") == true -> "Spotify"
            currentTrack?.sourcePackage?.contains("vk") == true -> "VK Музыке"
            else -> "плеере"
        }
    }

    // System Back handles exiting notes edit mode, exiting focus mode, or navigating back
    BackHandler {
        if (selectedMode == NowPlayingMode.NOTES && (isNotesFocused || isImeVisible)) {
            keyboardController?.hide()
            focusManager.clearFocus()
            isNotesFocused = false
            saveNotes()
        } else if (isFocusMode) {
            isFocusMode = false
        } else {
            onBack()
        }
    }

    if (!isNotificationPermissionGranted) {
        PermissionRequestBanner(
            onPermissionGranted = { isNotificationPermissionGranted = true }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(lirixColors.appBackground)
    ) {
        if (currentTrack == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Верхняя панель управления в пустом состоянии
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lirix",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = lirixColors.textPrimary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showConnectBottomSheet = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DevicesOther,
                                contentDescription = "Подключить устройство (ПК)",
                                tint = if (isRemotePlaying) lirixColors.accentPrimary else lirixColors.textPrimary
                            )
                        }

                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Меню",
                                tint = lirixColors.textSecondary
                            )
                        }
                    }
                }

                EmptyPlayerStateView(
                    stateType = PlayerEmptyStateType.NO_ACTIVE_PLAYER,
                    modifier = Modifier.weight(1f),
                    onConnectClick = { showConnectBottomSheet = true }
                )
            }
        } else if (isLandscape) {
            // =========================================================================
            // TASK-UI-MOB-03: Ландшафтный пульт 800x360 для пюпитра
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(WindowInsets.displayCutout.asPaddingValues())
                    .padding(WindowInsets.navigationBars.asPaddingValues())
            ) {
                // Левая колонка органов управления (280dp) с verticalScroll()
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(lirixColors.surfacePrimary)
                        .border(BorderStroke(1.dp, lirixColors.borderSubtle))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // TopBar: Назад, Источник, Меню
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = lirixColors.textPrimary
                            )
                        }

                        Text(
                            text = "В $sourceLabel ▾",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = lirixColors.accentPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Меню",
                                tint = lirixColors.textSecondary
                            )
                        }
                    }

                    // Блок метаданных
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtImage(
                            artUri = currentTrack.albumArtUri,
                            size = 48.dp,
                            shape = RoundedCornerShape(6.dp),
                            contentDescription = currentTrack.title
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = lirixColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack.artist,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = lirixColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Трекбар времени
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMs(playbackPositionMs),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = FontFeature.tabularFigures()
                                ),
                                color = lirixColors.textSecondary
                            )
                            Text(
                                text = if (totalTrackDuration > 0L) formatMs(totalTrackDuration) else "--:--",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = FontFeature.tabularFigures()
                                ),
                                color = lirixColors.textMuted
                            )
                        }
                        if (totalTrackDuration > 0L) {
                            Slider(
                                value = playbackPositionMs.toFloat().coerceIn(0f, totalTrackDuration.toFloat()),
                                onValueChange = { newPos ->
                                    val target = newPos.toLong()
                                    playbackPositionMs = target
                                    localPlaybackPositionMs = target
                                    MediaSessionCollector.seekTo((target - syncOffsetMs).coerceAtLeast(0L))
                                },
                                valueRange = 0f..totalTrackDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = lirixColors.accentPrimary,
                                    activeTrackColor = lirixColors.accentPrimary,
                                    inactiveTrackColor = lirixColors.borderSubtle
                                ),
                                modifier = Modifier.height(36.dp)
                            )
                        }
                    }

                    // Панель транспорта (Connect, ⏮, ▶ 56x56, ⏭)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = { showConnectBottomSheet = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Воспроизвести на устройстве"
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DevicesOther,
                                    contentDescription = null,
                                    tint = if (isRemotePlaying) lirixColors.accentPrimary else lirixColors.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (isRemotePlaying) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp, end = 8.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaSessionCollector.skipToPrevious()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Предыдущий", tint = lirixColors.textPrimary)
                        }

                        Surface(
                            shape = CircleShape,
                            color = lirixColors.accentPrimary,
                            modifier = Modifier
                                .size(56.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val newPlaying = !isPlaying
                                    localIsPlaying = newPlaying
                                    MediaSessionCollector.togglePlayPause()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                                    tint = lirixColors.appBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaSessionCollector.skipToNext()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, "Следующий", tint = lirixColors.textPrimary)
                        }
                    }

                    // Компактный сдвиг ⏱ и степпер 'Аа' (только в караоке при наличии текста)
                    AnimatedVisibility(visible = isKaraokeWithLyrics) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Сдвиг
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⏱", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "%.1f с".format(syncOffsetMs / 1000f),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFeatureSettings = FontFeature.tabularFigures()
                                    ),
                                    color = lirixColors.textSecondary
                                )
                                IconButton(
                                    onClick = {
                                        syncOffsetMs -= 100L
                                        syncOffsetStore.setOffset(currentTrack.trackKey, syncOffsetMs)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("−", fontWeight = FontWeight.Bold, color = lirixColors.textPrimary)
                                }
                                IconButton(
                                    onClick = {
                                        syncOffsetMs += 100L
                                        syncOffsetStore.setOffset(currentTrack.trackKey, syncOffsetMs)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, color = lirixColors.textPrimary)
                                }
                            }

                            // Степпер Аа
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Аа", style = MaterialTheme.typography.labelSmall, color = lirixColors.textSecondary)
                                IconButton(
                                    onClick = { textScaleFactor = (textScaleFactor - 0.1f).coerceIn(0.8f, 1.5f) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("−", fontWeight = FontWeight.Bold, color = lirixColors.textPrimary)
                                }
                                IconButton(
                                    onClick = { textScaleFactor = (textScaleFactor + 0.1f).coerceIn(0.8f, 1.5f) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, color = lirixColors.textPrimary)
                                }
                            }
                        }
                    }

                    // Селектор режимов (Караоке │ Аккорды │ Текст │ Зам.)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(lirixColors.surfaceCard)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        NowPlayingMode.entries.forEach { mode ->
                            val isSelected = selectedMode == mode
                            val shortLabel = if (mode == NowPlayingMode.NOTES) "Зам." else mode.label
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) lirixColors.accentPrimary else Color.Transparent)
                                    .clickable { selectedMode = mode }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) lirixColors.appBackground else lirixColors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Правая колонка суфлёра (520dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    RenderTeleprompterContent(
                        selectedMode = selectedMode,
                        lyricsCache = lyricsCache,
                        currentTrack = currentTrack,
                        isLoadingLyrics = isLoadingLyrics,
                        playbackPositionMs = playbackPositionMs,
                        isPlaying = isPlaying,
                        syncOffsetMs = syncOffsetMs,
                        canSeek = canSeek,
                        fontScale = textScaleFactor,
                        isLandscape = true,
                        isReducedMotion = isReducedMotion,
                        onSeekTo = { targetMs ->
                            playbackPositionMs = targetMs
                            localPlaybackPositionMs = targetMs
                            MediaSessionCollector.seekTo((targetMs - syncOffsetMs).coerceAtLeast(0L))
                        },
                        onStartTapSync = { showTapSyncStudio = true },
                        onEditLyrics = { showEditLyricsDialog = true },
                        onOpenTeachMode = { onOpenTeachMode?.invoke(currentTrack) },
                        transposeSemitones = transposeSemitones,
                        onTransposeChange = { transposeSemitones = it },
                        chordsScrollState = chordsScrollState,
                        notesText = notesInputText,
                        onNotesChange = { notesInputText = it },
                        onSaveNotes = saveNotes,
                        onNotesFocusChange = { isNotesFocused = it },
                        totalTrackDuration = totalTrackDuration
                    )
                }
            }
        } else {
            // =========================================================================
            // TASK-UI-MOB-02: Автономный NowPlayingScreen (Портрет 360x800)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. TopBar (48dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "В медиатеку",
                            tint = lirixColors.textPrimary
                        )
                    }

                    val headerText = if (isCompactPortrait || isFocusMode) {
                        "${currentTrack.title} — ${currentTrack.artist} ▾"
                    } else {
                        "В $sourceLabel ▾"
                    }

                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = lirixColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (selectedMode == NowPlayingMode.NOTES && (isImeVisible || isNotesFocused)) {
                        TextButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                isNotesFocused = false
                                saveNotes()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Готово",
                                tint = lirixColors.accentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Готово",
                                color = lirixColors.accentPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showConnectBottomSheet = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DevicesOther,
                                    contentDescription = "Подключить устройство (ПК)",
                                    tint = if (isRemotePlaying) lirixColors.accentPrimary else lirixColors.textSecondary
                                )
                            }

                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Меню",
                                    tint = lirixColors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 2. Блок метаданных (64dp): скрывается при автосжатии (<700dp), в режиме «Фокус» или при редактировании заметок
                AnimatedVisibility(visible = !isCompactPortrait && !isFocusMode && !isHideControlsForNotes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtImage(
                            artUri = currentTrack.albumArtUri,
                            size = 48.dp,
                            shape = RoundedCornerShape(6.dp),
                            contentDescription = currentTrack.title
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = lirixColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${currentTrack.artist}${if (currentTrack.album.isNotBlank()) ", «${currentTrack.album}»" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = lirixColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isLoadingLyrics -> "Поиск текста..."
                                    lyricsCache?.trackKey == currentTrack.trackKey && !lyricsCache?.syncedLyricsLrc.isNullOrBlank() -> "Текст: синхронно"
                                    lyricsCache?.trackKey == currentTrack.trackKey && !lyricsCache?.plainLyrics.isNullOrBlank() -> "Текст: статично"
                                    else -> "Текст не найден"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = if (isLoadingLyrics) lirixColors.textSecondary else lirixColors.accentPrimary
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch(Dispatchers.IO) {
                                    db.musicDao().setFavorite(currentTrack.trackKey, !currentTrack.isFavorite)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Избранное",
                                tint = if (currentTrack.isFavorite) lirixColors.accentSecondary else lirixColors.textSecondary
                            )
                        }
                    }
                }

                // 3. Колонка лирики (Суфлёр) — Modifier.weight(1f)
                // Поддерживает Двойной тап (Double-Tap) для включения режима «Фокус»
                // и семантику TalkBack customAction
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Скрыть метаданные / Переключить фокус") {
                                    isFocusMode = !isFocusMode
                                    true
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isFocusMode = !isFocusMode
                                }
                            )
                        }
                        .padding(horizontal = 16.dp)
                ) {
                    RenderTeleprompterContent(
                        selectedMode = selectedMode,
                        lyricsCache = lyricsCache,
                        currentTrack = currentTrack,
                        isLoadingLyrics = isLoadingLyrics,
                        playbackPositionMs = playbackPositionMs,
                        isPlaying = isPlaying,
                        syncOffsetMs = syncOffsetMs,
                        canSeek = canSeek,
                        fontScale = textScaleFactor,
                        isLandscape = false,
                        isReducedMotion = isReducedMotion,
                        onSeekTo = { targetMs ->
                            playbackPositionMs = targetMs
                            localPlaybackPositionMs = targetMs
                            MediaSessionCollector.seekTo((targetMs - syncOffsetMs).coerceAtLeast(0L))
                        },
                        onStartTapSync = { showTapSyncStudio = true },
                        onEditLyrics = { showEditLyricsDialog = true },
                        onOpenTeachMode = { onOpenTeachMode?.invoke(currentTrack) },
                        transposeSemitones = transposeSemitones,
                        onTransposeChange = { transposeSemitones = it },
                        chordsScrollState = chordsScrollState,
                        notesText = notesInputText,
                        onNotesChange = { notesInputText = it },
                        onSaveNotes = saveNotes,
                        onNotesFocusChange = { isNotesFocused = it },
                        totalTrackDuration = totalTrackDuration
                    )
                }

                // 4. Трекбар времени (48dp): скрывается в режиме «Фокус» и при редактировании заметок
                AnimatedVisibility(visible = !isFocusMode && !isHideControlsForNotes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMs(playbackPositionMs),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = FontFeature.tabularFigures()
                            ),
                            color = lirixColors.textSecondary
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (totalTrackDuration > 0L) {
                            Slider(
                                value = playbackPositionMs.toFloat().coerceIn(0f, totalTrackDuration.toFloat()),
                                onValueChange = { newPos ->
                                    val target = newPos.toLong()
                                    playbackPositionMs = target
                                    localPlaybackPositionMs = target
                                    MediaSessionCollector.seekTo((target - syncOffsetMs).coerceAtLeast(0L))
                                },
                                valueRange = 0f..totalTrackDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = lirixColors.accentPrimary,
                                    activeTrackColor = lirixColors.accentPrimary,
                                    inactiveTrackColor = lirixColors.borderSubtle
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(lirixColors.borderSubtle)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (totalTrackDuration > 0L) formatMs(totalTrackDuration) else "--:--",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = FontFeature.tabularFigures()
                            ),
                            color = lirixColors.textMuted
                        )
                    }
                }

                // 5. Контекстная строка сдвига и размера текста (48dp): только для караоке с синхронизацией
                AnimatedVisibility(visible = isKaraokeWithLyrics && !isFocusMode && !isHideControlsForNotes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Сдвиг текста
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Сдвиг %.1f с".format(syncOffsetMs / 1000f),
                                style = MaterialTheme.typography.bodySmall,
                                color = lirixColors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = lirixColors.surfaceCard,
                                border = BorderStroke(1.dp, lirixColors.borderSubtle),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        syncOffsetMs -= 100L
                                        syncOffsetStore.setOffset(currentTrack.trackKey, syncOffsetMs)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("−", fontWeight = FontWeight.Bold, color = lirixColors.textPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = lirixColors.surfaceCard,
                                border = BorderStroke(1.dp, lirixColors.borderSubtle),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        syncOffsetMs += 100L
                                        syncOffsetStore.setOffset(currentTrack.trackKey, syncOffsetMs)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("+", fontWeight = FontWeight.Bold, color = lirixColors.textPrimary)
                                }
                            }
                        }

                        // Степпер Аа
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Аа ${(textScaleFactor * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = lirixColors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = lirixColors.surfaceCard,
                                border = BorderStroke(1.dp, lirixColors.borderSubtle),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        textScaleFactor = (textScaleFactor - 0.1f).coerceIn(0.8f, 1.5f)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("−", fontWeight = FontWeight.Bold, color = lirixColors.textPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = lirixColors.surfaceCard,
                                border = BorderStroke(1.dp, lirixColors.borderSubtle),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        textScaleFactor = (textScaleFactor + 0.1f).coerceIn(0.8f, 1.5f)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("+", fontWeight = FontWeight.Bold, color = lirixColors.textPrimary)
                                }
                            }
                        }
                    }
                }

                // 6. Селектор режимов (48dp): Караоке │ Аккорды │ Текст │ Заметки
                AnimatedVisibility(visible = !isHideControlsForNotes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(lirixColors.pillCornerRadius))
                            .background(lirixColors.surfaceCard)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NowPlayingMode.entries.forEach { mode ->
                            val isSelected = selectedMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(lirixColors.pillCornerRadius))
                                    .background(if (isSelected) lirixColors.accentPrimary else Color.Transparent)
                                    .clickable { selectedMode = mode },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) lirixColors.appBackground else lirixColors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 7. Панель транспорта (56dp)
                AnimatedVisibility(visible = !isHideControlsForNotes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка Lirix Connect с бейджем активности
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = { showConnectBottomSheet = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Воспроизвести на устройстве"
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DevicesOther,
                                    contentDescription = null,
                                    tint = if (isRemotePlaying) lirixColors.accentPrimary else lirixColors.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (isRemotePlaying) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp, end = 8.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaSessionCollector.skipToPrevious()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Предыдущий",
                                tint = lirixColors.textPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = lirixColors.accentPrimary,
                            modifier = Modifier
                                .size(56.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val newPlaying = !isPlaying
                                    localIsPlaying = newPlaying
                                    MediaSessionCollector.togglePlayPause()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                                    tint = lirixColors.appBackground,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaSessionCollector.skipToNext()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Следующий",
                                tint = lirixColors.textPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Кнопка «Поделиться»
                        IconButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Поделиться карточкой"
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = lirixColors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // 8. Нижний буфер (8dp)
                if (!isHideControlsForNotes) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp)
        )

        if (showOverflowMenu) {
            NowPlayingOverflowMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
                isOverlayActive = isOverlayRunning,
                onConnectClick = {
                    showOverflowMenu = false
                    showConnectBottomSheet = true
                },
                onEditLyricsClick = {
                    showOverflowMenu = false
                    showEditLyricsDialog = true
                },
                onToggleOverlay = {
                    showOverflowMenu = false
                    if (!FloatingLyricsService.isPermissionGranted(context)) {
                        showOverlayPermissionDialog = true
                    } else {
                        val willEnable = !isOverlayRunning
                        FloatingLyricsService.toggle(context)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (willEnable) "Плавающий оверлей включён" else "Плавающий оверлей выключен",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onCalibrateClick = {
                    showOverflowMenu = false
                    showCalibrationDialog = true
                },
                onThemeSelectClick = {
                    showOverflowMenu = false
                    showThemeDialog = true
                },
                onTeachModeClick = {
                    showOverflowMenu = false
                    currentTrack?.let { onOpenTeachMode?.invoke(it) }
                },
                onOpenBlacklistSettings = {
                    showOverflowMenu = false
                    onOpenBlacklistSettings()
                },
                onShareClick = {
                    showOverflowMenu = false
                    if (currentTrack != null) {
                        showShareDialog = true
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Сначала включите воспроизведение трека")
                        }
                    }
                }
            )
        }

        if (showOverlayPermissionDialog) {
            OverlayPermissionDialog(
                onDismissRequest = { showOverlayPermissionDialog = false },
                onOpenSettings = {
                    showOverlayPermissionDialog = false
                    FloatingLyricsService.requestPermission(context)
                }
            )
        }

        if (showCalibrationDialog && currentTrack != null) {
            val track = currentTrack
            TimingCalibrationDialog(
                currentOffsetMs = syncOffsetMs,
                onOffsetChange = { newOffset ->
                    syncOffsetMs = newOffset.coerceIn(SyncOffsetStore.MIN_OFFSET_MS, SyncOffsetStore.MAX_OFFSET_MS)
                    syncOffsetStore.setOffset(track.trackKey, syncOffsetMs)
                },
                onResetOffset = {
                    syncOffsetMs = 0L
                    syncOffsetStore.setOffset(track.trackKey, 0L)
                },
                onDismissRequest = { showCalibrationDialog = false }
            )
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = currentTheme,
                onThemeSelected = { newTheme -> themeRepo.setTheme(newTheme) },
                onDismissRequest = { showThemeDialog = false }
            )
        }

        if (showTapSyncStudio && currentTrack != null) {
            val track = currentTrack
            val plainLyricsText = lyricsCache?.plainLyrics ?: track.plainLyrics.orEmpty()
            LrcTapSyncStudioDialog(
                trackTitle = track.title,
                artist = track.artist,
                initialPlainText = plainLyricsText,
                onDismissRequest = { showTapSyncStudio = false },
                onSaveLrc = { generatedLrc ->
                    showTapSyncStudio = false
                    scope.launch(Dispatchers.IO) {
                        val updatedEntity = LyricsCacheEntity(
                            trackKey = track.trackKey,
                            plainLyrics = plainLyricsText,
                            syncedLyricsLrc = generatedLrc,
                            chordsAmDm = lyricsCache?.chordsAmDm,
                            userNotes = lyricsCache?.userNotes,
                            provider = "USER:TAP_SYNC",
                            updatedAt = System.currentTimeMillis()
                        )
                        db.lyricsDao().saveLyrics(updatedEntity)
                        db.musicDao().updateLyrics(track.trackKey, plainLyricsText, generatedLrc)
                        withContext(Dispatchers.Main) {
                            lyricsCache = updatedEntity
                            selectedMode = NowPlayingMode.KARAOKE
                            snackbarHostState.showSnackbar("Караоке синхронизировано и сохранено!")
                        }
                    }
                }
            )
        }

        if (showEditLyricsDialog && currentTrack != null) {
            val track = currentTrack
            val plainLyricsText = lyricsCache?.plainLyrics ?: track.plainLyrics.orEmpty()
            EditLyricsDialog(
                trackTitle = track.title,
                artist = track.artist,
                initialLyrics = plainLyricsText,
                onDismissRequest = { showEditLyricsDialog = false },
                onSaveLyrics = { updatedLyrics ->
                    showEditLyricsDialog = false
                    scope.launch(Dispatchers.IO) {
                        val cached = db.lyricsDao().getLyrics(track.trackKey)
                        val updatedEntity = (cached ?: LyricsCacheEntity(
                            trackKey = track.trackKey,
                            plainLyrics = updatedLyrics,
                            syncedLyricsLrc = track.syncedLyrics,
                            chordsAmDm = null,
                            userNotes = track.userNotes,
                            provider = "USER:EDIT"
                        )).copy(
                            plainLyrics = updatedLyrics,
                            updatedAt = System.currentTimeMillis()
                        )
                        db.lyricsDao().saveLyrics(updatedEntity)
                        db.musicDao().updateLyrics(track.trackKey, updatedLyrics, updatedEntity.syncedLyricsLrc)
                        withContext(Dispatchers.Main) {
                            lyricsCache = updatedEntity
                            snackbarHostState.showSnackbar("Текст песни сохранён")
                        }
                    }
                }
            )
        }

        if (showShareDialog && currentTrack != null) {
            val track = currentTrack
            ShareFormatDialog(
                onDismissRequest = { showShareDialog = false },
                onFormatSelected = { format ->
                    val exportTheme = com.lirix.app.feature.share.PosterThemeSnapshot.fromColors(lirixColors)
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
                    val artBmp = com.lirix.app.feature.share.PosterArtworkLoader.load(context, track.albumArtUri)
                    val cardBitmap = withContext(Dispatchers.Default) { ShareCardGenerator.generateNowPlayingCard(
                        context = context,
                        track = track,
                        currentLyricLine = activeLyricLine,
                        format = format,
                        albumArtBitmap = artBmp,
                        theme = exportTheme
                    ) }
                    check(ShareManager.shareBitmap(
                        context = context,
                        bitmap = cardBitmap,
                        chooserTitle = "Поделиться треком ${track.title}"
                    )) { "Не удалось открыть меню отправки" }
                }
            )
        }

        if (showConnectBottomSheet) {
            DeviceConnectBottomSheet(
                onDismissRequest = { showConnectBottomSheet = false },
                currentTrackTitle = currentTrack?.title ?: "Lirix Connect",
                currentTrackArtist = currentTrack?.artist ?: "Беспроводное сопряжение",
                playbackPositionMs = playbackPositionMs,
                connectManager = connectManager
            )
        }
    }
}

/**
 * Shared teleprompter content renderer across portrait and landscape decks.
 */
@Composable
private fun RenderTeleprompterContent(
    selectedMode: NowPlayingMode,
    lyricsCache: LyricsCacheEntity?,
    currentTrack: TrackEntity,
    isLoadingLyrics: Boolean,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    syncOffsetMs: Long,
    canSeek: Boolean,
    fontScale: Float,
    isLandscape: Boolean,
    isReducedMotion: Boolean,
    onSeekTo: (Long) -> Unit,
    onStartTapSync: () -> Unit,
    onEditLyrics: () -> Unit = {},
    onOpenTeachMode: () -> Unit,
    transposeSemitones: Int,
    onTransposeChange: (Int) -> Unit,
    chordsScrollState: androidx.compose.foundation.ScrollState,
    notesText: String,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit = {},
    onNotesFocusChange: (Boolean) -> Unit = {},
    totalTrackDuration: Long = 0L
) {
    val tokens = LirixThemeTokens.colors

    if (selectedMode == NowPlayingMode.NOTES) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Личные заметки к песне",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.textSecondary
                )
                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onNotesFocusChange(false)
                        onSaveNotes()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Готово",
                        tint = tokens.accentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Готово",
                        color = tokens.accentPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            OutlinedTextField(
                value = notesText,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        onNotesFocusChange(focusState.isFocused)
                    },
                placeholder = {
                    Text("Личные заметки к песне (тональность, каподастр, структура)...")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = tokens.surfacePrimary,
                    unfocusedContainerColor = tokens.surfacePrimary,
                    focusedTextColor = tokens.textPrimary,
                    unfocusedTextColor = tokens.textPrimary,
                    focusedBorderColor = tokens.accentPrimary,
                    unfocusedBorderColor = tokens.borderSubtle
                )
            )
        }
        return
    }

    if (isLoadingLyrics) {
        EmptyPlayerStateView(
            stateType = PlayerEmptyStateType.SEARCHING_LYRICS,
            trackTitle = currentTrack.title,
            trackArtist = currentTrack.artist,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val validCache = if (lyricsCache?.trackKey == currentTrack.trackKey) lyricsCache else null
    val effectivePlain = validCache?.plainLyrics ?: currentTrack.plainLyrics
    val effectiveSynced = validCache?.syncedLyricsLrc ?: currentTrack.syncedLyrics

    when (selectedMode) {
        NowPlayingMode.KARAOKE -> {
            val hasLrc = !effectiveSynced.isNullOrBlank()
            val hasPlain = !effectivePlain.isNullOrBlank()

            if (hasLrc) {
                HighFidelityKaraokePlayer(
                    lrcText = effectiveSynced,
                    plainText = effectivePlain,
                    playbackPositionMs = playbackPositionMs,
                    isPlaying = isPlaying,
                    onSeekTo = onSeekTo,
                    onStartTapSync = onStartTapSync,
                    fontScale = fontScale,
                    isLandscape = isLandscape,
                    canSeek = canSeek,
                    isReducedMotion = isReducedMotion,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (hasPlain) {
                PlainLyricsReadingView(
                    lyricsText = effectivePlain.orEmpty(),
                    onStartTapSync = onStartTapSync,
                    onEditLyrics = onEditLyrics,
                    fontScale = fontScale,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                EmptyPlayerStateView(
                    stateType = PlayerEmptyStateType.LYRICS_NOT_FOUND,
                    trackTitle = currentTrack.title,
                    trackArtist = currentTrack.artist,
                    onSearchManual = onOpenTeachMode,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        NowPlayingMode.LYRICS -> {
            val context = LocalContext.current
            val database = remember(context) { AppDatabase.getInstance(context) }
            val provider = remember { com.lirix.app.translations.AmalgamaTranslationProvider() }
            Column(Modifier.fillMaxSize()) {
                Row {
                    TextButton(onClick = onEditLyrics) { Text("Редактировать текст") }
                    TextButton(onClick = onStartTapSync) { Text("Синхронизация") }
                }
                com.lirix.app.translations.TranslationPanel(currentTrack.trackKey, "${currentTrack.artist} — ${currentTrack.title}",
                    effectivePlain ?: effectiveSynced?.let { LrcSyncEngine.parseLrcLinesStrict(it).joinToString("\n") { line -> line.text } }.orEmpty(),
                    database.songTranslationDao(), provider::load, Modifier.weight(1f))
            }
        }

        NowPlayingMode.CHORDS -> {
            val chords = validCache?.chordsAmDm
                ?: AmDmChordParser.detectAndFormatInlineChords(effectivePlain.orEmpty())
            if (!chords.isNullOrBlank()) {
                ChordsTabContent(
                    chordsText = chords,
                    durationMs = totalTrackDuration,
                    initialTransposeSemitones = transposeSemitones,
                    onTransposeChange = onTransposeChange,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                EmptyPlayerStateView(
                    stateType = PlayerEmptyStateType.LYRICS_NOT_FOUND,
                    trackTitle = currentTrack.title,
                    trackArtist = currentTrack.artist,
                    onSearchManual = onOpenTeachMode,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        NowPlayingMode.NOTES -> { /* Handled above */ }
    }
}

/**
 * 120Hz Teleprompter Karaoke Player conforming to SPEC-UI-MOBILE-2.0.
 *
 * Characteristics:
 * - Golden ratio vertical anchor (~40% height)
 * - 3dp left accent bar on active block
 * - Literata Serif font with Sentence case
 * - Long-press for seekTo (blocked if canSeek == false)
 * - Instant transition on reduced motion
 */
@Composable
fun HighFidelityKaraokePlayer(
    lrcText: String?,
    plainText: String?,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onStartTapSync: (() -> Unit)? = null,
    fontScale: Float = 1.0f,
    isLandscape: Boolean = false,
    canSeek: Boolean = true,
    isReducedMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LirixThemeTokens.colors
    val lines = remember(lrcText) {
        if (!lrcText.isNullOrBlank()) parseLrcLinesStrict(lrcText) else emptyList()
    }

    if (lines.isEmpty()) {
        if (!plainText.isNullOrBlank()) {
            PlainLyricsReadingView(
                lyricsText = plainText,
                onStartTapSync = onStartTapSync,
                fontScale = fontScale,
                modifier = modifier
            )
        }
        return
    }

    val activeIndex = remember(lines, playbackPositionMs) {
        val idx = lines.indexOfLast { it.timestampMs <= playbackPositionMs }
        if (idx == -1) 0 else idx
    }

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // Auto-scroll anchor (~40% of viewport height)
    LaunchedEffect(activeIndex, isPlaying) {
        if (lines.isNotEmpty() && activeIndex in lines.indices) {
            if (isReducedMotion) {
                lazyListState.scrollToItem(index = activeIndex)
            } else {
                lazyListState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = 0
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Golden ratio ~40% anchor
        val topPadding = (maxHeight * 0.38f).coerceAtLeast(0.dp)
        val bottomPadding = (maxHeight * 0.50f).coerceAtLeast(0.dp)

        val baseFontSize = if (isLandscape) 28.sp else 24.sp
        val activeFontSize = (baseFontSize.value * fontScale).sp
        val nearFontSize = (activeFontSize.value * 0.82f).sp
        val farFontSize = (activeFontSize.value * 0.70f).sp

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(lines, key = { _, line -> line.timestampMs }) { index, line ->
                val distance = kotlin.math.abs(index - activeIndex)
                val isActive = index == activeIndex

                val lineAlpha by animateFloatAsState(
                    targetValue = when (distance) {
                        0 -> 1.0f
                        1 -> 0.55f
                        else -> 0.30f
                    },
                    animationSpec = if (isReducedMotion) tween(0) else tween(220, easing = FastOutSlowInEasing),
                    label = "alpha"
                )

                val fontSize = when (distance) {
                    0 -> activeFontSize
                    1 -> nearFontSize
                    else -> farFontSize
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(line.timestampMs, canSeek) {
                            detectTapGestures(
                                onLongPress = {
                                    if (canSeek) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSeekTo(line.timestampMs)
                                    }
                                }
                            )
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 3dp left accent bar for active line
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (isActive) 28.dp else 0.dp)
                            .background(
                                color = if (isActive) tokens.accentPrimary else Color.Transparent,
                                shape = RoundedCornerShape(1.5.dp)
                            )
                    )

                    Spacer(modifier = Modifier.width(9.dp))

                    Text(
                        text = line.text.toSentenceCase(),
                        fontFamily = LiterataFontFamily,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.35f).sp,
                        color = tokens.textPrimary.copy(alpha = lineAlpha),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Plain lyrics reader with Literata font and sentence case formatting.
 */
@Composable
fun PlainLyricsReadingView(
    lyricsText: String,
    onStartTapSync: (() -> Unit)? = null,
    onEditLyrics: (() -> Unit)? = null,
    fontScale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val tokens = LirixThemeTokens.colors
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = tokens.surfaceElevated,
                shape = RoundedCornerShape(tokens.pillCornerRadius),
                border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
            ) {
                Text(
                    text = "Текст песни без таймингов",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onEditLyrics != null) {
                    IconButton(
                        onClick = onEditLyrics,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать текст",
                            tint = tokens.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (onStartTapSync != null) {
                    Button(
                        onClick = onStartTapSync,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.surfaceElevated,
                            contentColor = tokens.accentPrimary
                        ),
                        shape = RoundedCornerShape(tokens.pillCornerRadius),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Синхронизировать", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboard.setText(AnnotatedString(lyricsText))
                        isCopied = true
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Копировать",
                        tint = if (isCopied) tokens.accentPrimary else tokens.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val sanitizedLyrics = remember(lyricsText) { LrcSyncEngine.sanitizePlainText(lyricsText) }
        Text(
            text = sanitizedLyrics.lines().joinToString("\n") { it.toSentenceCase() },
            fontFamily = LiterataFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (18 * fontScale).sp,
            lineHeight = (26 * fontScale).sp,
            color = tokens.textPrimary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
