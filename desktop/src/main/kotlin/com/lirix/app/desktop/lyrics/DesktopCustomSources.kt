package com.lirix.app.desktop.lyrics

import com.lirix.app.storage.CustomLyricsRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

data class SourceCandidate(val selector: String, val preview: String)
data class SourceInspection(val url: String, val candidates: List<SourceCandidate>)

object DesktopCustomSources {
    private fun validateUrl(url: String): URI = URI(url).also {
        require(it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null) { "Нужна ссылка http:// или https:// без пароля" }
    }
    private fun document(url: String): org.jsoup.nodes.Document {
        validateUrl(url)
        return Jsoup.connect(url).timeout(10_000).maxBodySize(2_000_000)
            .userAgent("Mozilla/5.0 Lirix/1.4 Desktop").get()
    }
    suspend fun inspect(url: String): SourceInspection = withContext(Dispatchers.IO) {
        val doc = document(url)
        val candidates = doc.select("article, main, pre, div[id], div[class], section, [itemprop=lyrics]")
            .filter { it.text().length in 80..40_000 }
            .sortedBy { it.text().length }.take(80).map { SourceCandidate(it.cssSelector(), it.text().take(320)) }
            .distinctBy { it.preview }
        SourceInspection(doc.location(), candidates)
    }
    fun extractHtml(html: String, selector: String, stripSelectors: List<String> = emptyList()): String {
        require(selector.isNotBlank()) { "Выберите блок с текстом" }
        val doc = Jsoup.parse(html)
        doc.select("script, style, nav, header, footer, button, iframe").remove()
        stripSelectors.filter { it.isNotBlank() }.forEach { doc.select(it).remove() }
        val elements = doc.select(selector)
        require(elements.isNotEmpty()) { "По селектору ничего не найдено" }
        return elements.joinToString("\n\n") { element ->
            element.select("p,div,section,li").forEach { it.appendText("\n") }
            element.wholeText()
        }.lines().map { it.trimEnd() }.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
            .also { require(it.isNotBlank()) { "Блок не содержит текста" } }
    }
    suspend fun extract(url: String, selector: String): String = withContext(Dispatchers.IO) {
        extractHtml(document(url).outerHtml(), selector)
    }
    fun rule(name: String, url: String, selector: String, searchTemplate: String = ""): CustomLyricsRuleEntity {
        val domain = validateUrl(url).host.lowercase()
        require(name.isNotBlank() && selector.isNotBlank())
        val json = buildJsonObject {
            put("domain", domain); put("name", name)
            put("content", buildJsonObject { put("selector", selector); put("lineBreakStrategy", "PRESERVE_BR") })
            put("pageUrl", url)
            if (searchTemplate.isNotBlank()) put("search", buildJsonObject { put("urlTemplate", searchTemplate) })
        }
        return CustomLyricsRuleEntity(java.util.UUID.randomUUID().toString(), domain, name, json.toString())
    }
    suspend fun resolve(rule: CustomLyricsRuleEntity, artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val config = Json.parseToJsonElement(rule.ruleJson).jsonObject
        val content = config["content"]?.jsonObject ?: config
        val selector = content["selector"]?.jsonPrimitive?.content ?: return@withContext null
        val template = config["search"]?.jsonObject?.get("urlTemplate")?.jsonPrimitive?.contentOrNull
        val url = if (!template.isNullOrBlank()) {
            fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
            val search = template.replace("{query}", encode("$artist $title")).replace("{artist}", encode(artist)).replace("{title}", encode(title))
            val doc = document(search)
            doc.select("a[href]").firstOrNull { link ->
                val target = runCatching { URI(link.absUrl("href")) }.getOrNull()
                target?.host?.removePrefix("www.") == rule.domain.removePrefix("www.") && link.text().contains(title, true)
            }?.absUrl("href") ?: return@withContext null
        } else {
            // A one-song binding is never reused for unrelated songs.
            val boundTitle = config["trackTitle"]?.jsonPrimitive?.contentOrNull
            val boundArtist = config["trackArtist"]?.jsonPrimitive?.contentOrNull
            if (boundTitle != title || boundArtist != artist) return@withContext null
            config["pageUrl"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
        }
        val strips = content["stripSelectors"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        extractHtml(document(url).outerHtml(), selector, strips)
    }
}
