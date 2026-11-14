package com.lirix.app.ui

import com.lirix.app.feature.lyrics.RuleOutcome
import com.lirix.app.feature.lyrics.RuleRendererRegistry
import com.lirix.app.feature.lyrics.RuleUrlPolicy
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.LyricsAttribution
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.LyricsSourceAttribution
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.libraryTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID

enum class TargetContentType {
    LYRICS,
    CHORDS
}

/**
 * Platform-light TeachMode logic (SPEC-TEACH-06 §5): domain detection, the single Tier A
 * binding transaction, the replacement guard and Tier B rule synthesis helpers.
 */
object TeachModeBindingHelper {

    /** New text shorter than this share of the stored text (lines or characters) needs confirmation. */
    const val SHRINK_CONFIRM_RATIO = 0.6

    fun isSearchEngineHost(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT).removePrefix("www.")
        return h.contains("google.") || h.contains("yandex.") || h == "ya.ru" ||
            h.contains("bing.") || h.contains("duckduckgo.")
    }

    /** Content-site host of [url], or "" for non-web schemes and search engines. */
    fun extractContentDomain(url: String?): String {
        val uri = runCatching { URI(url.orEmpty().trim()) }.getOrNull() ?: return ""
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme != "http" && scheme != "https") return ""
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty().removePrefix("www.")
        return if (host.isBlank() || isSearchEngineHost(host)) "" else host
    }

    /** Text of the given type that is already stored for the track (what a binding would replace). */
    fun existingContent(
        contentType: TargetContentType,
        cached: LyricsCacheEntity?,
        track: TrackEntity?
    ): String = when (contentType) {
        TargetContentType.LYRICS -> cached?.plainLyrics?.takeIf { it.isNotBlank() }
            ?: track?.plainLyrics?.takeIf { it.isNotBlank() }
            // A LYRICS binding also clears synced lyrics, so they count as stored text too.
            ?: (cached?.syncedLyricsLrc ?: track?.syncedLyrics).orEmpty()
                .replace(Regex("""\[[^\]]*]"""), "").lines().joinToString("\n") { it.trim() }.trim()
        TargetContentType.CHORDS -> cached?.chordsAmDm.orEmpty()
    }

    /**
     * True when replacing [existing] with [candidate] would silently drop a noticeable part of
     * the stored text (UI review S2: one tapped line replaced the whole song).
     */
    fun isSignificantShrink(existing: String, candidate: String): Boolean {
        val oldLines = nonBlankLines(existing)
        if (oldLines == 0) return false
        val newLines = nonBlankLines(candidate)
        val oldChars = existing.trim().length
        val newChars = candidate.trim().length
        return newLines < oldLines * SHRINK_CONFIRM_RATIO || newChars < oldChars * SHRINK_CONFIRM_RATIO
    }

    fun nonBlankLines(text: String): Int = text.lineSequence().count { it.isNotBlank() }

    /**
     * The only Tier A write path. Writes the selection to the track identified by [trackKey]
     * (captured when TeachMode opened, never the currently playing track) in one transaction.
     * LYRICS keeps the stored chords untouched; CHORDS keeps the stored plain lyrics.
     */
    suspend fun bindManualContentToTrack(
        db: AppDatabase,
        targetTrack: TrackEntity,
        selectedText: String,
        contentType: TargetContentType,
        domain: String,
        sourceUrl: String? = null,
        now: Long = System.currentTimeMillis()
    ): LyricsCacheEntity {
        val text = selectedText.trim('\n', '\r')
        require(text.isNotBlank()) { "Empty selection must not overwrite stored content" }
        val cleanDomain = domain.lowercase(Locale.ROOT).trim().removePrefix("www.")
        val effectiveDomain = if (cleanDomain.isBlank() || isSearchEngineHost(cleanDomain)) "manual" else cleanDomain
        val trackKey = targetTrack.trackKey
        val providerTag = "USER:teach:$effectiveDomain"

        return db.libraryTransaction {
            val existingTrack = db.musicDao().getTrackByKey(trackKey)
            val latestTrack = existingTrack ?: targetTrack
            val latestLyrics = db.lyricsDao().getLyrics(trackKey)

            val updatedPlain = if (contentType == TargetContentType.LYRICS) {
                text
            } else {
                latestLyrics?.plainLyrics ?: latestTrack.plainLyrics
            }
            val updatedChords = if (contentType == TargetContentType.CHORDS) text else latestLyrics?.chordsAmDm
            // New manual words invalidate the old timing (pre-WIP behaviour): a synced LRC of the
            // previous text must not keep showing under a manual provider tag (AGENTS 7.1).
            val syncedLrc = if (contentType == TargetContentType.LYRICS) {
                null
            } else {
                latestLyrics?.syncedLyricsLrc ?: latestTrack.syncedLyrics
            }
            val userNotes = latestLyrics?.userNotes ?: latestTrack.userNotes

            if (existingTrack == null) {
                db.musicDao().insertOrUpdateTrack(latestTrack.copy(plainLyrics = updatedPlain, syncedLyrics = syncedLrc))
            } else {
                db.musicDao().updateLyrics(trackKey, updatedPlain, syncedLrc)
            }

            val existingAttr = LyricsAttribution.decode(latestLyrics?.sourceAttributionJson)
            val origin = LyricsSourceAttribution(sourceId = providerTag, sourceUrl = sourceUrl?.takeIf { it.isNotBlank() })
            val updatedAttr = if (contentType == TargetContentType.CHORDS) {
                existingAttr.copy(chords = origin)
            } else {
                existingAttr.copy(lyrics = origin)
            }

            val lyricsEntity = LyricsCacheEntity(
                trackKey = trackKey,
                plainLyrics = updatedPlain,
                syncedLyricsLrc = syncedLrc,
                chordsAmDm = updatedChords,
                userNotes = userNotes,
                provider = providerTag,
                updatedAt = now,
                sourceAttributionJson = updatedAttr.encode()
            )
            db.lyricsDao().saveLyrics(lyricsEntity)
            lyricsEntity
        }
    }
}

