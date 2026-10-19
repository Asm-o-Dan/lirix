package com.lirix.app.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.lirix.app.desktop.library.DesktopLibraryRepository
import com.lirix.app.desktop.share.*
import com.lirix.app.desktop.system.*
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.platform.MediaIngressProvider
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.theme.*
import kotlinx.coroutines.*
import javax.imageio.ImageIO

@Composable
internal fun DesktopFeatureDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DialogWindow(onCloseRequest = onDismiss, title = title, state = rememberDialogState(width = 900.dp, height = 720.dp)) {
        val c = LirixThemeTokens.colors
        Surface(Modifier.fillMaxSize(), color = c.appBackground) { Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}

@Composable
internal fun DesktopHotkeyHelp(failures: List<String>, onDismiss: () -> Unit) = DesktopFeatureDialog("Горячие клавиши", onDismiss) {
    val c = LirixThemeTokens.colors
    Text("Горячие клавиши", color = c.textPrimary, fontSize = 26.sp)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Работают, даже когда окно Lirix скрыто", color = c.accentPrimary)
        HotkeyCatalog.global.forEach { binding -> Row(Modifier.fillMaxWidth()) { Text(binding.keys, color = c.textPrimary, modifier = Modifier.width(230.dp)); Text(binding.title, color = c.textSecondary) } }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("Внутри окна Lirix", color = c.accentPrimary)
        HotkeyCatalog.local.forEach { (keys, title) -> Row(Modifier.fillMaxWidth()) { Text(keys, color = c.textPrimary, modifier = Modifier.width(230.dp)); Text(title, color = c.textSecondary) } }
        if (failures.isNotEmpty()) { Text("Не удалось зарегистрировать", color = c.accentPrimary); failures.forEach { Text(it, color = c.textSecondary) } }
        Text("Закреплённый оверлей пропускает щелчки мыши. Ctrl+Alt+L возвращает перетаскивание, изменение размера и кнопки. А+/А− меняют размер текста.", color = c.textSecondary)
    }
    Button(onClick = onDismiss) { Text("Понятно") }
}

