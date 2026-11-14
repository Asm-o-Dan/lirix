package com.lirix.app.ui

import android.content.Context
import android.widget.Toast
import com.lirix.app.R
import com.lirix.app.feature.lyrics.CustomRuleLyricsProvider
import com.lirix.app.feature.lyrics.RuleOutcome
import com.lirix.app.feature.lyrics.RuleUrlPolicy
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/** Why a site was not turned into a working rule after a binding. */
enum class AutoRuleFailure { NO_SEARCH, SONG_NOT_FOUND, DIFFERENT_TEXT, SITE_BLOCKED, FAILED }

sealed interface AutoRuleResult {
    /** [dynamic]: the site builds pages with scripts, the rule runs through the hidden renderer. */
    data class Activated(val rule: CustomLyricsRuleEntity, val dynamic: Boolean) : AutoRuleResult
    /** [draft] is null when the user already has a rule for this site and type: it is left as is. */
    data class NotActivated(val reason: AutoRuleFailure, val draft: CustomLyricsRuleEntity?) : AutoRuleResult
    data object Skipped : AutoRuleResult
}

/** Everything the rule needs, captured at the moment of binding (the screen closes right after). */
data class AutoRuleRequest(
    val domain: String,
    val contentSelector: String,
    val stripSelectors: List<String>,
    val contentType: TargetContentType,
    val track: TrackEntity,
    /** The text the user bound: the rule is accepted only if it finds the same text by itself. */
    val boundText: String,
    val visitedUrls: List<String>,
    val linkSelectorByPage: Map<String, String>,
    /** Rendered HTML of the song page, used only to find the site's own search form. */
    val pageHtml: String?,
    val pageUrl: String
)

/**
 * Finds GET search forms of the site in a page and turns them into search URL templates.
 * Used when the user reached the song from a search engine and never searched on the site.
 */
object SearchFormDetector {
    private val TEXT_TYPES = setOf("", "text", "search")

    fun detect(html: String, pageUrl: String, domain: String): List<String> {
        val doc = runCatching { Jsoup.parse(html, pageUrl) }.getOrNull() ?: return emptyList()
        return doc.select("form").mapNotNull { templateOf(it, pageUrl, domain) }.distinct()
    }

    private fun templateOf(form: Element, pageUrl: String, domain: String): String? {
        if (form.attr("method").equals("post", ignoreCase = true)) return null
        val inputs = form.select("input[name]").filter { it.attr("name").isNotBlank() }
        if (form.select("input[type=password]").isNotEmpty()) return null
        fun type(e: Element) = e.attr("type").lowercase(Locale.ROOT)
        val textual = inputs.filter { type(it) in TEXT_TYPES }
        val field = textual.firstOrNull { type(it) == "search" }
            ?: textual.firstOrNull { it.attr("name").lowercase(Locale.ROOT) in SearchTemplateDetector.SEARCH_PARAM_NAMES }
            ?: textual.singleOrNull()?.takeIf { looksLikeSearch(form) }
            ?: return null

        val action = form.absUrl("action").ifBlank { pageUrl }
        val uri = runCatching { URI(action) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!RuleUrlPolicy.hostMatchesDomain(host, domain)) return null

        val params = mutableListOf<String>()
        // A GET form replaces the action's own query string with its fields.
        inputs.filter { type(it) == "hidden" }.forEach {
            params += "${encode(it.attr("name"))}=${encode(it.attr("value"))}"
        }
        params += "${encode(field.attr("name"))}={query}"
        val template = "https://${uri.rawAuthority}${uri.rawPath.orEmpty().ifBlank { "/" }}?${params.joinToString("&")}"
        return template.takeIf { CustomRuleLyricsProvider.validateSearchTemplate(it, domain) == null }
    }

