package com.lirix.app.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.desktop.library.DesktopLibraryRepository
import com.lirix.app.desktop.lyrics.*
import com.lirix.app.desktop.system.DesktopFiles
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder

@Composable
internal fun DesktopSourceDialog(track: TrackEntity, repository: DesktopLibraryRepository, onDismiss: () -> Unit,
    onRefresh: () -> Unit, onReject: () -> Unit, isCurrentTrack: Boolean = true, onAttach: suspend (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    DesktopFeatureDialog("Текст песни и источники", { if (!busy) onDismiss() }) {
    val c = LirixThemeTokens.colors
    val scope = rememberCoroutineScope()
    val rules by repository.sourceRules.collectAsState()
    var url by remember { mutableStateOf("") }
    var selector by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(emptyList<SourceCandidate>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf(false) }
    fun run(block: suspend () -> Unit) { if (busy) return; busy = true; scope.launch { error = null; try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } finally { busy = false } } }
    Text("${track.artist} — ${track.title}", color = c.textPrimary, fontSize = 22.sp)
    if (!isCurrentTrack) Text("Сейчас играет другой трек. Текст сохранится для песни, указанной выше.", color = c.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy && isCurrentTrack, onClick = { onRefresh(); onDismiss() }) { Text("Повторить поиск") }
        OutlinedButton(enabled = !busy && isCurrentTrack, onClick = { onReject(); onDismiss() }) { Text("Это не тот текст") }
        TextButton(enabled = !busy, onClick = { java.awt.Desktop.getDesktop().browse(URI("https://www.google.com/search?q=" + URLEncoder.encode("${track.artist} ${track.title} текст", "UTF-8"))) }) { Text("Искать в браузере") }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Правильный текст песни", color = c.textPrimary, fontSize = 20.sp)
        Text("Вставьте текст ниже и сохраните для этой песни. Он будет доступен без интернета.", color = c.textSecondary)
        TextButton(onClick = { linkInput = !linkInput }, enabled = !busy) { Text(if (linkInput) "Скрыть загрузку по ссылке" else "Загрузить текст по ссылке") }
        if (linkInput) {
        OutlinedTextField(url, { url = it; candidates = emptyList() }, enabled = !busy, label = { Text("Ссылка на страницу песни") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && url.isNotBlank(), onClick = { run { val inspection = DesktopCustomSources.inspect(url); url = inspection.url; candidates = inspection.candidates; if (candidates.isEmpty()) error = "Блоки не найдены. Укажите селектор или вставьте текст вручную." } }) { Text("Найти блоки с текстом") }
        candidates.take(15).forEachIndexed { index, candidate -> Surface(onClick = { if (!busy) { selector = candidate.selector; run { result = DesktopCustomSources.extract(url, selector) } } }, color = c.surfaceCard, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) { Text("Вариант ${index + 1}", color = c.accentPrimary, maxLines = 1); Text(candidate.preview, color = c.textSecondary, maxLines = 3) }
        } }
        }
        OutlinedTextField(result, { result = it }, enabled = !busy, label = { Text("Текст песни") }, modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 10)
        Button(enabled = result.isNotBlank() && !busy, onClick = { run { onAttach(result); onDismiss() } }) { Text(if (busy) "Сохраняем…" else "Сохранить текст песни") }
        HorizontalDivider()
        TextButton(onClick = { advanced = !advanced }, enabled = !busy) { Text(if (advanced) "Скрыть настройки источников" else "Дополнительные настройки источников") }
        if (advanced) {
        OutlinedTextField(selector, { selector = it; result = "" }, enabled = !busy, label = { Text("CSS-селектор блока") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = !busy && url.isNotBlank() && selector.isNotBlank(), onClick = { run { result = DesktopCustomSources.extract(url, selector) } }) { Text("Проверить извлечение") }

        OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Название источника") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(search, { search = it }, enabled = !busy, label = { Text("Адрес поиска с {query}, необязательно") }, modifier = Modifier.fillMaxWidth())
        Text("Без адреса поиска правило привязывается только к этой песне.", color = c.textMuted)
        OutlinedButton(enabled = !busy && name.isNotBlank() && result.isNotBlank() && selector.isNotBlank(), onClick = { run {
            val base = DesktopCustomSources.rule(name, url, selector, search)
            val json = Json.parseToJsonElement(base.ruleJson).jsonObject
            val bound = JsonObject(json + mapOf("trackTitle" to JsonPrimitive(track.title), "trackArtist" to JsonPrimitive(track.artist)))
            withContext(Dispatchers.IO) { repository.database.lyricsDao().saveRule(base.copy(ruleJson = bound.toString())) }
        } }) { Text("Сохранить правило источника") }
        HorizontalDivider()
        Text("Мои правила", color = c.textPrimary, fontSize = 20.sp)
        OutlinedButton(enabled = !busy, onClick = { run {
            val file = withContext(Dispatchers.IO) { DesktopFiles.choose("Импорт правила", "json", false) } ?: return@run
            val imported = withContext(Dispatchers.IO) { require(file.length() <= 500_000) { "Файл слишком большой" }; DesktopRuleBundles.import(file.readText()) }
            withContext(Dispatchers.IO) { repository.database.lyricsDao().saveRule(imported) }
        } }) { Text("Импортировать правило с телефона") }
        rules.forEach { rule -> Column(Modifier.fillMaxWidth().background(c.surfaceCard).padding(12.dp)) {
            Row { Checkbox(rule.isEnabled, { enabled -> repository.perform { repository.database.lyricsDao().setRuleEnabled(rule.id, enabled) } }); Text(rule.name, color = c.textPrimary, modifier = Modifier.weight(1f)); Text(rule.domain, color = c.textSecondary) }
            Row {
                TextButton(onClick = { run {
                    val file = withContext(Dispatchers.IO) { DesktopFiles.choose("Экспорт правила", "json", true, "lirix-rule.json") } ?: return@run
                    withContext(Dispatchers.IO) { file.writeText(DesktopRuleBundles.export(rule)) }
                } }) { Text("Экспорт") }
                TextButton(enabled = !rule.isBuiltIn, onClick = { repository.perform { repository.database.lyricsDao().deleteRule(rule.id) } }) { Text("Удалить") }
            }
        } }
        }
    }
    error?.let { Text(it, color = c.accentPrimary) }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    TextButton(onClick = onDismiss, enabled = !busy) { Text("Закрыть") }
}

}
