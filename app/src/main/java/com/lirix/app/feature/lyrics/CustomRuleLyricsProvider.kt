package com.lirix.app.feature.lyrics

import com.lirix.app.feature.FallbackLyricsScraper
import com.lirix.app.feature.JsonHelper
import com.lirix.app.feature.LyricsProvider
import com.lirix.app.feature.LyricsResult
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.TrackEntity
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import org.jsoup.select.QueryParser
import org.jsoup.select.Selector
import java.net.URI
import java.net.URLEncoder
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale

/**
 * Parsed configuration of a declarative JSON lyrics rule.
 * Spec: SPEC-TEACH-06 / TeachMode 2.0 (schema v2, §3 and §5.2).
 */
data class CustomRuleConfig(
    val domain: String,
    val name: String,
    val searchUrlTemplate: String? = null,
    val searchResultSelector: String? = null,
    val contentSelector: String,
    val stripSelectors: List<String> = emptyList(),
    val chordsSelector: String? = null,
    val lineBreakStrategy: String = "PRESERVE_BR",
    val mode: String = "LYRICS", // "LYRICS", "CHORDS", "MIXED"
    val status: String = "ACTIVE", // "ACTIVE", "DRAFT"
    val schemaVersion: Int = 2,
    /** "STATIC" (plain HTTP) or "WEBVIEW" (the site builds its pages with scripts). */
    val renderEngine: String = "STATIC",
    /** Render deadline measured when the rule was checked, with a margin; 0 = renderer default. */
    val renderTimeoutMs: Long = 0
) {
    val needsRenderer: Boolean get() = renderEngine.equals("WEBVIEW", ignoreCase = true)
}

/**
 * Process-wide source of the hidden page renderer for rules marked `render.engine = WEBVIEW`.
 * The app installs it at start; where it is absent (unit tests, Desktop) such rules report
 * `UnsupportedDynamicContent` exactly as before.
 */
object RuleRendererRegistry {
    const val MIN_TIMEOUT_MS = 4_000L
    const val MAX_TIMEOUT_MS = 20_000L

    @Volatile
    var factory: ((ruleDomain: String, timeoutMs: Long) -> RuleRenderer)? = null

    /**
     * Deadline stored in a rule from the time the page needed during the check: twice the
     * observed time plus a fixed margin, because a background load is slower than an
     * interactive one. The renderer still returns as soon as the text is there.
     */
    fun timeoutFor(observedMs: Long): Long =
        (observedMs.coerceAtLeast(0) * 2 + 1_500).coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

    internal fun create(config: CustomRuleConfig, domain: String): RuleRenderer? {
        if (!config.needsRenderer) return null
        val timeout = config.renderTimeoutMs.takeIf { it > 0 }?.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS) ?: MAX_TIMEOUT_MS
        return factory?.invoke(domain, timeout)
    }
}

enum class InvalidRuleReason { UNSUPPORTED_SCHEMA, MISSING_CONTENT_SELECTOR, MISSING_SEARCH_TEMPLATE, INVALID_SEARCH_TEMPLATE, UNKNOWN_MODE }

/** Typed result of executing a rule (R11). `getLyrics` maps it onto the cascade's `LyricsResult`. */
sealed interface RuleOutcome {
    data class Success(
        val text: String,
        val songUrl: String,
        val searchUrl: String?,
        val query: String?,
        val confidence: Float?,
        val mode: String,
        val usedWebView: Boolean,
        /** Longest time a rendered page needed before its content was ready; 0 without rendering. */
        val renderMs: Long = 0
    ) : RuleOutcome
    data object Draft : RuleOutcome
    data class InvalidRule(val reason: InvalidRuleReason) : RuleOutcome
    data class InvalidSelector(val selector: String) : RuleOutcome
    data class NotFound(val searchUrl: String, val query: String) : RuleOutcome
    data class LowConfidence(val searchUrl: String, val query: String, val best: RankedCandidate?) : RuleOutcome
    data class AmbiguousMatch(val searchUrl: String, val query: String, val candidates: List<RankedCandidate>) : RuleOutcome
    data class Blocked(val url: String, val reason: BlockReason, val httpCode: Int?) : RuleOutcome
    data class Timeout(val url: String) : RuleOutcome
    data class NetworkFailure(val url: String, val httpCode: Int?) : RuleOutcome
    data class UnsupportedDynamicContent(val url: String) : RuleOutcome
    data class SelectorNotMatched(val url: String, val usedWebView: Boolean) : RuleOutcome
    data class WebViewUnavailable(val url: String) : RuleOutcome
    data class EmptyContent(val url: String) : RuleOutcome
    data object Cancelled : RuleOutcome
}