/**
 * Derives a site search URL template from pages the user actually opened (R2): the encoded
 * query is replaced by `{query}`. Nothing is invented when no search page was visited.
 */
/**
 * Limits for values sent by teach_inspector.js. The bridge is reachable from any script of the
 * visited page, so selection events are accepted only in selection mode and within these sizes.
 */
object TeachBridgeGuard {
    const val MAX_SELECTOR_LENGTH = 2_000
    const val MAX_STRIP_JSON_LENGTH = 20_000
    const val MAX_TEXT_LENGTH = 200_000

    fun acceptsSelection(inspectorMode: Boolean, selector: String, stripSelectorsJson: String, text: String): Boolean =
        inspectorMode &&
            selector.isNotBlank() && selector.length <= MAX_SELECTOR_LENGTH &&
            stripSelectorsJson.length <= MAX_STRIP_JSON_LENGTH &&
            text.length <= MAX_TEXT_LENGTH
}

data class DetectedSearchTemplate(val template: String, val sourceUrl: String)

object SearchTemplateDetector {
    internal val SEARCH_PARAM_NAMES = setOf(
        "q", "query", "search", "s", "text", "k", "keyword", "keywords", "term", "searchtext", "search_query", "str"
    )
    internal val SEARCH_PATH_MARKERS = listOf("search", "find", "poisk", "поиск")

    /**
     * @param visitedUrls URLs opened in TeachMode, oldest first.
     * @param domain the rule's content domain (without `www.`).
     * @param knownQueries phrases the user may have typed (track title, artist, both).
     */
    fun detect(visitedUrls: List<String>, domain: String, knownQueries: List<String>): DetectedSearchTemplate? {
        val normalizedQueries = knownQueries.map(::normalize).filter { it.length >= 2 }.distinct()
        for (raw in visitedUrls.asReversed()) {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: continue
            if (!uri.scheme.equals("https", ignoreCase = true)) continue
            val host = uri.host?.lowercase(Locale.ROOT) ?: continue
            if (!RuleUrlPolicy.hostMatchesDomain(host, domain)) continue
            (fromQuery(uri, normalizedQueries) ?: fromPath(uri, normalizedQueries))
                ?.let { return DetectedSearchTemplate(it, raw.trim()) }
        }
        return null
    }

    private fun fromQuery(uri: URI, queries: List<String>): String? {
        val rawQuery = uri.rawQuery ?: return null
        val pairs = rawQuery.split('&').mapNotNull { part ->
            val name = part.substringBefore('=', "")
            val value = part.substringAfter('=', "")
            if (name.isBlank()) null else name to value
        }
        val target = pairs.firstOrNull { (_, value) ->
            value.isNotBlank() && queries.any { matchesQuery(decode(value), it) }
        } ?: pairs.firstOrNull { (name, value) ->
            value.isNotBlank() && name.lowercase(Locale.ROOT) in SEARCH_PARAM_NAMES
        } ?: return null
        val rebuilt = pairs.joinToString("&") { (name, value) ->
            if (name == target.first && value == target.second) "$name={query}" else "$name=$value"
        }
        return "https://${uri.rawAuthority}${uri.rawPath.orEmpty()}?$rebuilt"
    }

