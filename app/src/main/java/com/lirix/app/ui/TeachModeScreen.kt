package com.lirix.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lirix.app.R
import com.lirix.app.feature.lyrics.CustomRuleLyricsProvider
import com.lirix.app.feature.lyrics.RuleOutcome
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.components.ExportRuleDialog
import com.lirix.app.ui.components.ImportRuleDialog
import com.lirix.app.ui.theme.LirixMobileTypography
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder

/**
 * JS bridge receiving inspection events from teach_inspector.js. Every value comes from an
 * arbitrary web page: it is only stored as UI state and validated before use.
 */
class TeachJsBridge(
    private val onSelected: (parentSelector: String, stripSelectorsJson: String, text: String, linesCount: Int, parentsCount: Int, strippedCount: Int) -> Unit,
    private val onCleared: () -> Unit,
    private val onLinkFollowedFromPage: (selector: String) -> Unit,
    private val onNarrowAvailableChanged: (Boolean) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onElementSelected(
        parentSelector: String,
        stripSelectorsJson: String,
        text: String,
        linesCount: Int,
        @Suppress("UNUSED_PARAMETER") untrustedDomain: String,
        parentsCount: Int,
        strippedCount: Int
    ) {
        main.post { onSelected(parentSelector, stripSelectorsJson, text, linesCount, parentsCount, strippedCount) }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        main.post { onCleared() }
    }

    @JavascriptInterface
    fun onLinkFollowed(selector: String, @Suppress("UNUSED_PARAMETER") href: String) {
        main.post { onLinkFollowedFromPage(selector) }
    }

    @JavascriptInterface
    fun onNarrowAvailable(available: Boolean) {
        main.post { onNarrowAvailableChanged(available) }
    }
}

private data class PendingReplace(val text: String, val oldLines: Int, val newLines: Int)

private const val MAX_VISITED_URLS = 40
private const val MAX_LINK_SELECTOR_LENGTH = 200