@Composable
internal fun DesktopLyricsEditor(track: TrackEntity, repository: DesktopLibraryRepository, draft: DesktopLyricsDraft, onDismiss: () -> Unit,
    onSave: suspend (String?, String?, String?) -> Unit) {
    val content = draft.content
    var confirmClose by remember { mutableStateOf(false) }
    val requestClose = { if (!content.saving) { if (content.dirty) confirmClose = true else onDismiss() }; Unit }
    DesktopFeatureDialog("Текст — ${track.title}", requestClose) {
    val c = LirixThemeTokens.colors
    val scope = rememberCoroutineScope()
    val plain = content.value.plain
    val lrc = content.value.lrc
    val chords = content.value.chords
    var tab by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(track.trackKey, loadAttempt) {
        if (!draft.chordsLoaded) try { draft.loadChords(repository.savedLyrics(track.trackKey)?.chordsAmDm.orEmpty()); error = null }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = "Не удалось загрузить аккорды: ${e.message}" }
    }
    fun updateText(target: Int, text: String) {
        content.value = when (target) { 0 -> content.value.copy(plain = text); 1 -> content.value.copy(lrc = text); else -> content.value.copy(chords = text) }
    }
    val canSave = draft.chordsLoaded && !content.saving && (lrc.isBlank() || LrcSyncEngine.parseLrcLinesStrict(lrc).isNotEmpty())
    fun save() { if (canSave) scope.launch { if (content.save { onSave(it.plain.ifBlank { null }, it.lrc.ifBlank { null }, it.chords.ifBlank { null }) }) onDismiss() } }
    Text(track.title, color = c.textPrimary, fontSize = 24.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Текст", "LRC / караоке", "Аккорды").forEachIndexed { index, title -> FilterChip(tab == index, { tab = index }, label = { Text(title) }) } }
    OutlinedTextField(when(tab) { 0 -> plain; 1 -> lrc; else -> chords }, { updateText(tab, it) }, enabled = !content.saving && (tab != 2 || draft.chordsLoaded),
        modifier = Modifier.fillMaxWidth().weight(1f), label = { Text(if (tab == 1) "[00:12.34] Строка песни" else "Текст") })
    error?.let { Text(it, color = c.accentPrimary) }
    content.error?.let { Text("Ошибка сохранения: $it", color = c.accentPrimary) }
    if (!draft.chordsLoaded) TextButton(onClick = { loadAttempt++ }) { Text("Загрузить аккорды повторно") }
    if (lrc.isNotBlank() && !canSave && !content.saving && draft.chordsLoaded) Text("LRC не содержит корректных временных меток", color = c.accentPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(enabled = !content.saving && (tab != 2 || draft.chordsLoaded), onClick = { val targetTab = tab; scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { DesktopFiles.choose("Импортировать текст или LRC", if (targetTab == 1) "lrc" else "txt", false) } ?: return@launch
                val text = withContext(Dispatchers.IO) { require(file.length() <= 2_000_000) { "Файл слишком большой" }; file.readText() }
                if (!content.saving) updateText(targetTab, text)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
        } }) { Text("Импорт файла") }
        OutlinedButton(onClick = { scope.launch {
            try {
                val extension = if (tab == 1) "lrc" else "txt"
                val file = withContext(Dispatchers.IO) { DesktopFiles.choose("Экспортировать текст", extension, true) } ?: return@launch
                withContext(Dispatchers.IO) { file.writeText(when(tab) { 0 -> plain; 1 -> lrc; else -> chords }) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
        } }) { Text("Экспорт файла") }
        Spacer(Modifier.weight(1f))
        TextButton(enabled = !content.saving, onClick = requestClose) { Text("Отмена") }
        Button(enabled = canSave, onClick = ::save) { Text(if (content.saving) "Сохраняем…" else "Сохранить") }
    }
    if (confirmClose) DesktopDraftCloseConfirmation(content.saving, canSave, { confirmClose = false }, onDismiss, ::save)
    }
}

@Composable
internal fun DesktopTapSyncDialog(track: TrackEntity, playback: LivePlaybackSnapshot?, ingress: MediaIngressProvider,
    draft: DesktopDraft<List<com.lirix.app.feature.lyrics.SyncLineItem>>, onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    var confirmClose by remember { mutableStateOf(false) }
    val requestClose = { if (!draft.saving) { if (draft.dirty) confirmClose = true else onDismiss() }; Unit }
    DesktopFeatureDialog("Tap-to-Sync — ${track.title}", requestClose) {
    val c = LirixThemeTokens.colors
    var lines by draft::value
    val scope = rememberCoroutineScope()
    val canSave = !draft.saving && lines.isNotEmpty() && lines.all { it.timestampMs != null } && LrcSyncEngine.isMonotonic(lines)
    fun save() { if (canSave) scope.launch { if (draft.save { onSave(LrcSyncEngine.buildLrcString(it)) }) onDismiss() } }
    var index by remember(track.trackKey) { mutableIntStateOf(lines.indexOfFirst { it.timestampMs == null }.let { if (it < 0) lines.size else it }) }
    var position by remember { mutableLongStateOf(0) }
    var preview by remember { mutableStateOf(false) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(playback) { while (isActive) { position = playback?.currentPositionMs() ?: 0; delay(40) } }
    LaunchedEffect(index) { if (lines.isNotEmpty()) list.animateScrollToItem(index.coerceAtMost(lines.lastIndex)) }
    fun stamp() { if (!draft.saving && playback != null && index in lines.indices) { lines = LrcSyncEngine.recordTimestamp(lines, index, playback.currentPositionMs()); index++ } }
    fun undo() { if (!draft.saving) { val (updated, target) = LrcSyncEngine.undoTimestamp(lines, index); lines = updated; index = target } }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || preview) false
        else when { e.isCtrlPressed && e.key == Key.Z -> { undo(); true }; (e.key == Key.Spacebar || e.key == Key.Enter) && !e.isCtrlPressed -> { stamp(); true }; else -> false }
    }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${track.artist} — ${track.title}", color = c.textPrimary, fontSize = 23.sp)
        Text("Начало строки: Space или Enter · Отменить: Ctrl+Z", color = c.textSecondary)
        if (playback == null) Text("Для синхронизации включите этот трек в музыкальном плеере ПК", color = c.accentPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { ingress.seekTo((position - 5_000).coerceAtLeast(0)) }, enabled = playback != null) { Text("−5 сек") }
            OutlinedButton(onClick = { ingress.playPause() }, enabled = playback != null) { Text(if (playback?.isPlaying == true) "Пауза" else "Продолжить") }
            OutlinedButton(onClick = { ingress.seekTo(position + 5_000) }, enabled = playback != null) { Text("+5 сек") }
            Text(LrcSyncEngine.formatTimeLabel(position), color = c.textPrimary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { preview = !preview }) { Text(if (preview) "К разметке" else "Просмотр LRC") }
        }
        if (preview) SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { Text(LrcSyncEngine.buildLrcString(lines), color = c.textPrimary) }
        else LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(lines) { i, line -> Surface(color = if (i == index) c.surfaceElevated else c.surfaceCard, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { index = i }) { Text(line.timestampMs?.let(LrcSyncEngine::formatTimeLabel) ?: "—:—") }
                    Text(line.text, color = if (i == index) c.accentPrimary else c.textPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { lines = LrcSyncEngine.adjustTimestamp(lines, i, -100) }, enabled = !draft.saving && line.timestampMs != null) { Text("−0.1") }
                    TextButton(onClick = { lines = LrcSyncEngine.adjustTimestamp(lines, i, 100) }, enabled = !draft.saving && line.timestampMs != null) { Text("+0.1") }
                }
            } }
        }
        if (!LrcSyncEngine.isMonotonic(lines)) Text("Некоторые метки идут назад во времени. Исправьте их перед сохранением.", color = c.accentPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = ::stamp, enabled = playback != null && index in lines.indices) { Text("Начало строки · ${index.coerceAtMost(lines.size)}/${lines.size}") }
            OutlinedButton(onClick = ::undo, enabled = lines.any { it.timestampMs != null }) { Text("Отменить") }
            Spacer(Modifier.weight(1f)); TextButton(enabled = !draft.saving, onClick = requestClose) { Text("Закрыть") }
            Button(onClick = ::save, enabled = canSave) { Text(if (draft.saving) "Сохраняем…" else "Сохранить LRC") }
        }
        draft.error?.let { Text("Ошибка сохранения: $it", color = c.accentPrimary) }
    }
    if (confirmClose) DesktopDraftCloseConfirmation(draft.saving, canSave, { confirmClose = false }, onDismiss, ::save)
    }
}

