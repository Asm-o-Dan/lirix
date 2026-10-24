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
    val languagePreferences = remember { com.lirix.app.desktop.i18n.DesktopLanguageSettings.preferences }
    var languageDialog by remember { mutableStateOf(false) }
    if (languageDialog) {
        AlertDialog(onDismissRequest = { languageDialog = false },
            title = { Text(if (com.lirix.app.ui.i18n.LirixStrings.currentLanguage == com.lirix.app.ui.i18n.InterfaceLanguage.RU) com.lirix.app.ui.i18n.LirixStrings.message("Язык интерфейса") else "App language") },
            text = { Column {
                com.lirix.app.desktop.i18n.DesktopLanguage.entries.forEach { language ->
                    val label = when (language) {
                        com.lirix.app.desktop.i18n.DesktopLanguage.SYSTEM -> if (com.lirix.app.ui.i18n.LirixStrings.currentLanguage == com.lirix.app.ui.i18n.InterfaceLanguage.RU) com.lirix.app.ui.i18n.LirixStrings.message("Как в системе") else "System default"
                        com.lirix.app.desktop.i18n.DesktopLanguage.RUSSIAN -> com.lirix.app.ui.i18n.LirixStrings.message("Русский")
                        com.lirix.app.desktop.i18n.DesktopLanguage.ENGLISH -> "English"
                    }
                    Row(Modifier.fillMaxWidth().clickable { languagePreferences.setLanguage(language); languageDialog = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(languagePreferences.selection == language, { languagePreferences.setLanguage(language); languageDialog = false })
                        Text(label)
                    }
                }
            } }, confirmButton = {})
    }
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
    val translationProvider = remember { com.lirix.app.translations.SongTranslationProviders() }
    var message by remember { mutableStateOf<String?>(null) }
    var sourceTrack by remember { mutableStateOf<TrackEntity?>(null) }
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
            listOf(com.lirix.app.ui.i18n.LirixStrings.message("Сейчас играет"), com.lirix.app.ui.i18n.LirixStrings.message("Медиатека"), com.lirix.app.ui.i18n.LirixStrings.message("Итоги"), com.lirix.app.ui.i18n.LirixStrings.message("Настройки")).forEachIndexed { index, label ->
                if (page == index) Button(onClick = { page = index }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                else TextButton(onClick = { page = index }, modifier = Modifier.fillMaxWidth()) { Text(label, color = colors.textPrimary) }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onToggleHud, modifier = Modifier.fillMaxWidth()) { Text(if (hudVisible) com.lirix.app.ui.i18n.LirixStrings.message("Скрыть оверлей") else com.lirix.app.ui.i18n.LirixStrings.message("Оверлей")) }
            TextButton(onClick = onEditHud, modifier = Modifier.fillMaxWidth()) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Настроить оверлей")) }
            TextButton(onClick = { themeDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Тема оформления")) }
            TextButton(onClick = { languageDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (com.lirix.app.ui.i18n.LirixStrings.currentLanguage == com.lirix.app.ui.i18n.InterfaceLanguage.RU) com.lirix.app.ui.i18n.LirixStrings.message("Язык интерфейса") else "App language")
            }
            TextButton(onClick = { onHelp(true) }, modifier = Modifier.fillMaxWidth()) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Клавиши · F1")) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val pendingLyrics = tracks.filter { drafts.lyrics[it.trackKey]?.content?.dirty == true }
            val pendingSync = tracks.filter { drafts.sync[it.trackKey]?.dirty == true }
            val pendingNotes = tracks.filter { drafts.notes.containsKey(it.trackKey) }
            if (pendingLyrics.isNotEmpty() || pendingSync.isNotEmpty() || pendingNotes.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Несохранённые черновики"), color = colors.textSecondary)
                    pendingLyrics.forEach { track -> TextButton(onClick = { editor = track }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Текст: {0}", track.title), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    pendingSync.forEach { track -> TextButton(onClick = { syncTrack = track }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Разметка: {0}", track.title), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    pendingNotes.forEach { track -> TextButton(onClick = { page = 1; selectedKey = track.trackKey; query = "" }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Заметки: {0}", track.title), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
            }
            if (error != null || message != null) Row(Modifier.fillMaxWidth().background(colors.surfaceElevated).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error ?: message.orEmpty(), color = colors.textPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { repository.clearError(); message = null }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Закрыть")) }
            }
            Column(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                0 -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { currentTrack?.let { repository.favorite(it.trackKey, !it.isFavorite) } }, enabled = currentTrack != null) { Text(if (currentTrack?.isFavorite == true) com.lirix.app.ui.i18n.LirixStrings.message("В избранном") else com.lirix.app.ui.i18n.LirixStrings.message("В избранное")) }
                        ShortcutButton(com.lirix.app.ui.i18n.LirixStrings.message("Текст / LRC"), "Ctrl+E", currentTrack != null, ::editCurrent)
                        ShortcutButton("Tap-to-Sync", "Ctrl+T", currentTrack != null) { syncTrack = currentTrack }
                        ShortcutButton(com.lirix.app.ui.i18n.LirixStrings.message("Постер"), "Ctrl+Shift+S", currentTrack != null, ::exportCurrent)
                        TextButton(onClick = { sourceTrack = currentTrack }, enabled = currentTrack != null) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Источники")) }
                    }
                    DesktopDeckScreen(playback, ingress, selectedTheme, onTheme, lyrics, hudVisible, onToggleHud,
                        onToggleCompactMode = onMini, audioLevels = audioLevels, modifier = Modifier.weight(1f), connectRequestVersion = connectRequest,
                        onOpenTapSync = { syncTrack = currentTrack }, canTapSync = currentTrack != null, showTransport = false,
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
                2 -> DesktopWrappedPanel(tracks, sessions, achievements) { message = it }
                3 -> DesktopSettingsPanel(rules, tracks, hudSettings, onHudSettings, repository, selectedTheme, onTheme, onEditHud)
            }
            }
            DesktopPersistentPlayer(playback, ingress, audioLevels)
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
    translationTrack?.let { track -> DesktopFeatureDialog(com.lirix.app.ui.i18n.LirixStrings.message("Перевод — {0}", track.title), { translationTrack = null }) {
        com.lirix.app.translations.TranslationPanel(track.trackKey, "${track.artist} — ${track.title}",
            track.plainLyrics ?: track.syncedLyrics?.let { com.lirix.app.feature.lyrics.LrcSyncEngine.parseLrcLinesStrict(it).joinToString("\n") { line -> line.text } }.orEmpty(),
            repository.database.songTranslationDao(), translationProvider::load, Modifier.weight(1f), automaticTranslations, track.title, track.artist)
        TextButton(onClick = { translationTrack = null }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Закрыть")) }
    } }
    if (helpVisible) DesktopHotkeyHelp(hotkeyFailures) { onHelp(false) }
    sourceTrack?.let { track ->
        DesktopSourceDialog(track, repository, onDismiss = { sourceTrack = null },
            onRefresh = { if (currentKey == track.trackKey) lyricsService.onTrackChanged(track.title, track.artist, track.album, true) },
            onReject = { if (currentKey == track.trackKey) lyricsService.rejectCurrentSource(track.title, track.artist, track.album) },
            isCurrentTrack = currentKey == track.trackKey,
            onAttach = { text -> lyricsService.saveUserLyrics(track.trackKey, track.title, track.artist, text, null, null) })
    }
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
            Text(com.lirix.app.ui.i18n.LirixStrings.message("Медиатека"), color = c.textPrimary, fontSize = 24.sp)
            OutlinedTextField(query, onQuery, label = { Text(com.lirix.app.ui.i18n.LirixStrings.message("Поиск по названию, артисту, альбому и тексту")) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            ChoiceDropdown(com.lirix.app.ui.i18n.LirixStrings.message("Показать"), filter.label, DesktopLibraryFilter.entries.map { it.label }) { label -> filter = DesktopLibraryFilter.entries.first { it.label == label } }
            Text(com.lirix.app.ui.i18n.LirixStrings.message("Сохранённые тексты доступны без интернета. Аудиофайлы здесь не хранятся"), color = c.textMuted, fontSize = 12.sp)
            Row { FilterChip(!artistMode, { artistMode = false; artistFilter = null }, label = { Text(com.lirix.app.ui.i18n.LirixStrings.message("Треки")) }); Spacer(Modifier.width(8.dp)); FilterChip(artistMode, { artistMode = true; artistFilter = null }, label = { Text(com.lirix.app.ui.i18n.LirixStrings.message("Артисты")) }) }
            artistFilter?.let { TextButton(onClick = { artistFilter = null; artistMode = true }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("← Все артисты")) } }
            Text(artistFilter?.let { com.lirix.app.ui.i18n.LirixStrings.message("{0} · Треков: {1}", it, filtered.size) } ?: com.lirix.app.ui.i18n.LirixStrings.message("Треков: {0}", filtered.size), color = c.textSecondary)
            when (state.empty) {
                DesktopLibraryEmpty.NO_HISTORY -> {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("В медиатеке пока нет треков"), color = c.textPrimary)
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Включите музыку в плеере ПК — Lirix добавит прослушанное в историю"), color = c.textMuted)
                }
                DesktopLibraryEmpty.NO_MATCHES -> {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Ничего не найдено"), color = c.textPrimary)
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Измените запрос или сбросьте фильтры"), color = c.textMuted)
                    TextButton(onClick = { onQuery(""); filter = DesktopLibraryFilter.ALL; artistFilter = null; artistMode = false }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Сбросить поиск и фильтры")) }
                }
                DesktopLibraryEmpty.NONE -> Unit
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (artistMode) items(filtered.groupBy { it.artist }.toList(), key = { it.first }) { (artist, songs) ->
                    Surface(onClick = { artistFilter = artist; artistMode = false }, color = c.surfaceCard, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) { Text(artist, color = c.textPrimary); Text(com.lirix.app.ui.i18n.LirixStrings.message("Треков: {0}", songs.size), color = c.textSecondary) }
                    }
                }
                else items(filtered, key = { it.trackKey }) { t ->
                    Surface(onClick = { onSelect(t) }, color = if (t.trackKey == picked?.trackKey) c.surfaceElevated else c.surfaceCard, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) { Text(t.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(t.artist, color = c.textSecondary, fontSize = 12.sp); Text(com.lirix.app.ui.i18n.LirixStrings.message("Запусков: {0}", t.playCount), color = c.textMuted, fontSize = 11.sp) }
                    }
                }
            }
        }
        if (picked != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(picked.title, color = c.textPrimary, fontSize = 26.sp); Text(picked.artist, color = c.textSecondary)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onFavorite(picked) }) { Text(if (picked.isFavorite) com.lirix.app.ui.i18n.LirixStrings.message("Убрать из избранного") else com.lirix.app.ui.i18n.LirixStrings.message("В избранное"), maxLines = 1, softWrap = false) }
                TextButton(onClick = { onEdit(picked) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Редактировать текст"), maxLines = 1, softWrap = false) }
                TextButton(onClick = { onPoster(picked) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Постер"), maxLines = 1, softWrap = false) }
                TextButton(onClick = { onTranslation(picked) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Перевод"), maxLines = 1, softWrap = false) }
            }
            val notes = notesDrafts[picked.trackKey] ?: picked.userNotes
            OutlinedTextField(notes, { onNotesDraft(picked.trackKey, it) }, label = { Text(com.lirix.app.ui.i18n.LirixStrings.message("Заметки к песне")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Button(onClick = { onNotes(picked, notes) }, enabled = notes != picked.userNotes) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Сохранить заметки")) }
            SelectionContainer { Text(desktopLibraryLyrics(picked.plainLyrics, picked.syncedLyrics), color = c.textPrimary, fontSize = 17.sp) }
            Text(com.lirix.app.ui.i18n.LirixStrings.message("История прослушиваний"), color = c.textPrimary, fontSize = 18.sp)
            sessions.asReversed().filter { it.trackKey == picked.trackKey }.take(30).forEach { s ->
                Text(com.lirix.app.ui.i18n.LirixStrings.message("{0} · {1} сек", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(s.startTimeMs)), s.durationMs / 1000), color = c.textSecondary)
            }
        } else Text(com.lirix.app.ui.i18n.LirixStrings.message("Выберите трек из списка"), color = c.textMuted, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DesktopWrappedPanel(tracks: List<TrackEntity>, sessions: List<ListeningSessionEntity>, achievements: List<AchievementEntity>,
    onMessage: (String) -> Unit) {
    val c = LirixThemeTokens.colors
    var period by remember { mutableStateOf(AnalyticsTimeframe.MONTH) }
    var stats by remember { mutableStateOf<WrappedStats?>(null) }
    var posterSnapshot by remember { mutableStateOf<Pair<WrappedStats, AnalyticsTimeframe>?>(null) }
    var showAchievements by remember { mutableStateOf(false) }
    LaunchedEffect(tracks, sessions, period) {
        stats = null
        stats = withContext(Dispatchers.Default) { WrappedStatsEngine.calculateStats(tracks, sessions, period, includeLiveSessionEstimate = false) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Итоги прослушиваний"), color = c.textPrimary, fontSize = 28.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AnalyticsTimeframe.entries.forEach { p -> FilterChip(period == p, { period = p }, label = { Text(if (p == AnalyticsTimeframe.MONTH) com.lirix.app.ui.i18n.LirixStrings.message("30 дней") else p.labelRu) }) } }
        stats?.let { s ->
            Button(onClick = { posterSnapshot = s to period }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Создать постер итогов")) }
            Text(com.lirix.app.ui.i18n.LirixStrings.message("{0} ч {1} мин в музыке", s.totalListeningTimeMs / 3_600_000, (s.totalListeningTimeMs / 60_000) % 60), color = c.textPrimary, fontSize = 36.sp)
            Text(com.lirix.app.ui.i18n.LirixStrings.message("Треков: {0} · Исполнителей: {1}", s.uniqueTracksCount, s.uniqueArtistsCount), color = c.textSecondary)
            if (s.uniqueTracksCount > 0) {
                Text(s.archetype.titleRu, color = c.accentPrimary, fontSize = 22.sp)
                Text(s.archetype.descriptionRu, color = c.textSecondary)
                Text(com.lirix.app.ui.i18n.LirixStrings.message("С сохранённым текстом: {0}% · Запусков на трек: {1}", (s.lyricsCoverageRatio * 100).toInt(), "%.1f".format(s.repeatRatio)), color = c.textSecondary)
            }
            if (s.uniqueTracksCount == 0) Text(com.lirix.app.ui.i18n.LirixStrings.message("Итоги появятся после прослушивания музыки на этом ПК"), color = c.textMuted)
            Text(com.lirix.app.ui.i18n.LirixStrings.message("Любимые треки"), color = c.textPrimary, fontSize = 20.sp)
            s.topTracks.take(5).forEachIndexed { i, t -> Text("${i + 1}. ${t.artist} — ${t.title} · ${t.playCount}", color = c.textPrimary) }
            Text(com.lirix.app.ui.i18n.LirixStrings.message("Исполнители"), color = c.textPrimary, fontSize = 20.sp)
            s.topArtists.take(5).forEach { Text("${it.artist} · ${it.playCount}", color = c.textSecondary) }
            s.topObsession?.let { Text(com.lirix.app.ui.i18n.LirixStrings.message("На повторе: {0} — {1}", it.artist, it.title), color = c.accentPrimary) }
            s.timeOfDayDistribution.forEach { (slot, share) -> Text(com.lirix.app.ui.i18n.LirixStrings.message("{0}: {1}% запусков", when(slot) { TimeOfDaySlot.NIGHT -> com.lirix.app.ui.i18n.LirixStrings.message("Ночью"); TimeOfDaySlot.MORNING -> com.lirix.app.ui.i18n.LirixStrings.message("Утром"); TimeOfDaySlot.AFTERNOON -> com.lirix.app.ui.i18n.LirixStrings.message("Днём"); TimeOfDaySlot.EVENING -> com.lirix.app.ui.i18n.LirixStrings.message("Вечером") }, (share * 100).toInt()), color = c.textSecondary) }
            if (s.weeklyActivity.any { it.totalDurationMs > 0 }) {
                Text(com.lirix.app.ui.i18n.LirixStrings.message("Активность по дням недели"), color = c.textPrimary, fontSize = 20.sp)
                val peak = s.weeklyActivity.maxOf { it.totalDurationMs }.coerceAtLeast(1)
                s.weeklyActivity.forEach { day -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day.dayName, color = c.textSecondary, modifier = Modifier.width(40.dp))
                    LinearProgressIndicator(progress = { day.totalDurationMs.toFloat() / peak }, modifier = Modifier.weight(1f))
                    Text(com.lirix.app.ui.i18n.LirixStrings.message(" {0} мин", day.totalDurationMs / 60_000), color = c.textSecondary, modifier = Modifier.width(100.dp))
                } }
            }
        } ?: CircularProgressIndicator()
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Достижения · {0} из {1}", achievements.count { it.isUnlocked }, achievements.size), color = c.textPrimary, fontSize = 22.sp)
        TextButton(onClick = { showAchievements = !showAchievements }) { Text(if (showAchievements) com.lirix.app.ui.i18n.LirixStrings.message("Свернуть достижения") else com.lirix.app.ui.i18n.LirixStrings.message("Показать достижения")) }
        if (showAchievements) achievements.forEach { a -> Surface(color = c.surfaceCard, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text((if (a.isUnlocked) "✓ " else "") + a.title, color = if (a.isUnlocked) c.accentPrimary else c.textPrimary)
            Text(a.description, color = c.textSecondary); LinearProgressIndicator(progress = (a.currentProgress.toFloat() / a.maxProgress.coerceAtLeast(1)).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        } } }
    }
    posterSnapshot?.let { (snapshot, snapshotPeriod) ->
        DesktopWrappedPosterDialog(snapshot, snapshotPeriod, c, { posterSnapshot = null }, onMessage)
    }
}

@Composable
internal fun ChoiceDropdown(label: String, selected: String, options: List<String>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }, enabled = enabled) { Text("$label: $selected") }; DropdownMenu(open, { open = false }) { options.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); open = false }) } } }
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
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Настройки"), color = c.textPrimary, fontSize = 28.sp)
        ChoiceDropdown(com.lirix.app.ui.i18n.LirixStrings.message("Тема"), theme.title, AppThemeType.entries.map { it.title }) { label -> onTheme(AppThemeType.entries.first { it.title == label }) }
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Оверлей"), color = c.textPrimary, fontSize = 22.sp)
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Перемещайте за заголовок, меняйте размер за правый нижний угол. Ctrl+Alt+L переключает настройку и сквозной клик."), color = c.textSecondary)
        Button(onClick = onEditHud) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Переместить и изменить размер")) }
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Размер текста: {0}%", (hud.textScale * 100).toInt()), color = c.textSecondary)
        Slider(hud.textScale, { onHud(hud.copy(textScale = it)) }, valueRange = .7f..2f)
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Непрозрачность: {0}%", (hud.opacity * 100).toInt()), color = c.textSecondary)
        Slider(hud.opacity, { onHud(hud.copy(opacity = it)) }, valueRange = .35f..1f)
        OutlinedButton(onClick = { onHud(HudSettings()) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Сбросить размер и положение")) }
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Чёрный список"), color = c.textPrimary, fontSize = 22.sp)
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Совпавшие треки не записываются в историю. Существующие записи сохраняются."), color = c.textSecondary)
        ChoiceDropdown(com.lirix.app.ui.i18n.LirixStrings.message("Поле"), type.name, BlacklistRuleType.entries.map { it.name }) { type = BlacklistRuleType.valueOf(it) }
        ChoiceDropdown(com.lirix.app.ui.i18n.LirixStrings.message("Совпадение"), mode.name, BlacklistMatchMode.entries.map { it.name }) { mode = BlacklistMatchMode.valueOf(it) }
        OutlinedTextField(pattern, { pattern = it }, label = { Text(com.lirix.app.ui.i18n.LirixStrings.message("Артист, название или источник")) }, isError = pattern.isNotBlank() && !valid, modifier = Modifier.fillMaxWidth())
        val preview = remember(pattern, type, mode, tracks) { if (!valid) 0 else tracks.count { com.lirix.app.feature.blacklist.BlacklistMatcher.matches(it.title, it.artist, it.sourcePackage, BlacklistRuleEntity("preview", type, pattern, mode)) } }
        Text(com.lirix.app.ui.i18n.LirixStrings.message("Совпадений в медиатеке: {0}", preview), color = c.textSecondary)
        Button(onClick = { repository.saveRule(BlacklistRuleEntity(UUID.randomUUID().toString(), type, pattern.trim(), mode)); pattern = "" }, enabled = valid) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Добавить правило")) }
        rules.forEach { rule -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rule.isEnabled, { repository.setRuleEnabled(rule, it) }); Text("${rule.ruleType}: ${rule.pattern}", color = c.textPrimary, modifier = Modifier.weight(1f)); TextButton(onClick = { repository.deleteRule(rule.id) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Удалить")) }
        } }
    }
}

internal fun desktopLibraryLyrics(plain: String?, synced: String?): String = plain?.takeIf { it.isNotBlank() }
    ?: synced?.let { com.lirix.app.feature.lyrics.LrcSyncEngine.parseLrcLinesStrict(it).joinToString("\n") { line -> line.text } }
        ?.takeIf { it.isNotBlank() } ?: com.lirix.app.ui.i18n.LirixStrings.message("Текст пока не сохранён")
