package com.lirix.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
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
import com.lirix.app.ui.theme.LirixMobileTypography
import com.lirix.app.ui.theme.LirixThemeTokens
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

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
 * SPEC-UI-MOBILE-2.0 / ADR-023 / TASK-UI-MOB-06:
 * - Header has exactly 3 elements: [✕ Закрыть], [Обучение: Название — Артист], [⋮ Меню].
 * - DOM element expansion labeled [ Расширить выделение ].
 * - Color-blind accessible outlines (solid accent for selection, dashed hatched for exclusion).
 * - False touches filtered via scaledTouchSlop.
 * - Outdated rule banner when site layout changes.
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
    val tokens = LirixThemeTokens.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }

    var isInspectorMode by rememberSaveable { mutableStateOf(true) } // Default: Inspector enabled
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
    var isDockExpanded by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isRuleOutdated by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var ruleToExport by remember { mutableStateOf<CustomLyricsRuleEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.appBackground)
    ) {
        // 1. Uncluttered Header: EXACTLY 3 elements (Close, Title, Menu)
        Surface(
            color = tokens.surfaceCard,
            border = BorderStroke(1.dp, tokens.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Element 1: Close button
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = tokens.textPrimary
                    )
                }

                // Element 2: Context Title (Weight 1f)
                val trackTitle = if (targetTrack != null) "${targetTrack.title} — ${targetTrack.artist}" else "Режим обучения"
                Text(
                    text = "Обучение: $trackTitle",
                    style = LirixMobileTypography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                // Element 3: Overflow Menu ⋮
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Меню",
                            tint = tokens.textPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(tokens.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isInspectorMode) "Режим сёрфинга" else "Режим инспектора",
                                    color = tokens.textPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = tokens.accentPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                isInspectorMode = !isInspectorMode
                                webViewRef?.evaluateJavascript("window.setTeachMode($isInspectorMode);", null)
                            }
                        )

                        if (canGoBack) {
                            DropdownMenuItem(
                                text = { Text("Назад в браузере", color = tokens.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = tokens.accentPrimary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    webViewRef?.goBack()
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Сбросить выделения", color = tokens.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = tokens.textSecondary)
                            },
                            onClick = {
                                showMenu = false
                                webViewRef?.evaluateJavascript("window.clearTeachSelection();", null)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Импорт правила", color = tokens.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = tokens.accentPrimary)
                            },
                            onClick = {
                                showMenu = false
                                showImportDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Экспорт правила", color = tokens.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, tint = tokens.accentPrimary)
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
                            text = { Text("Открыть в браузере", color = tokens.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = tokens.textSecondary)
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

        // Outdated Rule Banner: Shown when site layout changed and selector returns empty
        if (isRuleOutdated) {
            Surface(
                color = tokens.surfaceElevated,
                border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = tokens.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Вёрстка страницы изменилась. Выделите текст заново",
                            style = LirixMobileTypography.bodySmall,
                            color = tokens.textPrimary
                        )
                    }
                    IconButton(
                        onClick = { isRuleOutdated = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Скрыть подсказку",
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 2. Embedded WebView
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
                                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                                view?.evaluateJavascript(script) {
                                    view.evaluateJavascript("window.setTeachMode($isInspectorMode);", null)
                                    view.evaluateJavascript("if (window.setTouchSlop) window.setTouchSlop($touchSlop);", null)
                                }

                                // Check for existing rule for this domain to detect outdated markup
                                val cleanDomain = Uri.parse(url).host.orEmpty()
                                if (cleanDomain.isNotBlank()) {
                                    scope.launch(Dispatchers.IO) {
                                        val existingRules = db.lyricsDao().getRulesForDomain(cleanDomain)
                                        val rule = existingRules.firstOrNull { it.isEnabled }
                                        if (rule != null) {
                                            val ruleJson = runCatching { JSONObject(rule.ruleJson) }.getOrNull()
                                            val contentObj = ruleJson?.optJSONObject("content")
                                            val sel = contentObj?.optString("selector")?.ifBlank { null }
                                                ?: contentObj?.optString("chordsSelector")?.ifBlank { null }
                                            if (!sel.isNullOrBlank()) {
                                                withContext(Dispatchers.Main) {
                                                    view?.evaluateJavascript(
                                                        "(function() { var el = document.querySelector('$sel'); return el ? el.innerText.trim().length : 0; })();"
                                                    ) { lenStr ->
                                                        val textLen = lenStr?.toIntOrNull() ?: 0
                                                        if (textLen == 0) {
                                                            isRuleOutdated = true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
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

        // 3. Lower Action Dock (Inspector Dock)
        if (selectedSelector.isNotBlank() || selectedBlocksCount > 0) {
            Surface(
                color = tokens.surfaceCard,
                border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Row 1: Selector Chip, Stripped Count, Expand Selection Button, Clear
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
                                color = tokens.surfaceElevated,
                                border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "● $selectedSelector",
                                    color = tokens.accentPrimary,
                                    style = LirixMobileTypography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (strippedBlocksCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x33EF4444),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "$strippedBlocksCount вырезано",
                                        color = Color(0xFFFF8A8A),
                                        style = LirixMobileTypography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // DOM Expansion button: strictly [ Расширить выделение ]
                            TextButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.expandTeachSelectionToParent();", null)
                                }
                            ) {
                                Text(
                                    text = "[ Расширить ]",
                                    color = tokens.accentPrimary,
                                    style = LirixMobileTypography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            TextButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.clearTeachSelection();", null)
                                }
                            ) {
                                Text(
                                    text = "✕ Сброс",
                                    color = tokens.textSecondary,
                                    style = LirixMobileTypography.labelSmall
                                )
                            }

                            IconButton(
                                onClick = { isDockExpanded = !isDockExpanded },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDockExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isDockExpanded) "Свернуть панель" else "Развернуть панель",
                                    tint = tokens.textSecondary
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = isDockExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 2: Target Type Switcher [Текст песни] / [Аккорды] with vector icons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(tokens.surfaceElevated)
                                    .padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (contentType == TargetContentType.LYRICS) tokens.accentPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (contentType == TargetContentType.LYRICS) tokens.accentPrimary else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { contentType = TargetContentType.LYRICS }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (contentType == TargetContentType.LYRICS) tokens.accentPrimary else tokens.textSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Текст песни",
                                            style = LirixMobileTypography.labelSmall.copy(
                                                fontWeight = if (contentType == TargetContentType.LYRICS) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (contentType == TargetContentType.LYRICS) tokens.accentPrimary else tokens.textSecondary
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (contentType == TargetContentType.CHORDS) tokens.accentSecondary.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (contentType == TargetContentType.CHORDS) tokens.accentSecondary else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { contentType = TargetContentType.CHORDS }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (contentType == TargetContentType.CHORDS) tokens.accentSecondary else tokens.textSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Аккорды",
                                            style = LirixMobileTypography.labelSmall.copy(
                                                fontWeight = if (contentType == TargetContentType.CHORDS) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (contentType == TargetContentType.CHORDS) tokens.accentSecondary else tokens.textSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 3: Preview Cleaned Text
                            Text(
                                text = selectedText.lines().filter { it.isNotBlank() }.take(3).joinToString("\n").ifBlank { "..." },
                                style = LirixMobileTypography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = tokens.textSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 4: Single Action Button
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
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Сохранить правило и привязать к треку",
                            color = if (tokens.isDark) Color(0xFF181B20) else Color(0xFFF2EFE9),
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
