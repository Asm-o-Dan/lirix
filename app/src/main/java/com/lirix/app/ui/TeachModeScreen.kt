package com.lirix.app.ui

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    private val onSelected: (selector: String, text: String, linesCount: Int, domain: String, blocksCount: Int) -> Unit,
    private val onCleared: () -> Unit
) {
    @JavascriptInterface
    fun onElementSelected(selector: String, text: String, linesCount: Int, domain: String, blocksCount: Int) {
        Handler(Looper.getMainLooper()).post {
            onSelected(selector, text, linesCount, domain, blocksCount)
        }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        Handler(Looper.getMainLooper()).post {
            onCleared()
        }
    }
}

/**
 * Interactive WebView Teach Mode Screen for inspecting websites,
 * selecting lyrics containers, and synthesizing custom parser rules.
 *
 * Spec: TASK-LYR-04-D / TASK-TEACH-02
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var selectedSelector by remember { mutableStateOf("") }
    var selectedText by remember { mutableStateOf("") }
    var selectedLinesCount by remember { mutableIntStateOf(0) }
    var detectedDomain by remember { mutableStateOf("") }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var ruleToExport by remember { mutableStateOf<CustomLyricsRuleEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AmoledBlack)
    ) {
        // Заголовок экрана с навигацией и переключателем режимов
        Surface(
            color = AppColors.SurfaceLevel1,
            border = BorderStroke(1.dp, AppColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = if (canGoBack) AppColors.CyberCyan else AppColors.TextTertiary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Teach Mode",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AppColors.TextPrimary
                    )
                    if (targetTrack != null) {
                        Text(
                            text = "${targetTrack.title} — ${targetTrack.artist}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.CyberCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Тумблер режимов [🌐 Серфинг / 🎯 Инспектор]
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
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "🌐 Серфинг",
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
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = { showImportDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Импорт правила",
                        tint = AppColors.CyberCyan
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = AppColors.TextPrimary
                    )
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
                            onSelected = { selector, text, lines, domain, blocks ->
                                selectedSelector = selector
                                selectedText = text
                                selectedLinesCount = lines
                                detectedDomain = domain
                                selectedBlocksCount = blocks
                            },
                            onCleared = {
                                selectedSelector = ""
                                selectedText = ""
                                selectedLinesCount = 0
                                selectedBlocksCount = 0
                            }
                        ),
                        "TeachBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() == true
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
                    loadUrl(startUrl)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Нижняя панель действий при выборе элементов
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
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppColors.SurfaceLevel3,
                            border = BorderStroke(1.dp, AppColors.CyberCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = selectedSelector,
                                color = AppColors.CyberCyan,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${selectedBlocksCount} бл. • $selectedLinesCount строк",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.clearTeachSelection();", null)
                                }
                            ) {
                                Text(
                                    text = "✕ Сбросить",
                                    color = AppColors.TextTertiary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = selectedText.lines().take(3).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = AppColors.TextTertiary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (targetTrack != null) {
                            Button(
                                onClick = {
                                    val trackKey = targetTrack.trackKey
                                    scope.launch(Dispatchers.IO) {
                                        db.musicDao().updateLyrics(trackKey, selectedText, null)
                                        db.lyricsDao().saveLyrics(
                                            LyricsCacheEntity(
                                                trackKey = trackKey,
                                                plainLyrics = selectedText,
                                                syncedLyricsLrc = null,
                                                chordsAmDm = AmDmChordParser.parseAmDmHtml(selectedText),
                                                userNotes = targetTrack.userNotes,
                                                provider = "teach:manual"
                                            )
                                        )
                                    }
                                    onLyricsAttached(selectedText)
                                    Toast.makeText(context, "Текст привязан к ${targetTrack.title}", Toast.LENGTH_SHORT).show()
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ElectricMint),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Привязать к треку", color = AppColors.AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        IconButton(
                            onClick = {
                                val cleanDomain = detectedDomain.ifBlank { "custom" }
                                val ruleId = "rule_${cleanDomain.replace(".", "_")}_${System.currentTimeMillis() % 10000}"
                                val ruleJson = JSONObject().apply {
                                    put("domain", cleanDomain)
                                    put("name", cleanDomain)
                                    put("content", JSONObject().apply {
                                        put("selector", selectedSelector)
                                        put("stripSelectors", JSONArray(listOf("script", "style", ".ads", "button")))
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
                                ruleToExport = entity
                                showExportDialog = true
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppColors.SurfaceLevel3)
                                .border(1.dp, AppColors.HyperViolet.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Поделиться правилом",
                                tint = AppColors.HyperViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val cleanDomain = detectedDomain.ifBlank { "custom" }
                                val ruleId = "rule_${cleanDomain.replace(".", "_")}_${System.currentTimeMillis() % 10000}"
                                val ruleJson = JSONObject().apply {
                                    put("domain", cleanDomain)
                                    put("name", cleanDomain)
                                    put("content", JSONObject().apply {
                                        put("selector", selectedSelector)
                                        put("stripSelectors", JSONArray(listOf("script", "style", ".ads", "button")))
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
                                }
                                onRuleSaved(entity)
                                Toast.makeText(context, "Правило для $cleanDomain сохранено!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.HyperViolet),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = AppColors.AmoledBlack, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сохранить правило", color = AppColors.AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
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
