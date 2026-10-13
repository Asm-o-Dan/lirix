package com.lirix.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lirix.app.feature.lyrics.AmDmChordParser
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.components.ExportRuleDialog
import com.lirix.app.ui.components.ImportRuleDialog
import com.lirix.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder

/**
 * JS bridge to receive element inspection events from teach_inspector.js.
 */
class TeachJsBridge(
    private val onSelected: (parentSelector: String, stripSelectorsJson: String, text: String, linesCount: Int, domain: String, parentsCount: Int, strippedCount: Int) -> Unit,
    private val onCleared: () -> Unit
) {
    @JavascriptInterface
    fun onElementSelected(
        parentSelector: String,
        stripSelectorsJson: String,
        text: String,
        linesCount: Int,
        domain: String,
        parentsCount: Int,
        strippedCount: Int
    ) {
        Handler(Looper.getMainLooper()).post {
            onSelected(parentSelector, stripSelectorsJson, text, linesCount, domain, parentsCount, strippedCount)
        }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        Handler(Looper.getMainLooper()).post {
            onCleared()
        }
    }
}

enum class TargetContentType {
    LYRICS,
    CHORDS
}

/**
 * Interactive WebView Teach Mode Screen for inspecting websites,
 * selecting lyrics containers, excluding unwanted sub-elements, and synthesizing custom parser rules.
 *
 * Spec: SPEC-TEACH-05 / TASK-TEACH-05 / ADR-014
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TeachModeScreen(
    initialUrl: String?,
    targetTrack: TrackEntity?,
    onClose: () -> Unit,
    onRuleSaved: (CustomLyricsRuleEntity) -> Unit = {},
    onLyricsAttached: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }

    var isInspectorMode by rememberSaveable { mutableStateOf(false) } // false = Серфинг, true = Инспектор
    var canGoBack by remember { mutableStateOf(false) }
    var selectedBlocksCount by remember { mutableIntStateOf(0) }
    var strippedBlocksCount by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var selectedSelector by remember { mutableStateOf("") }
    var stripSelectorsJson by remember { mutableStateOf("[]") }
    var selectedText by remember { mutableStateOf("") }
    var selectedLinesCount by remember { mutableIntStateOf(0) }
    var detectedDomain by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(initialUrl.orEmpty()) }

    var contentType by rememberSaveable { mutableStateOf(TargetContentType.LYRICS) }
    var showMenu by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var ruleToExport by remember { mutableStateOf<CustomLyricsRuleEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AmoledBlack)
    ) {
        // Разгруженная шапка экрана
        Surface(
            color = AppColors.SurfaceLevel1,
            border = BorderStroke(1.dp, AppColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Слева: Назад (если история WebView) и Закрыть
                if (canGoBack) {
                    IconButton(
                        onClick = { webViewRef?.goBack() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = AppColors.CyberCyan
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = AppColors.TextSecondary
                    )
                }

                // По центру: Название трека и текущий хост
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = if (targetTrack != null) "${targetTrack.title} — ${targetTrack.artist}" else "Режим обучения",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = detectedDomain.ifBlank { "Веб-инспектор" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isInspectorMode) AppColors.HyperViolet else AppColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Справа: Капсула [🌐 Сёрфинг | 🎯 Инспектор]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceLevel2)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isInspectorMode) AppColors.SurfaceLevel3 else Color.Transparent)
                            .clickable {
                                isInspectorMode = false
                                webViewRef?.evaluateJavascript("window.setTeachMode(false);", null)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "🌐 Сёрфинг",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (!isInspectorMode) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (!isInspectorMode) AppColors.CyberCyan else AppColors.TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isInspectorMode) AppColors.HyperViolet else Color.Transparent)
                            .clickable {
                                isInspectorMode = true
                                webViewRef?.evaluateJavascript("window.setTeachMode(true);", null)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "🎯 Инспектор",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isInspectorMode) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isInspectorMode) AppColors.AmoledBlack else AppColors.TextSecondary
                        )
                    }
                }

                // Меню ⋮ (Overflow Menu)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Меню",
                            tint = AppColors.TextPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(AppColors.SurfaceLevel2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Импорт правила", color = AppColors.TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = AppColors.CyberCyan)
                            },
                            onClick = {
                                showMenu = false
                                showImportDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Экспорт правила", color = AppColors.TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, tint = AppColors.HyperViolet)
                            },
                            onClick = {
                                showMenu = false
                                val cleanDomain = detectedDomain.ifBlank { "custom" }
                                val ruleId = "rule_${cleanDomain.replace(".", "_")}_${System.currentTimeMillis() % 10000}"
                                val searchTemplate = "https://$cleanDomain/search?q={query}"
                                val parsedStrip = runCatching { JSONArray(stripSelectorsJson) }.getOrDefault(JSONArray())

                                val ruleJson = JSONObject().apply {
                                    put("domain", cleanDomain)
                                    put("name", cleanDomain)
                                    put("search", JSONObject().apply {
                                        put("urlTemplate", searchTemplate)
                                    })
                                    put("content", JSONObject().apply {
                                        if (contentType == TargetContentType.CHORDS) {
                                            put("chordsSelector", selectedSelector.ifBlank { "body" })
                                        } else {
                                            put("selector", selectedSelector.ifBlank { "body" })
                                        }
                                        put("stripSelectors", parsedStrip)
                                    })
                                }.toString()

                                ruleToExport = CustomLyricsRuleEntity(
                                    id = ruleId,
                                    domain = cleanDomain,
                                    name = cleanDomain,
                                    ruleJson = ruleJson,
                                    isEnabled = true,
                                    priority = 100
                                )
                                showExportDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Сбросить выделения", color = AppColors.TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = AppColors.AmberGold)
                            },
                            onClick = {
                                showMenu = false
                                webViewRef?.evaluateJavascript("window.clearTeachSelection();", null)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Открыть в браузере", color = AppColors.TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = AppColors.TextSecondary)
                            },
                            onClick = {
                                showMenu = false
                                val urlToOpen = currentUrl.ifBlank { webViewRef?.url.orEmpty() }
                                if (urlToOpen.isNotBlank()) {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Встроенный WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false

                    addJavascriptInterface(
                        TeachJsBridge(
                            onSelected = { parentSelector, stripJson, text, lines, domain, parents, stripped ->
                                selectedSelector = parentSelector
                                stripSelectorsJson = stripJson
                                selectedText = text
                                selectedLinesCount = lines
                                detectedDomain = domain
                                selectedBlocksCount = parents
                                strippedBlocksCount = stripped
                            },
                            onCleared = {
                                selectedSelector = ""
                                stripSelectorsJson = "[]"
                                selectedText = ""
                                selectedLinesCount = 0
                                selectedBlocksCount = 0
                                strippedBlocksCount = 0
                            }
                        ),
                        "TeachBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() == true
                            if (!url.isNullOrBlank()) {
                                currentUrl = url
                                detectedDomain = Uri.parse(url).host.orEmpty()
                            }
                            try {
                                val script = context.assets.open("teach_inspector.js").bufferedReader().use { it.readText() }
                                view?.evaluateJavascript(script) {
                                    view.evaluateJavascript("window.setTeachMode($isInspectorMode);", null)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to inject teach_inspector.js")
                            }
                        }
                    }

                    val startUrl = initialUrl?.takeIf { it.isNotBlank() }
                        ?: ("https://www.google.com/search?q=" + URLEncoder.encode("${targetTrack?.artist.orEmpty()} ${targetTrack?.title.orEmpty()} текст песни", "UTF-8"))
                    currentUrl = startUrl
                    loadUrl(startUrl)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Нижняя панель действий (Inspector Dock)
        if (selectedSelector.isNotBlank() || selectedBlocksCount > 0) {
            Surface(
                color = AppColors.SurfaceLevel2,
                border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Строка 1: Чип селектора, бейдж исключений, кнопка Родитель, сброс
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppColors.SurfaceLevel3,
                                border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "🟣 $selectedSelector",
                                    color = AppColors.HyperViolet,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (strippedBlocksCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF3F1414),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "🔴 $strippedBlocksCount вырезано",
                                        color = Color(0xFFFF8A8A),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.expandTeachSelectionToParent();", null)
                                }
                            ) {
                                Text(
                                    text = "▲ Родитель",
                                    color = AppColors.CyberCyan,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            TextButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.clearTeachSelection();", null)
                                }
                            ) {
                                Text(
                                    text = "✕ Сброс",
                                    color = AppColors.TextTertiary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка 2: Переключатель целевого типа [📝 Текст песни] / [🎸 Аккорды]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.SurfaceLevel3)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (contentType == TargetContentType.LYRICS) AppColors.CyberCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (contentType == TargetContentType.LYRICS) AppColors.CyberCyan else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { contentType = TargetContentType.LYRICS }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "📝 Текст песни",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (contentType == TargetContentType.LYRICS) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (contentType == TargetContentType.LYRICS) AppColors.CyberCyan else AppColors.TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (contentType == TargetContentType.CHORDS) AppColors.AmberGold.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (contentType == TargetContentType.CHORDS) AppColors.AmberGold else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { contentType = TargetContentType.CHORDS }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🎸 Аккорды",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (contentType == TargetContentType.CHORDS) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (contentType == TargetContentType.CHORDS) AppColors.AmberGold else AppColors.TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка 3: Превью очищенного текста
                    Text(
                        text = selectedText.lines().filter { it.isNotBlank() }.take(3).joinToString("\n").ifBlank { "..." },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = AppColors.TextTertiary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Строка 4: Единая акцентная кнопка действия
                    Button(
                        onClick = {
                            val cleanDomain = detectedDomain.ifBlank { "custom" }
                            val ruleId = "rule_${cleanDomain.replace(".", "_")}_${System.currentTimeMillis() % 10000}"
                            val searchTemplate = "https://$cleanDomain/search?q={query}"
                            val parsedStrip = runCatching { JSONArray(stripSelectorsJson) }.getOrDefault(JSONArray())

                            val ruleJson = JSONObject().apply {
                                put("domain", cleanDomain)
                                put("name", cleanDomain)
                                put("search", JSONObject().apply {
                                    put("urlTemplate", searchTemplate)
                                })
                                put("content", JSONObject().apply {
                                    if (contentType == TargetContentType.CHORDS) {
                                        put("chordsSelector", selectedSelector)
                                    } else {
                                        put("selector", selectedSelector)
                                    }
                                    put("stripSelectors", parsedStrip)
                                })
                            }.toString()

                            val entity = CustomLyricsRuleEntity(
                                id = ruleId,
                                domain = cleanDomain,
                                name = cleanDomain,
                                ruleJson = ruleJson,
                                isEnabled = true,
                                priority = 100
                            )

                            scope.launch(Dispatchers.IO) {
                                db.lyricsDao().saveRule(entity)
                                if (selectedText.isNotBlank() && targetTrack != null) {
                                    val trackKey = targetTrack.trackKey
                                    val plainLyrics = if (contentType == TargetContentType.LYRICS) selectedText else targetTrack.plainLyrics
                                    val chords = if (contentType == TargetContentType.CHORDS) selectedText else AmDmChordParser.parseAmDmHtml(selectedText)

                                    db.musicDao().updateLyrics(trackKey, plainLyrics, null)
                                    db.lyricsDao().saveLyrics(
                                        LyricsCacheEntity(
                                            trackKey = trackKey,
                                            plainLyrics = plainLyrics,
                                            syncedLyricsLrc = null,
                                            chordsAmDm = chords,
                                            userNotes = targetTrack.userNotes,
                                            provider = "teach:$cleanDomain"
                                        )
                                    )
                                }
                            }
                            onRuleSaved(entity)
                            if (selectedText.isNotBlank() && targetTrack != null) {
                                onLyricsAttached(selectedText)
                                val msg = if (contentType == TargetContentType.CHORDS) "Правило сохранено и аккорды привязаны!" else "Правило сохранено и текст привязан!"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onClose()
                            } else {
                                Toast.makeText(context, "Правило для $cleanDomain сохранено!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.HyperViolet),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚡ Сохранить правило и привязать к треку",
                            color = AppColors.AmoledBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Export rule dialog
    if (showExportDialog && ruleToExport != null) {
        ExportRuleDialog(
            rule = ruleToExport!!,
            onDismiss = {
                showExportDialog = false
                ruleToExport = null
            }
        )
    }

    // Import rule dialog
    if (showImportDialog) {
        ImportRuleDialog(
            onDismiss = { showImportDialog = false },
            onRuleImported = { importedRule ->
                onRuleSaved(importedRule)
            }
        )
    }
}