/**
 * Interactive WebView Teach Mode (SPEC-TEACH-06, §5):
 * - Tier A: bind the selected block to the track captured when the screen opened, with a
 *   confirmation only when it would replace noticeably longer stored text.
 * - Tier B: site rule whose search template comes from the user's own navigation; it can be
 *   turned on only after a successful check, otherwise it stays a non-executing draft.
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
    val onAccent = tokens.appBackground
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }

    // Track identity is fixed when TeachMode opens (AGENTS 7.2): a track change in the player
    // while this screen is open must not redirect the binding.
    val boundTrack = remember { targetTrack }

    var isInspectorMode by rememberSaveable { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var selectedBlocksCount by remember { mutableIntStateOf(0) }
    var strippedBlocksCount by remember { mutableIntStateOf(0) }
    var canNarrow by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var selectedSelector by remember { mutableStateOf("") }
    var stripSelectorsJson by remember { mutableStateOf("[]") }
    var selectedText by remember { mutableStateOf("") }
    var selectedLinesCount by remember { mutableIntStateOf(0) }
    var detectedDomain by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(initialUrl.orEmpty()) }
    var isAddressBarExpanded by rememberSaveable { mutableStateOf(false) }
    var addressInput by remember { mutableStateOf(initialUrl.orEmpty()) }
    var isSavingBinding by remember { mutableStateOf(false) }
    var pendingReplace by remember { mutableStateOf<PendingReplace?>(null) }

    val visitedUrls = remember { mutableStateListOf<String>() }
    val linkSelectorByPage = remember { mutableStateMapOf<String, String>() }

    var showSaveRuleDialog by remember { mutableStateOf(false) }
    var isSavingRule by remember { mutableStateOf(false) }
    var failedRuleActivation by remember { mutableStateOf<Boolean?>(null) }

    var contentType by rememberSaveable { mutableStateOf(TargetContentType.LYRICS) }
    var showBindingPreview by remember(selectedText, contentType) { mutableStateOf(false) }
    var bindingError by remember(selectedText, contentType) { mutableStateOf(false) }
    var showSelectionTools by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isRuleOutdated by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var ruleToExport by remember { mutableStateOf<CustomLyricsRuleEntity?>(null) }

    fun clearSelectionState() {
        selectedSelector = ""
        stripSelectorsJson = "[]"
        selectedText = ""
        selectedLinesCount = 0
        selectedBlocksCount = 0
        strippedBlocksCount = 0
        canNarrow = false
    }

    fun setInspector(enabled: Boolean) {
        isInspectorMode = enabled
        webViewRef?.evaluateJavascript("window.setTeachMode($enabled);", null)
    }

    fun contentDomain(): String = detectedDomain.ifBlank {
        TeachModeBindingHelper.extractContentDomain(webViewRef?.url ?: currentUrl)
    }

    fun detectTemplate(domain: String): DetectedSearchTemplate? {
        val track = boundTrack ?: return SearchTemplateDetector.detect(visitedUrls.toList(), domain, emptyList())
        val queries = listOf(CustomRuleLyricsProvider.buildQuery(track), track.title, track.artist)
        return SearchTemplateDetector.detect(visitedUrls.toList(), domain, queries)
    }

    fun linkSelectorFor(template: DetectedSearchTemplate?): String =
        template?.let { linkSelectorByPage[it.sourceUrl] }.orEmpty()

    fun performBinding(track: TrackEntity, text: String, type: TargetContentType) {
        isSavingBinding = true
        bindingError = false
        val domain = contentDomain()
        val sourceUrl = webViewRef?.url ?: currentUrl
        // Captured now: the screen closes after the binding, the site rule is built after that.
        val ruleSelector = selectedSelector
        val ruleStrip = TeachRuleFactory.parseStripSelectors(stripSelectorsJson)
        val ruleVisited = visitedUrls.toList()
        val ruleLinks = linkSelectorByPage.toMap()
        scope.launch {
            try {
                val pageHtml = if (domain.isNotBlank() && ruleSelector.isNotBlank()) webViewRef?.let { readPageHtml(it) } else null
                withContext(Dispatchers.IO) {
                    TeachModeBindingHelper.bindManualContentToTrack(db, track, text, type, domain, sourceUrl)
                }
                isSavingBinding = false
                onLyricsAttached(text)
                // The binding is saved and does not depend on what follows (AGENTS 7.3).
                if (domain.isNotBlank() && ruleSelector.isNotBlank()) {
                    TeachAutoRule.launch(
                        context, db,
                        AutoRuleRequest(domain, ruleSelector, ruleStrip, type, track, text, ruleVisited, ruleLinks, pageHtml, sourceUrl),
                        onRuleSaved
                    )
                }
                val message = context.getString(
                    if (type == TargetContentType.CHORDS) R.string.teach_bound_chords else R.string.teach_bound_lyrics,
                    track.title
                )
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                onClose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // R1/R8: stay open, keep the selection, allow retry; no raw exception text in the UI.
                Timber.e(e, "TeachMode: manual binding failed")
                isSavingBinding = false
                bindingError = true
            }
        }
    }

    fun requestBinding() {
        val track = boundTrack
        val text = selectedText
        val type = contentType
        if (text.isBlank()) {
            Toast.makeText(context, R.string.teach_need_selection, Toast.LENGTH_SHORT).show()
            return
        }
        if (track == null) {
            Toast.makeText(context, R.string.teach_no_target_track, Toast.LENGTH_SHORT).show()
            return
        }
        if (isSavingBinding) return
        bindingError = false
        isSavingBinding = true
        scope.launch {
            val existing = try {
                withContext(Dispatchers.IO) {
                    TeachModeBindingHelper.existingContent(
                        type,
                        db.lyricsDao().getLyrics(track.trackKey),
                        db.musicDao().getTrackByKey(track.trackKey) ?: track
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "TeachMode: could not read stored lyrics before binding")
                isSavingBinding = false
                bindingError = true
                return@launch
            }
            if (TeachModeBindingHelper.isSignificantShrink(existing, text)) {
                isSavingBinding = false
                pendingReplace = PendingReplace(
                    text = text,
                    oldLines = TeachModeBindingHelper.nonBlankLines(existing),
                    newLines = TeachModeBindingHelper.nonBlankLines(text)
                )
            } else {
                performBinding(track, text, type)
            }
        }
    }

    // A page left open must not keep running its scripts and timers while the app is hidden.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> webViewRef?.let { it.onPause(); it.pauseTimers() }
                Lifecycle.Event.ON_START -> webViewRef?.let { it.resumeTimers(); it.onResume() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // pauseTimers is process-wide: never leave it on for the hidden rule renderer.
            webViewRef?.resumeTimers()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.removeAllViews()
                wv.destroy()
            }
            webViewRef = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.appBackground)
    ) {
        // 1. Header: Close, title, menu
        Surface(
            color = tokens.surfaceCard,
            border = BorderStroke(1.dp, tokens.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.teach_close), tint = tokens.textPrimary)
                }

                Text(
                    text = if (boundTrack != null) {
                        stringResource(R.string.teach_title_track, boundTrack.title, boundTrack.artist)
                    } else {
                        stringResource(R.string.teach_title_generic)
                    },
                    style = LirixMobileTypography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.teach_menu), tint = tokens.textPrimary)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(tokens.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(if (isInspectorMode) R.string.teach_menu_surfing else R.string.teach_menu_inspector),
                                    color = tokens.textPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isInspectorMode) Icons.Default.Language else Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = tokens.accentPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                setInspector(!isInspectorMode)
                            }
                        )

                        if (canGoBack) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.teach_menu_back), color = tokens.textPrimary) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.accentPrimary) },
                                onClick = {
                                    showMenu = false
                                    webViewRef?.goBack()
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.teach_menu_reset), color = tokens.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = tokens.textSecondary) },
                            onClick = {
                                showMenu = false
                                webViewRef?.evaluateJavascript("window.clearTeachSelection();", null)
                                clearSelectionState()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.teach_menu_import), color = tokens.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = tokens.accentPrimary) },
                            onClick = {
                                showMenu = false
                                showImportDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.teach_menu_export), color = tokens.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = tokens.accentPrimary) },
                            onClick = {
                                showMenu = false
                                val cleanDomain = contentDomain()
                                if (cleanDomain.isBlank()) {
                                    Toast.makeText(context, R.string.teach_export_need_page, Toast.LENGTH_SHORT).show()
                                    return@DropdownMenuItem
                                }
                                val detected = detectTemplate(cleanDomain)
                                val selector = selectedSelector.ifBlank { "body" }
                                val strip = TeachRuleFactory.parseStripSelectors(stripSelectorsJson)
                                val type = contentType
                                scope.launch {
                                    val entity = try {
                                        withContext(Dispatchers.IO) {
                                            db.lyricsDao().getRulesForDomain(cleanDomain).firstOrNull()
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Timber.e(e, "TeachMode: could not read rules for export")
                                        null
                                    } ?: TeachRuleFactory.buildRule(
                                        id = TeachRuleFactory.newRuleId(cleanDomain),
                                        domain = cleanDomain,
                                        contentSelector = selector,
                                        stripSelectors = strip,
                                        contentType = type,
                                        searchTemplate = detected?.template,
                                        resultSelector = linkSelectorFor(detected),
                                        activate = false
                                    )
                                    ruleToExport = entity
                                    showExportDialog = true
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.teach_menu_open_browser), color = tokens.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = tokens.textSecondary) },
                            onClick = {
                                showMenu = false
                                val urlToOpen = currentUrl.ifBlank { webViewRef?.url.orEmpty() }
                                if (urlToOpen.startsWith("https://") || urlToOpen.startsWith("http://")) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))) }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Outdated rule banner: only on content sites, never on search engines.
        if (isRuleOutdated) {
            Surface(
                color = tokens.surfaceElevated,
                border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = tokens.accentPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.teach_outdated_banner),
                        style = LirixMobileTypography.bodySmall,
                        color = tokens.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isRuleOutdated = false }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.teach_hide_hint), tint = tokens.textSecondary)
                    }
                }
            }
        }

        // Collapsible address bar (R4)
        Surface(
            color = tokens.surfaceElevated,
            border = BorderStroke(1.dp, tokens.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedVisibility(visible = !isAddressBarExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canGoBack) {
                        IconButton(onClick = { webViewRef?.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.teach_nav_back), tint = tokens.textPrimary)
                        }
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.teach_nav_reload), tint = tokens.textSecondary)
                    }

                    val displayDomain = detectedDomain.ifBlank {
                        runCatching { Uri.parse(currentUrl).host.orEmpty() }.getOrDefault("")
                    }.removePrefix("www.")
                    val expandLabel = stringResource(R.string.teach_address_expand)
                    Surface(
                        onClick = { isAddressBarExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        color = tokens.surfaceCard,
                        border = BorderStroke(1.dp, tokens.borderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = tokens.accentPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayDomain.ifBlank { stringResource(R.string.teach_address_placeholder) },
                                style = LirixMobileTypography.bodySmall,
                                color = if (displayDomain.isNotBlank()) tokens.textPrimary else tokens.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = expandLabel, tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        onClick = { setInspector(!isInspectorMode) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isInspectorMode) tokens.accentPrimary else tokens.surfaceCard,
                        border = BorderStroke(1.dp, if (isInspectorMode) tokens.accentPrimary else tokens.borderSubtle)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isInspectorMode) Icons.Default.TouchApp else Icons.Default.Language,
                                contentDescription = null,
                                tint = if (isInspectorMode) onAccent else tokens.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(if (isInspectorMode) R.string.teach_mode_select else R.string.teach_mode_surf),
                                style = LirixMobileTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isInspectorMode) onAccent else tokens.textPrimary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isAddressBarExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        placeholder = {
                            Text(stringResource(R.string.teach_address_input_hint), style = LirixMobileTypography.bodySmall, color = tokens.textSecondary)
                        },
                        textStyle = LirixMobileTypography.bodySmall.copy(color = tokens.textPrimary),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.accentPrimary,
                            unfocusedBorderColor = tokens.borderSubtle
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    val clipboard = LocalClipboardManager.current
                    IconButton(onClick = {
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { addressInput = it }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.teach_paste), tint = tokens.accentPrimary)
                    }

                    IconButton(onClick = {
                        val raw = addressInput.trim()
                        if (raw.isNotBlank()) {
                            clearSelectionState()
                            isRuleOutdated = false
                            val targetUrl = when {
                                raw.startsWith("http://") || raw.startsWith("https://") -> raw
                                raw.contains(".") && !raw.contains(" ") -> "https://$raw"
                                else -> "https://www.google.com/search?q=" + URLEncoder.encode(raw, "UTF-8")
                            }
                            detectedDomain = TeachModeBindingHelper.extractContentDomain(targetUrl)
                            webViewRef?.loadUrl(targetUrl)
                            isAddressBarExpanded = false
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.teach_go), tint = tokens.accentPrimary)
                    }

                    IconButton(onClick = { isAddressBarExpanded = false }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.teach_collapse), tint = tokens.textSecondary)
                    }
                }
            }
        }

        // 2. WebView and floating hints
        val isSelectorValid = selectedSelector.isNotBlank() &&
            !selectedSelector.contains("textarea", ignoreCase = true) &&
            !selectedSelector.contains("input", ignoreCase = true) &&
            selectedText.isNotBlank()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
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
                                onSelected = onSelected@{ parentSelector, stripJson, text, lines, parents, stripped ->
                                    if (!TeachBridgeGuard.acceptsSelection(isInspectorMode, parentSelector, stripJson, text)) return@onSelected
                                    selectedSelector = parentSelector
                                    stripSelectorsJson = stripJson
                                    selectedText = text
                                    selectedLinesCount = lines
                                    selectedBlocksCount = parents
                                    strippedBlocksCount = stripped
                                    // The domain comes from the native WebView URL, never from the page.
                                    val nativeDomain = TeachModeBindingHelper.extractContentDomain(webViewRef?.url ?: currentUrl)
                                    if (nativeDomain.isNotBlank()) detectedDomain = nativeDomain
                                },
                                onCleared = { if (isInspectorMode) clearSelectionState() },
                                onLinkFollowedFromPage = { selector ->
                                    val page = webViewRef?.url ?: currentUrl
                                    val clean = selector.trim()
                                    if (clean.isNotBlank() && clean.length <= MAX_LINK_SELECTOR_LENGTH &&
                                        CustomRuleLyricsProvider.isParsableSelector(clean) && page.isNotBlank()
                                    ) {
                                        linkSelectorByPage[page] = clean
                                    }
                                },
                                onNarrowAvailableChanged = { if (isInspectorMode) canNarrow = it }
                            ),
                            "TeachBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                clearSelectionState()
                                isRuleOutdated = false
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                // Recorded so a site search can become the rule's search template (R2).
                                if (!url.isNullOrBlank() && visitedUrls.lastOrNull() != url) {
                                    visitedUrls.add(url)
                                    while (visitedUrls.size > MAX_VISITED_URLS) visitedUrls.removeAt(0)
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase().orEmpty()
                                // Block non-web schemes (javascript:, file:, content:, intent:, …).
                                if (scheme != "http" && scheme != "https") {
                                    Timber.w("TeachMode: blocked navigation with scheme '%s'", scheme)
                                    return true
                                }
                                val host = uri.host?.lowercase().orEmpty()
                                val path = uri.path.orEmpty()
                                // Google redirect wrapper (/url?q=… or /url?url=…)
                                if (host.contains("google.") && path.startsWith("/url")) {
                                    val targetParam = uri.getQueryParameter("q") ?: uri.getQueryParameter("url")
                                    val targetScheme = targetParam?.let { Uri.parse(it).scheme?.lowercase() }
                                    if (targetParam != null && (targetScheme == "http" || targetScheme == "https")) {
                                        view?.loadUrl(targetParam)
                                        return true
                                    }
                                }
                                // Google AMP wrapper (/amp/s/<host>/<path>)
                                if (host.contains("google.") && path.startsWith("/amp/s/")) {
                                    view?.loadUrl("https://" + uri.toString().substringAfter("/amp/s/"))
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() == true
                                if (!url.isNullOrBlank()) {
                                    currentUrl = url
                                    addressInput = url
                                    detectedDomain = TeachModeBindingHelper.extractContentDomain(url)
                                    if (detectedDomain.isBlank()) isRuleOutdated = false
                                }
                                injectInspector(context, view, isInspectorMode)
                                val domain = detectedDomain
                                if (domain.isNotBlank()) {
                                    scope.launch {
                                        val selector = try {
                                            withContext(Dispatchers.IO) {
                                                db.lyricsDao().getRulesForDomain(domain).firstOrNull { it.isEnabled }?.let { rule ->
                                                    val config = CustomRuleLyricsProvider.parseConfig(rule.ruleJson, rule.domain, rule.name)
                                                    CustomRuleLyricsProvider.targetSelector(config).takeIf { it.isNotBlank() }
                                                }
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Timber.w(e, "TeachMode: outdated-rule check failed")
                                            null
                                        } ?: return@launch
                                        val quoted = JSONObject.quote(selector)
                                        view?.evaluateJavascript(
                                            "(function(){try{var el=document.querySelector($quoted);return el?el.innerText.trim().length:0;}catch(e){return 0;}})();"
                                        ) { len -> if ((len?.toIntOrNull() ?: 0) == 0) isRuleOutdated = true }
                                    }
                                }
                            }
                        }

                        val startUrl = initialUrl?.takeIf { it.isNotBlank() }
                            ?: ("https://www.google.com/search?q=" + URLEncoder.encode(
                                "${boundTrack?.artist.orEmpty()} ${boundTrack?.title.orEmpty()} " + ctx.getString(R.string.teach_search_suffix),
                                "UTF-8"
                            ))
                        currentUrl = startUrl
                        loadUrl(startUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!isInspectorMode && !isSelectorValid) {
                Surface(
                    onClick = { setInspector(true) },
                    shape = RoundedCornerShape(24.dp),
                    color = tokens.accentPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = onAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.teach_pick_on_page),
                            style = LirixMobileTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = onAccent
                        )
                    }
                }
            }

            if (isInspectorMode && !isSelectorValid) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = tokens.surfaceElevated,
                    border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.6f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = tokens.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.teach_tap_lyrics),
                            style = LirixMobileTypography.bodySmall,
                            color = tokens.textPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = { setInspector(false) }) {
                            Text(
                                text = stringResource(R.string.teach_mode_surf),
                                style = LirixMobileTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = tokens.accentPrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. Action dock
        if (isSelectorValid) {
            Surface(
                color = tokens.surfaceCard,
                border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tokens.surfaceElevated,
                                border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = pluralStringResource(R.plurals.teach_lines_count, selectedLinesCount, selectedLinesCount),
                                    color = tokens.accentPrimary,
                                    style = LirixMobileTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                            if (strippedBlocksCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = tokens.clipIndicator.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, tokens.clipIndicator.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = pluralStringResource(R.plurals.teach_stripped_count, strippedBlocksCount, strippedBlocksCount),
                                        color = tokens.clipIndicator,
                                        style = LirixMobileTypography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Box {
                            TextButton(onClick = { showSelectionTools = true }, enabled = !isSavingBinding) {
                                Text(stringResource(R.string.teach_change_selection))
                            }
                            DropdownMenu(expanded = showSelectionTools, onDismissRequest = { showSelectionTools = false }) {
                                if (canNarrow) DropdownMenuItem(text = { Text(stringResource(R.string.teach_narrow)) }, onClick = {
                                    showSelectionTools = false
                                    webViewRef?.evaluateJavascript("window.narrowTeachSelection();", null)
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.teach_expand)) }, onClick = {
                                    showSelectionTools = false
                                    webViewRef?.evaluateJavascript("window.expandTeachSelectionToParent();", null)
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.teach_reset)) }, onClick = {
                                    showSelectionTools = false
                                    webViewRef?.evaluateJavascript("window.clearTeachSelection();", null)
                                    clearSelectionState()
                                })
                                if (contentDomain().isNotBlank()) DropdownMenuItem(
                                    text = { Text(stringResource(R.string.teach_save_as_rule)) },
                                    onClick = {
                                        showSelectionTools = false
                                        if (selectedSelector.isBlank()) Toast.makeText(context, R.string.teach_need_container, Toast.LENGTH_SHORT).show()
                                        else showSaveRuleDialog = true
                                    })
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.teach_type_label), style = LirixMobileTypography.labelSmall, color = tokens.textSecondary)
                        TypeChip(
                            selected = contentType == TargetContentType.LYRICS,
                            label = stringResource(R.string.teach_type_lyrics),
                            icon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { contentType = TargetContentType.LYRICS }
                        )
                        TypeChip(
                            selected = contentType == TargetContentType.CHORDS,
                            label = stringResource(R.string.teach_type_chords),
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { contentType = TargetContentType.CHORDS }
                        )
                    }

                    // Primary Tier A action
                    Button(
                        onClick = { showBindingPreview = true },
                        enabled = !isSavingBinding && selectedText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary, contentColor = onAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        if (isSavingBinding) {
                            CircularProgressIndicator(color = onAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = stringResource(R.string.teach_preview_selection),
                                color = onAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }


                }
            }
        }
    }

    if (showBindingPreview && pendingReplace == null) {
        AlertDialog(
            onDismissRequest = { if (!isSavingBinding) showBindingPreview = false },
            title = { Text(stringResource(R.string.teach_preview_selection)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(selectedText, color = tokens.textPrimary)
                    if (bindingError) Text(stringResource(R.string.teach_bind_failed), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = { requestBinding() }, enabled = !isSavingBinding && selectedText.isNotBlank() && boundTrack != null) {
                    if (isSavingBinding) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(if (bindingError) R.string.lookup_retry else R.string.teach_save_selection))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBindingPreview = false }, enabled = !isSavingBinding) {
                    Text(stringResource(R.string.teach_change_selection))
                }
            },
            containerColor = tokens.surfaceElevated
        )
    }

    pendingReplace?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingReplace = null },
            title = {
                Text(stringResource(R.string.teach_replace_title), style = LirixMobileTypography.titleSmall.copy(fontWeight = FontWeight.Bold), color = tokens.textPrimary)
            },
            text = {
                Text(
                    stringResource(
                        R.string.teach_replace_message,
                        pluralStringResource(R.plurals.teach_lines_count, pending.oldLines, pending.oldLines),
                        pluralStringResource(R.plurals.teach_lines_count, pending.newLines, pending.newLines)
                    ),
                    style = LirixMobileTypography.bodyMedium,
                    color = tokens.textPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingReplace = null
                    boundTrack?.let { performBinding(it, pending.text, contentType) }
                }) {
                    Text(stringResource(R.string.teach_replace_confirm), color = tokens.accentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplace = null }) {
                    Text(stringResource(R.string.teach_cancel), color = tokens.textSecondary)
                }
            },
            containerColor = tokens.surfaceElevated
        )
    }

    if (showSaveRuleDialog) {
        val domain = contentDomain()
        val detected = remember(showSaveRuleDialog) { detectTemplate(domain) }
        SaveRuleDialog(
            domain = domain,
            contentSelector = selectedSelector,
            stripSelectors = TeachRuleFactory.parseStripSelectors(stripSelectorsJson),
            contentType = contentType,
            detectedTemplate = detected?.template,
            initialLinkSelector = linkSelectorFor(detected),
            targetTrack = boundTrack,
            isSaving = isSavingRule,
            saveFailedActivation = failedRuleActivation,
            onDismiss = { if (!isSavingRule) showSaveRuleDialog = false },
            onSave = { entity ->
                if (isSavingRule) return@SaveRuleDialog
                isSavingRule = true
                failedRuleActivation = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { db.lyricsDao().saveRule(entity) }
                        isSavingRule = false
                        onRuleSaved(entity)
                        val message = context.getString(
                            if (entity.isEnabled) R.string.teach_rule_saved_active else R.string.teach_rule_saved_draft,
                            domain
                        )
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        showSaveRuleDialog = false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "TeachMode: failed to save rule")
                        isSavingRule = false
                        failedRuleActivation = entity.isEnabled
                    }
                }
            }
        )
    }

    if (showExportDialog && ruleToExport != null) {
        ExportRuleDialog(
            rule = ruleToExport!!,
            onDismiss = {
                showExportDialog = false
                ruleToExport = null
            }
        )
    }

    if (showImportDialog) {
        ImportRuleDialog(
            onDismiss = { showImportDialog = false },
            onRuleImported = { importedRule -> onRuleSaved(importedRule) },
            validationTrack = boundTrack
        )
    }
}

/** Rendered HTML of the open page, or null when it is too large or the page does not answer. */
private suspend fun readPageHtml(webView: WebView): String? = withTimeoutOrNull(1_500L) {
    val raw = suspendCancellableCoroutine<String?> { cont ->
        webView.evaluateJavascript("document.documentElement.outerHTML") { value -> if (cont.isActive) cont.resume(value) }
    }
    if (raw == null || raw == "null" || raw.length > 3_000_000) null
    else runCatching { org.json.JSONArray("[$raw]").getString(0) }.getOrNull()
}

