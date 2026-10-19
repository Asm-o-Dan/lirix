package com.lirix.app.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.analytics.*
import com.lirix.app.desktop.hud.HudSettings
import com.lirix.app.desktop.ingress.AudioLevels
import com.lirix.app.desktop.library.DesktopLibraryRepository
import com.lirix.app.desktop.library.DesktopLibraryFilter
import com.lirix.app.desktop.library.DesktopLibraryEmpty
import com.lirix.app.desktop.library.buildDesktopLibraryViewState
import com.lirix.app.desktop.lyrics.DesktopLyricsService
import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.desktop.share.*
import com.lirix.app.desktop.system.*
import com.lirix.app.desktop.ui.components.ShortcutButton
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.platform.MediaIngressProvider
import com.lirix.app.storage.*
import com.lirix.app.storage.blacklist.*
import com.lirix.app.ui.components.ThemeSelectionDialog
import com.lirix.app.ui.theme.*
import kotlinx.coroutines.*
import java.util.UUID
import javax.imageio.ImageIO

@Composable
fun DesktopWorkspace(
    repository: DesktopLibraryRepository, lyricsService: DesktopLyricsService,
    playback: LivePlaybackSnapshot?, ingress: MediaIngressProvider, lyrics: DesktopLyricsState,
    selectedTheme: AppThemeType, onTheme: (AppThemeType) -> Unit,
    hudVisible: Boolean, onToggleHud: () -> Unit, hudSettings: HudSettings, onHudSettings: (HudSettings) -> Unit,
    onEditHud: () -> Unit, onMini: () -> Unit, audioLevels: AudioLevels?,
    helpVisible: Boolean, onHelp: (Boolean) -> Unit, hotkeyFailures: List<String>,
    drafts: DesktopDraftStore = remember { DesktopDraftStore() },
    automaticTranslations: com.lirix.app.translations.AutomaticSongTranslations? = null
) {
    val colors = LirixThemeTokens.colors
    val scope = rememberCoroutineScope()
    val tracks by repository.tracks.collectAsState()
    val sessions by repository.sessions.collectAsState()
    val achievements by repository.achievements.collectAsState()
    val rules by repository.rules.collectAsState()
    val error by repository.error.collectAsState()
    var page by remember { mutableIntStateOf(0) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val notesDrafts = drafts.notes
    LaunchedEffect(tracks) {
        tracks.forEach { track ->
            if (notesDrafts[track.trackKey] == track.userNotes) notesDrafts.remove(track.trackKey)
        }
    }
    var query by remember { mutableStateOf("") }
    var themeDialog by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<TrackEntity?>(null) }
    var syncTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var exportTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var translationTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val translationProvider = remember { com.lirix.app.translations.AmalgamaTranslationProvider() }
    var message by remember { mutableStateOf<String?>(null) }
    var showSourceTools by remember { mutableStateOf(false) }
    var connectRequest by remember { mutableIntStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    val currentKey = playback?.let(repository::keyFor)
    val currentTrack = tracks.firstOrNull { it.trackKey == currentKey }
    fun editCurrent() { currentTrack?.let { editor = it } }
    fun exportCurrent() { currentTrack?.let { exportTrack = it } }
    Row(Modifier.fillMaxSize().background(colors.appBackground).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when {
            event.key == Key.F1 -> { onHelp(true); true }
            event.isCtrlPressed && event.isShiftPressed && event.key == Key.D -> { page = 0; connectRequest++; true }
            event.isCtrlPressed && !event.isAltPressed && event.key == Key.One -> { page = 0; true }
            event.isCtrlPressed && !event.isAltPressed && event.key == Key.Two -> { page = 1; true }
            event.isCtrlPressed && !event.isAltPressed && event.key == Key.Three -> { page = 2; true }
            event.isCtrlPressed && !event.isAltPressed && event.key == Key.F -> { page = 1; scope.launch { delay(100); searchFocus.requestFocus() }; true }
            event.isCtrlPressed && !event.isAltPressed && event.key == Key.E -> { editCurrent(); true }
            event.isCtrlPressed && !event.isAltPressed && event.key == Key.T -> { syncTrack = currentTrack; true }
            event.isCtrlPressed && event.isShiftPressed && event.key == Key.S -> { exportCurrent(); true }
            else -> false
        }
    }) {
        Column(Modifier.width(172.dp).fillMaxHeight().background(colors.surfaceCard).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lirix", color = colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
            listOf("Сейчас играет", "Медиатека", "Итоги", "Настройки").forEachIndexed { index, label ->
                if (page == index) Button(onClick = { page = index }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                else TextButton(onClick = { page = index }, modifier = Modifier.fillMaxWidth()) { Text(label, color = colors.textPrimary) }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onToggleHud, modifier = Modifier.fillMaxWidth()) { Text(if (hudVisible) "Скрыть оверлей" else "Оверлей") }
            TextButton(onClick = onEditHud, modifier = Modifier.fillMaxWidth()) { Text("Настроить оверлей") }
            TextButton(onClick = { themeDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Тема оформления") }
            TextButton(onClick = { onHelp(true) }, modifier = Modifier.fillMaxWidth()) { Text("Клавиши · F1") }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val pendingLyrics = tracks.filter { drafts.lyrics[it.trackKey]?.content?.dirty == true }
            val pendingSync = tracks.filter { drafts.sync[it.trackKey]?.dirty == true }
            val pendingNotes = tracks.filter { drafts.notes.containsKey(it.trackKey) }
            if (pendingLyrics.isNotEmpty() || pendingSync.isNotEmpty() || pendingNotes.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                    Text("Несохранённые черновики", color = colors.textSecondary)
                    pendingLyrics.forEach { track -> TextButton(onClick = { editor = track }) { Text("Текст: ${track.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    pendingSync.forEach { track -> TextButton(onClick = { syncTrack = track }) { Text("Разметка: ${track.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    pendingNotes.forEach { track -> TextButton(onClick = { page = 1; selectedKey = track.trackKey; query = "" }) { Text("Заметки: ${track.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
            }
            if (error != null || message != null) Row(Modifier.fillMaxWidth().background(colors.surfaceElevated).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error ?: message.orEmpty(), color = colors.textPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { repository.clearError(); message = null }) { Text("Закрыть") }
            }
            when (page) {
                0 -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { currentTrack?.let { repository.favorite(it.trackKey, !it.isFavorite) } }, enabled = currentTrack != null) { Text(if (currentTrack?.isFavorite == true) "В избранном" else "В избранное") }
                        ShortcutButton("Текст / LRC", "Ctrl+E", currentTrack != null, ::editCurrent)
                        ShortcutButton("Tap-to-Sync", "Ctrl+T", currentTrack != null) { syncTrack = currentTrack }
                        ShortcutButton("Постер", "Ctrl+Shift+S", currentTrack != null, ::exportCurrent)
                        TextButton(onClick = { showSourceTools = true }, enabled = currentTrack != null) { Text("Источники") }
                    }
                    DesktopDeckScreen(playback, ingress, selectedTheme, onTheme, lyrics, hudVisible, onToggleHud,
                        onToggleCompactMode = onMini, audioLevels = audioLevels, modifier = Modifier.weight(1f), connectRequestVersion = connectRequest,
                        onOpenTapSync = { syncTrack = currentTrack }, canTapSync = currentTrack != null,
                        textContent = currentTrack?.let { track -> @Composable {
                            com.lirix.app.translations.TranslationPanel(track.trackKey, "${track.artist} — ${track.title}",
                                com.lirix.app.desktop.ui.components.desktopPlainLyrics(lyrics), repository.database.songTranslationDao(), translationProvider::load,
                                automatic = automaticTranslations, title = track.title, artist = track.artist)
                        } })
                }
                1 -> DesktopLibraryPanel(tracks, sessions, selectedKey, query, { query = it }, searchFocus,
                    onSelect = { selectedKey = it.trackKey }, onFavorite = { t -> repository.favorite(t.trackKey, !t.isFavorite) },
                    onNotes = { t, notes -> repository.editNotes(t.trackKey, notes) }, onEdit = { editor = it }, onPoster = { exportTrack = it }, onTranslation = { translationTrack = it },
                    notesDrafts = notesDrafts, onNotesDraft = { key, value ->
                        if (tracks.firstOrNull { it.trackKey == key }?.userNotes == value) notesDrafts.remove(key)
                        else notesDrafts[key] = value
                    })
                2 -> DesktopWrappedPanel(tracks, sessions, achievements) { stats, period, format, section ->
                    scope.launch {
                        try {
                            val destination = withContext(Dispatchers.IO) { DesktopFiles.choose("Сохранить итоги", "png", true, "lirix-wrapped.png") } ?: return@launch
                            withContext(Dispatchers.IO) { ImageIO.write(DesktopPosterRenderer.wrapped(stats, period, colors, format, section), "png", destination) }
                            message = "Постер сохранён: ${destination.name}"
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { message = "Ошибка экспорта: ${e.message}" }
                    }
                }
                3 -> DesktopSettingsPanel(rules, tracks, hudSettings, onHudSettings, repository, selectedTheme, onTheme, onEditHud)
            }
        }
    }
    if (themeDialog) ThemeSelectionDialog(selectedTheme, onTheme) { themeDialog = false }
    editor?.let { track -> DesktopLyricsEditor(track, repository, drafts.lyricsFor(track), onDismiss = { drafts.lyrics.remove(track.trackKey); editor = null }) { plain, lrc, chords ->
        lyricsService.saveUserLyrics(track.trackKey, track.title, track.artist, plain, lrc, chords)
    } }
    syncTrack?.let { track -> DesktopTapSyncDialog(track, playback?.takeIf { currentKey == track.trackKey }, ingress, drafts.syncFor(track), onDismiss = { drafts.sync.remove(track.trackKey); syncTrack = null }) { lrc ->
                val cached = repository.savedLyrics(track.trackKey)
                lyricsService.saveUserLyrics(track.trackKey, track.title, track.artist, track.plainLyrics, lrc, cached?.chordsAmDm)
    } }
    exportTrack?.let { track -> DesktopPosterDialog(track, colors, onDismiss = { exportTrack = null }) { text -> message = text } }
    translationTrack?.let { track -> DesktopFeatureDialog("Перевод — ${track.title}", { translationTrack = null }) {
        com.lirix.app.translations.TranslationPanel(track.trackKey, "${track.artist} — ${track.title}",
            track.plainLyrics ?: track.syncedLyrics?.let { com.lirix.app.feature.lyrics.LrcSyncEngine.parseLrcLinesStrict(it).joinToString("\n") { line -> line.text } }.orEmpty(),
            repository.database.songTranslationDao(), translationProvider::load, Modifier.weight(1f), automaticTranslations, track.title, track.artist)
        TextButton(onClick = { translationTrack = null }) { Text("Закрыть") }
    } }
    if (helpVisible) DesktopHotkeyHelp(hotkeyFailures) { onHelp(false) }
    if (showSourceTools && currentTrack != null) DesktopSourceDialog(currentTrack, repository, onDismiss = { showSourceTools = false },
        onRefresh = { lyricsService.onTrackChanged(playback?.title, playback?.artist, playback?.album, true) },
        onReject = { lyricsService.rejectCurrentSource(currentTrack.title, currentTrack.artist, currentTrack.album) },
        onAttach = { text -> scope.launch { lyricsService.saveUserLyrics(currentTrack.trackKey, currentTrack.title, currentTrack.artist, text, null, null); showSourceTools = false } })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DesktopLibraryPanel(tracks: List<TrackEntity>, sessions: List<ListeningSessionEntity>, selectedKey: String?, query: String,
    onQuery: (String) -> Unit, focus: FocusRequester, onSelect: (TrackEntity) -> Unit, onFavorite: (TrackEntity) -> Unit,
    onNotes: (TrackEntity, String) -> Unit, onEdit: (TrackEntity) -> Unit, onPoster: (TrackEntity) -> Unit, onTranslation: (TrackEntity) -> Unit,
    notesDrafts: Map<String, String>, onNotesDraft: (String, String) -> Unit) {
    val c = LirixThemeTokens.colors
    var filter by remember { mutableStateOf(DesktopLibraryFilter.ALL) }
    var artistMode by remember { mutableStateOf(false) }
    var artistFilter by remember { mutableStateOf<String?>(null) }
    val state = remember(tracks, query, filter, artistFilter, selectedKey) {
        buildDesktopLibraryViewState(tracks, query, filter, artistFilter, selectedKey)
    }
    val filtered = state.visibleTracks
    val picked = state.selectedTrack
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Медиатека", color = c.textPrimary, fontSize = 24.sp)
            OutlinedTextField(query, onQuery, label = { Text("Поиск по названию, артисту, альбому и тексту") }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            ChoiceDropdown("Показать", filter.label, DesktopLibraryFilter.entries.map { it.label }) { label -> filter = DesktopLibraryFilter.entries.first { it.label == label } }
            Text("Сохранённые тексты доступны без интернета. Аудиофайлы здесь не хранятся", color = c.textMuted, fontSize = 12.sp)
            Row { FilterChip(!artistMode, { artistMode = false; artistFilter = null }, label = { Text("Треки") }); Spacer(Modifier.width(8.dp)); FilterChip(artistMode, { artistMode = true; artistFilter = null }, label = { Text("Артисты") }) }
            artistFilter?.let { TextButton(onClick = { artistFilter = null; artistMode = true }) { Text("← Все артисты") } }
            Text(artistFilter?.let { "$it · Треков: ${filtered.size}" } ?: "Треков: ${filtered.size}", color = c.textSecondary)
            when (state.empty) {
                DesktopLibraryEmpty.NO_HISTORY -> {
                    Text("В медиатеке пока нет треков", color = c.textPrimary)
                    Text("Включите музыку в плеере ПК — Lirix добавит прослушанное в историю", color = c.textMuted)
                }
                DesktopLibraryEmpty.NO_MATCHES -> {
                    Text("Ничего не найдено", color = c.textPrimary)
                    Text("Измените запрос или сбросьте фильтры", color = c.textMuted)
                    TextButton(onClick = { onQuery(""); filter = DesktopLibraryFilter.ALL; artistFilter = null; artistMode = false }) { Text("Сбросить поиск и фильтры") }
                }
                DesktopLibraryEmpty.NONE -> Unit
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (artistMode) items(filtered.groupBy { it.artist }.toList(), key = { it.first }) { (artist, songs) ->
                    Surface(onClick = { artistFilter = artist; artistMode = false }, color = c.surfaceCard, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) { Text(artist, color = c.textPrimary); Text("Треков: ${songs.size}", color = c.textSecondary) }
                    }
                }
                else items(filtered, key = { it.trackKey }) { t ->
                    Surface(onClick = { onSelect(t) }, color = if (t.trackKey == picked?.trackKey) c.surfaceElevated else c.surfaceCard, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) { Text(t.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(t.artist, color = c.textSecondary, fontSize = 12.sp); Text("Запусков: ${t.playCount}", color = c.textMuted, fontSize = 11.sp) }
                    }
                }
            }
        }
        if (picked != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(picked.title, color = c.textPrimary, fontSize = 26.sp); Text(picked.artist, color = c.textSecondary)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onFavorite(picked) }) { Text(if (picked.isFavorite) "Убрать из избранного" else "В избранное", maxLines = 1, softWrap = false) }
                TextButton(onClick = { onEdit(picked) }) { Text("Редактировать текст", maxLines = 1, softWrap = false) }
                TextButton(onClick = { onPoster(picked) }) { Text("Постер", maxLines = 1, softWrap = false) }
                TextButton(onClick = { onTranslation(picked) }) { Text("Перевод", maxLines = 1, softWrap = false) }
            }
            val notes = notesDrafts[picked.trackKey] ?: picked.userNotes
            OutlinedTextField(notes, { onNotesDraft(picked.trackKey, it) }, label = { Text("Заметки к песне") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Button(onClick = { onNotes(picked, notes) }, enabled = notes != picked.userNotes) { Text("Сохранить заметки") }
            SelectionContainer { Text(picked.plainLyrics ?: picked.syncedLyrics ?: "Текст пока не сохранён", color = c.textPrimary, fontSize = 17.sp) }
            Text("История прослушиваний", color = c.textPrimary, fontSize = 18.sp)
            sessions.asReversed().filter { it.trackKey == picked.trackKey }.take(30).forEach { s ->
                Text("${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(s.startTimeMs))} · ${s.durationMs / 1000} сек", color = c.textSecondary)
            }
        } else Text("Выберите трек из списка", color = c.textMuted, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DesktopWrappedPanel(tracks: List<TrackEntity>, sessions: List<ListeningSessionEntity>, achievements: List<AchievementEntity>,
    onExport: (WrappedStats, AnalyticsTimeframe, PosterFormat, PosterSection) -> Unit) {
    val c = LirixThemeTokens.colors
    var period by remember { mutableStateOf(AnalyticsTimeframe.MONTH) }
    var stats by remember { mutableStateOf<WrappedStats?>(null) }
    var format by remember { mutableStateOf(PosterFormat.STORIES) }
    var section by remember { mutableStateOf(PosterSection.FULL) }
    LaunchedEffect(tracks, sessions, period) {
        stats = null
        stats = withContext(Dispatchers.Default) { WrappedStatsEngine.calculateStats(tracks, sessions, period, includeLiveSessionEstimate = false) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Итоги прослушиваний", color = c.textPrimary, fontSize = 28.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AnalyticsTimeframe.entries.forEach { p -> FilterChip(period == p, { period = p }, label = { Text(if (p == AnalyticsTimeframe.MONTH) "30 дней" else p.labelRu) }) } }
        stats?.let { s ->
            Text("${s.totalListeningTimeMs / 3_600_000} ч ${(s.totalListeningTimeMs / 60_000) % 60} мин в музыке", color = c.textPrimary, fontSize = 36.sp)
            Text("Треков: ${s.uniqueTracksCount} · Исполнителей: ${s.uniqueArtistsCount}", color = c.textSecondary)
            if (s.uniqueTracksCount > 0) {
                Text(s.archetype.titleRu, color = c.accentPrimary, fontSize = 22.sp)
                Text(s.archetype.descriptionRu, color = c.textSecondary)
                Text("С сохранённым текстом: ${(s.lyricsCoverageRatio * 100).toInt()}% · Запусков на трек: ${"%.1f".format(s.repeatRatio)}", color = c.textSecondary)
            }
            if (s.uniqueTracksCount == 0) Text("Итоги появятся после прослушивания музыки на этом ПК", color = c.textMuted)
            Text("Любимые треки", color = c.textPrimary, fontSize = 20.sp)
            s.topTracks.take(5).forEachIndexed { i, t -> Text("${i + 1}. ${t.artist} — ${t.title} · ${t.playCount}", color = c.textPrimary) }
            Text("Исполнители", color = c.textPrimary, fontSize = 20.sp)
            s.topArtists.take(5).forEach { Text("${it.artist} · ${it.playCount}", color = c.textSecondary) }
            s.topObsession?.let { Text("На повторе: ${it.artist} — ${it.title}", color = c.accentPrimary) }
            s.timeOfDayDistribution.forEach { (slot, share) -> Text("${when(slot) { TimeOfDaySlot.NIGHT -> "Ночью"; TimeOfDaySlot.MORNING -> "Утром"; TimeOfDaySlot.AFTERNOON -> "Днём"; TimeOfDaySlot.EVENING -> "Вечером" }}: ${(share * 100).toInt()}% запусков", color = c.textSecondary) }
            if (s.weeklyActivity.any { it.totalDurationMs > 0 }) {
                Text("Активность по дням недели", color = c.textPrimary, fontSize = 20.sp)
                val peak = s.weeklyActivity.maxOf { it.totalDurationMs }.coerceAtLeast(1)
                s.weeklyActivity.forEach { day -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day.dayName, color = c.textSecondary, modifier = Modifier.width(40.dp))
                    LinearProgressIndicator(progress = { day.totalDurationMs.toFloat() / peak }, modifier = Modifier.weight(1f))
                    Text(" ${day.totalDurationMs / 60_000} мин", color = c.textSecondary, modifier = Modifier.width(100.dp))
                } }
            }
            ChoiceDropdown("Формат", format.label, PosterFormat.entries.map { it.label }) { label -> format = PosterFormat.entries.first { it.label == label } }
            ChoiceDropdown("Содержимое", section.label, PosterSection.entries.map { it.label }) { label -> section = PosterSection.entries.first { it.label == label } }
            Button(onClick = { onExport(s, period, format, section) }) { Text("Сохранить постер итогов") }
        } ?: CircularProgressIndicator()
        Text("Достижения · ${achievements.count { it.isUnlocked }} из ${achievements.size}", color = c.textPrimary, fontSize = 22.sp)
        achievements.forEach { a -> Surface(color = c.surfaceCard, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text((if (a.isUnlocked) "✓ " else "") + a.title, color = if (a.isUnlocked) c.accentPrimary else c.textPrimary)
            Text(a.description, color = c.textSecondary); LinearProgressIndicator(progress = (a.currentProgress.toFloat() / a.maxProgress.coerceAtLeast(1)).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        } } }
    }
}

@Composable
internal fun ChoiceDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text("$label: $selected") }; DropdownMenu(open, { open = false }) { options.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); open = false }) } } }
}