@Composable
private fun DesktopDraftCloseConfirmation(busy: Boolean, canSave: Boolean, onCancel: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("Сохранить изменения?") },
        text = { Text("В редакторе остались несохранённые правки.") },
        confirmButton = { TextButton(enabled = canSave, onClick = { onCancel(); onSave() }) { Text("Сохранить") } },
        dismissButton = { Row { TextButton(enabled = !busy, onClick = onCancel) { Text("Продолжить редактирование") }; TextButton(enabled = !busy, onClick = onDiscard) { Text("Не сохранять") } } })
}

@Composable
internal fun DesktopPosterDialog(track: TrackEntity, colors: LirixColors, onDismiss: () -> Unit, onMessage: (String) -> Unit) = DesktopFeatureDialog("Постер — ${track.title}", onDismiss) {
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(PosterFormat.STORIES) }
    var lyric by remember { mutableStateOf(track.plainLyrics?.lineSequence()?.firstOrNull { it.isNotBlank() }.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(track, lyric, format, colors) {
        preview = withContext(Dispatchers.Default) { DesktopPosterRenderer.track(track, lyric, colors, format).toComposeImageBitmap() }
    }
    ChoiceDropdown("Формат", format.label, PosterFormat.entries.map { it.label }) { label -> format = PosterFormat.entries.first { it.label == label } }
    OutlinedTextField(lyric, { lyric = it }, label = { Text("Цитата / строка песни") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    preview?.let { Image(it, "Предпросмотр постера", modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit) } ?: Spacer(Modifier.weight(1f))
    error?.let { Text(it, color = colors.accentPrimary) }
    Row { TextButton(onClick = onDismiss) { Text("Закрыть") }; Spacer(Modifier.weight(1f)); Button(enabled = !busy, onClick = { scope.launch {
        busy = true
        try {
            val destination = withContext(Dispatchers.IO) { DesktopFiles.choose("Сохранить постер", "png", true, "lirix-track.png") } ?: return@launch
            val snapshotText = lyric; val snapshotFormat = format
            withContext(Dispatchers.IO) { ImageIO.write(DesktopPosterRenderer.track(track, snapshotText, colors, snapshotFormat), "png", destination) }
            onMessage("Постер сохранён: ${destination.name}"); onDismiss()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } finally { busy = false }
    } }) { Text(if (busy) "Сохранение…" else "Сохранить PNG") } }
}