private fun injectInspector(context: Context, view: WebView?, inspectorEnabled: Boolean) {
    try {
        val script = context.assets.open("teach_inspector.js").bufferedReader().use { it.readText() }
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        view?.evaluateJavascript(script) {
            view.evaluateJavascript("window.setTeachMode($inspectorEnabled);", null)
            view.evaluateJavascript("if (window.setTouchSlop) window.setTouchSlop($touchSlop);", null)
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to inject teach_inspector.js")
    }
}

@Composable
private fun TypeChip(selected: Boolean, label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val tokens = LirixThemeTokens.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = LirixMobileTypography.labelMedium) },
        leadingIcon = icon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = tokens.surfaceElevated,
            labelColor = tokens.textSecondary,
            iconColor = tokens.textSecondary,
            selectedContainerColor = tokens.accentPrimary.copy(alpha = 0.2f),
            selectedLabelColor = tokens.accentPrimary,
            selectedLeadingIconColor = tokens.accentPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = tokens.borderSubtle,
            selectedBorderColor = tokens.accentPrimary
        )
    )
}

private sealed interface ValidationState {
    data object Idle : ValidationState
    data object Running : ValidationState
    data class Done(val outcome: RuleOutcome, val template: String, val linkSelector: String) : ValidationState
}

/**
 * Tier B rule dialog (R2, R9, R10): the search template is pre-filled only from the user's
 * own site search; "Turn rule on" is available only after a successful check of exactly the
 * current template and link selector. Everything else is saved as a non-executing draft.
 */
