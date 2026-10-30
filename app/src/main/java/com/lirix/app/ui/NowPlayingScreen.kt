package com.lirix.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.lirix.app.R
import androidx.lifecycle.repeatOnLifecycle
import com.lirix.app.feature.lyrics.*
import com.lirix.app.feature.lyrics.romanization.*
import com.lirix.app.ui.i18n.findHostActivity
import com.lirix.app.ui.components.RomanizationModeDialog
import com.lirix.app.ui.components.RomanizedKaraokeLine
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
import com.lirix.app.connect.AndroidConnectManager
import com.lirix.app.service.FloatingLyricsService
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.automaticLookupAcceptedTrackField
import com.lirix.app.ui.connect.DeviceConnectBottomSheet
import com.lirix.app.ui.player.ChordsTabContent
import com.lirix.app.ui.components.AlbumArtImage
import com.lirix.app.ui.components.EditLyricsDialog
import com.lirix.app.ui.components.EmptyPlayerStateView
import com.lirix.app.ui.components.*
import com.lirix.app.ui.components.NowPlayingOverflowMenu
import com.lirix.app.ui.components.OverlayPermissionDialog
import com.lirix.app.ui.components.PermissionRequestBanner
import com.lirix.app.ui.components.PlayerEmptyStateType
import com.lirix.app.ui.components.ShareFormatDialog
import com.lirix.app.ui.components.TimingCalibrationDialog
import com.lirix.app.ui.theme.FontFeature
import com.lirix.app.ui.theme.GolosTextFontFamily
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LiterataFontFamily
import com.lirix.app.ui.theme.toSentenceCase
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 4 modes of NowPlayingScreen deck (SPEC-UI-MOBILE-2.0 / ADR-023).
 */
