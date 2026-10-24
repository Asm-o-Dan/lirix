package com.lirix.app.ui.components

import android.os.SystemClock
import com.lirix.app.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lirix.app.feature.lyrics.*
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.*
import java.io.File

/** Quick tapping and precise editing use one captured document, not two LRC round trips. */
@Composable
fun KaraokeStudioDialog(
    trackTitle: String,
    artist: String,
    initialPlainText: String,
    initialLrc: String? = null,
    trackKey: String,
    sourcePackage: String,
    onDismissRequest: () -> Unit,
    onSaveLrc: suspend (String, String) -> Unit
) {
    val colors = LirixThemeTokens.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val store = remember { KaraokeStudioDraftStore(File(context.filesDir, "karaoke-studio-drafts")) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val live by MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle()
    var state by remember(trackKey) { mutableStateOf(KaraokeStudioState(trackKey, emptyList(), emptyList(), 0)) }
    val latestState by rememberUpdatedState(state)
    var expectedPlain by remember(trackKey) { mutableStateOf(initialPlainText) }
    var expectedLrc by remember(trackKey) { mutableStateOf(initialLrc) }
    var originalPlain by remember(trackKey) { mutableStateOf(initialPlainText) }
    var originalLrc by remember(trackKey) { mutableStateOf(initialLrc) }
    var audioUri by remember(trackKey) { mutableStateOf<String?>(null) }
    var waveform by remember(trackKey) { mutableStateOf<KaraokeAudioWaveform?>(null) }
    var waveProgress by remember { mutableStateOf<Float?>(null) }
    var waveError by remember { mutableStateOf<String?>(null) }
    var durationEvidence by remember(trackKey) { mutableStateOf(StudioDurationEvidence()) }
    var ready by remember(trackKey) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var autosaveJob by remember { mutableStateOf<Job?>(null) }
    var conflictedDraft by remember { mutableStateOf<KaraokeStudioDraft?>(null) }
    var corruptDraft by remember { mutableStateOf(false) }
    var discardClose by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(false) }
    var warnText by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var showLrc by remember { mutableStateOf(false) }
    var timeEntry by remember { mutableStateOf<String?>(null) }
    var tailEntry by remember { mutableStateOf<String?>(null) }
    var follow by remember { mutableStateOf(false) }
    var playback by remember { mutableStateOf(StudioPlaybackReading()) }
    var loopA by remember { mutableStateOf<Long?>(null) }
    var loopB by remember { mutableStateOf<Long?>(null) }
    var loopEnabled by remember { mutableStateOf(false) }
    var pendingLoopSeek by remember { mutableStateOf<Long?>(null) }
    val prefs = remember { context.getSharedPreferences("lirix_sync_prefs", android.content.Context.MODE_PRIVATE) }
    var reactionMs by remember { mutableLongStateOf(prefs.getLong("reaction_lag_ms", 200).coerceIn(0, 1000)) }

    fun updateDuration(evidence: StudioDurationEvidence) {
        durationEvidence = evidence
        if (state.durationMs != evidence.effectiveMs) state = state.copy(durationMs = evidence.effectiveMs)
    }
    fun invalidateAudio() {
        waveform = null
        waveProgress = null
        waveError = null
        val reading = readStudioPlayback(trackKey, sourcePackage)
        updateDuration(durationEvidence.invalidateFile().source(reading.matchesTrack, reading.durationMs))
    }
    fun restoreDraft(value: KaraokeStudioDraft) {
        val reading = readStudioPlayback(trackKey, sourcePackage)
        state = value.state
        updateDuration(StudioDurationEvidence(originalDurationMs = value.originalDurationMs.takeIf { it > 0 } ?: durationEvidence.originalDurationMs,
            fileGeneration = durationEvidence.fileGeneration + 1).source(reading.matchesTrack, reading.durationMs))
        originalPlain = value.originalPlain
        originalLrc = value.originalLrc
        waveform = null
        audioUri = value.audioUri
    }
    fun draft(snapshot: KaraokeStudioState = state) = KaraokeStudioDraft(snapshot, expectedPlain, expectedLrc, originalPlain, originalLrc, audioUri, durationEvidence.originalDurationMs)
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onFailure { error = context.getString(R.string.studio_audio_access_is_temporary_you_may_need_to_select_83d66) }
            invalidateAudio()
            audioUri = uri.toString()
        }
    }
    fun edit(action: KaraokeStudioEdit): Boolean {
        if (!ready || busy) return false
        return when (val result = KaraokeStudioEngine.reduce(state, action)) {
            is KaraokeStudioResult.Rejected -> { error = result.reason; false }
            is KaraokeStudioResult.Accepted -> { state = result.state; error = null; status = null; true }
        }
    }
    fun close() {
        if (busy) return
        if (!ready) { onDismissRequest(); return }
        scope.launch {
            busy = true
            try {
                autosaveJob?.cancelAndJoin()
                store.save(draft())
                onDismissRequest()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = context.getString(R.string.studio_could_not_save_the_draft_retry_or_leave_without__fc89e)
                discardClose = true
            } finally { busy = false }
        }
    }
    fun changeMode(mode: StudioMode) {
        if (!ready || busy || state.mode == mode) return
        scope.launch {
            busy = true
            try {
                autosaveJob?.cancelAndJoin()
                val next = state.copy(mode = mode)
                store.save(draft(next))
                state = next
                error = null
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = context.getString(R.string.studio_could_not_save_the_draft_before_switching_modes_6e35c)
            } finally { busy = false }
        }
    }

    LaunchedEffect(trackKey) {
        val initialReading = readStudioPlayback(trackKey, sourcePackage)
        val originalDuration = if (initialReading.matchesTrack) initialReading.durationMs.coerceAtLeast(0) else 0
        val initial = withContext(Dispatchers.Default) {
            runCatching { KaraokeStudioEngine.createSession(trackKey, initialPlainText, initialLrc, originalDuration) }
        }
        state = initial.getOrElse {
            error = it.message ?: context.getString(R.string.studio_could_not_open_timings_a3b40)
            KaraokeStudioState(trackKey, emptyList(), emptyList(), 0)
        }
        updateDuration(StudioDurationEvidence(originalDurationMs = originalDuration).source(initialReading.matchesTrack, initialReading.durationMs))
        when (val loaded = store.load(trackKey)) {
            StudioDraftLoad.Missing -> ready = true
            is StudioDraftLoad.Invalid -> { corruptDraft = true; error = loaded.message }
            is StudioDraftLoad.Found -> {
                if (loaded.draft.expectedPlain != initialPlainText || loaded.draft.expectedLrc.orEmpty() != initialLrc.orEmpty()) {
                    conflictedDraft = loaded.draft
                } else {
                    restoreDraft(loaded.draft)
                    ready = true
                    status = context.getString(R.string.studio_draft_restored_8fef0)
                }
            }
        }
    }

    LaunchedEffect(ready, state.lines, state.mode, state.selectedIndex, state.spanMs, if (follow) null else state.cursorMs, expectedPlain, expectedLrc, audioUri, durationEvidence.originalDurationMs) {
        if (!ready) return@LaunchedEffect
        autosaveJob = coroutineContext.job
        delay(450)
        try { store.save(draft(latestState)); status = context.getString(R.string.studio_draft_saved_64ab0) }
        catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = context.getString(R.string.studio_could_not_save_the_draft_retry_saving_before_clo_de459)
        }
    }

    LaunchedEffect(audioUri, durationEvidence.fileGeneration, ready) {
        waveform = null
        waveError = null
        val uri = audioUri
        val generation = durationEvidence.fileGeneration
        if (!ready || uri == null) { waveProgress = null; return@LaunchedEffect }
        waveProgress = 0f
        try {
            val decoded = decodeKaraokeWaveform(context, Uri.parse(uri)) { if (audioUri == uri && durationEvidence.fileGeneration == generation) waveProgress = it }
            ensureActive()
            if (audioUri != uri || durationEvidence.fileGeneration != generation) return@LaunchedEffect
            waveform = decoded
            val reading = readStudioPlayback(trackKey, sourcePackage)
            updateDuration(durationEvidence.decoded(generation, decoded.durationMs).source(reading.matchesTrack, reading.durationMs))
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (audioUri == uri && durationEvidence.fileGeneration == generation)
                waveError = failure.message ?: context.getString(R.string.studio_could_not_read_audio_select_the_file_again_75ba5)
        } finally { if (audioUri == uri && durationEvidence.fileGeneration == generation) waveProgress = null }
    }

    LaunchedEffect(live, ready) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                val reading = readStudioPlayback(trackKey, sourcePackage)
                playback = reading
                updateDuration(durationEvidence.source(reading.matchesTrack, reading.durationMs))
                if (follow && reading.matchesTrack) state = state.copy(cursorMs = reading.positionMs)
                if (loopEnabled) {
                    val a = loopA
                    val b = loopB
                    if (a == null || b == null) {
                        loopEnabled = false; pendingLoopSeek = null; error = context.getString(R.string.studio_loop_disabled_the_song_source_changed_88ab3)
                    } else {
                        val tick = KaraokeLoopEngine.tick(KaraokeLoop(a, b, pendingLoopSeek), reading.positionMs,
                            reading.playing, reading.canSeek, reading.matchesTrack, SystemClock.elapsedRealtime())
                        loopEnabled = tick.loop != null
                        pendingLoopSeek = tick.loop?.awaitingSinceMs
                        tick.error?.let { error = it }
                        tick.seekToMs?.let {
                            if (!seekStudioPlayback(trackKey, sourcePackage, it)) {
                                loopEnabled = false; pendingLoopSeek = null; error = context.getString(R.string.studio_the_source_does_not_support_seeking_8799a)
                            }
                        }
                    }
                }
                delay(if (reading.playing) 100L else 250L)
            } while (isActive)
        }
    }

    val validation = KaraokeStudioEngine.validationError(state)
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = colors.appBackground) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trackTitle, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artist, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { close() }, enabled = !busy) { Text(context.getString(R.string.studio_close_4ae50), color = colors.textSecondary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.mode == StudioMode.QUICK, enabled = ready && !busy,
                        onClick = { changeMode(StudioMode.QUICK) }, label = { Text("Tap-to-Sync") })
                    FilterChip(selected = state.mode == StudioMode.PRECISE, enabled = ready && !busy,
                        onClick = { changeMode(StudioMode.PRECISE) }, label = { Text(context.getString(R.string.studio_precise_studio_3d525)) })
                }
                if (!ready) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (!corruptDraft && conflictedDraft == null) CircularProgressIndicator(color = colors.accentPrimary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            status?.let { Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                            if (!playback.matchesTrack) Text(context.getString(R.string.studio_open_this_song_in_your_player_to_control_playbac_bb3e5), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            else if (!playback.canSeek) Text(context.getString(R.string.studio_the_source_does_not_allow_seeking_the_studio_cur_96fb4), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            validation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
                        }
                        item {
                            val selected = state.lines.getOrNull(state.selectedIndex)
                            Text(selected?.text ?: context.getString(R.string.studio_add_lyrics_d0c58), style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), color = colors.textPrimary)
                            Text(context.getString(R.string.studio_line_progress, if (selected == null) 0 else state.selectedIndex + 1, state.lines.size, state.lines.count { it.timeMs != null }),
                                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            Text(context.getString(R.string.studio_timestamp, selected?.timeMs?.let { KaraokeStudioEngine.formatTime(it) } ?: context.getString(R.string.studio_unstamped)),
                                fontFamily = FontFamily.Monospace, color = colors.accentPrimary)
                        }
                        if (state.mode == StudioMode.PRECISE) {
                            item {
                                KaraokeStudioTimeline(state, onCursor = { state = state.copy(cursorMs = it) },
                                    onSelect = { state = state.copy(selectedIndex = it) }, onCommit = { i, time -> edit(KaraokeStudioEdit.SetTime(i, time)) },
                                    onInteraction = { follow = false }, waveform = waveform?.peaks.orEmpty(), waveformDurationMs = waveform?.durationMs ?: 0)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { timeEntry = "cursor:" + KaraokeStudioEngine.formatTime(state.cursorMs) }, enabled = !busy) {
                                        Text(KaraokeStudioEngine.formatTime(state.cursorMs), fontFamily = FontFamily.Monospace, color = colors.accentPrimary)
                                    }
                                    TextButton(onClick = { state = state.copy(spanMs = (state.spanMs * 2).coerceAtMost(120000)) }) { Text("−") }
                                    TextButton(onClick = { state = state.copy(spanMs = (state.spanMs / 2).coerceAtLeast(2000)) }) { Text("+") }
                                    Text(context.getString(R.string.studio_span_seconds, (state.spanMs / 1000f).toString()), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    runCatching { audioPicker.launch(arrayOf("audio/*")) }.onFailure { waveError = context.getString(R.string.studio_could_not_open_the_audio_file_picker_6e0b8) }
                                }, enabled = !busy) { Text(if (audioUri == null) context.getString(R.string.studio_choose_audio_for_waveform_37a5e) else context.getString(R.string.studio_choose_another_audio_file_b5ce1)) }
                                waveform?.let {
                                    Text("${it.name} · ${KaraokeStudioEngine.formatTime(it.durationMs)}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    if (playback.matchesTrack && playback.durationMs > 0 && kotlin.math.abs(it.durationMs - playback.durationMs) > 3000)
                                        Text(context.getString(R.string.studio_the_file_duration_differs_from_the_song_in_your__9d999), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                waveProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = colors.accentPrimary) }
                                waveError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                                if (audioUri != null) TextButton(onClick = { invalidateAudio(); audioUri = null }, enabled = !busy) { Text(context.getString(R.string.studio_remove_waveform_a5dfb)) }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StudioNudgeButton(context.getString(R.string.studio_50_ms_822ce), !busy) { edit(KaraokeStudioEdit.Nudge(state.selectedIndex, -50)) }
                                    StudioNudgeButton(context.getString(R.string.studio_50_ms_f5f98), !busy) { edit(KaraokeStudioEdit.Nudge(state.selectedIndex, 50)) }
                                }
                                TextButton(onClick = { timeEntry = "line:" + KaraokeStudioEngine.formatTime(state.lines.getOrNull(state.selectedIndex)?.timeMs ?: state.cursorMs) }, enabled = !busy) { Text(context.getString(R.string.studio_enter_line_time_142d3)) }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { edit(KaraokeStudioEdit.SetTime(state.selectedIndex, state.cursorMs)) }, enabled = !busy) { Text(context.getString(R.string.studio_at_cursor_63db9)) }
                                    TextButton(onClick = { tailEntry = "+500" }, enabled = !busy) { Text(context.getString(R.string.studio_shift_section_96394)) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { loopA = state.cursorMs; loopEnabled = false }, enabled = !busy) { Text("A: ${loopA?.let { KaraokeStudioEngine.formatTime(it) } ?: "—"}") }
                                    TextButton(onClick = { loopB = state.cursorMs; loopEnabled = false }, enabled = !busy) { Text("B: ${loopB?.let { KaraokeStudioEngine.formatTime(it) } ?: "—"}") }
                                }
                                TextButton(enabled = playback.canSeek && !busy, onClick = {
                                    val a = loopA; val b = loopB
                                    if (loopEnabled) { loopEnabled = false; pendingLoopSeek = null }
                                    else if (a == null || b == null || KaraokeLoopEngine.start(a, b, state.durationMs, SystemClock.elapsedRealtime()) == null) error = context.getString(R.string.studio_choose_an_a_b_section_at_least_0_5_seconds_long_81144)
                                    else if (seekStudioPlayback(trackKey, sourcePackage, a)) { loopEnabled = true; pendingLoopSeek = SystemClock.elapsedRealtime() }
                                }) { Text(if (loopEnabled) context.getString(R.string.studio_disable_a_b_loop_1730b) else context.getString(R.string.studio_loop_a_b_5eb9f)) }
                            }
                        } else {
                            item {
                                Button(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                    enabled = playback.matchesTrack && playback.playing && state.lines.isNotEmpty() && !busy,
                                    onClick = {
                                        val now = readStudioPlayback(trackKey, sourcePackage)
                                        if (now.matchesTrack && now.playing && edit(KaraokeStudioEdit.Stamp(state.selectedIndex, now.positionMs, reactionMs))) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            state = state.copy(selectedIndex = (state.selectedIndex + 1).coerceAtMost((state.lines.size - 1).coerceAtLeast(0)))
                                        }
                                    }) { Text(context.getString(R.string.studio_stamp_the_next_line_41c6e)) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(context.getString(R.string.studio_reaction_ms, reactionMs), modifier = Modifier.weight(1f), color = colors.textSecondary)
                                    TextButton(onClick = { reactionMs = (reactionMs - 50).coerceAtLeast(0); prefs.edit().putLong("reaction_lag_ms", reactionMs).apply() }) { Text("−") }
                                    TextButton(onClick = { reactionMs = (reactionMs + 50).coerceAtMost(1000); prefs.edit().putLong("reaction_lag_ms", reactionMs).apply() }) { Text("+") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(enabled = playback.canSeek && !busy, onClick = {
                                        loopEnabled = false
                                        val current = readStudioPlayback(trackKey, sourcePackage)
                                        if (!current.matchesTrack || !seekStudioPlayback(trackKey, sourcePackage, (current.positionMs - 5000).coerceAtLeast(0))) error = context.getString(R.string.studio_could_not_seek_the_source_27ad6)
                                    }) { Text(context.getString(R.string.studio_5_s_1bf9a)) }
                                    OutlinedButton(enabled = playback.canSeek && !busy, onClick = {
                                        loopEnabled = false
                                        val current = readStudioPlayback(trackKey, sourcePackage)
                                        if (!current.matchesTrack || !seekStudioPlayback(trackKey, sourcePackage, current.positionMs + 5000)) error = context.getString(R.string.studio_could_not_seek_the_source_27ad6)
                                    }) { Text(context.getString(R.string.studio_5_s_06bb6)) }
                                }
                            }
                        }
                        item {
                            Row {
                                TextButton(enabled = state.undo.isNotEmpty() && !busy, onClick = { edit(KaraokeStudioEdit.Undo) }) { Text(context.getString(R.string.studio_cancel_0ec75)) }
                                TextButton(enabled = state.redo.isNotEmpty() && !busy, onClick = { edit(KaraokeStudioEdit.Redo) }) { Text(context.getString(R.string.studio_redo_e923b)) }
                                TextButton(enabled = !busy, onClick = { restore = true }) { Text(context.getString(R.string.studio_original_1b2f7)) }
                            }
                            Row {
                                TextButton(enabled = !busy, onClick = { warnText = true }) { Text(context.getString(R.string.studio_lyrics_93970)) }
                                TextButton(enabled = !busy, onClick = { showLrc = true }) { Text("LRC") }
                                TextButton(enabled = !busy && state.lines.isNotEmpty(), onClick = { edit(KaraokeStudioEdit.Clear(state.selectedIndex)) }) { Text(context.getString(R.string.studio_clear_timestamp_47f2f)) }
                            }
                        }
                        itemsIndexed(state.lines, key = { _, line -> line.id }) { index, line ->
                            Column(modifier = Modifier.fillMaxWidth().background(if (index == state.selectedIndex) colors.surfaceCard else colors.appBackground)
                                .clickable(enabled = !busy) { follow = false; state = state.copy(selectedIndex = index, cursorMs = line.timeMs ?: state.cursorMs) }
                                .padding(12.dp).heightIn(min = 48.dp)) {
                                Text("${index + 1} · ${line.timeMs?.let { KaraokeStudioEngine.formatTime(it) } ?: stringResource(R.string.studio_no_marker)}",
                                    style = MaterialTheme.typography.labelMedium, color = colors.accentPrimary)
                                Text(line.text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(KaraokeStudioEngine.formatTime(playback.positionMs), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    TextButton(enabled = playback.canToggle && !busy, onClick = { toggleStudioPlayback(trackKey, sourcePackage) }) { Text(if (playback.playing) context.getString(R.string.studio_pause_03498) else context.getString(R.string.studio_play_c750d)) }
                    TextButton(enabled = playback.matchesTrack && !busy, onClick = { follow = true; state = state.copy(cursorMs = playback.positionMs) }) { Text(context.getString(R.string.studio_to_player_c9976)) }
                    TextButton(enabled = playback.canSeek && !busy, onClick = {
                        loopEnabled = false; pendingLoopSeek = null
                        if (!seekStudioPlayback(trackKey, sourcePackage, state.cursorMs)) error = context.getString(R.string.studio_could_not_seek_the_source_27ad6)
                    }) { Text(context.getString(R.string.studio_to_cursor_61a58)) }
                }
                Button(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = ready && !busy && validation == null,
                    onClick = {
                        val snapshot = state
                        val lrc = KaraokeStudioEngine.exportLrc(snapshot) ?: return@Button
                        scope.launch {
                            busy = true
                            var applied = false
                            try {
                                autosaveJob?.cancelAndJoin()
                                store.save(draft(snapshot))
                                val text = snapshot.lines.map { it.text }
                                val expected = KaraokeStudioEngine.createSession(trackKey, expectedPlain, expectedLrc, snapshot.durationMs).lines.map { it.text }
                                val plain = when {
                                    text == expected -> expectedPlain
                                    snapshot.lines == snapshot.originalLines -> originalPlain
                                    else -> text.joinToString("\n")
                                }
                                onSaveLrc(lrc, plain)
                                applied = true
                                expectedLrc = lrc; expectedPlain = plain
                                store.save(draft(snapshot))
                                status = context.getString(R.string.studio_timings_applied_to_the_song_f8571)
                                error = null
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                error = if (applied) context.getString(R.string.studio_the_song_was_saved_but_the_draft_was_not_retry_s_30f6f)
                                    else failure.message ?: context.getString(R.string.studio_could_not_apply_timings_3f46a)
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) context.getString(R.string.studio_saving_73bfb) else context.getString(R.string.studio_apply_to_song_bcfc3)) }
            }
        }
    }

    conflictedDraft?.let { recovery ->
        AlertDialog(onDismissRequest = {}, title = { Text(context.getString(R.string.studio_a_draft_exists_for_another_lyrics_version_a8480)) },
            text = { Text(error ?: context.getString(R.string.studio_lyrics_changed_after_this_draft_was_created_star_61a76)) },
            confirmButton = { TextButton(onClick = {
                restoreDraft(recovery)
                conflictedDraft = null; ready = true; status = context.getString(R.string.studio_draft_restored_applying_it_will_replace_the_curr_6a8f2)
            }) { Text(context.getString(R.string.studio_restore_draft_f38a8)) } },
            dismissButton = { TextButton(onClick = { scope.launch {
                try { store.archive(trackKey); conflictedDraft = null; ready = true }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message }
            } }) { Text(context.getString(R.string.studio_start_from_saved_song_90026)) } })
    }
    if (corruptDraft) AlertDialog(onDismissRequest = { onDismissRequest() }, title = { Text(context.getString(R.string.studio_draft_is_damaged_ed4ef)) },
        text = { Text(error.orEmpty()) }, confirmButton = { TextButton(onClick = { scope.launch {
            try { store.archive(trackKey); corruptDraft = false; ready = true; error = null }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message }
        } }) { Text(context.getString(R.string.studio_keep_a_copy_and_start_over_93f00)) } }, dismissButton = { TextButton(onClick = onDismissRequest) { Text(context.getString(R.string.studio_close_4ae50)) } })
    if (discardClose) AlertDialog(onDismissRequest = { discardClose = false }, title = { Text(context.getString(R.string.studio_draft_not_saved_83cdf)) },
        text = { Text(context.getString(R.string.studio_your_work_is_still_in_memory_retry_saving_or_lea_41b1e)) },
        confirmButton = { TextButton(onClick = { discardClose = false; close() }) { Text(context.getString(R.string.studio_retry_9e506)) } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(context.getString(R.string.studio_leave_without_saving_b9a56)) } })
    if (restore) AlertDialog(onDismissRequest = { restore = false }, title = { Text(context.getString(R.string.studio_restore_original_timings_45b56)) },
        text = { Text(context.getString(R.string.studio_only_the_draft_will_change_you_can_undo_this_the_ad0bf)) },
        confirmButton = { TextButton(onClick = { edit(KaraokeStudioEdit.RestoreOriginal); restore = false }) { Text(context.getString(R.string.studio_restore_c7aaa)) } },
        dismissButton = { TextButton(onClick = { restore = false }) { Text(context.getString(R.string.studio_cancel_0ec75)) } })
    if (warnText) AlertDialog(onDismissRequest = { warnText = false }, title = { Text(context.getString(R.string.studio_edit_lyrics_5df5b)) },
        text = { Text(context.getString(R.string.studio_changing_lines_resets_their_timestamps_you_can_u_7a4be)) },
        confirmButton = { TextButton(onClick = { warnText = false; editText = true }) { Text(context.getString(R.string.studio_edit_901be)) } },
        dismissButton = { TextButton(onClick = { warnText = false }) { Text(context.getString(R.string.studio_cancel_0ec75)) } })
    if (editText) EditLyricsDialog(trackTitle, artist, state.lines.joinToString("\n") { it.text },
        onDismissRequest = { editText = false }, onSaveLyrics = { if (edit(KaraokeStudioEdit.ReplaceText(it))) editText = false })
    if (showLrc) AlertDialog(onDismissRequest = { showLrc = false }, title = { Text(context.getString(R.string.studio_lrc_precision_0_01_s_ceecc)) },
        text = { Text(KaraokeStudioEngine.exportLrc(state) ?: context.getString(R.string.studio_stamp_all_lines_and_fix_timestamp_order_first_a15ff),
            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace) },
        confirmButton = { TextButton(onClick = { showLrc = false }) { Text(context.getString(R.string.studio_close_4ae50)) } })
    timeEntry?.let { entry ->
        val cursor = entry.startsWith("cursor:")
        var value by remember(entry) { mutableStateOf(entry.substringAfter(':')) }
        var inputError by remember(entry) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { timeEntry = null }, title = { Text(if (cursor) context.getString(R.string.studio_cursor_time_9fef9) else context.getString(R.string.studio_line_time_670fd)) },
            text = { Column { OutlinedTextField(value, onValueChange = { value = it }, label = { Text(context.getString(R.string.studio_mm_ss_mmm_daf54)) }, singleLine = true)
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(onClick = {
                val parsed = KaraokeStudioEngine.parseTimeInput(value)
                if (parsed == null) inputError = context.getString(R.string.studio_enter_a_time_for_example_01_23_450_a2a57)
                else if (cursor) { follow = false; state = state.copy(cursorMs = if (state.durationMs > 0) parsed.coerceAtMost(state.durationMs) else parsed); timeEntry = null }
                else if (edit(KaraokeStudioEdit.SetTime(state.selectedIndex, parsed))) timeEntry = null
                else inputError = error
            }) { Text(context.getString(R.string.studio_done_398c7)) } }, dismissButton = { TextButton(onClick = { timeEntry = null }) { Text(context.getString(R.string.studio_cancel_0ec75)) } })
    }
    tailEntry?.let { entry ->
        var value by remember(entry) { mutableStateOf(entry) }
        var inputError by remember(entry) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { tailEntry = null }, title = { Text(context.getString(R.string.studio_shift_this_line_and_following_lines_eaede)) },
            text = { Column { Text(context.getString(R.string.studio_positive_moves_later_negative_moves_earlier_the__0a074))
                OutlinedTextField(value, onValueChange = { value = it }, label = { Text(context.getString(R.string.studio_milliseconds_500_or_500_7f0ac)) }, singleLine = true)
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(onClick = {
                val delta = value.replace('−', '-').trim().toLongOrNull()
                if (delta == null) inputError = context.getString(R.string.studio_enter_a_whole_number_of_milliseconds_7101a)
                else if (edit(KaraokeStudioEdit.ShiftTail(state.selectedIndex, delta))) tailEntry = null
                else inputError = error
            }) { Text(context.getString(R.string.studio_shift_8b4ba)) } }, dismissButton = { TextButton(onClick = { tailEntry = null }) { Text(context.getString(R.string.studio_cancel_0ec75)) } })
    }
}

@Composable
private fun StudioNudgeButton(label: String, enabled: Boolean, onNudge: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val action by rememberUpdatedState(onNudge)
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        delay(400)
        while (isActive) { action(); delay(100) }
    }
    OutlinedButton(onClick = onNudge, enabled = enabled, interactionSource = interactions, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}
