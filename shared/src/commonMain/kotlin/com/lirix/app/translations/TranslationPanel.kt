package com.lirix.app.translations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.platform.currentTimeMillis
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.*

/** Static paired source text; never assigns timestamps or changes the player's lyrics. */
@Composable
fun TranslationPanel(trackKey: String, trackLabel: String, original: String, dao: SongTranslationDao,
                     load: suspend (String) -> SongTranslation, modifier: Modifier = Modifier,
                     automatic: AutomaticSongTranslations? = null, title: String = "", artist: String = "",
                     compact: Boolean = false, settingsExpanded: Boolean = false, onDismissSettings: () -> Unit = {},
                     machineFallback: MachineTranslationFallback? = null,
                     machineAttribution: (@Composable () -> Unit)? = null,
                     originalLineContent: (@Composable (String) -> Unit)? = null) {
    require(machineFallback == null || machineAttribution != null)
    var localSettings by remember(trackKey) { mutableStateOf(false) }
    val language = automatic?.language?.collectAsState()?.value ?: "ru"
    key(trackKey, language) { TranslationPanelContent(trackKey, trackLabel, original, dao, load, modifier, automatic, title, artist, language, compact, settingsExpanded || localSettings, { localSettings = false; onDismissSettings() }, { localSettings = true }, machineFallback, machineAttribution, originalLineContent) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun TranslationPanelContent(trackKey: String, trackLabel: String, original: String, dao: SongTranslationDao,
                                    load: suspend (String) -> SongTranslation, modifier: Modifier,
                                    automatic: AutomaticSongTranslations?, title: String, artist: String, language: String,
                                    compact: Boolean, settingsExpanded: Boolean, onDismissSettings: () -> Unit, onOpenSettings: () -> Unit,
                                    machineFallback: MachineTranslationFallback?, machineAttribution: (@Composable () -> Unit)?,
                                    originalLineContent: (@Composable (String) -> Unit)?) {
    val c = LirixThemeTokens.colors
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var saved by remember { mutableStateOf<List<SongTranslationEntity>>(emptyList()) }
    var savedLoaded by remember { mutableStateOf(false) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableIntStateOf(if (automatic != null) 2 else 0) }
    var importing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<SongTranslation?>(null) }
    var uncertainCandidate by remember { mutableStateOf<TranslationCandidate?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var observeRevision by remember { mutableIntStateOf(0) }
    var retryAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var languageMenu by remember { mutableStateOf(false) }
    var showAttribution by remember { mutableStateOf(false) }
    val autoState = automatic?.states?.collectAsState()?.value?.get(automatic.stateKey(trackKey, language))
    LaunchedEffect(automatic, trackKey, title, artist) { automatic?.resolve(trackKey, title, artist, language = language) }
    LaunchedEffect(dao, trackKey, observeRevision) {
        savedLoaded = false
        try { dao.observe(trackKey, language).collect { saved = it; savedLoaded = true } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            savedLoaded = false
            error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось прочитать сохранённый перевод: {0}", e.message?.let { com.lirix.app.ui.i18n.LirixStrings.message(it) })
            retryAction = { error = null; observeRevision++ }
        }
    }
    val entity = saved.firstOrNull { it.sourceUrl == selectedUrl } ?: saved.firstOrNull()
    val cached = remember(entity) { entity?.let { runCatching { TranslationDocuments.decode(it.documentJson) }.getOrNull() } }
    val modelDownload = machineFallback?.downloadProgress?.collectAsState()?.value
    val machineEnabled = machineFallback?.enabled?.collectAsState()?.value ?: false
    val machineRequest = remember(trackKey, title, artist, original, language) {
        MachineTranslationRequest(trackKey, title, artist, original, language)
    }
    var machineResult by remember(machineRequest) { mutableStateOf<MachineTranslationResult?>(null) }
    var machineBusy by remember(machineRequest) { mutableStateOf(false) }
    var machineError by remember(machineRequest) { mutableStateOf(false) }
    var machineRetry by remember(machineRequest) { mutableIntStateOf(0) }
    var downloadRequested by remember(machineRequest) { mutableStateOf(false) }
    val useMachine = machineFallbackAllowed(autoState, machineEnabled, !savedLoaded || saved.isNotEmpty(), preview != null, original)
    LaunchedEffect(machineRequest, useMachine, machineRetry) {
        machineResult = null; machineError = false
        if (!useMachine) downloadRequested = false
        if (useMachine && machineFallback != null) {
            machineBusy = true
            val download = downloadRequested
            downloadRequested = false
            try {
                val result = machineFallback.translate(machineRequest, download)
                currentCoroutineContext().ensureActive()
                machineResult = result
            } catch (e: TimeoutCancellationException) { machineError = true }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { machineError = true }
            finally { machineBusy = false }
        }
    }
    val machineDocument = if (useMachine) (machineResult as? MachineTranslationResult.Translated)?.document else null
    val document = preview ?: cached ?: machineDocument
    fun importPreview() {
        if (busy || input.isBlank()) return
        busy = true; error = null; preview = null; confirmed = false
        retryAction = { importPreview() }
        scope.launch {
            try {
                preview = withTimeout(25_000) { load(input) }
                uncertainCandidate = null; mode = 2
                if (compact) onDismissSettings()
            } catch (_: TimeoutCancellationException) {
                error = com.lirix.app.ui.i18n.LirixStrings.message("Источник не ответил вовремя. Попробуйте ещё раз.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message?.let { com.lirix.app.ui.i18n.LirixStrings.message(it) } ?: com.lirix.app.ui.i18n.LirixStrings.message("Не удалось загрузить перевод") }
            finally { busy = false }
        }
    }
    fun savePreview() {
        if (!confirmed || busy) return
        val pending = preview ?: return
        if (pending.language != language) {
            error = com.lirix.app.ui.i18n.LirixStrings.message("Выберите язык загруженного перевода")
            retryAction = null
            return
        }
        busy = true; error = null
        retryAction = { savePreview() }
        scope.launch {
            try {
                val row = SongTranslationEntity(trackKey, pending.language, pending.sourceUrl,
                    TranslationDocuments.encode(pending), currentTimeMillis())
                withContext(Dispatchers.IO) { dao.save(row) }
                saved = listOf(row) + saved.filterNot { it.sourceUrl == row.sourceUrl }
                selectedUrl = row.sourceUrl; preview = null; importing = false; confirmed = false
                busy = false
                retryAction = null
                snackbar.showSnackbar(com.lirix.app.ui.i18n.LirixStrings.message("Перевод сохранён"))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось сохранить: {0}", e.message?.let { com.lirix.app.ui.i18n.LirixStrings.message(it) }) }
            finally { busy = false }
        }
    }
    fun chooseCandidate(candidate: TranslationCandidate) {
        if (busy) return
        retryAction = { chooseCandidate(candidate) }
        busy = true
        scope.launch {
            error = null
            try {
                val loaded = withTimeout(25_000) { load(candidate.sourceUrl) }
                fun sameIdentity(a: String, b: String): Boolean =
                    a.isNotBlank() && b.isNotBlank() && a.trim().replace(Regex("\\s+"), " ").equals(b.trim().replace(Regex("\\s+"), " "), ignoreCase = true)
                // Search may include a Latin transcription that the detail title omits.
                // This is not proof of equivalence: show both names and require confirmation.
                val detailTitle = loaded.title.trim()
                val suffix = candidate.title.trim().removePrefix(detailTitle).trim()
                val latinSuffix = suffix.matches(Regex("\\([A-Za-z][A-Za-z '\u2019-]*\\)"))
                val uncertainAlias = loaded.sourceName == "LyricsTranslate" &&
                    candidate.sourceUrl.startsWith("https://lyricstranslate.com/") &&
                    detailTitle.any { it in '\u0400'..'\u052f' } &&
                    candidate.title.trim().startsWith(detailTitle) && latinSuffix &&
                    !Regex("(?i)\\b(remaster(?:ed)?|live|version|edit|mix|acoustic|anniversary|demo|feat|featuring)\\b").containsMatchIn(suffix)
                if (!sameIdentity(candidate.artist, loaded.artist) ||
                    (!sameIdentity(candidate.title, loaded.title) && !uncertainAlias)) {
                    error = com.lirix.app.ui.i18n.LirixStrings.message("Страница изменилась и больше не соответствует выбранному варианту. Найдите перевод снова.")
                } else {
                    uncertainCandidate = if (uncertainAlias && !sameIdentity(candidate.title, loaded.title)) candidate else null
                    preview = loaded
                    input = candidate.sourceUrl; importing = true; confirmed = false; mode = 2
                }
            } catch (_: TimeoutCancellationException) { error = com.lirix.app.ui.i18n.LirixStrings.message("Источник не ответил вовремя") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось загрузить перевод") }
            finally { busy = false }
        }
    }
    @Composable
    fun ErrorFeedback() {
        error?.let { message ->
            Column {
                Text(message, color = MaterialTheme.colorScheme.error)
                retryAction?.let { retry ->
                    TextButton(onClick = retry, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Повторить"))
                    }
                }
            }
        }
    }
    fun openSource(url: String) {
        error = null
        retryAction = { openSource(url) }
        runCatching { uri.openUri(url) }.onFailure {
            error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось открыть источник")
        }
    }
    @Composable
    fun Controls() {
        if (automatic != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box {
                TextButton(onClick = { languageMenu = true }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Выбрать язык перевода") }) {
                    Text("${TranslationLanguages.labels[language]} ▾", maxLines = 1)
                }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    TranslationLanguages.labels.forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(if (code == language) "$label ✓" else label) }, onClick = {
                            languageMenu = false
                            automatic.setLanguage(code)
                        })
                    }
                }
            }
            TextButton(enabled = !busy, onClick = { importing = !importing; preview = null; confirmed = false }) {
                Text(if (importing) com.lirix.app.ui.i18n.LirixStrings.message("Закрыть") else com.lirix.app.ui.i18n.LirixStrings.message("По ссылке"), maxLines = 1)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал"), if (automatic != null) com.lirix.app.ui.i18n.LirixStrings.message("Перевод") else TranslationLanguages.labels[language] ?: language, com.lirix.app.ui.i18n.LirixStrings.message("Вместе")).forEachIndexed { index, label ->
                FilterChip(mode == index, { mode = index }, label = { Text(label) })
            }
            if (automatic == null) TextButton(enabled = !busy, onClick = { importing = !importing; preview = null; confirmed = false }) {
                Text(if (importing) com.lirix.app.ui.i18n.LirixStrings.message("Закрыть импорт") else if (automatic != null) com.lirix.app.ui.i18n.LirixStrings.message("Ссылка вручную") else com.lirix.app.ui.i18n.LirixStrings.message("Добавить перевод"))
            }
        }
    }
    @Composable
    fun ImportForm() {
        if (importing) {
            Text(com.lirix.app.ui.i18n.LirixStrings.message("Перевод для: {0}", trackLabel), color = c.textPrimary)
            OutlinedTextField(input, { input = it; preview = null; confirmed = false; error = null; retryAction = null }, enabled = !busy,
                label = { Text(com.lirix.app.ui.i18n.LirixStrings.message("Ссылка Amalgama или LyricsTranslate")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(enabled = !busy && input.isNotBlank(), onClick = { importPreview() }) {
                Text(com.lirix.app.ui.i18n.LirixStrings.message(if (busy) "Загрузка…" else "Предпросмотр"))
            }
        }
    }
    if (compact && settingsExpanded) {
        ModalBottomSheet(onDismissRequest = onDismissSettings) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                .imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(com.lirix.app.ui.i18n.LirixStrings.message("Перевод и язык"), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Controls()
                if (machineFallback != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Машинный перевод · {0}", machineFallback.engineInfo.name), modifier = Modifier.weight(1f))
                        Switch(checked = machineEnabled, onCheckedChange = { value ->
                            runCatching { machineFallback.setEnabled(value) }.onFailure {
                                error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось изменить настройку")
                            }
                        }, modifier = Modifier.semantics { contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Машинный перевод · {0}", machineFallback.engineInfo.name) })
                    }
                    Text(com.lirix.app.ui.i18n.LirixStrings.message(machineFallback.engineInfo.notice), color = c.textSecondary)
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Машинный перевод может ошибаться в стихах и идиомах."), color = c.textSecondary)
                    TextButton(onClick = { openSource(machineFallback.engineInfo.helpUrl) }) {
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("{0}: справка и условия", machineFallback.engineInfo.name))
                    }
                    if (machineFallback.engineInfo.diagnostics) {
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Google не предоставляет гарантий точности, надёжности, пригодности для конкретной цели или отсутствия нарушений прав в машинном переводе."), color = c.textSecondary)
                        TextButton(onClick = { openSource("https://docs.cloud.google.com/translate/attribution#disclaimer") }) {
                            Text(com.lirix.app.ui.i18n.LirixStrings.message("Отказ Google от гарантий точности перевода"))
                        }
                    }
                }
                ImportForm()
                if (automatic != null && saved.isEmpty()) TextButton(
                    enabled = !busy && autoState != AutomaticTranslationState.Searching,
                    onClick = { scope.launch { automatic.resolve(trackKey, title, artist, true, language) } }
                ) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Искать готовый перевод снова")) }
                if (saved.size > 1) {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Сохранённые варианты"), color = c.textSecondary)
                    saved.forEach { row ->
                        val variant = remember(row) { runCatching { TranslationDocuments.decode(row.documentJson) }.getOrNull() }
                        TextButton(enabled = !busy, onClick = { selectedUrl = row.sourceUrl }) {
                            Text((if (row.sourceUrl == entity?.sourceUrl) "✓ " else "") +
                                (variant?.let { "${it.title} · ${it.translator.ifBlank { it.sourceName }}" } ?: row.sourceUrl))
                        }
                    }
                }
                if (document != null) TextButton(onClick = { showAttribution = true }) {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Об источнике перевода: {0}; {1}", document.sourceName, document.translator))
                }
                ErrorFeedback()
                TextButton(onClick = onDismissSettings) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Готово")) }
            }
        }
    }
    if (compact && showAttribution && document != null) {
        AlertDialog(onDismissRequest = { showAttribution = false },
            title = { Text(com.lirix.app.ui.i18n.LirixStrings.message("Об источнике перевода")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${document.artist} — ${document.title}")
                if (document.translatedTitle.isNotBlank()) Text(document.translatedTitle)
                Text(com.lirix.app.ui.i18n.LirixStrings.message("Источник: {0}", document.sourceName))
                Text(com.lirix.app.ui.i18n.LirixStrings.message("Перевод: {0}", document.translator.ifBlank { com.lirix.app.ui.i18n.LirixStrings.message("автор не указан") }))
                if (document.sourceUrl.isNotBlank()) Text(document.sourceUrl)
                document.warnings.forEach { Text(it) }
            } },
            confirmButton = { if (document.sourceUrl.isNotBlank()) TextButton(onClick = {
                openSource(document.sourceUrl)
            }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Открыть источник")) } },
            dismissButton = { TextButton(onClick = { showAttribution = false }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Закрыть")) } })
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SnackbarHost(snackbar)
        if (!compact) Controls()
        else if (document != null) TextButton(onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            val modeLabel = com.lirix.app.ui.i18n.LirixStrings.message(when (mode) {
                0 -> "Оригинал"
                1 -> "Перевод"
                else -> "Вместе"
            })
            Text("${TranslationLanguages.labels[language] ?: language} · $modeLabel ▾",
                color = c.textSecondary, style = MaterialTheme.typography.labelLarge)
        }
        if (machineDocument != null && preview == null && cached == null) {
            Text(com.lirix.app.ui.i18n.LirixStrings.message("Машинный перевод"), color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
            machineAttribution?.invoke()
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (document == null && automatic != null && error == null) {
                when (autoState) {
                    AutomaticTranslationState.Searching -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Ищем перевод…"), color = c.textSecondary)
                    }
                    AutomaticTranslationState.Missing -> if (!machineEnabled) Text(com.lirix.app.ui.i18n.LirixStrings.message("Перевод на выбранный язык не найден"), color = c.textSecondary)
                    is AutomaticTranslationState.Failed -> Text(com.lirix.app.ui.i18n.LirixStrings.message("Поиск перевода: {0}", com.lirix.app.ui.i18n.LirixStrings.message(autoState.message)), color = c.textSecondary)
                    is AutomaticTranslationState.Candidates -> {
                        if (autoState.unavailableSources.isNotEmpty()) Text(
                            com.lirix.app.ui.i18n.LirixStrings.message("Не все источники ответили ({0}). Показаны доступные варианты.", autoState.unavailableSources.joinToString(", ")),
                            color = c.textSecondary
                        )
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Есть несколько вариантов или отличается версия. Выберите нужную песню:"), color = c.textSecondary)
                        autoState.values.forEach { candidate ->
                            TextButton(enabled = !busy, onClick = { chooseCandidate(candidate) }) { Text("${candidate.artist} — ${candidate.title}${candidate.translator.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}") }
                        }
                    }
                    else -> Unit
                }
                if (autoState != null && autoState != AutomaticTranslationState.Searching && autoState != AutomaticTranslationState.Ready)
                    TextButton(enabled = !busy, onClick = { scope.launch { automatic.resolve(trackKey, title, artist, true, language) } }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Искать снова")) }
            }
            if (autoState == AutomaticTranslationState.Missing && machineEnabled && document == null) {
                when {
                    original.isBlank() -> Text(com.lirix.app.ui.i18n.LirixStrings.message("Для машинного перевода нужен исходный текст песни"), color = c.textSecondary)
                    machineBusy -> {
                        if (modelDownload != null && modelDownload.total > 0) {
                            LinearProgressIndicator(progress = (modelDownload.received.toFloat() / modelDownload.total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                            Text(com.lirix.app.ui.i18n.LirixStrings.message("Загрузка моделей: {0} / {1} МБ", modelDownload.received / 1_000_000, modelDownload.total / 1_000_000), color = c.textSecondary)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(com.lirix.app.ui.i18n.LirixStrings.message("Переводим с {0}…", machineFallback?.engineInfo?.name.orEmpty()), color = c.textSecondary)
                        }
                    }
                    machineError -> {
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Машинный перевод не выполнен. Оригинал сохранён."), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { machineRetry++ }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Перевести с {0} ещё раз", machineFallback?.engineInfo?.name.orEmpty())) }
                    }
                    machineResult is MachineTranslationResult.ModelRequired -> {
                        Text(com.lirix.app.ui.i18n.LirixStrings.message(machineFallback?.engineInfo?.downloadHint.orEmpty()), color = c.textSecondary)
                        TextButton(onClick = { downloadRequested = true; machineRetry++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(com.lirix.app.ui.i18n.LirixStrings.message("Скачать модели и перевести с {0}", machineFallback?.engineInfo?.name.orEmpty()))
                        }
                    }
                    machineResult is MachineTranslationResult.UnsupportedPair -> Text(com.lirix.app.ui.i18n.LirixStrings.message("Машинный перевод этой пары языков пока не поддерживается"), color = c.textSecondary)
                    machineResult == MachineTranslationResult.SameLanguage -> Text(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал уже на выбранном языке"), color = c.textSecondary)
                    machineResult == MachineTranslationResult.UnknownLanguage -> Text(com.lirix.app.ui.i18n.LirixStrings.message("Язык оригинала не определён или не поддерживается"), color = c.textSecondary)
                }
            }
            if (!compact) ImportForm()
            if (!settingsExpanded) ErrorFeedback()
            if (entity != null && cached == null) Text(com.lirix.app.ui.i18n.LirixStrings.message("Сохранённый перевод повреждён. Можно импортировать его заново."), color = c.textSecondary)
            if (preview != null) {
                Text(com.lirix.app.ui.i18n.LirixStrings.message("Предпросмотр — ещё не сохранено"), color = c.accentPrimary, fontWeight = FontWeight.Bold)
                if (compact) Text("${preview!!.artist} — ${preview!!.title}", color = c.textPrimary, fontWeight = FontWeight.Bold)
                uncertainCandidate?.let { chosen ->
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Названия отличаются. Проверьте оба варианта перед сохранением."), color = c.textSecondary)
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("В поиске: {0} — {1}", chosen.artist, chosen.title), color = c.textPrimary)
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("На странице: {0} — {1}", preview!!.artist, preview!!.title), color = c.textPrimary)
                }
                Row {
                    Checkbox(confirmed, { confirmed = it }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Подтвердить соответствие перевода текущему треку") })
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Это перевод нужной песни и версии записи"), color = c.textPrimary)
                }
                Button(enabled = confirmed && !busy, onClick = { savePreview() }) {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Сохранить перевод офлайн"))
                }
            } else if (!compact && saved.size > 1) {
                Text(com.lirix.app.ui.i18n.LirixStrings.message("Сохранённые варианты"), color = c.textSecondary)
                saved.forEach { row ->
                    val variant = remember(row) { runCatching { TranslationDocuments.decode(row.documentJson) }.getOrNull() }
                    TextButton(onClick = { selectedUrl = row.sourceUrl }) { Text(variant?.let { "${it.title} · ${it.translator.ifBlank { it.sourceName }}" } ?: row.sourceUrl) }
                }
            }
            document?.let { translation ->
                if (!compact) Text("${translation.artist} — ${translation.title}", color = c.textPrimary, fontWeight = FontWeight.Bold)
                if (!compact && translation.translatedTitle.isNotBlank()) Text(translation.translatedTitle, color = c.textSecondary)
                if (!compact) Text(com.lirix.app.ui.i18n.LirixStrings.message("Источник: {0} · Перевод: {1}", translation.sourceName, translation.translator.ifBlank { com.lirix.app.ui.i18n.LirixStrings.message("автор не указан") }), color = c.textSecondary)
                if (!compact) translation.warnings.forEach { Text(it, color = c.textSecondary) }
                if (!compact) TextButton(onClick = { openSource(translation.sourceUrl) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Открыть источник")) }
                if (mode == 2 && (!compact || !translation.aligned)) Text(if (translation.aligned) com.lirix.app.ui.i18n.LirixStrings.message("Оригинал и перевод со страницы источника. Без синхронизации караоке.")
                    else com.lirix.app.ui.i18n.LirixStrings.message("Оригинал и перевод отдельными текстами. Соответствие строк не подтверждено."), color = c.textMuted, fontSize = 12.sp)
            }
            if (mode == 0 || (automatic != null && document == null && original.isNotBlank())) {
                SelectionContainer {
                    val sourceText = original.ifBlank { document?.blocks?.joinToString("\n") { it.original }.orEmpty() }
                    if (sourceText.isBlank()) Text(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал пока не сохранён"), color = c.textPrimary, fontSize = 18.sp)
                    else if (originalLineContent == null) Text(sourceText, color = c.textPrimary, fontSize = 18.sp)
                    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sourceText.lines().forEach { originalLineContent(it) }
                    }
                }
            } else if (document == null) {
                if (automatic == null && error == null) {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Перевод на выбранный язык пока не добавлен"), color = c.textPrimary)
                    Text(if (automatic == null) com.lirix.app.ui.i18n.LirixStrings.message("Откройте «Добавить перевод», вставьте ссылку источника и проверьте предпросмотр. Сохранённый перевод будет доступен без интернета.")
                        else com.lirix.app.ui.i18n.LirixStrings.message("Перевод ищется по исполнителю и названию. Точное совпадение сохраняется автоматически и будет доступно без интернета."), color = c.textSecondary)
                }
            } else if (!document.aligned) SelectionContainer {
                val sourceText = original.ifBlank { document.blocks.joinToString("\n") { it.original } }
                @Composable
                fun OriginalSection() {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал"), color = c.textSecondary, fontWeight = FontWeight.Bold)
                    if (sourceText.isBlank()) {
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал пока не сохранён"), color = c.textSecondary, fontSize = 17.sp)
                    } else if (originalLineContent != null) {
                        sourceText.lines().forEach { originalLineContent(it) }
                    } else {
                        Text(sourceText, color = c.textSecondary, fontSize = 17.sp)
                    }
                }
                @Composable
                fun TranslatedSection(showHeading: Boolean) {
                    if (showHeading) Text(com.lirix.app.ui.i18n.LirixStrings.message("Перевод"), color = c.textSecondary, fontWeight = FontWeight.Bold)
                    document.blocks.filter { it.translated.isNotBlank() }.forEach { block ->
                        Text(block.translated, color = c.textPrimary, fontSize = 18.sp,
                            fontWeight = if (block.section) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (mode == 2 && maxWidth >= 600.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { OriginalSection() }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { TranslatedSection(true) }
                        }
                    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (mode == 2) OriginalSection()
                        TranslatedSection(mode == 2)
                    }
                }
            } else SelectionContainer {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = mode == 2 && maxWidth >= 600.dp
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    document.blocks.forEach { block ->
                        if (block.original.isBlank() && block.translated.isBlank()) Spacer(Modifier.height(12.dp))
                        else if (columns) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Box(Modifier.weight(1f)) {
                                if (originalLineContent != null) originalLineContent(block.original)
                                else Text(block.original.ifBlank { com.lirix.app.ui.i18n.LirixStrings.message("[Нет строки оригинала]") }, color = c.textSecondary, fontSize = 17.sp)
                            }
                            Text(block.translated.ifBlank { com.lirix.app.ui.i18n.LirixStrings.message("[Нет перевода строки]") }, color = c.textPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        } else
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (mode == 2) {
                                if (originalLineContent != null) originalLineContent(block.original)
                                else Text(block.original.ifBlank { com.lirix.app.ui.i18n.LirixStrings.message("[Нет строки оригинала]") }, color = c.textSecondary, fontSize = 17.sp)
                            }
                            Text(block.translated.ifBlank { com.lirix.app.ui.i18n.LirixStrings.message("[Нет перевода строки]") }, color = c.textPrimary,
                                fontSize = 18.sp, fontWeight = if (block.section) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                }
            }
        }
    }
}