/**
 * Keeps hidden page rendering in the automatic cascade cheap: a few sessions per time window
 * for the whole app, and a pause for a site that keeps failing. The interactive check in
 * TeachMode is not limited.
 */
object RuleRenderBudget {
    const val MAX_PER_WINDOW = 6
    const val WINDOW_MS = 10 * 60_000L
    const val FAILURES_BEFORE_PAUSE = 3
    const val PAUSE_MS = 60 * 60_000L

    private val starts = ArrayDeque<Long>()
    private val failures = HashMap<String, Int>()
    private val pausedUntil = HashMap<String, Long>()

    @Synchronized
    fun tryAcquire(domain: String, now: Long): Boolean {
        while (starts.isNotEmpty() && now - starts.first() >= WINDOW_MS) starts.removeFirst()
        if ((pausedUntil[domain] ?: 0L) > now) return false
        if (starts.size >= MAX_PER_WINDOW) return false
        starts.addLast(now)
        return true
    }

    @Synchronized
    fun report(domain: String, success: Boolean, now: Long) {
        if (success) {
            failures.remove(domain)
            pausedUntil.remove(domain)
            return
        }
        val count = (failures[domain] ?: 0) + 1
        if (count >= FAILURES_BEFORE_PAUSE) {
            failures.remove(domain)
            pausedUntil[domain] = now + PAUSE_MS
        } else {
            failures[domain] = count
        }
    }

    @Synchronized
    fun reset() {
        starts.clear(); failures.clear(); pausedUntil.clear()
    }
}

/** Result of rendering a JavaScript page in a controlled WebView (R6). */
sealed interface RenderResult {
    data class Html(val html: String, val finalUrl: String, val elapsedMs: Long = 0) : RenderResult
    /** The page loaded but [readySelector] never produced text before the deadline. */
    data class NotReady(val html: String?) : RenderResult
    data object Timeout : RenderResult
    data object Unavailable : RenderResult
    data object Cancelled : RenderResult
    data object Failed : RenderResult
}

/** Interactive-only renderer (see `RuleWebViewRenderer`); absent in the autonomous cascade. */
fun interface RuleRenderer {
    suspend fun render(url: String, readySelector: String): RenderResult
}

/**
 * Executes a declarative JSON rule: search template → ranked result link → content page →
 * Jsoup extraction. Drafts never run in the cascade; failures are typed, never silently `null`.
 */
