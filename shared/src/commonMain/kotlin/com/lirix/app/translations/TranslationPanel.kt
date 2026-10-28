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
                     originalLineContent: (@Composable (String) -> Unit)? = null) {
    val language = automatic?.language?.collectAsState()?.value ?: "ru"
    key(trackKey, language) { TranslationPanelContent(trackKey, trackLabel, original, dao, load, modifier, automatic, title, artist, language, compact, settingsExpanded, onDismissSettings, originalLineContent) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun TranslationPanelContent(trackKey: String, trackLabel: String, original: String, dao: SongTranslationDao,
                                    load: suspend (String) -> SongTranslation, modifier: Modifier,
                                    automatic: AutomaticSongTranslations?, title: String, artist: String, language: String,
                                    compact: Boolean, settingsExpanded: Boolean, onDismissSettings: () -> Unit,
                                    originalLineContent: (@Composable (String) -> Unit)?) {
    val c = LirixThemeTokens.colors
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var saved by remember { mutableStateOf<List<SongTranslationEntity>>(emptyList()) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableIntStateOf(if (automatic != null) 2 else 0) }
    var importing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<SongTranslation?>(null) }
    var uncertainCandidate by remember { mutableStateOf<TranslationCandidate?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var languageMenu by remember { mutableStateOf(false) }
    var showAttribution by remember { mutableStateOf(false) }
    val autoState = automatic?.states?.collectAsState()?.value?.get(automatic.stateKey(trackKey, language))
    LaunchedEffect(automatic, trackKey, title, artist) { automatic?.resolve(trackKey, title, artist, language = language) }
    LaunchedEffect(dao, trackKey) {
        try { dao.observe(trackKey, language).collect { saved = it } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Не удалось прочитать сохранённый перевод: ${e.message}" }
    }
    val entity = saved.firstOrNull { it.sourceUrl == selectedUrl } ?: saved.firstOrNull()
    val cached = remember(entity) { entity?.let { runCatching { TranslationDocuments.decode(it.documentJson) }.getOrNull() } }
    val document = preview ?: cached
    @Composable
    fun Controls() {
        if (automatic != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box {
                TextButton(onClick = { languageMenu = true }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Выбрать язык перевода" }) {
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
                Text(if (importing) "Закрыть" else "По ссылке", maxLines = 1)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал"), if (automatic != null) com.lirix.app.ui.i18n.LirixStrings.message("Перевод") else TranslationLanguages.labels[language] ?: language, com.lirix.app.ui.i18n.LirixStrings.message("Вместе")).forEachIndexed { index, label ->
                FilterChip(mode == index, { mode = index }, label = { Text(label) })
            }
            if (automatic == null) TextButton(enabled = !busy, onClick = { importing = !importing; preview = null; confirmed = false }) {
                Text(if (importing) "Закрыть импорт" else if (automatic != null) "Ссылка вручную" else "Добавить перевод")
            }
        }
    }
    @Composable
    fun ImportForm() {
            if (importing) {
                Text("Перевод для: $trackLabel", color = c.textPrimary)
                OutlinedTextField(input, { input = it; preview = null; confirmed = false }, enabled = !busy,
                    label = { Text("Ссылка Amalgama или LyricsTranslate") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(enabled = !busy && input.isNotBlank(), onClick = {
                    if (busy || input.isBlank()) return@Button
                    busy = true
                    scope.launch {
                    busy = true; error = null; preview = null; confirmed = false
                    try { preview = withTimeout(25_000) { load(input) }; uncertainCandidate = null; mode = 2; if (compact) onDismissSettings() }
                    catch (e: TimeoutCancellationException) { error = "Источник не ответил вовремя. Попробуйте ещё раз." }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Не удалось загрузить перевод" }
                    finally { busy = false }
                } }) { Text(if (busy) "Загрузка…" else "Предпросмотр") }
            }
    }
    if (compact && settingsExpanded) {
        ModalBottomSheet(onDismissRequest = onDismissSettings) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                .imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Перевод и язык", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Controls()
                ImportForm()
                error?.let { Text(it, color = c.accentPrimary) }
                TextButton(onClick = onDismissSettings) { Text("Готово") }
            }
        }
    }
    if (compact && showAttribution && document != null) {
        AlertDialog(onDismissRequest = { showAttribution = false },
            title = { Text("Об источнике перевода") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${document.artist} — ${document.title}")
                if (document.translatedTitle.isNotBlank()) Text(document.translatedTitle)
                Text("Источник: ${document.sourceName}")
                Text("Перевод: ${document.translator.ifBlank { "автор не указан" }}")
                Text(document.sourceUrl)
                document.warnings.forEach { Text(it) }
            } },
            confirmButton = { TextButton(onClick = {
                runCatching { uri.openUri(document.sourceUrl) }.onFailure { error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось открыть источник") }
            }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Открыть источник")) } },
            dismissButton = { TextButton(onClick = { showAttribution = false }) { Text("Закрыть") } })
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!compact) Controls()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cached == null && preview == null && automatic != null) {
                when (autoState) {
                    AutomaticTranslationState.Searching -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Ищем перевод…", color = c.textSecondary)
                    }
                    AutomaticTranslationState.Missing -> Text("Перевод на выбранный язык не найден", color = c.textSecondary)
                    is AutomaticTranslationState.Failed -> Text(com.lirix.app.ui.i18n.LirixStrings.message("Поиск перевода: {0}", com.lirix.app.ui.i18n.LirixStrings.message(autoState.message)), color = c.textSecondary)
                    is AutomaticTranslationState.Candidates -> {
                        if (autoState.unavailableSources.isNotEmpty()) Text(
                            com.lirix.app.ui.i18n.LirixStrings.message("Не все источники ответили ({0}). Показаны доступные варианты.", autoState.unavailableSources.joinToString(", ")),
                            color = c.textSecondary
                        )
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Есть несколько вариантов или отличается версия. Выберите нужную песню:"), color = c.textSecondary)
                        autoState.values.forEach { candidate ->
                            TextButton(enabled = !busy, onClick = {
                                if (busy) return@TextButton
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
                            }) { Text("${candidate.artist} — ${candidate.title}${candidate.translator.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}") }
                        }
                    }
                    else -> Unit
                }
                if (autoState != null && autoState != AutomaticTranslationState.Searching && autoState != AutomaticTranslationState.Ready)
                    TextButton(enabled = !busy, onClick = { scope.launch { automatic.resolve(trackKey, title, artist, true, language) } }) { Text("Искать снова") }
            }
            if (!compact) ImportForm()
            error?.let { Text(it, color = c.accentPrimary) }
            if (entity != null && cached == null) Text("Сохранённый перевод повреждён. Можно импортировать его заново.", color = c.textSecondary)
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
                Button(enabled = confirmed && !busy, onClick = {
                    if (!confirmed || busy) return@Button
                    val pending = preview ?: return@Button
                    if (pending.language != language) { error = com.lirix.app.ui.i18n.LirixStrings.message("Выберите язык загруженного перевода"); return@Button }
                    busy = true
                    scope.launch {
                    busy = true; error = null
                    try {
                        val row = SongTranslationEntity(trackKey, pending.language, pending.sourceUrl,
                            TranslationDocuments.encode(pending), currentTimeMillis())
                        withContext(Dispatchers.IO) { dao.save(row) }
                        // Update immediately; the Room observer confirms the persisted document separately.
                        saved = listOf(row) + saved.filterNot { it.sourceUrl == row.sourceUrl }
                        selectedUrl = row.sourceUrl; preview = null; importing = false; confirmed = false
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = "Не удалось сохранить: ${e.message}" }
                    finally { busy = false }
                } }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Сохранить перевод офлайн")) }
            } else if (saved.size > 1) {
                Text("Сохранённые варианты", color = c.textSecondary)
                saved.forEach { row ->
                    val variant = remember(row) { runCatching { TranslationDocuments.decode(row.documentJson) }.getOrNull() }
                    TextButton(onClick = { selectedUrl = row.sourceUrl }) { Text(variant?.let { "${it.title} · ${it.translator.ifBlank { it.sourceName }}" } ?: row.sourceUrl) }
                }
            }
            document?.let { translation ->
                if (compact) TextButton(onClick = { showAttribution = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(listOf(translation.sourceName, translation.translator).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Об источнике перевода: ${translation.sourceName}; ${translation.translator}" })
                }
                if (!compact) Text("${translation.artist} — ${translation.title}", color = c.textPrimary, fontWeight = FontWeight.Bold)
                if (!compact && translation.translatedTitle.isNotBlank()) Text(translation.translatedTitle, color = c.textSecondary)
                if (!compact) Text(com.lirix.app.ui.i18n.LirixStrings.message("Источник: {0} · Перевод: {1}", translation.sourceName, translation.translator.ifBlank { com.lirix.app.ui.i18n.LirixStrings.message("автор не указан") }), color = c.textSecondary)
                if (!compact) translation.warnings.forEach { Text(it, color = c.textSecondary) }
                if (!compact) TextButton(onClick = { runCatching { uri.openUri(translation.sourceUrl) }.onFailure { error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось открыть источник") } }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Открыть источник")) }
                if (mode == 2 && (!compact || !translation.aligned)) Text(if (translation.aligned) "Оригинал и перевод со страницы источника. Без синхронизации караоке."
                    else com.lirix.app.ui.i18n.LirixStrings.message("Оригинал и перевод отдельными текстами. Соответствие строк не подтверждено."), color = c.textMuted, fontSize = 12.sp)
            }
            if (mode == 0 || (automatic != null && document == null && original.isNotBlank())) {
                SelectionContainer {
                    val sourceText = original.ifBlank { document?.blocks?.joinToString("\n") { it.original }.orEmpty() }
                    if (sourceText.isBlank()) Text(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал пока не сохранён"), color = c.textPrimary, fontSize = 18.sp)
                    else if (originalLineContent == null) Text(sourceText, color = c.textPrimary, fontSize = 18.sp)
                    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { sourceText.lines().forEach { originalLineContent(it) } }
                }
            } else if (document == null) {
                Text("Перевод на выбранный язык пока не добавлен", color = c.textPrimary)
                Text(if (automatic == null) "Откройте «Добавить перевод», вставьте ссылку источника и проверьте предпросмотр. Сохранённый перевод будет доступен без интернета."
                    else "Перевод ищется по исполнителю и названию. Точное совпадение сохраняется автоматически и будет доступно без интернета.", color = c.textSecondary)
            } else if (!document.aligned) SelectionContainer {
                val sourceText = original.ifBlank { document.blocks.joinToString("\n") { it.original } }
                @Composable
                fun OriginalSection() {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал"), color = c.textSecondary, fontWeight = FontWeight.Bold)
                    if (sourceText.isBlank()) Text(com.lirix.app.ui.i18n.LirixStrings.message("Оригинал пока не сохранён"), color = c.textSecondary, fontSize = 17.sp)
                    else if (originalLineContent != null) sourceText.lines().forEach { originalLineContent(it) }
                    else Text(sourceText, color = c.textSecondary, fontSize = 17.sp)
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
                        if (columns) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Box(Modifier.weight(1f)) {
                                if (originalLineContent != null) originalLineContent(block.original)
                                else Text(block.original.ifBlank { "[Нет строки оригинала]" }, color = c.textSecondary, fontSize = 17.sp)
                            }
                            Text(block.translated.ifBlank { "[Нет перевода строки]" }, color = c.textPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        } else
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (mode == 2) {
                                if (originalLineContent != null) originalLineContent(block.original)
                                else Text(block.original.ifBlank { "[Нет строки оригинала]" }, color = c.textSecondary, fontSize = 17.sp)
                            }
                            Text(block.translated.ifBlank { "[Нет перевода строки]" }, color = c.textPrimary,
                                fontSize = 18.sp, fontWeight = if (block.section) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                }
            }
        }
    }
}