enum class NowPlayingMode(@StringRes val labelRes: Int) {
    KARAOKE(R.string.karaoke_mode_karaoke),
    CHORDS(R.string.karaoke_mode_chords),
    LYRICS(R.string.karaoke_mode_lyrics),
    NOTES(R.string.karaoke_mode_notes)
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
 * 9. Landscape prompter: fixed 280dp left deck without scrolling; remaining width for lyrics.
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
    onNavigationGestureBlocked: (Boolean) -> Unit = {},
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
            (recentTracks.find { it.trackKey == trackKey } ?: TrackEntity(
                trackKey = trackKey, title = livePlayback!!.title, artist = cleanArtist,
                sourcePackage = livePlayback!!.packageName
            )).copy(
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
            recentTracks.find { it.trackKey == selectedTrack.trackKey } ?: selectedTrack
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

    val translationProvider = remember { com.lirix.app.translations.SongTranslationProviders() }
    val translationPreferences = remember(context) {
        val storage = context.getSharedPreferences("lirix_translations", Context.MODE_PRIVATE)
        com.lirix.app.translations.MemoryTranslationLanguagePreferences(storage.getString("target_language", "ru") ?: "ru") {
            storage.edit().putString("target_language", it).apply()
        }
    }
    val automaticTranslations = remember(db) { com.lirix.app.translations.AutomaticSongTranslations(db.songTranslationDao(), translationProvider::find, preferences = translationPreferences, searchLanguage = translationProvider::findLanguage) }
    val translationLanguage by automaticTranslations.language.collectAsState()
    LaunchedEffect(translationLanguage, currentTrack?.trackKey, currentTrack?.title, currentTrack?.artist) {
        currentTrack?.let { automaticTranslations.resolve(it.trackKey, it.title, it.artist) }
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
    val lookupTarget = if (selectedMode == NowPlayingMode.CHORDS) LookupTarget.CHORDS else LookupTarget.LYRICS
    val lookupCoordinator = remember(db) { AutomaticLookupCoordinator.get(db) }
    val lookupMonitor = remember(context.applicationContext) { LookupNetworkMonitor(context.applicationContext) }
    val lookupRecoveries = remember(currentTrack?.trackKey, lookupCoordinator) {
        LookupTarget.entries.associateWith { target ->
            ForegroundLookupRecovery(currentTrack?.trackKey.orEmpty(), target, lookupCoordinator, scope)
        }
    }
    val lookupRecovery = lookupRecoveries.getValue(lookupTarget)
    val recoveryPhase by lookupRecovery.phase.collectAsStateWithLifecycle()
    val lookupPhase = if (recoveryPhase == LookupPhase.Available &&
        !automaticLookupSatisfied(lookupTarget, currentTrack, lyricsCache)) LookupPhase.Incomplete else recoveryPhase
    val isLoadingLyrics = currentTrack != null && lookupPhase == LookupPhase.Loading &&
        !automaticLookupSatisfied(lookupTarget, currentTrack, lyricsCache)
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

    val isReducedMotion = com.lirix.app.ui.components.rememberReducedMotion()

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
        val window = context.findHostActivity()?.window
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
                    message = context.getString(R.string.hint_double_tap_focus),
                    duration = SnackbarDuration.Short
                )
                prefs.edit().putBoolean("hint_focus_shown", true).apply()
                hasShownFocusHint = true
            } else if (!hasShownSeekHint && canSeek) {
                delay(2000L)
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.hint_hold_line_to_seek),
                    duration = SnackbarDuration.Short
                )
                prefs.edit().putBoolean("hint_seek_shown", true).apply()
                hasShownSeekHint = true
            }
        }
    }

    var showShareDialog by remember { mutableStateOf(false) }
    var infoTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var tapSyncTarget by remember { mutableStateOf<TapSyncEditingTarget?>(null) }
    val displayedTrackKey by rememberUpdatedState(currentTrack?.trackKey)
    fun openTapSync() {
        currentTrack?.let { track ->
            tapSyncTarget = TapSyncEditingTarget(track.copy(sourcePackage = tapSyncSourcePackage(track, livePlayback)), lyricsCache?.plainLyrics ?: track.plainLyrics.orEmpty(),
                lyricsCache?.syncedLyricsLrc ?: track.syncedLyrics)
        }
    }
    var favoriteSaving by remember(currentTrack?.trackKey) { mutableStateOf(false) }
    var showEditLyricsDialog by remember { mutableStateOf(false) }
    var showTranslationSettings by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    val readingPreferences = remember(context.applicationContext) { LyricsReadingPreferences(context) }
    var romanizationDisplayMode by remember { mutableStateOf(readingPreferences.mode()) }
    var lyricsReadingLanguage by remember(currentTrack?.trackKey) { mutableStateOf(readingPreferences.language(currentTrack?.trackKey)) }
    val readingCache = lyricsCache?.takeIf { it.trackKey == currentTrack?.trackKey }
    val readingResults = rememberDocumentReadings(currentTrack?.trackKey,
        readingCache?.syncedLyricsLrc ?: currentTrack?.syncedLyrics,
        readingCache?.plainLyrics ?: currentTrack?.plainLyrics,
        romanizationDisplayMode, lyricsReadingLanguage)
    var showRomanizationDialog by remember { mutableStateOf(false) }
    val connectManager = remember { AndroidConnectManager.getInstance(context) }
    var showConnectBottomSheet by remember { mutableStateOf(false) }
    val isLocalPlayback by connectManager.handoffStateMachine.isLocalPlayback.collectAsStateWithLifecycle()
    val isRemotePlaying = !isLocalPlayback
    val isOverlayRunning by FloatingLyricsService.isRunning.collectAsStateWithLifecycle()
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    val blockNavigationGesture = selectedMode == NowPlayingMode.NOTES || isNotesFocused || isImeVisible ||
        isFocusMode || infoTrack != null || showShareDialog || tapSyncTarget != null || showEditLyricsDialog ||
        showTranslationSettings || showCalibrationDialog || showRomanizationDialog ||
        showConnectBottomSheet || showOverlayPermissionDialog
    SideEffect { onNavigationGestureBlocked(blockNavigationGesture) }
    DisposableEffect(Unit) { onDispose { onNavigationGestureBlocked(false) } }

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
                    playbackPositionMs = karaokeLyricPosition(raw, syncOffsetMs)
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
                    playbackPositionMs = karaokeLyricPosition(localPlaybackPositionMs, syncOffsetMs)
                }
            }
        } else {
            playbackPositionMs = karaokeLyricPosition(localPlaybackPositionMs, syncOffsetMs)
        }
    }

    // Subscribe by identity; shared automatic jobs may finish under their own key after this UI leaves.
    val lookupLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lookupRecoveries) { onDispose { lookupRecoveries.values.forEach { it.dispose() } } }
    LaunchedEffect(currentTrack?.trackKey, lookupRecovery, lookupLifecycleOwner, selectedMode) {
        val track = currentTrack ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            if (db.musicDao().getTrackByKey(track.trackKey) == null) db.musicDao().insertOrUpdateTrack(track)
        }
        if (selectedMode == NowPlayingMode.NOTES) return@LaunchedEffect
        lookupLifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            lookupMonitor.observe().collect { lookupRecovery.onNetwork(it) }
        }
    }
    LaunchedEffect(currentTrack?.trackKey, lookupRecovery) {
        val track = currentTrack ?: return@LaunchedEffect
        db.lyricsDao().observeLyrics(track.trackKey).collect { cached ->
            // LaunchedEffect cancellation + keyed state prevents an old track publication.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            lyricsCache = cached
            if (automaticLookupSatisfied(lookupTarget, track, cached)) lookupRecovery.contentAvailable()
        }
    }

    // Orientation and Screen arithmetic
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightDp = configuration.screenHeightDp
    val isCompactPortrait = !isLandscape && screenHeightDp < 700

    val sourceLabel = remember(currentTrack?.sourcePackage, context) {
        when {
            currentTrack?.sourcePackage?.contains("yandex") == true -> context.getString(R.string.source_yandex)
            currentTrack?.sourcePackage?.contains("spotify") == true -> "Spotify"
            currentTrack?.sourcePackage?.contains("vk") == true -> context.getString(R.string.source_vk)
            else -> context.getString(R.string.source_generic)
        }
    }

    LaunchedEffect(selectedMode, currentTrack?.trackKey, isLandscape) {
        showTranslationSettings = false
    }
    val overflow: @Composable () -> Unit = {
        PlayerContextOverflow(
                mode = selectedMode, trackKey = currentTrack?.trackKey, landscape = isLandscape,
                onTrackInfo = currentTrack?.let { track -> { infoTrack = track } },
                isOverlayActive = isOverlayRunning,
                onEditLyrics = { showEditLyricsDialog = true },
                onSync = { openTapSync() },
                onTranslations = { showTranslationSettings = true },
                onRomanization = { showRomanizationDialog = true },
                onSaveNotes = saveNotes,
                onToggleOverlay = {
                    if (!FloatingLyricsService.isPermissionGranted(context)) {
                        showOverlayPermissionDialog = true
                    } else {
                        val willEnable = !isOverlayRunning
                        FloatingLyricsService.toggle(context)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (willEnable) context.getString(R.string.overlay_enabled) else context.getString(R.string.overlay_disabled),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onCalibrate = { showCalibrationDialog = true },
                onTeach = onOpenTeachMode?.let { callback -> { callback(currentTrack) } }
            )
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

                        overflow()
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
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                com.lirix.app.ui.components.LandscapePlayerPanel(
                    modifier = Modifier.width(280.dp),
                    onTrackInfo = { infoTrack = currentTrack },
                    title = currentTrack.title, artist = currentTrack.artist, albumArtUri = currentTrack.albumArtUri,
                    sourceLabel = sourceLabel, isPlaying = isPlaying, isRemotePlaying = isRemotePlaying,
                    selectedMode = selectedMode, onSelectMode = { selectedMode = it }, onBack = onBack,
                    onConnect = { showConnectBottomSheet = true },
                    onPrevious = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        MediaSessionCollector.skipToPrevious()
                    },
                    onTogglePlaying = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        localIsPlaying = !isPlaying
                        MediaSessionCollector.togglePlayPause()
                    },
                    onNext = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        MediaSessionCollector.skipToNext()
                    },
                    onShare = { showShareDialog = true }, overflow = overflow,
                    seek = {
                            com.lirix.app.ui.components.LandscapePlayerSeekBar(
                                positionMs = karaokeMediaSeekPosition(playbackPositionMs, syncOffsetMs, totalTrackDuration),
                                durationMs = totalTrackDuration,
                                onSeek = { target ->
                                    localPlaybackPositionMs = target
                                    playbackPositionMs = karaokeLyricPosition(target, syncOffsetMs)
                                    MediaSessionCollector.seekTo(target)
                                })
                    })

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
                        lookupPhase = lookupPhase, onRetryLookup = lookupRecovery::retry,
                        playbackPositionMs = playbackPositionMs,
                        isPlaying = isPlaying,
                        syncOffsetMs = syncOffsetMs,
                        canSeek = canSeek,
                        fontScale = textScaleFactor,
                        isLandscape = true,
                        isReducedMotion = isReducedMotion,
                        onSeekTo = { targetMs ->
                            val rawTarget = karaokeMediaSeekPosition(targetMs, syncOffsetMs, totalTrackDuration)
                            localPlaybackPositionMs = rawTarget
                            playbackPositionMs = karaokeLyricPosition(rawTarget, syncOffsetMs)
                            MediaSessionCollector.seekTo(rawTarget)
                        },
                        onStartTapSync = { openTapSync() },
                        onEditLyrics = { showEditLyricsDialog = true },
                        onOpenTeachMode = { onOpenTeachMode?.invoke(currentTrack) },
                        transposeSemitones = transposeSemitones,
                        onTransposeChange = { transposeSemitones = it },
                        chordsScrollState = chordsScrollState,
                        notesText = notesInputText,
                        onNotesChange = { notesInputText = it },
                        onSaveNotes = saveNotes,
                        onNotesFocusChange = { isNotesFocused = it },
                        totalTrackDuration = totalTrackDuration,
                        automaticTranslations = automaticTranslations,
                        showTranslationSettings = showTranslationSettings,
                        onDismissTranslationSettings = { showTranslationSettings = false },
                        romanizationMode = romanizationDisplayMode,
                        readingResults = readingResults
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
                            contentDescription = stringResource(R.string.action_to_library),
                            tint = lirixColors.textPrimary
                        )
                    }

                    val headerText = if (isCompactPortrait || isFocusMode) {
                        "${currentTrack.title} — ${currentTrack.artist} ▾"
                    } else {
                        stringResource(R.string.player_in_source, sourceLabel)
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        overflow()
                    }
                }

                // 2. Блок метаданных (64dp): скрывается при автосжатии (<700dp), в режиме «Фокус» или при редактировании заметок
                AnimatedVisibility(visible = !isCompactPortrait && !isFocusMode && !isHideControlsForNotes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtImage(
                            artUri = currentTrack.albumArtUri,
                            size = 48.dp,
                            shape = RoundedCornerShape(6.dp),
                            contentDescription = currentTrack.title
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = context.getString(R.string.menu_track_info)) { infoTrack = currentTrack }) {
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
                            val trackCache = lyricsCache?.takeIf { it.trackKey == currentTrack.trackKey }
                            val statusCache = trackCache?.takeUnless { it.provider == "note:user" }
                            val statusPlain = statusCache?.plainLyrics?.takeIf { it.isNotBlank() }
                                ?: automaticLookupAcceptedTrackField(currentTrack.plainLyrics, currentTrack, trackCache)
                            val statusLrc = statusCache?.syncedLyricsLrc?.takeIf { it.isNotBlank() }
                                ?: automaticLookupAcceptedTrackField(currentTrack.syncedLyrics, currentTrack, trackCache)
                            val hasTimedLines = remember(statusLrc) { statusLrc != null && parseLrcLinesStrict(statusLrc).isNotEmpty() }
                            val hasChords = remember(statusCache?.chordsAmDm, statusPlain) {
                                !statusCache?.chordsAmDm.isNullOrBlank() || !AmDmChordParser.detectAndFormatInlineChords(statusPlain.orEmpty()).isNullOrBlank()
                            }
                            val statusRes = when (selectedMode) {
                                NowPlayingMode.NOTES -> null
                                NowPlayingMode.CHORDS -> when {
                                    hasChords -> R.string.player_chords_available
                                    lookupPhase == LookupPhase.Loading -> R.string.player_searching_chords_status
                                    lookupPhase == LookupPhase.WaitingForNetwork -> R.string.lookup_waiting_network
                                    else -> R.string.player_chords_lookup_incomplete
                                }
                                else -> when {
                                    lookupPhase == LookupPhase.WaitingForNetwork && (!statusPlain.isNullOrBlank() || hasTimedLines) -> R.string.player_saved_content
                                    selectedMode == NowPlayingMode.KARAOKE && hasTimedLines -> R.string.lyrics_status_synced
                                    !statusPlain.isNullOrBlank() || hasTimedLines -> R.string.lyrics_status_static
                                    lookupPhase == LookupPhase.Loading -> R.string.lyrics_status_searching
                                    lookupPhase == LookupPhase.WaitingForNetwork -> R.string.lookup_waiting_network
                                    else -> R.string.lookup_incomplete
                                }
                            }
                            if (statusRes != null) Text(
                                text = stringResource(statusRes),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = lirixColors.textSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                favoriteSaving = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { db.musicDao().toggleFavorite(currentTrack) }
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.favorite_save_failed_error))
                                    } finally {
                                        favoriteSaving = false
                                    }
                                }
                            },
                            enabled = !favoriteSaving,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.action_favorite),
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
                                CustomAccessibilityAction(context.getString(R.string.action_toggle_focus)) {
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
                        lookupPhase = lookupPhase, onRetryLookup = lookupRecovery::retry,
                        playbackPositionMs = playbackPositionMs,
                        isPlaying = isPlaying,
                        syncOffsetMs = syncOffsetMs,
                        canSeek = canSeek,
                        fontScale = textScaleFactor,
                        isLandscape = false,
                        isReducedMotion = isReducedMotion,
                        onSeekTo = { targetMs ->
                            val rawTarget = karaokeMediaSeekPosition(targetMs, syncOffsetMs, totalTrackDuration)
                            localPlaybackPositionMs = rawTarget
                            playbackPositionMs = karaokeLyricPosition(rawTarget, syncOffsetMs)
                            MediaSessionCollector.seekTo(rawTarget)
                        },
                        onStartTapSync = { openTapSync() },
                        onEditLyrics = { showEditLyricsDialog = true },
                        onOpenTeachMode = { onOpenTeachMode?.invoke(currentTrack) },
                        transposeSemitones = transposeSemitones,
                        onTransposeChange = { transposeSemitones = it },
                        chordsScrollState = chordsScrollState,
                        notesText = notesInputText,
                        onNotesChange = { notesInputText = it },
                        onSaveNotes = saveNotes,
                        onNotesFocusChange = { isNotesFocused = it },
                        totalTrackDuration = totalTrackDuration,
                        automaticTranslations = automaticTranslations,
                        showTranslationSettings = showTranslationSettings,
                        onDismissTranslationSettings = { showTranslationSettings = false },
                        romanizationMode = romanizationDisplayMode,
                        readingResults = readingResults
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
                            text = formatMs(karaokeMediaSeekPosition(playbackPositionMs, syncOffsetMs, totalTrackDuration)),
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
                                value = karaokeMediaSeekPosition(playbackPositionMs, syncOffsetMs, totalTrackDuration).toFloat(),
                                onValueChange = { newPos ->
                                    val target = newPos.toLong()
                                    localPlaybackPositionMs = target
                                    playbackPositionMs = karaokeLyricPosition(target, syncOffsetMs)
                                    MediaSessionCollector.seekTo(target)
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
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TimingCorrectionControls(
                            offsetMs = syncOffsetMs,
                            onChange = { syncOffsetMs = syncOffsetStore.setOffsetAndGet(currentTrack.trackKey, it) },
                            onDetails = { showCalibrationDialog = true }
                        )

                        com.lirix.app.ui.components.TextSizeControls(
                            scale = textScaleFactor, onChange = { textScaleFactor = it }
                        )
                    }
                }

                AnimatedVisibility(visible = !isHideControlsForNotes) {
                    com.lirix.app.ui.components.PlayerModeSelector(
                        selected = selectedMode, onSelect = { selectedMode = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // 7. Панель транспорта (56dp)
                AnimatedVisibility(visible = !isHideControlsForNotes) {
                    PlayerTransportRow(
                        isPlaying = isPlaying, connected = isRemotePlaying,
                        onConnect = { showConnectBottomSheet = true },
                        onPrevious = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            MediaSessionCollector.skipToPrevious()
                        },
                        onTogglePlayback = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val newPlaying = !isPlaying

                            localIsPlaying = newPlaying
                            MediaSessionCollector.togglePlayPause()
                        },
                        onNext = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            MediaSessionCollector.skipToNext()
                        },
                        onShare = { showShareDialog = true }
                    )
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

        infoTrack?.let { track ->
            com.lirix.app.ui.components.TrackInfoDialog(track, db.lyricsDao(), db.songTranslationDao(),
                language = translationLanguage, onDismiss = { infoTrack = null })
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
                    syncOffsetMs = syncOffsetStore.setOffsetAndGet(track.trackKey, newOffset)
                },
                onResetOffset = {
                    syncOffsetMs = syncOffsetStore.setOffsetAndGet(track.trackKey, 0L)
                },
                onDismissRequest = { showCalibrationDialog = false }
            )
        }

        tapSyncTarget?.let { target ->
            KaraokeStudioDialog(
                trackTitle = target.title,
                artist = target.artist,
                initialPlainText = target.plainLyrics,
                initialLrc = target.initialLrc,
                trackKey = target.trackKey,
                sourcePackage = target.track.sourcePackage,
                onDismissRequest = { tapSyncTarget = null },
                onSaveLrc = { generatedLrc, generatedPlain ->
                    val saved = saveTapSyncLyrics(db, target, generatedLrc, generatedPlain)
                    if (shouldDisplayTapSyncSave(target, displayedTrackKey)) {
                        lyricsCache = saved
                        selectedMode = NowPlayingMode.KARAOKE
                    }
                    tapSyncTarget = target.copy(plainLyrics = generatedPlain, initialLrc = generatedLrc)
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_karaoke_synced)) }
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
                            provider = "USER:EDIT",
                            updatedAt = System.currentTimeMillis()
                        )
                        db.lyricsDao().saveLyrics(updatedEntity)
                        db.musicDao().updateLyrics(track.trackKey, updatedLyrics, updatedEntity.syncedLyricsLrc)
                        withContext(Dispatchers.Main) {
                            lyricsCache = updatedEntity
                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_lyrics_saved))
                        }
                    }
                }
            )
        }

        if (showShareDialog && currentTrack != null) {
            val track = remember { requireNotNull(currentTrack) }
            val exportTheme = remember { com.lirix.app.feature.share.PosterThemeSnapshot.fromColors(lirixColors) }
            val exportLabels = remember { com.lirix.app.feature.share.PosterTextSnapshot.from(context) }
            val animationPalette = remember { com.lirix.app.feature.share.animation.ThemeAnimationPalette.fromColors(lirixColors) }
            var shareArtBmp by remember(track.albumArtUri) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(track.albumArtUri) {
                shareArtBmp = com.lirix.app.feature.share.PosterArtworkLoader.load(context, track.albumArtUri)
            }
            val activeLyricLine = remember {
                val lrc = lyricsCache?.syncedLyricsLrc
                if (!lrc.isNullOrBlank()) {
                    val parsed = parseLrcLinesStrict(lrc)
                    val idx = parsed.indexOfLast { it.timestampMs <= playbackPositionMs }
                    if (idx != -1) parsed[idx].text else parsed.firstOrNull()?.text
                } else {
                    lyricsCache?.plainLyrics?.lineSequence()?.firstOrNull { it.isNotBlank() }
                }
            }
            ShareFormatDialog(
                onDismissRequest = { showShareDialog = false },
                animatorProvider = {
                    com.lirix.app.feature.share.animation.VinylSpinAnimator(
                        palette = animationPalette,
                        albumArt = shareArtBmp,
                        trackTitle = track.title,
                        artistName = track.artist,
                        lyricLine = activeLyricLine
                    )
                },
                onFormatSelected = { format ->
                    val artBmp = shareArtBmp
                    val cardBitmap = withContext(Dispatchers.Default) { ShareCardGenerator.generateNowPlayingCard(
                        context = context,
                        track = track,
                        currentLyricLine = activeLyricLine,
                        format = format,
                        albumArtBitmap = artBmp,
                        theme = exportTheme,
                        labels = exportLabels
                    ) }
                    cardBitmap
                }
            )
        }

        if (showConnectBottomSheet) {
            DeviceConnectBottomSheet(
                onDismissRequest = { showConnectBottomSheet = false },
                currentTrackTitle = currentTrack?.title ?: stringResource(R.string.connect_title),
                currentTrackArtist = currentTrack?.artist ?: stringResource(R.string.connect_wireless_pairing),
                playbackPositionMs = playbackPositionMs,
                connectManager = connectManager
            )
        }

        if (showRomanizationDialog) {
            RomanizationModeDialog(
                currentMode = romanizationDisplayMode,
                onModeSelected = { romanizationDisplayMode = it; readingPreferences.saveMode(it) },
                currentLanguage = lyricsReadingLanguage,
                onLanguageSelected = { lyricsReadingLanguage = it; readingPreferences.saveLanguage(currentTrack?.trackKey, it) },
                onDismissRequest = { showRomanizationDialog = false }
            )
        }
    }
}

