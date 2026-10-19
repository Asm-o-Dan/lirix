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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.platform.currentTimeMillis
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.*

/** Static paired source text; never assigns timestamps or changes the player's lyrics. */
@Composable
fun TranslationPanel(trackKey: String, trackLabel: String, original: String, dao: SongTranslationDao,
                     load: suspend (String) -> SongTranslation, modifier: Modifier = Modifier,
                     automatic: AutomaticSongTranslations? = null, title: String = "", artist: String = "") {
    val language = automatic?.language?.collectAsState()?.value ?: "ru"
    key(trackKey, language) { TranslationPanelContent(trackKey, trackLabel, original, dao, load, modifier, automatic, title, artist, language) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TranslationPanelContent(trackKey: String, trackLabel: String, original: String, dao: SongTranslationDao,
                                    load: suspend (String) -> SongTranslation, modifier: Modifier,
                                    automatic: AutomaticSongTranslations?, title: String, artist: String, language: String) {
    val c = LirixThemeTokens.colors
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var saved by remember { mutableStateOf<List<SongTranslationEntity>>(emptyList()) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableIntStateOf(if (automatic != null) 2 else 0) }
    var importing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<SongTranslation?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
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
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (automatic != null) Text("Язык перевода", color = c.textSecondary, fontSize = 12.sp)
        if (automatic != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TranslationLanguages.labels.forEach { (code, label) ->
                FilterChip(language == code, { automatic.setLanguage(code) }, label = { Text(label) }, enabled = !busy)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Оригинал", TranslationLanguages.labels[language] ?: language, "Вместе").forEachIndexed { index, label ->
                FilterChip(mode == index, { mode = index }, label = { Text(label) })
            }
            TextButton(enabled = !busy, onClick = { importing = !importing; preview = null; confirmed = false }) {
                Text(if (importing) "Закрыть импорт" else if (automatic != null) "Ссылка вручную" else "Добавить перевод")
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cached == null && preview == null && automatic != null) {
                when (autoState) {
                    AutomaticTranslationState.Searching -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Ищем перевод…", color = c.textSecondary)
                    }
                    AutomaticTranslationState.Missing -> Text("Перевод на выбранный язык не найден", color = c.textSecondary)
                    is AutomaticTranslationState.Failed -> Text("Поиск перевода: ${autoState.message}", color = c.textSecondary)
                    is AutomaticTranslationState.Candidates -> {
                        Text("Есть несколько вариантов или отличается версия. Выберите нужную песню:", color = c.textSecondary)
                        autoState.values.forEach { candidate ->
                            TextButton(enabled = !busy, onClick = {
                                if (busy) return@TextButton
                                busy = true
                                scope.launch {
                                    error = null
                                    try {
                                        preview = withTimeout(25_000) { load(candidate.sourceUrl) }
                                        input = candidate.sourceUrl; importing = true; confirmed = false; mode = 2
                                    } catch (_: TimeoutCancellationException) { error = "Источник не ответил вовремя" }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { error = e.message ?: "Не удалось загрузить перевод" }
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
            if (importing) {
                Text("Перевод для: $trackLabel", color = c.textPrimary)
                OutlinedTextField(input, { input = it; preview = null; confirmed = false }, enabled = !busy,
                    label = { Text("Ссылка Amalgama или LyricsTranslate") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(enabled = !busy && input.isNotBlank(), onClick = {
                    if (busy || input.isBlank()) return@Button
                    busy = true
                    scope.launch {
                    busy = true; error = null; preview = null; confirmed = false
                    try { preview = withTimeout(25_000) { load(input) }; mode = 2 }
                    catch (e: TimeoutCancellationException) { error = "Источник не ответил вовремя. Попробуйте ещё раз." }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Не удалось загрузить перевод" }
                    finally { busy = false }
                } }) { Text(if (busy) "Загрузка…" else "Предпросмотр") }
            }
            error?.let { Text(it, color = c.accentPrimary) }
            if (entity != null && cached == null) Text("Сохранённый перевод повреждён. Можно импортировать его заново.", color = c.textSecondary)
            if (preview != null) {
                Text("Предпросмотр — ещё не сохранено", color = c.accentPrimary, fontWeight = FontWeight.Bold)
                Row {
                    Checkbox(confirmed, { confirmed = it }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Подтвердить соответствие перевода текущему треку" })
                    Text("Это перевод нужной песни и версии записи", color = c.textPrimary)
                }
                Button(enabled = confirmed && !busy, onClick = {
                    if (!confirmed || busy) return@Button
                    val pending = preview ?: return@Button
                    if (pending.language != language) { error = "Выберите язык загруженного перевода"; return@Button }
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
                } }) { Text("Сохранить перевод офлайн") }
            } else if (saved.size > 1) {
                Text("Сохранённые варианты", color = c.textSecondary)
                saved.forEach { row ->
                    val variant = remember(row) { runCatching { TranslationDocuments.decode(row.documentJson) }.getOrNull() }
                    TextButton(onClick = { selectedUrl = row.sourceUrl }) { Text(variant?.let { "${it.title} · ${it.translator.ifBlank { it.sourceName }}" } ?: row.sourceUrl) }
                }
            }
            document?.let { translation ->
                Text("${translation.artist} — ${translation.title}", color = c.textPrimary, fontWeight = FontWeight.Bold)
                if (translation.translatedTitle.isNotBlank()) Text(translation.translatedTitle, color = c.textSecondary)
                Text("Источник: ${translation.sourceName} · Перевод: ${translation.translator.ifBlank { "автор не указан" }}", color = c.textSecondary)
                translation.warnings.forEach { Text(it, color = c.textSecondary) }
                TextButton(onClick = { runCatching { uri.openUri(translation.sourceUrl) }.onFailure { error = "Не удалось открыть источник" } }) { Text("Открыть источник") }
                if (mode == 2) Text(if (translation.aligned) "Оригинал и перевод со страницы источника. Без синхронизации караоке."
                    else "Оригинал и перевод отдельными текстами. Соответствие строк не подтверждено.", color = c.textMuted, fontSize = 12.sp)
            }
            if (mode == 0) {
                SelectionContainer { Text(original.ifBlank { document?.blocks?.joinToString("\n") { it.original }.orEmpty().ifBlank { "Оригинал пока не сохранён" } }, color = c.textPrimary, fontSize = 18.sp) }
            } else if (document == null) {
                Text("Перевод на выбранный язык пока не добавлен", color = c.textPrimary)
                Text(if (automatic == null) "Откройте «Добавить перевод», вставьте ссылку источника и проверьте предпросмотр. Сохранённый перевод будет доступен без интернета."
                    else "Перевод ищется по исполнителю и названию. Точное совпадение сохраняется автоматически и будет доступно без интернета.", color = c.textSecondary)
            } else SelectionContainer {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = mode == 2 && maxWidth >= 600.dp
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    document.blocks.forEach { block ->
                        if (columns) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(block.original.ifBlank { "[Нет строки оригинала]" }, color = c.textSecondary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Text(block.translated.ifBlank { "[Нет перевода строки]" }, color = c.textPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        } else
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (mode == 2) Text(block.original.ifBlank { "[Нет строки оригинала]" }, color = c.textSecondary, fontSize = 17.sp)
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