    private fun fromPath(uri: URI, queries: List<String>): String? {
        val path = uri.rawPath ?: return null
        val segments = path.split('/')
        val lowerPath = decode(path).lowercase(Locale.ROOT)
        if (SEARCH_PATH_MARKERS.none { lowerPath.contains(it) }) return null
        val index = segments.indexOfFirst { seg -> seg.isNotBlank() && queries.any { matchesQuery(decode(seg), it) } }
        if (index < 0) return null
        val rebuiltPath = segments.mapIndexed { i, seg -> if (i == index) "{query}" else seg }.joinToString("/")
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "https://${uri.rawAuthority}$rebuiltPath$query"
    }

    private fun matchesQuery(value: String, normalizedQuery: String): Boolean {
        val v = normalize(value)
        return v.isNotBlank() && (v == normalizedQuery || (v.length >= 3 && normalizedQuery.contains(v)) || v.contains(normalizedQuery))
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun normalize(s: String): String =
        s.lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("""[^\p{L}\p{Nd}]+"""), " ").trim()
}

/** Builds schema v2 rule entities from TeachMode state (SPEC-TEACH-06 §3, §5.2). */
object TeachRuleFactory {
    fun newRuleId(domain: String): String {
        val slug = domain.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "_")
        return "rule_${slug}_${UUID.randomUUID().toString().take(8)}"
    }

    /**
     * A rule may be turned on only after a successful check. A success that needed the page
     * renderer is stored with `render.engine = WEBVIEW` (see [buildRule]) so the automatic
     * cascade repeats it the same way. Where no renderer took part in the check (rule import)
     * a rendered success cannot occur.
     */
    fun canActivate(outcome: RuleOutcome?): Boolean = outcome is RuleOutcome.Success

    /** Render deadline to store for a checked rule, or null when plain HTTP was enough. */
    fun renderTimeoutFor(outcome: RuleOutcome?): Long? =
        (outcome as? RuleOutcome.Success)?.takeIf { it.usedWebView }?.let { RuleRendererRegistry.timeoutFor(it.renderMs) }

    /**
     * @param activate true only after a successful validation run (R9/R10); otherwise a DRAFT
     * that the cascade never executes.
     */
    fun buildRule(
        id: String,
        domain: String,
        contentSelector: String,
        stripSelectors: List<String>,
        contentType: TargetContentType,
        searchTemplate: String?,
        resultSelector: String?,
        activate: Boolean,
        now: Long = System.currentTimeMillis(),
        /** Non-null for a site whose pages are built by scripts: see [renderTimeoutFor]. */
        renderTimeoutMs: Long? = null
    ): CustomLyricsRuleEntity {
        val template = searchTemplate?.trim().orEmpty()
        val active = activate && template.isNotBlank()
        val status = if (active) "ACTIVE" else "DRAFT"
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("status", status)
            put("site", JSONObject().apply {
                put("domain", domain)
                put("name", domain)
            })
            if (template.isNotBlank()) {
                put("search", JSONObject().apply {
                    put("urlTemplate", template)
                    resultSelector?.trim()?.takeIf { it.isNotBlank() }?.let { put("resultListSelector", it) }
                })
            }
            if (renderTimeoutMs != null) {
                put("render", JSONObject().apply {
                    put("engine", "WEBVIEW")
                    put("timeoutMs", renderTimeoutMs)
                })
            }
            put("content", JSONObject().apply {
                put("mode", if (contentType == TargetContentType.CHORDS) "CHORDS" else "LYRICS")
                put("contentSelector", contentSelector)
                if (contentType == TargetContentType.CHORDS) put("chordsSelector", contentSelector)
                put("stripSelectors", JSONArray(stripSelectors))
                put("lineBreakStrategy", "PRESERVE_BR")
            })
        }.toString()
        return CustomLyricsRuleEntity(
            id = id,
            domain = domain,
            name = domain,
            ruleJson = json,
            isEnabled = active,
            priority = 100,
            createdAt = now,
            updatedAt = now
        )
    }

    fun parseStripSelectors(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())
}