@Composable
fun SaveRuleDialog(
    domain: String,
    contentSelector: String,
    stripSelectors: List<String>,
    contentType: TargetContentType,
    detectedTemplate: String?,
    initialLinkSelector: String,
    targetTrack: TrackEntity?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (CustomLyricsRuleEntity) -> Unit,
    saveFailedActivation: Boolean? = null
) {
    val tokens = LirixThemeTokens.colors
    val onAccent = tokens.appBackground
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchTemplate by remember { mutableStateOf(detectedTemplate.orEmpty()) }
    var linkSelector by remember { mutableStateOf(initialLinkSelector) }
    var validation by remember { mutableStateOf<ValidationState>(ValidationState.Idle) }
    val ruleId = remember { TeachRuleFactory.newRuleId(domain) }

    val template = searchTemplate.trim()
    val templateError = template.isNotBlank() && CustomRuleLyricsProvider.validateSearchTemplate(template, domain) != null
    val validated = (validation as? ValidationState.Done)?.takeIf {
        TeachRuleFactory.canActivate(it.outcome) && it.template == template && it.linkSelector == linkSelector.trim()
    }

    fun build(activate: Boolean) = TeachRuleFactory.buildRule(
        renderTimeoutMs = if (activate) TeachRuleFactory.renderTimeoutFor(validated?.outcome) else null,
        id = ruleId,
        domain = domain,
        contentSelector = contentSelector,
        stripSelectors = stripSelectors,
        contentType = contentType,
        searchTemplate = template.takeIf { it.isNotBlank() && !templateError },
        resultSelector = linkSelector,
        activate = activate
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.teach_rule_title, domain),
                style = LirixMobileTypography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = tokens.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.teach_rule_search_template), style = LirixMobileTypography.labelMedium, color = tokens.textSecondary)
                OutlinedTextField(
                    value = searchTemplate,
                    onValueChange = { searchTemplate = it },
                    textStyle = LirixMobileTypography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = tokens.textPrimary),
                    singleLine = true,
                    isError = templateError,
                    placeholder = { Text("https://$domain/…{query}", style = LirixMobileTypography.bodySmall, color = tokens.textMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.accentPrimary,
                        unfocusedBorderColor = tokens.borderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = when {
                        templateError -> stringResource(R.string.teach_rule_template_invalid, domain)
                        template.isBlank() -> stringResource(R.string.teach_rule_template_missing)
                        template == detectedTemplate -> stringResource(R.string.teach_rule_template_detected)
                        else -> ""
                    },
                    style = LirixMobileTypography.bodySmall,
                    color = if (templateError) tokens.clipIndicator else tokens.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.teach_rule_link_selector), style = LirixMobileTypography.labelMedium, color = tokens.textSecondary)
                OutlinedTextField(
                    value = linkSelector,
                    onValueChange = { linkSelector = it },
                    placeholder = { Text("a[href*='/song/']", style = LirixMobileTypography.bodySmall, color = tokens.textMuted) },
                    textStyle = LirixMobileTypography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = tokens.textPrimary),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.accentPrimary,
                        unfocusedBorderColor = tokens.borderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.teach_rule_content_selector, contentSelector), style = LirixMobileTypography.bodySmall, color = tokens.textSecondary)
                Text(
                    stringResource(
                        R.string.teach_rule_mode,
                        stringResource(if (contentType == TargetContentType.CHORDS) R.string.teach_type_chords else R.string.teach_type_lyrics)
                    ),
                    style = LirixMobileTypography.bodySmall,
                    color = tokens.textSecondary
                )

                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(
                    onClick = {
                        val track = targetTrack
                        if (track == null) {
                            Toast.makeText(context, R.string.teach_rule_need_track, Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        val checkedTemplate = template
                        val checkedSelector = linkSelector.trim()
                        val candidate = build(activate = false)
                        validation = ValidationState.Running
                        scope.launch {
                            val outcome = try {
                                CustomRuleLyricsProvider(
                                    candidate,
                                    explicitRenderer = RuleWebViewRenderer(context, domain)
                                ).resolve(track, allowDraft = true)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "TeachMode: rule check failed unexpectedly")
                                RuleOutcome.NetworkFailure(checkedTemplate, null)
                            }
                            validation = ValidationState.Done(outcome, checkedTemplate, checkedSelector)
                        }
                    },
                    enabled = validation != ValidationState.Running && template.isNotBlank() && !templateError && !isSaving,
                    border = BorderStroke(1.dp, tokens.accentPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    if (validation == ValidationState.Running) {
                        CircularProgressIndicator(color = tokens.accentPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.teach_rule_validating), color = tokens.accentPrimary)
                    } else {
                        Text(stringResource(R.string.teach_rule_validate), color = tokens.accentPrimary)
                    }
                }

                (validation as? ValidationState.Done)?.let { done ->
                    Spacer(modifier = Modifier.size(8.dp))
                    RulePreview(done.outcome)
                }
                if (saveFailedActivation != null) {
                    Text(stringResource(R.string.teach_rule_save_failed), color = MaterialTheme.colorScheme.error)
                }
                val webViewOnly = ((validation as? ValidationState.Done)?.outcome as? RuleOutcome.Success)?.usedWebView == true
                if (validated == null || webViewOnly) {
                    Text(
                        stringResource(if (webViewOnly) R.string.teach_rule_webview_only_hint else R.string.teach_rule_activate_hint),
                        style = LirixMobileTypography.bodySmall,
                        color = tokens.textMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!isSaving) onSave(build(activate = true)) },
                enabled = !isSaving && validated != null,
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary, contentColor = onAccent),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text(stringResource(if (saveFailedActivation == true) R.string.lookup_retry else R.string.teach_rule_activate), color = onAccent)
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss, enabled = !isSaving, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.teach_cancel), color = tokens.textSecondary)
                }
                TextButton(onClick = { if (!isSaving) onSave(build(activate = false)) }, enabled = !isSaving, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (saveFailedActivation == false) R.string.lookup_retry else R.string.teach_rule_save_draft), color = tokens.textPrimary)
                }
            }
        },
        containerColor = tokens.surfaceElevated
    )
}