class CustomRuleLyricsProvider(
    val ruleEntity: CustomLyricsRuleEntity,
    private val fetcher: RuleFetcher = RuleHttpClient,
    explicitRenderer: RuleRenderer? = null
) : LyricsProvider {

    /** Compatibility constructor for callers and tests that supply a plain text transport. */
    constructor(
        ruleEntity: CustomLyricsRuleEntity,
        httpGet: suspend (String) -> String?,
        renderer: RuleRenderer? = null
    ) : this(
        ruleEntity,
        RuleFetcher { url, _ -> httpGet(url)?.let { RuleFetchResult.Body(it, url) } ?: RuleFetchResult.Failure(url) },
        renderer
    )

    val config: CustomRuleConfig = parseConfig(ruleEntity.ruleJson, ruleEntity.domain, ruleEntity.name)
    private val domain = RuleUrlPolicy.normalizeDomain(config.domain.ifBlank { ruleEntity.domain })

    /** The interactive check passes its own renderer; the cascade gets one only for WEBVIEW rules. */
    private val renderer: RuleRenderer? = explicitRenderer ?: RuleRendererRegistry.create(config, domain)

    override suspend fun getLyrics(track: TrackEntity): LyricsResult = getLyrics(track, emptySet(), false)

    override suspend fun getLyrics(
        track: TrackEntity,
        rejectedSourceIds: Set<String>,
        forceNetwork: Boolean
    ): LyricsResult {
        val canonicalId = "rule:${ruleEntity.id}"
        if (canonicalId in rejectedSourceIds) {
            return LyricsResult(false, "", ruleEntity.name, sourceId = canonicalId)
        }
        return when (val outcome = resolve(track)) {
            is RuleOutcome.Success -> {
                val hasChords = !config.mode.equals("LYRICS", ignoreCase = true)
                LyricsResult(
                    hasLyrics = true,
                    lyricsText = outcome.text,
                    chords = if (hasChords) outcome.text else null,
                    source = ruleEntity.name,
                    plainLyrics = outcome.text,
                    sourceId = canonicalId
                )
            }
            else -> LyricsResult(false, failureMessage(outcome), ruleEntity.name, sourceId = canonicalId)
        }
    }

    /**
     * Runs the full pipeline for [track]. [allowDraft] is used only by the interactive rule
     * preview, which must validate a draft before it may be activated (R10).
     */
    suspend fun resolve(track: TrackEntity, allowDraft: Boolean = false): RuleOutcome {
        if (config.status.equals("DRAFT", ignoreCase = true) && !allowDraft) return RuleOutcome.Draft
        validateConfig(config)?.let { return it }

        val mode = config.mode.uppercase(Locale.ROOT)
        val targetSelector = targetSelector(config)

        // 1. Search step
        // validateConfig guarantees a template: there is no "read the site root" fallback (spec 5.2.6).
        val template = config.searchUrlTemplate?.takeIf { it.isNotBlank() }
            ?: return RuleOutcome.InvalidRule(InvalidRuleReason.MISSING_SEARCH_TEMPLATE)
        val searchUrl: String
        val query: String
        val confidence: Float
        // A rule whose search results exist only after JavaScript cannot run in the renderer-less cascade.
        var searchRendered = false
        var renderMs = 0L
        val songUrl: String = run {
            query = buildQuery(track)
            val builtSearchUrl = buildSearchUrl(template, track)
            searchUrl = builtSearchUrl
            val searchPage = when (val fetched = fetcher.fetch(builtSearchUrl, domain)) {
                is RuleFetchResult.Body -> fetched
                else -> return fetchFailure(fetched)
            }
            var ranking = RuleCandidateRanker.rank(searchPage.html, searchPage.finalUrl, config.searchResultSelector, track.title, track.artist, domain)
            if (ranking is CandidateRanking.NoCandidates && renderer != null) {
                // Search results rendered by JavaScript: one bounded render, then rank again.
                when (val rendered = renderer.render(builtSearchUrl, config.searchResultSelector ?: "a[href]")) {
                    is RenderResult.Html -> {
                        searchRendered = true
                        renderMs = rendered.elapsedMs
                        ranking = RuleCandidateRanker.rank(rendered.html, rendered.finalUrl, config.searchResultSelector, track.title, track.artist, domain)
                    }
                    RenderResult.Cancelled -> return RuleOutcome.Cancelled
                    else -> Unit
                }
            }
            when (ranking) {
                is CandidateRanking.Selected -> {
                    confidence = ranking.best.score
                    ranking.best.url
                }
                is CandidateRanking.InvalidSelector -> return RuleOutcome.InvalidSelector(ranking.selector)
                is CandidateRanking.LowConfidence -> return RuleOutcome.LowConfidence(builtSearchUrl, query, ranking.best)
                is CandidateRanking.Ambiguous -> return RuleOutcome.AmbiguousMatch(builtSearchUrl, query, ranking.candidates)
                CandidateRanking.NoCandidates -> return RuleOutcome.NotFound(builtSearchUrl, query)
            }
        }

        // 2. Content step
        val songPage = when (val fetched = fetcher.fetch(songUrl, domain)) {
            is RuleFetchResult.Body -> fetched
            else -> return fetchFailure(fetched)
        }
        when (val extracted = extract(songPage.html, config)) {
            is ExtractResult.Text -> return if (isPlausibleText(extracted.text)) {
                RuleOutcome.Success(extracted.text, songPage.finalUrl, searchUrl, query, confidence, mode, usedWebView = searchRendered, renderMs = renderMs)
            } else {
                RuleOutcome.EmptyContent(songPage.finalUrl)
            }
            is ExtractResult.InvalidSelector -> return RuleOutcome.InvalidSelector(extracted.selector)
            ExtractResult.NoMatch, ExtractResult.Empty -> {
                val staticResult = extracted
                if (renderer == null) {
                    return when {
                        staticResult == ExtractResult.Empty -> RuleOutcome.EmptyContent(songPage.finalUrl)
                        looksScriptRendered(songPage.html) -> RuleOutcome.UnsupportedDynamicContent(songPage.finalUrl)
                        else -> RuleOutcome.SelectorNotMatched(songPage.finalUrl, usedWebView = false)
                    }
                }
                // 3. Controlled WebView fallback: only for "page loaded but content missing".
                return when (val rendered = renderer.render(songPage.finalUrl, targetSelector)) {
                    is RenderResult.Html -> when (val again = extract(rendered.html, config)) {
                        is ExtractResult.Text -> if (isPlausibleText(again.text)) {
                            RuleOutcome.Success(again.text, rendered.finalUrl, searchUrl, query, confidence, mode, usedWebView = true, renderMs = maxOf(renderMs, rendered.elapsedMs))
                        } else {
                            RuleOutcome.EmptyContent(rendered.finalUrl)
                        }
                        is ExtractResult.InvalidSelector -> RuleOutcome.InvalidSelector(again.selector)
                        ExtractResult.Empty -> RuleOutcome.EmptyContent(rendered.finalUrl)
                        ExtractResult.NoMatch -> RuleOutcome.SelectorNotMatched(rendered.finalUrl, usedWebView = true)
                    }
                    is RenderResult.NotReady -> RuleOutcome.SelectorNotMatched(songPage.finalUrl, usedWebView = true)
                    RenderResult.Timeout -> RuleOutcome.Timeout(songPage.finalUrl)
                    RenderResult.Unavailable -> RuleOutcome.WebViewUnavailable(songPage.finalUrl)
                    RenderResult.Cancelled -> RuleOutcome.Cancelled
                    RenderResult.Failed -> RuleOutcome.NetworkFailure(songPage.finalUrl, null)
                }
            }
        }
    }

    private fun fetchFailure(result: RuleFetchResult): RuleOutcome = when (result) {
        is RuleFetchResult.Blocked -> RuleOutcome.Blocked(result.url, result.reason, result.httpCode)
        is RuleFetchResult.Timeout -> RuleOutcome.Timeout(result.url)
        is RuleFetchResult.HttpError -> RuleOutcome.NetworkFailure(result.url, result.code)
        is RuleFetchResult.Failure -> RuleOutcome.NetworkFailure(result.url, null)
        is RuleFetchResult.Body -> error("Body is not a failure")
    }

    private fun validateConfig(config: CustomRuleConfig): RuleOutcome? {
        if (config.schemaVersion > SUPPORTED_SCHEMA_VERSION || config.schemaVersion < 1) {
            return RuleOutcome.InvalidRule(InvalidRuleReason.UNSUPPORTED_SCHEMA)
        }
        if (config.mode.uppercase(Locale.ROOT) !in MODES) return RuleOutcome.InvalidRule(InvalidRuleReason.UNKNOWN_MODE)
        if (config.contentSelector.isBlank()) return RuleOutcome.InvalidRule(InvalidRuleReason.MISSING_CONTENT_SELECTOR)
        val template = config.searchUrlTemplate?.takeIf { it.isNotBlank() }
        if (template == null) return RuleOutcome.InvalidRule(InvalidRuleReason.MISSING_SEARCH_TEMPLATE)
        validateSearchTemplate(template, domain)?.let { return RuleOutcome.InvalidRule(it) }
        val selectors = listOfNotNull(normalizeSelector(config.contentSelector), config.chordsSelector?.let(::normalizeSelector), config.searchResultSelector) +
            config.stripSelectors
        selectors.filter { it.isNotBlank() }.firstOrNull { !isParsableSelector(it) }?.let { return RuleOutcome.InvalidSelector(it) }
        return null
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 2
        private val MODES = setOf("LYRICS", "CHORDS", "MIXED")
        private val PLACEHOLDERS = listOf("{query}", "{artist}", "{title}")
        private val UNKNOWN_ARTISTS = setOf("unknown artist", "unknown", "неизвестный исполнитель", "various artists")

        fun failureMessage(outcome: RuleOutcome): String = when (outcome) {
            RuleOutcome.Draft -> "Правило является черновиком (DRAFT) и не выполняется автоматически"
            is RuleOutcome.NotFound -> "Песня не найдена на сайте правила"
            is RuleOutcome.LowConfidence -> "Песня не найдена: нет достаточно похожего результата"
            is RuleOutcome.AmbiguousMatch -> "Несколько похожих результатов, нужен ручной выбор"
            is RuleOutcome.Blocked -> "Сайт недоступен для автоматического запроса"
            is RuleOutcome.Timeout -> "Сайт не ответил вовремя"
            is RuleOutcome.NetworkFailure -> "Ошибка сетевого запроса"
            is RuleOutcome.InvalidRule, is RuleOutcome.InvalidSelector -> "Правило настроено неверно"
            is RuleOutcome.UnsupportedDynamicContent -> "Страница формируется скриптом, автоматическое извлечение недоступно"
            is RuleOutcome.WebViewUnavailable -> "Отрисовка страницы недоступна"
            is RuleOutcome.SelectorNotMatched, is RuleOutcome.EmptyContent -> "Текст не извлечен по селектору"
            RuleOutcome.Cancelled -> "Запрос отменён"
            is RuleOutcome.Success -> ""
        }

        /** A lone word or label is not song text; the automatic path must not store it as lyrics. */
        fun isPlausibleText(text: String): Boolean =
            text.lineSequence().count { it.isNotBlank() } >= 2 || text.trim().length >= 30

        fun buildQuery(track: TrackEntity): String {
            val artist = track.artist.trim().takeIf { it.lowercase(Locale.ROOT) !in UNKNOWN_ARTISTS }.orEmpty()
            return "$artist ${track.title.trim()}".trim()
        }

        /**
         * Substitutes placeholders, encoding each for the URL component it sits in:
         * form encoding in the query string, `%20`-encoding in the path.
         */
        fun buildSearchUrl(template: String, track: TrackEntity): String {
            val values = mapOf(
                "{query}" to buildQuery(track),
                "{artist}" to track.artist.trim(),
                "{title}" to track.title.trim()
            )
            val queryStart = template.indexOf('?')
            val sb = StringBuilder()
            var i = 0
            while (i < template.length) {
                val placeholder = PLACEHOLDERS.firstOrNull { template.startsWith(it, i) }
                if (placeholder != null) {
                    val encoded = URLEncoder.encode(values.getValue(placeholder), "UTF-8")
                    val inQuery = queryStart in 0 until i
                    sb.append(if (inQuery) encoded else encoded.replace("+", "%20"))
                    i += placeholder.length
                } else {
                    sb.append(template[i])
                    i++
                }
            }
            return sb.toString()
        }

        fun validateSearchTemplate(template: String, ruleDomain: String): InvalidRuleReason? {
            if (PLACEHOLDERS.none { template.contains(it) }) return InvalidRuleReason.INVALID_SEARCH_TEMPLATE
            val probe = PLACEHOLDERS.fold(template) { acc, p -> acc.replace(p, "x") }
            if (runCatching { URI(probe) }.isFailure) return InvalidRuleReason.INVALID_SEARCH_TEMPLATE
            return if (RuleUrlPolicy.check(probe, ruleDomain) == null) null else InvalidRuleReason.INVALID_SEARCH_TEMPLATE
        }

        fun isParsableSelector(selector: String): Boolean = try {
            QueryParser.parse(selector)
            true
        } catch (_: Selector.SelectorParseException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        /** Legacy `itemprop=x` (not valid CSS) becomes `[itemprop="x"]`. */
        fun normalizeSelector(selector: String): String {
            val s = selector.trim()
            // Legacy rules matched a bare word ("lyrics") as class or id, not as an unknown tag.
            if (BARE_WORD.matches(s) && s.lowercase(Locale.ROOT) !in HTML_TAGS) return "$s, .$s, #$s"
            val legacy = Regex("""^itemprop\s*=\s*["']?([\w-]+)["']?$""").find(s) ?: return s
            return "[itemprop=\"${legacy.groupValues[1]}\"]"
        }

        fun targetSelector(config: CustomRuleConfig): String {
            val chords = config.chordsSelector?.takeIf { it.isNotBlank() && it != "null" }
            return normalizeSelector(
                if (config.mode.equals("CHORDS", ignoreCase = true) && chords != null) chords else config.contentSelector
            )
        }

        fun parseConfig(jsonStr: String, defaultDomain: String, defaultName: String): CustomRuleConfig {
            var domain = defaultDomain
            var name = defaultName
            var searchUrlTemplate: String? = null
            var searchResultSelector: String? = null
            var contentSelector = ""
            val stripList = mutableListOf<String>()
            var chordsSelector: String? = null
            var lineBreakStrategy = "PRESERVE_BR"
            var mode = "LYRICS"
            var status = "ACTIVE"
            var schemaVersion = 1
            var renderEngine = "STATIC"
            var renderTimeoutMs = 0L

            fun JSONObject.text(key: String): String? =
                if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() && it != "null" } else null

            try {
                val json = JSONObject(jsonStr)
                schemaVersion = json.optInt("schemaVersion", 1)
                json.text("status")?.let { status = it }

                val siteObj = json.optJSONObject("site")
                if (siteObj != null) {
                    siteObj.text("domain")?.let { domain = it }
                    siteObj.text("name")?.let { name = it }
                } else {
                    json.text("domain")?.let { domain = it }
                    json.text("name")?.let { name = it }
                }

                json.optJSONObject("search")?.let { searchObj ->
                    searchObj.text("urlTemplate")?.let { searchUrlTemplate = it }
                    searchObj.text("resultListSelector")?.let { searchResultSelector = it }
                    searchObj.text("searchResultSelector")?.let { searchResultSelector = it }
                }

                json.optJSONObject("render")?.let { renderObj ->
                    renderObj.text("engine")?.let { renderEngine = it }
                    renderTimeoutMs = renderObj.optLong("timeoutMs", 0L)
                }

                val contentObj = json.optJSONObject("content") ?: json
                contentObj.text("mode")?.let { mode = it }
                contentObj.text("selector")?.let { contentSelector = it }
                contentObj.text("contentSelector")?.let { contentSelector = it }
                contentObj.text("chordsSelector")?.let { chordsSelector = it }
                contentObj.text("lineBreakStrategy")?.let { lineBreakStrategy = it }

                contentObj.optJSONArray("stripSelectors")?.let { stripArray ->
                    for (i in 0 until stripArray.length()) {
                        val item = stripArray.optString(i)
                        if (!item.isNullOrBlank()) stripList.add(item)
                    }
                }
            } catch (_: Throwable) {
            }

            // Fallback for JVM unit tests where org.json is stubbed by the Android SDK.
            if (contentSelector.isBlank()) {
                val extractedSelector = JsonHelper.extractStringField(jsonStr, "contentSelector")
                    ?: JsonHelper.extractStringField(jsonStr, "selector")
                if (!extractedSelector.isNullOrBlank()) {
                    contentSelector = extractedSelector
                    JsonHelper.extractStringField(jsonStr, "domain")?.let { domain = it }
                    JsonHelper.extractStringField(jsonStr, "name")?.let { name = it }
                    JsonHelper.extractStringField(jsonStr, "urlTemplate")?.let { searchUrlTemplate = it }
                    JsonHelper.extractStringField(jsonStr, "resultListSelector")?.let { searchResultSelector = it }
                    JsonHelper.extractStringField(jsonStr, "searchResultSelector")?.let { searchResultSelector = it }
                    JsonHelper.extractStringField(jsonStr, "chordsSelector")?.let { chordsSelector = it }
                    JsonHelper.extractStringField(jsonStr, "lineBreakStrategy")?.let { lineBreakStrategy = it }
                    JsonHelper.extractStringField(jsonStr, "mode")?.let { mode = it }
                    JsonHelper.extractStringField(jsonStr, "status")?.let { status = it }
                    JsonHelper.extractStringField(jsonStr, "engine")?.let { renderEngine = it }
                    renderTimeoutMs = Regex(""""timeoutMs"\s*:\s*(\d+)""").find(jsonStr)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    // A stubbed org.json reports 0 for every int; the raw text is authoritative here.
                    schemaVersion = Regex(""""schemaVersion"\s*:\s*(\d+)""").find(jsonStr)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                    val stripMatch = Regex(""""stripSelectors"\s*:\s*\[([^\]]*)\]""").find(jsonStr)
                    if (stripMatch != null) {
                        val items = Regex(""""((?:\\.|[^"\\])*)"""").findAll(stripMatch.groupValues[1])
                            .map { it.groupValues[1] }
                            .filter { it.isNotBlank() }
                            .toList()
                        if (items.isNotEmpty()) {
                            stripList.clear()
                            stripList.addAll(items)
                        }
                    }
                }
            }

            return CustomRuleConfig(
                domain = domain,
                name = name,
                searchUrlTemplate = searchUrlTemplate,
                searchResultSelector = searchResultSelector,
                contentSelector = contentSelector,
                stripSelectors = stripList,
                chordsSelector = chordsSelector,
                lineBreakStrategy = lineBreakStrategy,
                mode = mode,
                status = status,
                schemaVersion = schemaVersion,
                renderEngine = renderEngine,
                renderTimeoutMs = renderTimeoutMs
            )
        }

        // --- DOM extraction (R3, R9) ---

        sealed interface ExtractResult {
            data class Text(val text: String) : ExtractResult
            data object NoMatch : ExtractResult
            data object Empty : ExtractResult
            data class InvalidSelector(val selector: String) : ExtractResult
        }

        private val SKIP_TAGS = setOf("script", "style", "noscript", "iframe", "template", "svg", "button", "input", "select", "textarea")
        private val BLOCK_TAGS = setOf(
            "address", "article", "aside", "blockquote", "dd", "div", "dl", "dt", "fieldset", "figcaption", "figure",
            "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p",
            "pre", "section", "table", "tbody", "thead", "tfoot", "tr", "ul"
        )
        private val PRESERVE_TAGS = setOf("pre", "code", "textarea", "xmp", "listing", "plaintext")
        private val COLLAPSIBLE_WHITESPACE = Regex("""[ \t\n\r\u000C]+""")
        private const val STRIP_MARK = '\u0001'
        private val HTML_TAGS = setOf(
            "a", "article", "b", "blockquote", "body", "code", "dd", "div", "dl", "dt", "em", "font", "h1", "h2", "h3",
            "h4", "h5", "h6", "i", "li", "main", "ol", "p", "pre", "section", "span", "strong", "table", "td", "tr", "ul"
        )
        private val BARE_WORD = Regex("""^[A-Za-z][\w-]*$""")

        /** Extraction with the rule's mode semantics; one DOM traversal shared by all modes. */
        fun extract(html: String, config: CustomRuleConfig): ExtractResult {
            val mode = config.mode.uppercase(Locale.ROOT)
            val target = targetSelector(config)
            val extraStrip = config.chordsSelector
                ?.takeIf { mode == "LYRICS" && it.isNotBlank() && it != "null" && normalizeSelector(it) != target }
                ?.let { listOf(normalizeSelector(it)) }
                .orEmpty()
            return extract(html, target, config.stripSelectors + extraStrip, mode)
        }

        fun extract(html: String, selector: String, stripSelectors: List<String>, mode: String): ExtractResult {
            val cleanSel = normalizeSelector(selector)
            if (cleanSel.isBlank()) return ExtractResult.InvalidSelector(selector)
            val doc = Jsoup.parse(html)
            doc.outputSettings().prettyPrint(false)
            val matched: Elements = try {
                doc.select(cleanSel)
            } catch (_: Selector.SelectorParseException) {
                return ExtractResult.InvalidSelector(selector)
            }
            if (matched.isEmpty()) return ExtractResult.NoMatch

            // Overlapping matches (a container and its child) are extracted once, via the outermost.
            val matchedSet: MutableSet<Element> = Collections.newSetFromMap(IdentityHashMap())
            matchedSet.addAll(matched)
            val roots = matched.filter { el -> el.parents().none { it in matchedSet } }

            val preserveAlignment = mode != "LYRICS"
            val blocks = LinkedHashSet<String>()
            for (root in roots) {
                val clone = root.clone()
                for (strip in stripSelectors.filter { it.isNotBlank() }) {
                    val expanded = if (BARE_WORD.matches(strip.trim())) {
                        // Legacy bare words ("banner") match the tag, the class or the id.
                        val w = strip.trim()
                        "$w, .$w, #$w"
                    } else {
                        strip
                    }
                    val found = try {
                        clone.select(expanded)
                    } catch (_: Selector.SelectorParseException) {
                        return ExtractResult.InvalidSelector(strip)
                    }
                    // A marker keeps the line structure: a line emptied only by stripping is dropped,
                    // not turned into a blank line (e.g. chord rows removed in LYRICS mode).
                    found.filter { it !== clone }.forEach { it.replaceWith(TextNode(STRIP_MARK.toString())) }
                }
                val text = formatElement(clone, preserveAlignment)
                if (text.isNotBlank()) blocks.add(text)
            }
            if (blocks.isEmpty()) return ExtractResult.Empty
            return ExtractResult.Text(blocks.joinToString("\n\n"))
        }

        /**
         * Text with line structure: `<br>` → line break, block elements → line boundaries,
         * `<p>` → blank line (verse boundary). Source whitespace collapses except inside
         * `<pre>`-like elements; `&nbsp;` survives as alignment in CHORDS/MIXED.
         */
        fun formatElement(element: Element, preserveAlignment: Boolean): String {
            val out = StringBuilder()
            appendNode(element, out, preserve = false)
            val lines = out.toString().replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ').split('\n')
                .filterNot { it.contains(STRIP_MARK) && it.replace(STRIP_MARK.toString(), "").isBlank() }
                .map { it.replace(STRIP_MARK.toString(), "") }
                .map { if (preserveAlignment) it.trimEnd() else it.trim() }
            return lines.joinToString("\n")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trimStart('\n')
                .trimEnd()
        }

        private fun appendNode(node: Node, out: StringBuilder, preserve: Boolean) {
            when (node) {
                is TextNode -> {
                    val raw = node.wholeText
                    if (preserve) {
                        out.append(raw)
                    } else {
                        var collapsed = raw.replace(COLLAPSIBLE_WHITESPACE, " ")
                        if (out.isEmpty() || out.endsWith("\n") || out.endsWith(" ")) collapsed = collapsed.trimStart(' ')
                        out.append(collapsed)
                    }
                }
                is Element -> {
                    val tag = node.normalName()
                    if (tag == "br") {
                        trimTrailingSpaces(out)
                        out.append('\n')
                        return
                    }
                    if (tag in SKIP_TAGS) return
                    val style = node.attr("style").lowercase(Locale.ROOT).replace(" ", "")
                    val childPreserve = preserve || tag in PRESERVE_TAGS || style.contains("white-space:pre")
                    val isBlock = tag in BLOCK_TAGS
                    if (isBlock) ensureNewline(out)
                    if (tag == "td" || tag == "th") {
                        if (out.isNotEmpty() && !out.endsWith("\n") && !out.endsWith(" ")) out.append(' ')
                    }
                    for (child in node.childNodes()) appendNode(child, out, childPreserve)
                    if (isBlock) ensureNewline(out)
                    if (tag == "p") ensureBlankLine(out)
                }
            }
        }

        private fun trimTrailingSpaces(out: StringBuilder) {
            while (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
        }

        private fun ensureNewline(out: StringBuilder) {
            trimTrailingSpaces(out)
            if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
        }

        private fun ensureBlankLine(out: StringBuilder) {
            ensureNewline(out)
            if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append('\n')
        }

        /** Static HTML with almost no visible text but scripts: likely a JavaScript-rendered page. */
        fun looksScriptRendered(html: String): Boolean {
            val doc = Jsoup.parse(html)
            val scripts = doc.select("script").size
            doc.select("script, style, noscript, template").remove()
            return scripts > 0 && doc.body().text().length < 200
        }

        // --- Backward compatibility companion API (not used by the rule pipeline) ---

        /** Jsoup extraction returning null on any failure; kept for existing callers. */
        fun extractContentJsoup(
            html: String,
            contentSelector: String,
            stripSelectors: List<String> = emptyList(),
            mode: String = "LYRICS"
        ): String? = (extract(html, contentSelector, stripSelectors, mode.uppercase(Locale.ROOT)) as? ExtractResult.Text)?.text

        fun extractContent(html: String, contentSelector: String, stripSelectors: List<String>): String? =
            extractContentJsoup(html, contentSelector, stripSelectors, "LYRICS")

        fun cleanHtmlToPlainLyrics(rawHtml: String): String {
            return rawHtml
                .replace(Regex("""(?i)<br\s*/?>\s*\n?"""), "\n")
                .replace(Regex("""(?i)</p>\s*\n?"""), "\n\n")
                .replace(Regex("""(?i)</div>\s*\n?"""), "\n")
                .replace(Regex("""<[^>]+>"""), "")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .lines()
                .map { it.trim() }
                .joinToString("\n")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
        }

        fun extractContainerBySelector(html: String, selector: String): String? {
            val cleanSel = selector.trim()
            if (cleanSel.isBlank()) return null

            if (cleanSel.contains(",")) {
                val subSelectors = cleanSel.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val extractedBlocks = subSelectors.mapNotNull { extractSingleContainer(html, it)?.takeIf { b -> b.isNotBlank() } }
                return if (extractedBlocks.isNotEmpty()) extractedBlocks.joinToString("\n\n") else null
            }
            return extractSingleContainer(html, cleanSel)
        }

        fun extractSingleContainer(html: String, selector: String): String? {
            val cleanSel = selector.trim()
            if (cleanSel.startsWith("#")) {
                val id = cleanSel.removePrefix("#")
                return FallbackLyricsScraper.extractBalancedTags(html, "id=\"$id\"").firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, "id='$id'").firstOrNull()
            }
            if (cleanSel.startsWith(".")) {
                val cls = cleanSel.removePrefix(".")
                return FallbackLyricsScraper.extractBalancedTags(html, "class=\"$cls\"").firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, "class='$cls'").firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, cls).firstOrNull()
            }
            if (cleanSel.contains("itemprop")) {
                val propVal = cleanSel.substringAfter("itemprop=").substringAfter("itemprop").trim('=', '"', '\'')
                return FallbackLyricsScraper.extractBalancedTags(html, "itemprop=\"$propVal\"").firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, "itemprop='$propVal'").firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, "itemprop=$propVal").firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, propVal).firstOrNull()
            }
            if (cleanSel.contains(".")) {
                val parts = cleanSel.split('.')
                val tag = parts[0].ifBlank { "div" }
                val cls = parts[1]
                return FallbackLyricsScraper.extractBalancedTags(html, "class=\"$cls\"", tag).firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, "class='$cls'", tag).firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, cls, tag).firstOrNull()
            }
            if (cleanSel.contains("#")) {
                val parts = cleanSel.split('#')
                val tag = parts[0].ifBlank { "div" }
                val id = parts[1]
                return FallbackLyricsScraper.extractBalancedTags(html, "id=\"$id\"", tag).firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, "id='$id'", tag).firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, id, tag).firstOrNull()
            }
            return FallbackLyricsScraper.extractBalancedTags(html, cleanSel).firstOrNull()
        }
    }
}