@Composable
private fun DesktopSettingsPanel(rules: List<BlacklistRuleEntity>, tracks: List<TrackEntity>, hud: HudSettings,
    onHud: (HudSettings) -> Unit, repository: DesktopLibraryRepository, theme: AppThemeType, onTheme: (AppThemeType) -> Unit, onEditHud: () -> Unit) {
    val c = LirixThemeTokens.colors
    var pattern by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(BlacklistRuleType.ARTIST) }
    var mode by remember { mutableStateOf(BlacklistMatchMode.CONTAINS_IGNORE_CASE) }
    val valid = pattern.isNotBlank() && (mode != BlacklistMatchMode.REGEX || runCatching { Regex(pattern) }.isSuccess)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Настройки", color = c.textPrimary, fontSize = 28.sp)
        ChoiceDropdown("Тема", theme.title, AppThemeType.entries.map { it.title }) { label -> onTheme(AppThemeType.entries.first { it.title == label }) }
        Text("Оверлей", color = c.textPrimary, fontSize = 22.sp)
        Text("Перемещайте за заголовок, меняйте размер за правый нижний угол. Ctrl+Alt+L переключает настройку и сквозной клик.", color = c.textSecondary)
        Button(onClick = onEditHud) { Text("Переместить и изменить размер") }
        Text("Размер текста: ${(hud.textScale * 100).toInt()}%", color = c.textSecondary)
        Slider(hud.textScale, { onHud(hud.copy(textScale = it)) }, valueRange = .7f..2f)
        Text("Непрозрачность: ${(hud.opacity * 100).toInt()}%", color = c.textSecondary)
        Slider(hud.opacity, { onHud(hud.copy(opacity = it)) }, valueRange = .35f..1f)
        OutlinedButton(onClick = { onHud(HudSettings()) }) { Text("Сбросить размер и положение") }
        Text("Чёрный список", color = c.textPrimary, fontSize = 22.sp)
        Text("Совпавшие треки не записываются в историю. Существующие записи сохраняются.", color = c.textSecondary)
        ChoiceDropdown("Поле", type.name, BlacklistRuleType.entries.map { it.name }) { type = BlacklistRuleType.valueOf(it) }
        ChoiceDropdown("Совпадение", mode.name, BlacklistMatchMode.entries.map { it.name }) { mode = BlacklistMatchMode.valueOf(it) }
        OutlinedTextField(pattern, { pattern = it }, label = { Text("Артист, название или источник") }, isError = pattern.isNotBlank() && !valid, modifier = Modifier.fillMaxWidth())
        val preview = remember(pattern, type, mode, tracks) { if (!valid) 0 else tracks.count { com.lirix.app.feature.blacklist.BlacklistMatcher.matches(it.title, it.artist, it.sourcePackage, BlacklistRuleEntity("preview", type, pattern, mode)) } }
        Text("Совпадений в медиатеке: $preview", color = c.textSecondary)
        Button(onClick = { repository.saveRule(BlacklistRuleEntity(UUID.randomUUID().toString(), type, pattern.trim(), mode)); pattern = "" }, enabled = valid) { Text("Добавить правило") }
        rules.forEach { rule -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rule.isEnabled, { repository.setRuleEnabled(rule, it) }); Text("${rule.ruleType}: ${rule.pattern}", color = c.textPrimary, modifier = Modifier.weight(1f)); TextButton(onClick = { repository.deleteRule(rule.id) }) { Text("Удалить") }
        } }
    }
}