    private fun looksLikeSearch(form: Element): Boolean {
        if (form.attr("role").equals("search", ignoreCase = true)) return true
        val hint = "${form.className()} ${form.id()} ${form.attr("action")}".lowercase(Locale.ROOT)
        return SearchTemplateDetector.SEARCH_PATH_MARKERS.any { hint.contains(it) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

/**
 * Turns a manual binding into a permanent site rule without asking the user anything
 * (owner request 2026-11-13). The binding itself is already saved and never depends on this
 * (AGENTS 7.3). A rule is switched on only when the real rule engine, starting from the track's
 * artist and title alone, arrives at the same text the user selected (AGENTS 7.4).
 */
object TeachAutoRule {
    const val MAX_ATTEMPTS = 3
    const val SAME_SONG_RATIO = 0.7

    /** Search templates to try, best first: the user's own site search, then the page's search forms. */
    fun candidates(request: AutoRuleRequest): List<Pair<String, String?>> {
        val track = request.track
        val queries = listOf(CustomRuleLyricsProvider.buildQuery(track), track.title, track.artist)
        val detected = SearchTemplateDetector.detect(request.visitedUrls, request.domain, queries)
        val fromNavigation = detected?.let { it.template to request.linkSelectorByPage[it.sourceUrl]?.takeIf { s -> s.isNotBlank() } }
        val fromForms = request.pageHtml
            ?.let { SearchFormDetector.detect(it, request.pageUrl, request.domain) }
            .orEmpty()
            .map { it to null as String? }
        return (listOfNotNull(fromNavigation) + fromForms).distinctBy { it.first }.take(MAX_ATTEMPTS)
    }

    /** True when [extracted] is essentially the text the user bound (same words, any layout). */
    fun sameSong(bound: String, extracted: String): Boolean {
        val a = words(bound)
        val b = words(extracted)
        if (a.isEmpty() || b.isEmpty()) return false
        val left = b.groupingBy { it }.eachCount().toMutableMap()
        var common = 0
        for (word in a) {
            val n = left[word] ?: 0
            if (n > 0) {
                common++
                left[word] = n - 1
            }
        }
        return common.toDouble() / maxOf(a.size, b.size) >= SAME_SONG_RATIO
    }

    private fun words(text: String): List<String> =
        text.lowercase(Locale.ROOT).replace('ё', 'е').split(Regex("""[^\p{L}\p{Nd}#]+""")).filter { it.isNotBlank() }

    /**
     * @param existing rules already stored for the request's domain.
     * @param check runs the real engine for a draft rule and the request's track.
     */
    suspend fun synthesize(
        request: AutoRuleRequest,
        existing: List<CustomLyricsRuleEntity>,
        now: Long = System.currentTimeMillis(),
        check: suspend (CustomLyricsRuleEntity) -> RuleOutcome
    ): AutoRuleResult {
        if (request.domain.isBlank() || request.contentSelector.isBlank() || request.boundText.isBlank()) {
            return AutoRuleResult.Skipped
        }
        val mode = if (request.contentType == TargetContentType.CHORDS) "CHORDS" else "LYRICS"
        val previous = existing.firstOrNull {
            CustomRuleLyricsProvider.parseConfig(it.ruleJson, it.domain, it.name).mode.equals(mode, ignoreCase = true)
        }

        fun build(template: String?, linkSelector: String?, outcome: RuleOutcome?) = TeachRuleFactory.buildRule(
            id = previous?.id ?: TeachRuleFactory.newRuleId(request.domain),
            domain = request.domain,
            contentSelector = request.contentSelector,
            stripSelectors = request.stripSelectors,
            contentType = request.contentType,
            searchTemplate = template,
            resultSelector = linkSelector,
            activate = outcome != null,
            now = now,
            renderTimeoutMs = TeachRuleFactory.renderTimeoutFor(outcome)
        )

        val candidates = candidates(request)
        var failure: AutoRuleFailure? = null
        for ((template, linkSelector) in candidates) {
            val outcome = check(build(template, linkSelector, null))
            if (outcome == RuleOutcome.Cancelled) return AutoRuleResult.Skipped
            if (outcome is RuleOutcome.Success && sameSong(request.boundText, outcome.text)) {
                val rule = build(template, linkSelector, outcome).let {
                    if (previous == null) it else it.copy(createdAt = previous.createdAt, priority = previous.priority)
                }
                return AutoRuleResult.Activated(rule, dynamic = outcome.usedWebView)
            }
            // The first candidate comes from what the user did, so its failure is the one to report.
            if (failure == null) failure = failureOf(outcome)
        }
        val reason = failure ?: AutoRuleFailure.NO_SEARCH
        // A rule the user already has is never replaced by an unchecked draft (AGENTS 7.1).
        val draft = if (previous == null) build(candidates.firstOrNull()?.first, candidates.firstOrNull()?.second, null) else null
        return AutoRuleResult.NotActivated(reason, draft)
    }

    private fun failureOf(outcome: RuleOutcome): AutoRuleFailure = when (outcome) {
        is RuleOutcome.Success -> AutoRuleFailure.DIFFERENT_TEXT
        is RuleOutcome.NotFound, is RuleOutcome.LowConfidence, is RuleOutcome.AmbiguousMatch -> AutoRuleFailure.SONG_NOT_FOUND
        is RuleOutcome.Blocked -> AutoRuleFailure.SITE_BLOCKED
        else -> AutoRuleFailure.FAILED
    }

    // --- Android glue: outlives the TeachMode screen, which closes right after the binding ---

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = HashMap<String, Job>()

    /** Starts the synthesis in the background and reports the result with a toast. Main thread. */
    fun launch(context: Context, db: AppDatabase, request: AutoRuleRequest, onRuleSaved: (CustomLyricsRuleEntity) -> Unit = {}) {
        val appContext = context.applicationContext
        val key = "${request.domain}|${request.contentType}"
        running.remove(key)?.cancel()
        running[key] = scope.launch {
            try {
                val result = run(appContext, db, request)
                val saved = (result as? AutoRuleResult.Activated)?.rule ?: (result as? AutoRuleResult.NotActivated)?.draft
                saved?.let(onRuleSaved)
                messageFor(result)?.let { Toast.makeText(appContext, appContext.getString(it, request.domain), Toast.LENGTH_LONG).show() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "TeachMode: automatic rule was not created")
            } finally {
                if (running[key] == coroutineContext[Job]) running.remove(key)
            }
        }
    }

    suspend fun run(appContext: Context, db: AppDatabase, request: AutoRuleRequest): AutoRuleResult {
        val existing = withContext(Dispatchers.IO) { db.lyricsDao().getRulesForDomain(request.domain) }
        val result = synthesize(request, existing) { draft ->
            CustomRuleLyricsProvider(draft, explicitRenderer = RuleWebViewRenderer(appContext, request.domain))
                .resolve(request.track, allowDraft = true)
        }
        val toSave = (result as? AutoRuleResult.Activated)?.rule ?: (result as? AutoRuleResult.NotActivated)?.draft
        if (toSave != null) withContext(Dispatchers.IO) { db.lyricsDao().saveRule(toSave) }
        return result
    }

    private fun messageFor(result: AutoRuleResult): Int? = when (result) {
        is AutoRuleResult.Activated -> if (result.dynamic) R.string.teach_auto_rule_on_dynamic else R.string.teach_auto_rule_on
        is AutoRuleResult.NotActivated -> when (result.reason) {
            AutoRuleFailure.NO_SEARCH -> R.string.teach_auto_rule_no_search
            AutoRuleFailure.SONG_NOT_FOUND, AutoRuleFailure.DIFFERENT_TEXT -> R.string.teach_auto_rule_not_found
            AutoRuleFailure.SITE_BLOCKED -> R.string.teach_auto_rule_blocked
            AutoRuleFailure.FAILED -> R.string.teach_auto_rule_failed
        }
        AutoRuleResult.Skipped -> null
    }
}
