package com.lirix.app.feature.lyrics

import com.lirix.app.feature.FallbackLyricsScraper
import com.lirix.app.feature.HttpTextClient
import com.lirix.app.feature.JsonHelper
import com.lirix.app.feature.LyricsProvider
import com.lirix.app.feature.LyricsResult
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.TrackEntity
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Parsed configuration of a declarative JSON lyrics rule.
 */
data class CustomRuleConfig(
    val domain: String,
    val name: String,
    val searchUrlTemplate: String? = null,
    val searchResultSelector: String? = null,
    val contentSelector: String,
    val stripSelectors: List<String> = emptyList(),
    val chordsSelector: String? = null,
    val lineBreakStrategy: String = "PRESERVE_BR"
)

/**
 * Provider that executes a declarative JSON rule against web endpoints.
 * Strictly non-executable: interprets only CSS selectors, tags, and string replacements.
 */
class CustomRuleLyricsProvider(
    val ruleEntity: CustomLyricsRuleEntity,
    private val httpGet: suspend (String) -> String? = { url -> HttpTextClient.get(url) }
) : LyricsProvider {

    val config: CustomRuleConfig = parseConfig(ruleEntity.ruleJson, ruleEntity.domain, ruleEntity.name)

    override suspend fun getLyrics(track: TrackEntity): LyricsResult {
        return getLyrics(track, emptySet(), false)
    }

    override suspend fun getLyrics(
        track: TrackEntity,
        rejectedSourceIds: Set<String>,
        forceNetwork: Boolean
    ): LyricsResult {
        val canonicalId = "rule:${ruleEntity.id}"
        if (canonicalId in rejectedSourceIds) {
            return LyricsResult(false, "", ruleEntity.name, sourceId = canonicalId)
        }

        val searchTemplate = config.searchUrlTemplate
        val songUrl = if (!searchTemplate.isNullOrBlank()) {
            val query = "${track.artist} ${track.title}".trim()
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = searchTemplate
                .replace("{query}", encodedQuery)
                .replace("{artist}", URLEncoder.encode(track.artist, "UTF-8"))
                .replace("{title}", URLEncoder.encode(track.title, "UTF-8"))

            val searchHtml = httpGet(searchUrl)
                ?: return LyricsResult(false, "Ошибка сетевого запроса к ${config.domain}", ruleEntity.name, sourceId = canonicalId)

            resolveSongUrl(searchHtml, config.searchResultSelector, track.title, track.artist, config.domain)
                ?: return LyricsResult(false, "Песня не найдена на ${config.domain}", ruleEntity.name, sourceId = canonicalId)
        } else {
            "https://${config.domain}/"
        }

        val songHtml = httpGet(songUrl)
            ?: return LyricsResult(false, "Ошибка загрузки страницы песни", ruleEntity.name, sourceId = canonicalId)

        val extractedText = extractContent(songHtml, config.contentSelector, config.stripSelectors)
        if (extractedText.isNullOrBlank() || extractedText.length < 30) {
            return LyricsResult(false, "Текст не извлечен по селектору", ruleEntity.name, sourceId = canonicalId)
        }

        return LyricsResult(
            hasLyrics = true,
            lyricsText = extractedText,
            source = ruleEntity.name,
            plainLyrics = extractedText,
            sourceId = canonicalId
        )
    }

    companion object {
        fun parseConfig(jsonStr: String, defaultDomain: String, defaultName: String): CustomRuleConfig {
            var domain = defaultDomain
            var name = defaultName
            var searchUrlTemplate: String? = null
            var searchResultSelector: String? = null
            var contentSelector = ""
            val stripList = mutableListOf<String>()
            var chordsSelector: String? = null
            var lineBreakStrategy = "PRESERVE_BR"

            try {
                val json = JSONObject(jsonStr)
                val searchObj = json.optJSONObject("search")
                val contentObj = json.optJSONObject("content") ?: json

                val stripArray = contentObj.optJSONArray("stripSelectors")
                if (stripArray != null) {
                    for (i in 0 until stripArray.length()) {
                        val item = stripArray.optString(i)
                        if (!item.isNullOrBlank()) {
                            stripList.add(item)
                        }
                    }
                }

                json.optString("domain", defaultDomain)?.takeIf { !it.isNullOrBlank() }?.let { domain = it }
                json.optString("name", defaultName)?.takeIf { !it.isNullOrBlank() }?.let { name = it }
                searchObj?.optString("urlTemplate")?.takeIf { !it.isNullOrBlank() }?.let { searchUrlTemplate = it }
                searchObj?.optString("resultListSelector")?.takeIf { !it.isNullOrBlank() }?.let { searchResultSelector = it }
                contentObj.optString("selector", "")?.let { contentSelector = it }
                contentObj.optString("chordsSelector")?.takeIf { !it.isNullOrBlank() }?.let { chordsSelector = it }
                contentObj.optString("lineBreakStrategy", "PRESERVE_BR")?.takeIf { !it.isNullOrBlank() }?.let { lineBreakStrategy = it }
            } catch (_: Throwable) {
            }

            // Robust fallback for JVM unit tests where org.json is stubbed by Android SDK
            if (contentSelector.isBlank()) {
                val extractedSelector = JsonHelper.extractStringField(jsonStr, "selector")
                if (!extractedSelector.isNullOrBlank()) {
                    contentSelector = extractedSelector
                    JsonHelper.extractStringField(jsonStr, "domain")?.let { domain = it }
                    JsonHelper.extractStringField(jsonStr, "name")?.let { name = it }
                    JsonHelper.extractStringField(jsonStr, "urlTemplate")?.let { searchUrlTemplate = it }
                    JsonHelper.extractStringField(jsonStr, "resultListSelector")?.let { searchResultSelector = it }
                    JsonHelper.extractStringField(jsonStr, "chordsSelector")?.let { chordsSelector = it }
                    JsonHelper.extractStringField(jsonStr, "lineBreakStrategy")?.let { lineBreakStrategy = it }

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
                lineBreakStrategy = lineBreakStrategy
            )
        }

        fun extractContent(html: String, contentSelector: String, stripSelectors: List<String>): String? {
            var block = extractContainerBySelector(html, contentSelector) ?: return null

            for (strip in stripSelectors) {
                block = removeStripElements(block, strip)
            }

            val cleaned = cleanHtmlToPlainLyrics(block)
            return cleaned.ifBlank { null }
        }

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

            // Support composite selectors separated by comma (e.g. "div.verse1, div.verse2")
            if (cleanSel.contains(",")) {
                val subSelectors = cleanSel.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val extractedBlocks = mutableListOf<String>()
                for (subSel in subSelectors) {
                    val block = extractSingleContainer(html, subSel)
                    if (!block.isNullOrBlank()) {
                        extractedBlocks.add(block)
                    }
                }
                return if (extractedBlocks.isNotEmpty()) {
                    extractedBlocks.joinToString("\n\n")
                } else {
                    null
                }
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

        fun removeStripElements(html: String, stripSelector: String): String {
            val clean = stripSelector.trim()
            var result = html
            if (clean.contains(".")) {
                val tag = clean.substringBefore(".").ifBlank { "[a-zA-Z0-9]+" }
                val cls = clean.substringAfter(".")
                result = result.replace(Regex("""(?si)<$tag\b[^>]*class=["'][^"']*\b$cls\b[^"']*["'][^>]*>.*?</$tag>"""), "")
            } else if (clean.contains("#")) {
                val tag = clean.substringBefore("#").ifBlank { "[a-zA-Z0-9]+" }
                val id = clean.substringAfter("#")
                result = result.replace(Regex("""(?si)<$tag\b[^>]*id=["']$id["'][^>]*>.*?</$tag>"""), "")
            } else if (clean.startsWith(".")) {
                val cls = clean.removePrefix(".")
                result = result.replace(Regex("""(?si)<([a-zA-Z0-9]+)\b[^>]*class=["'][^"']*\b$cls\b[^"']*["'][^>]*>.*?</\1>"""), "")
            } else if (clean.startsWith("#")) {
                val id = clean.removePrefix("#")
                result = result.replace(Regex("""(?si)<([a-zA-Z0-9]+)\b[^>]*id=["']$id["'][^>]*>.*?</\1>"""), "")
            } else {
                if (clean.equals("script", ignoreCase = true) || clean.equals("style", ignoreCase = true) || clean.equals("button", ignoreCase = true) || clean.equals("header", ignoreCase = true) || clean.equals("footer", ignoreCase = true)) {
                    result = result.replace(Regex("""(?si)<$clean\b[^>]*>.*?</$clean>"""), "")
                } else {
                    result = result.replace(Regex("""(?si)<([a-zA-Z0-9]+)\b[^>]*class=["'][^"']*\b$clean\b[^"']*["'][^>]*>.*?</\1>"""), "")
                    result = result.replace(Regex("""(?si)<$clean\b[^>]*>.*?</$clean>"""), "")
                }
            }
            return result
        }

        fun resolveSongUrl(
            html: String,
            selector: String?,
            title: String,
            artist: String,
            domain: String
        ): String? {
            val linkRegex = Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
            val matches = linkRegex.findAll(html).toList()
            for (match in matches) {
                val href = match.groupValues[1]
                val text = match.groupValues[2]
                if (text.contains(title, ignoreCase = true) || (artist.isNotBlank() && text.contains(artist, ignoreCase = true))) {
                    return if (href.startsWith("http")) href else "https://$domain${if (href.startsWith("/")) "" else "/"}$href"
                }
            }
            return matches.firstOrNull()?.groupValues?.get(1)?.let { href ->
                if (href.startsWith("http")) href else "https://$domain${if (href.startsWith("/")) "" else "/"}$href"
            }
        }
    }
}
