package com.lirix.app.ui.components

import android.os.SystemClock
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
                .onFailure { error = "Доступ к аудио временный: после перезапуска может потребоваться выбрать файл снова." }
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
                error = "Не удалось сохранить черновик. Можно повторить или выйти без последних изменений."
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
                error = "Не удалось сохранить черновик перед сменой режима"
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
            error = it.message ?: "Не удалось открыть разметку"
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
                    status = "Черновик восстановлен"
                }
            }
        }
    }

    LaunchedEffect(ready, state.lines, state.mode, state.selectedIndex, state.spanMs, if (follow) null else state.cursorMs, expectedPlain, expectedLrc, audioUri, durationEvidence.originalDurationMs) {
        if (!ready) return@LaunchedEffect
        autosaveJob = coroutineContext.job
        delay(450)
        try { store.save(draft(latestState)); status = "Черновик сохранён" }
        catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = "Не удалось записать черновик. Не закрывайте студию, пока не повторите сохранение."
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
                waveError = failure.message ?: "Не удалось прочитать аудио. Выберите файл заново."
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
                        loopEnabled = false; pendingLoopSeek = null; error = "Повтор выключен: источник песни изменился"
                    } else {
                        val tick = KaraokeLoopEngine.tick(KaraokeLoop(a, b, pendingLoopSeek), reading.positionMs,
                            reading.playing, reading.canSeek, reading.matchesTrack, SystemClock.elapsedRealtime())
                        loopEnabled = tick.loop != null
                        pendingLoopSeek = tick.loop?.awaitingSinceMs
                        tick.error?.let { error = it }
                        tick.seekToMs?.let {
                            if (!seekStudioPlayback(trackKey, sourcePackage, it)) {
                                loopEnabled = false; pendingLoopSeek = null; error = "Источник не поддерживает перемотку"
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
                    TextButton(onClick = { close() }, enabled = !busy) { Text("Закрыть", color = colors.textSecondary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.mode == StudioMode.QUICK, enabled = ready && !busy,
                        onClick = { changeMode(StudioMode.QUICK) }, label = { Text("Tap-to-Sync") })
                    FilterChip(selected = state.mode == StudioMode.PRECISE, enabled = ready && !busy,
                        onClick = { changeMode(StudioMode.PRECISE) }, label = { Text("Точная студия") })
                }
                if (!ready) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (!corruptDraft && conflictedDraft == null) CircularProgressIndicator(color = colors.accentPrimary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            status?.let { Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                            if (!playback.matchesTrack) Text("Откройте эту песню в плеере для управления воспроизведением. Метки можно править без него.", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            else if (!playback.canSeek) Text("Источник не разрешает перемотку; курсор студии остаётся независимым.", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            validation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
                        }
                        item {
                            val selected = state.lines.getOrNull(state.selectedIndex)
                            Text(selected?.text ?: "Добавьте текст песни", style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), color = colors.textPrimary)
                            Text("Строка ${if (selected == null) 0 else state.selectedIndex + 1} из ${state.lines.size} · ${state.lines.count { it.timeMs != null }} меток",
                                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            Text("Метка: ${selected?.timeMs?.let { KaraokeStudioEngine.formatTime(it) } ?: "не поставлена"}",
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
                                    Text("${(state.spanMs / 1000f).toString().replace('.', ',')} с", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    runCatching { audioPicker.launch(arrayOf("audio/*")) }.onFailure { waveError = "Не удалось открыть выбор аудиофайла" }
                                }, enabled = !busy) { Text(if (audioUri == null) "Выбрать аудио для волны" else "Другой аудиофайл") }
                                waveform?.let {
                                    Text("${it.name} · ${KaraokeStudioEngine.formatTime(it.durationMs)}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    if (playback.matchesTrack && playback.durationMs > 0 && kotlin.math.abs(it.durationMs - playback.durationMs) > 3000)
                                        Text("Длительность файла отличается от песни в плеере. Проверьте, что это нужная версия записи.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                waveProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = colors.accentPrimary) }
                                waveError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                                if (audioUri != null) TextButton(onClick = { invalidateAudio(); audioUri = null }, enabled = !busy) { Text("Убрать волну") }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StudioNudgeButton("−50 мс", !busy) { edit(KaraokeStudioEdit.Nudge(state.selectedIndex, -50)) }
                                    StudioNudgeButton("+50 мс", !busy) { edit(KaraokeStudioEdit.Nudge(state.selectedIndex, 50)) }
                                }
                                TextButton(onClick = { timeEntry = "line:" + KaraokeStudioEngine.formatTime(state.lines.getOrNull(state.selectedIndex)?.timeMs ?: state.cursorMs) }, enabled = !busy) { Text("Ввести время строки") }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { edit(KaraokeStudioEdit.SetTime(state.selectedIndex, state.cursorMs)) }, enabled = !busy) { Text("На курсор") }
                                    TextButton(onClick = { tailEntry = "+500" }, enabled = !busy) { Text("Сдвинуть фрагмент") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { loopA = state.cursorMs; loopEnabled = false }, enabled = !busy) { Text("A: ${loopA?.let { KaraokeStudioEngine.formatTime(it) } ?: "—"}") }
                                    TextButton(onClick = { loopB = state.cursorMs; loopEnabled = false }, enabled = !busy) { Text("B: ${loopB?.let { KaraokeStudioEngine.formatTime(it) } ?: "—"}") }
                                }
                                TextButton(enabled = playback.canSeek && !busy, onClick = {
                                    val a = loopA; val b = loopB
                                    if (loopEnabled) { loopEnabled = false; pendingLoopSeek = null }
                                    else if (a == null || b == null || KaraokeLoopEngine.start(a, b, state.durationMs, SystemClock.elapsedRealtime()) == null) error = "Выберите фрагмент A–B длиной не меньше 0,5 с"
                                    else if (seekStudioPlayback(trackKey, sourcePackage, a)) { loopEnabled = true; pendingLoopSeek = SystemClock.elapsedRealtime() }
                                }) { Text(if (loopEnabled) "Выключить повтор A–B" else "Повторять A–B") }
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
                                    }) { Text("Поставить метку следующей строки") }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Реакция: $reactionMs мс", modifier = Modifier.weight(1f), color = colors.textSecondary)
                                    TextButton(onClick = { reactionMs = (reactionMs - 50).coerceAtLeast(0); prefs.edit().putLong("reaction_lag_ms", reactionMs).apply() }) { Text("−") }
                                    TextButton(onClick = { reactionMs = (reactionMs + 50).coerceAtMost(1000); prefs.edit().putLong("reaction_lag_ms", reactionMs).apply() }) { Text("+") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(enabled = playback.canSeek && !busy, onClick = {
                                        loopEnabled = false
                                        val current = readStudioPlayback(trackKey, sourcePackage)
                                        if (!current.matchesTrack || !seekStudioPlayback(trackKey, sourcePackage, (current.positionMs - 5000).coerceAtLeast(0))) error = "Не удалось перемотать источник"
                                    }) { Text("−5 с") }
                                    OutlinedButton(enabled = playback.canSeek && !busy, onClick = {
                                        loopEnabled = false
                                        val current = readStudioPlayback(trackKey, sourcePackage)
                                        if (!current.matchesTrack || !seekStudioPlayback(trackKey, sourcePackage, current.positionMs + 5000)) error = "Не удалось перемотать источник"
                                    }) { Text("+5 с") }
                                }
                            }
                        }
                        item {
                            Row {
                                TextButton(enabled = state.undo.isNotEmpty() && !busy, onClick = { edit(KaraokeStudioEdit.Undo) }) { Text("Отмена") }
                                TextButton(enabled = state.redo.isNotEmpty() && !busy, onClick = { edit(KaraokeStudioEdit.Redo) }) { Text("Повтор") }
                                TextButton(enabled = !busy, onClick = { restore = true }) { Text("Исходник") }
                            }
                            Row {
                                TextButton(enabled = !busy, onClick = { warnText = true }) { Text("Текст") }
                                TextButton(enabled = !busy, onClick = { showLrc = true }) { Text("LRC") }
                                TextButton(enabled = !busy && state.lines.isNotEmpty(), onClick = { edit(KaraokeStudioEdit.Clear(state.selectedIndex)) }) { Text("Убрать метку") }
                            }
                        }
                        itemsIndexed(state.lines, key = { _, line -> line.id }) { index, line ->
                            Column(modifier = Modifier.fillMaxWidth().background(if (index == state.selectedIndex) colors.surfaceCard else colors.appBackground)
                                .clickable(enabled = !busy) { follow = false; state = state.copy(selectedIndex = index, cursorMs = line.timeMs ?: state.cursorMs) }
                                .padding(12.dp).heightIn(min = 48.dp)) {
                                Text("${index + 1} · ${line.timeMs?.let { KaraokeStudioEngine.formatTime(it) } ?: "Без метки"}",
                                    style = MaterialTheme.typography.labelMedium, color = colors.accentPrimary)
                                Text(line.text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(KaraokeStudioEngine.formatTime(playback.positionMs), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    TextButton(enabled = playback.canToggle && !busy, onClick = { toggleStudioPlayback(trackKey, sourcePackage) }) { Text(if (playback.playing) "Пауза" else "Играть") }
                    TextButton(enabled = playback.matchesTrack && !busy, onClick = { follow = true; state = state.copy(cursorMs = playback.positionMs) }) { Text("К плееру") }
                    TextButton(enabled = playback.canSeek && !busy, onClick = {
                        loopEnabled = false; pendingLoopSeek = null
                        if (!seekStudioPlayback(trackKey, sourcePackage, state.cursorMs)) error = "Не удалось перемотать источник"
                    }) { Text("К курсору") }
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
                                status = "Разметка применена к песне"
                                error = null
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                error = if (applied) "Песня сохранена, но черновик не записан. Повторите сохранение перед выходом."
                                    else failure.message ?: "Не удалось применить разметку"
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "Сохраняем…" else "Применить к песне") }
            }
        }
    }

    conflictedDraft?.let { recovery ->
        AlertDialog(onDismissRequest = {}, title = { Text("Есть черновик другой версии текста") },
            text = { Text(error ?: "Текст песни изменился после создания черновика. Можно начать с сохранённой песни или явно восстановить прежнюю работу.") },
            confirmButton = { TextButton(onClick = {
                restoreDraft(recovery)
                conflictedDraft = null; ready = true; status = "Черновик восстановлен. Применение заменит текущую разметку песни."
            }) { Text("Восстановить черновик") } },
            dismissButton = { TextButton(onClick = { scope.launch {
                try { store.archive(trackKey); conflictedDraft = null; ready = true }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message }
            } }) { Text("Начать с песни") } })
    }
    if (corruptDraft) AlertDialog(onDismissRequest = { onDismissRequest() }, title = { Text("Черновик повреждён") },
        text = { Text(error.orEmpty()) }, confirmButton = { TextButton(onClick = { scope.launch {
            try { store.archive(trackKey); corruptDraft = false; ready = true; error = null }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message }
        } }) { Text("Сохранить копию и начать заново") } }, dismissButton = { TextButton(onClick = onDismissRequest) { Text("Закрыть") } })
    if (discardClose) AlertDialog(onDismissRequest = { discardClose = false }, title = { Text("Черновик не записан") },
        text = { Text("Работа пока остаётся в памяти. Повторите сохранение или выйдите без последних изменений.") },
        confirmButton = { TextButton(onClick = { discardClose = false; close() }) { Text("Повторить") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Выйти без сохранения") } })
    if (restore) AlertDialog(onDismissRequest = { restore = false }, title = { Text("Вернуть исходную разметку?") },
        text = { Text("Изменится только черновик. Действие можно отменить; песня изменится после применения.") },
        confirmButton = { TextButton(onClick = { edit(KaraokeStudioEdit.RestoreOriginal); restore = false }) { Text("Вернуть") } },
        dismissButton = { TextButton(onClick = { restore = false }) { Text("Отмена") } })
    if (warnText) AlertDialog(onDismissRequest = { warnText = false }, title = { Text("Изменить текст?") },
        text = { Text("Если строки изменятся, их временные метки будут сброшены. Это можно отменить.") },
        confirmButton = { TextButton(onClick = { warnText = false; editText = true }) { Text("Редактировать") } },
        dismissButton = { TextButton(onClick = { warnText = false }) { Text("Отмена") } })
    if (editText) EditLyricsDialog(trackTitle, artist, state.lines.joinToString("\n") { it.text },
        onDismissRequest = { editText = false }, onSaveLyrics = { if (edit(KaraokeStudioEdit.ReplaceText(it))) editText = false })
    if (showLrc) AlertDialog(onDismissRequest = { showLrc = false }, title = { Text("LRC · точность 0,01 с") },
        text = { Text(KaraokeStudioEngine.exportLrc(state) ?: "Сначала разметьте все строки и исправьте порядок меток.",
            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace) },
        confirmButton = { TextButton(onClick = { showLrc = false }) { Text("Закрыть") } })
    timeEntry?.let { entry ->
        val cursor = entry.startsWith("cursor:")
        var value by remember(entry) { mutableStateOf(entry.substringAfter(':')) }
        var inputError by remember(entry) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { timeEntry = null }, title = { Text(if (cursor) "Время курсора" else "Время строки") },
            text = { Column { OutlinedTextField(value, onValueChange = { value = it }, label = { Text("мм:сс.мс") }, singleLine = true)
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(onClick = {
                val parsed = KaraokeStudioEngine.parseTimeInput(value)
                if (parsed == null) inputError = "Введите время, например 01:23.450"
                else if (cursor) { follow = false; state = state.copy(cursorMs = if (state.durationMs > 0) parsed.coerceAtMost(state.durationMs) else parsed); timeEntry = null }
                else if (edit(KaraokeStudioEdit.SetTime(state.selectedIndex, parsed))) timeEntry = null
                else inputError = error
            }) { Text("Готово") } }, dismissButton = { TextButton(onClick = { timeEntry = null }) { Text("Отмена") } })
    }
    tailEntry?.let { entry ->
        var value by remember(entry) { mutableStateOf(entry) }
        var inputError by remember(entry) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { tailEntry = null }, title = { Text("Сдвиг выбранной и следующих строк") },
            text = { Column { Text("Плюс — позже, минус — раньше. Сдвиг применяется ко всему фрагменту сразу.")
                OutlinedTextField(value, onValueChange = { value = it }, label = { Text("Миллисекунды: +500 или −500") }, singleLine = true)
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(onClick = {
                val delta = value.replace('−', '-').trim().toLongOrNull()
                if (delta == null) inputError = "Введите целое число миллисекунд"
                else if (edit(KaraokeStudioEdit.ShiftTail(state.selectedIndex, delta))) tailEntry = null
                else inputError = error
            }) { Text("Сдвинуть") } }, dismissButton = { TextButton(onClick = { tailEntry = null }) { Text("Отмена") } })
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