/**
 * Shared teleprompter content renderer across portrait and landscape decks.
 */
@Composable
internal fun RenderTeleprompterContent(
    selectedMode: NowPlayingMode,
    lyricsCache: LyricsCacheEntity?,
    currentTrack: TrackEntity,
    isLoadingLyrics: Boolean,
    lookupPhase: LookupPhase = LookupPhase.Incomplete,
    onRetryLookup: (() -> Unit)? = null,
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
    totalTrackDuration: Long = 0L,
    automaticTranslations: com.lirix.app.translations.AutomaticSongTranslations? = null,
    showTranslationSettings: Boolean = false,
    onDismissTranslationSettings: () -> Unit = {},
    romanizationMode: RomanizationDisplayMode = RomanizationDisplayMode.ORIGINAL_ONLY,
    readingResults: Map<String, ReadingResult> = emptyMap()
) {
    Crossfade(targetState = selectedMode, animationSpec = tween(if (isReducedMotion) 0 else 280),
        modifier = Modifier.fillMaxSize(), label = "PlayerReadingMode") { presentedMode ->
        val tokens = LirixThemeTokens.colors

        if (presentedMode == NowPlayingMode.NOTES) {
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
                        text = stringResource(R.string.notes_title),
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
                            contentDescription = stringResource(R.string.action_done),
                            tint = tokens.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_done),
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
                        Text(stringResource(R.string.notes_examples_hint))
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
            return@Crossfade
        }

        if (isLoadingLyrics && presentedMode != NowPlayingMode.CHORDS) {
            EmptyPlayerStateView(
                stateType = PlayerEmptyStateType.SEARCHING_LYRICS,
                trackTitle = currentTrack.title,
                trackArtist = currentTrack.artist,
                modifier = Modifier.fillMaxSize()
            )
            return@Crossfade
        }

        val validCache = if (lyricsCache?.trackKey == currentTrack.trackKey) lyricsCache else null
        val effectivePlain = validCache?.plainLyrics?.takeUnless { validCache?.provider == "note:user" }?.takeIf { it.isNotBlank() } ?: automaticLookupAcceptedTrackField(currentTrack.plainLyrics, currentTrack, validCache)
        val effectiveSynced = validCache?.syncedLyricsLrc?.takeUnless { validCache?.provider == "note:user" }?.takeIf { it.isNotBlank() } ?: automaticLookupAcceptedTrackField(currentTrack.syncedLyrics, currentTrack, validCache)

        when (presentedMode) {
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
                        romanizationMode = romanizationMode,
                        readingResults = readingResults,
                        modifier = Modifier.fillMaxSize()
                    )

                } else if (hasPlain) {
                    PlainLyricsReadingView(
                        lyricsText = effectivePlain.orEmpty(),
                        onStartTapSync = onStartTapSync,
                        onEditLyrics = onEditLyrics,
                        fontScale = fontScale,
                        romanizationMode = romanizationMode,
                            readingResults = readingResults,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EmptyPlayerStateView(
                        stateType = PlayerEmptyStateType.LYRICS_NOT_FOUND,
                        lookupPhase = lookupPhase, onRetry = onRetryLookup,
                        trackTitle = currentTrack.title,
                        trackArtist = currentTrack.artist,
                        onSearchManual = onOpenTeachMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            NowPlayingMode.LYRICS -> {
                if (effectivePlain.isNullOrBlank() && effectiveSynced.isNullOrBlank()) {
                    EmptyPlayerStateView(
                        stateType = PlayerEmptyStateType.LYRICS_NOT_FOUND,
                        lookupPhase = lookupPhase, onRetry = onRetryLookup,
                        trackTitle = currentTrack.title, trackArtist = currentTrack.artist,
                        onSearchManual = onOpenTeachMode, modifier = Modifier.fillMaxSize())
                    return@Crossfade
                }
                val context = LocalContext.current
                val database = remember(context) { AppDatabase.getInstance(context) }
                val provider = remember { com.lirix.app.translations.SongTranslationProviders() }
                Column(Modifier.fillMaxSize()) {
                    com.lirix.app.translations.TranslationPanel(currentTrack.trackKey, "${currentTrack.artist} — ${currentTrack.title}",
                        effectivePlain ?: effectiveSynced?.let { LrcSyncEngine.parseLrcLinesStrict(it).joinToString("\n") { line -> line.text } }.orEmpty(),
                        database.songTranslationDao(), provider::load, Modifier.weight(1f), automaticTranslations, currentTrack.title, currentTrack.artist,
                        compact = true, settingsExpanded = showTranslationSettings, onDismissSettings = onDismissTranslationSettings,
                        originalLineContent = if (romanizationMode == RomanizationDisplayMode.ORIGINAL_ONLY) null else { raw ->
                            RomanizedKaraokeLine(raw, romanizationMode, reading = readingResults[raw],
                                fontFamily = LiterataFontFamily, fontSize = (18 * fontScale).sp)
                        })
                }
            }

            NowPlayingMode.CHORDS -> {
                val chords = validCache?.chordsAmDm?.takeIf { it.isNotBlank() }
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
                    val retainedWords = effectivePlain?.takeIf { it.isNotBlank() }
                        ?: effectiveSynced?.let { LrcSyncEngine.parseLrcLinesStrict(it).joinToString("\n") { line -> line.text } }
                    Column(Modifier.fillMaxSize()) {
                        if (!retainedWords.isNullOrBlank()) {
                            com.lirix.app.ui.components.LookupRetryStatus(lookupPhase, onRetryLookup, searchingChords = true)
                            PlainLyricsReadingView(
                                lyricsText = retainedWords, onStartTapSync = onStartTapSync,
                                onEditLyrics = onEditLyrics, showUnsyncedBadge = false, fontScale = fontScale,
                                modifier = Modifier.weight(1f))
                        } else {
                            EmptyPlayerStateView(
                                stateType = if (lookupPhase == LookupPhase.Loading) PlayerEmptyStateType.SEARCHING_LYRICS else PlayerEmptyStateType.LYRICS_NOT_FOUND,
                                trackTitle = currentTrack.title, trackArtist = currentTrack.artist,
                                lookupPhase = lookupPhase, onRetry = onRetryLookup, onSearchManual = onOpenTeachMode,
                                searchingChords = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            NowPlayingMode.NOTES -> { /* Handled above */ }
        }
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
    romanizationMode: RomanizationDisplayMode = RomanizationDisplayMode.ORIGINAL_ONLY,
    readingResults: Map<String, ReadingResult> = emptyMap(),
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
                romanizationMode = romanizationMode,
                        readingResults = readingResults,
                modifier = modifier
            )
        }
        return
    }

    val activeIndex = remember(lines, playbackPositionMs) {
        karaokeActiveLineIndex(lines.map { it.timestampMs }, playbackPositionMs)
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
        // A tall wrapped row must be able to reach the viewport top and scroll fully past it.
        val topPadding = (maxHeight * 0.15f).coerceAtMost(48.dp)
        val bottomPadding = maxHeight.coerceAtLeast(48.dp)

        val baseFontSize = if (isLandscape) 28.sp else 24.sp
        val activeFontSize = (baseFontSize.value * fontScale).sp

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(lines, key = { index, line -> karaokeOccurrenceKey(index, line.timestampMs) }) { index, line ->
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

                // Stable wrapping and height prevent active-row changes from moving the scroll anchor.
                val fontSize = activeFontSize

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

                    RomanizedKaraokeLine(
                        originalText = line.text,
                        reading = readingResults[line.text],
                        displayMode = romanizationMode,
                        fontFamily = LiterataFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.35f).sp,
                        textColor = tokens.textPrimary,
                        alpha = lineAlpha,
                        rubyColor = tokens.accentPrimary.copy(alpha = 0.9f * lineAlpha),
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlainLyricsReadingView(
    lyricsText: String,
    onStartTapSync: (() -> Unit)? = null,
    onEditLyrics: (() -> Unit)? = null,
    showUnsyncedBadge: Boolean = true,
    fontScale: Float = 1.0f,
    romanizationMode: RomanizationDisplayMode = RomanizationDisplayMode.ORIGINAL_ONLY,
    readingResults: Map<String, ReadingResult> = emptyMap(),
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
        if (showUnsyncedBadge) {
            Surface(
                color = tokens.surfaceElevated,
                shape = RoundedCornerShape(tokens.pillCornerRadius),
                border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
            ) {
                Text(
                    text = stringResource(R.string.lyrics_unsynced_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onEditLyrics != null) {
                IconButton(onClick = onEditLyrics, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Edit, stringResource(R.string.action_edit_lyrics), tint = tokens.accentPrimary)
                }
            }
            if (onStartTapSync != null) {
                val studioDescription = stringResource(R.string.player_open_sync_studio)
                Button(
                    onClick = onStartTapSync,
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.surfaceElevated, contentColor = tokens.accentPrimary),
                    shape = RoundedCornerShape(tokens.pillCornerRadius),
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = studioDescription }
                ) { Text(stringResource(R.string.player_studio)) }
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                clipboard.setText(AnnotatedString(lyricsText))
                isCopied = true
            }, modifier = Modifier.size(48.dp)) {
                Icon(if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    stringResource(R.string.action_copy), tint = if (isCopied) tokens.accentPrimary else tokens.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val sanitizedLyrics = remember(lyricsText) { LrcSyncEngine.sanitizePlainText(lyricsText) }
        if (romanizationMode == RomanizationDisplayMode.ORIGINAL_ONLY) {
            Text(
                text = sanitizedLyrics.lines().joinToString("\n") { it.toSentenceCase() },
                fontFamily = LiterataFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = (18 * fontScale).sp,
                lineHeight = (26 * fontScale).sp,
                color = tokens.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sanitizedLyrics.lines().forEach { rawLine ->
                    RomanizedKaraokeLine(
                        originalText = rawLine,
                        reading = readingResults[rawLine],
                        displayMode = romanizationMode,
                        fontFamily = LiterataFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = (18 * fontScale).sp,
                        lineHeight = (26 * fontScale).sp,
                        textColor = tokens.textPrimary,
                        rubyColor = tokens.accentPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