@Composable
private fun RulePreview(outcome: RuleOutcome) {
    val tokens = LirixThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tokens.surfaceCard,
        border = BorderStroke(1.dp, if (outcome is RuleOutcome.Success) tokens.accentPrimary else tokens.clipIndicator.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = outcomeMessage(outcome),
                style = LirixMobileTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (outcome is RuleOutcome.Success) tokens.accentPrimary else tokens.clipIndicator
            )
            val query = when (outcome) {
                is RuleOutcome.Success -> outcome.query
                is RuleOutcome.NotFound -> outcome.query
                is RuleOutcome.LowConfidence -> outcome.query
                is RuleOutcome.AmbiguousMatch -> outcome.query
                else -> null
            }
            query?.let { Text(stringResource(R.string.teach_preview_query, it), style = LirixMobileTypography.bodySmall, color = tokens.textSecondary) }
            when (outcome) {
                is RuleOutcome.Success -> {
                    Text(stringResource(R.string.teach_preview_url, outcome.songUrl), style = LirixMobileTypography.bodySmall, color = tokens.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    outcome.confidence?.let {
                        Text(stringResource(R.string.teach_preview_confidence, (it * 100).toInt()), style = LirixMobileTypography.bodySmall, color = tokens.textSecondary)
                    }
                    if (outcome.usedWebView) {
                        Text(stringResource(R.string.teach_preview_webview), style = LirixMobileTypography.bodySmall, color = tokens.textSecondary)
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = outcome.text.lineSequence().take(8).joinToString("\n"),
                        style = LirixMobileTypography.bodySmall.copy(fontFamily = if (outcome.mode == "LYRICS") FontFamily.Default else FontFamily.Monospace),
                        color = tokens.textPrimary
                    )
                }
                is RuleOutcome.AmbiguousMatch -> outcome.candidates.take(3).forEach {
                    Text("• ${it.label.ifBlank { it.url }}", style = LirixMobileTypography.bodySmall, color = tokens.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun outcomeMessage(outcome: RuleOutcome): String = when (outcome) {
    is RuleOutcome.Success -> stringResource(R.string.teach_preview_ok)
    RuleOutcome.Draft -> stringResource(R.string.teach_outcome_draft)
    is RuleOutcome.NotFound -> stringResource(R.string.teach_outcome_not_found)
    is RuleOutcome.LowConfidence -> outcome.best?.let { stringResource(R.string.teach_outcome_low_confidence_best, (it.score * 100).toInt()) }
        ?: stringResource(R.string.teach_outcome_low_confidence)
    is RuleOutcome.AmbiguousMatch -> stringResource(R.string.teach_outcome_ambiguous)
    is RuleOutcome.Blocked -> stringResource(R.string.teach_outcome_blocked)
    is RuleOutcome.Timeout -> stringResource(R.string.teach_outcome_timeout)
    is RuleOutcome.NetworkFailure -> stringResource(R.string.teach_outcome_network)
    is RuleOutcome.InvalidRule -> stringResource(R.string.teach_outcome_invalid_rule)
    is RuleOutcome.InvalidSelector -> stringResource(R.string.teach_outcome_invalid_selector, outcome.selector)
    is RuleOutcome.UnsupportedDynamicContent -> stringResource(R.string.teach_outcome_dynamic)
    is RuleOutcome.WebViewUnavailable -> stringResource(R.string.teach_outcome_webview_unavailable)
    is RuleOutcome.SelectorNotMatched -> stringResource(R.string.teach_outcome_selector_not_matched)
    is RuleOutcome.EmptyContent -> stringResource(R.string.teach_outcome_empty)
    RuleOutcome.Cancelled -> stringResource(R.string.teach_outcome_cancelled)
}
