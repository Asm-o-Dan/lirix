package com.lirix.app.translations

import com.lirix.app.feature.lyrics.LyricsSourceHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

/** Public server-rendered Russian translations; search matches are verified against the song page. */
class LyrsenseTranslationProvider(
    private val fetch: suspend (String) -> String? = { LyricsSourceHttp.get(it, setOf("lyrsense.com")) }
) {
    private val gate = Mutex()
    private val misses = LinkedHashMap<String, Long>()
    private var unavailableUntil = 0L

    companion object {
        private const val ROOT = "https://lyrsense.com"
        private const val MAX_CHARS = 2_000_000

        fun canonicalUrl(input: String): String {
            val uri = URI(input.trim())
            require(uri.scheme == "https" && uri.host == "lyrsense.com" && uri.port == -1 &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.orEmpty().matches(Regex("/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+"))) {
                "Нужна HTTPS-ссылка на песню Lyrsense"
            }
            return "$ROOT${uri.rawPath}"
        }

        fun searchUrl(title: String, artist: String): String = "$ROOT/search?s=" + URLEncoder.encode(
            "${TranslationMetadata.primaryArtist(artist)} ${TranslationMetadata.title(title, artist)}", "UTF-8"
        )

        fun searchCandidates(html: String, title: String, artist: String): List<TranslationCandidate> {
            require(html.length <= MAX_CHARS) { "Страница слишком большая" }
            val doc = Jsoup.parse(html, ROOT)
            // An empty result page has the same search form. A challenge or unrelated page does not.
            require(doc.selectFirst("form[action=/search] input[name=s]") != null) { "Изменился поиск Lyrsense" }
            return doc.select("li.element.radioFilterElement").mapNotNull { row ->
                val link = row.children().firstOrNull { it.tagName() == "a" && it.hasAttr("href") }
                    ?: return@mapNotNull null
                val performer = row.children().firstOrNull { it.tagName() == "span" }?.text().orEmpty()
                val name = link.text().trim()
                if (TranslationMetadata.artistKey(performer) != TranslationMetadata.artistKey(artist) ||
                    TranslationMetadata.titleKey(name) != TranslationMetadata.titleKey(title, artist)) return@mapNotNull null
                val url = runCatching { canonicalUrl(URI(ROOT).resolve(link.attr("href")).toString()) }
                    .getOrNull() ?: return@mapNotNull null
                TranslationCandidate(url, name, performer)
            }.distinctBy { it.sourceUrl }.take(8)
        }

        fun parse(html: String, url: String): SongTranslation {
            require(html.length <= MAX_CHARS) { "Страница слишком большая" }
            val requested = canonicalUrl(url)
            val doc = Jsoup.parse(html, requested)
            val canonical = canonicalUrl(doc.selectFirst("link[rel=canonical]")?.absUrl("href")
                ?: error("Нет адреса песни Lyrsense"))
            require(canonical == requested) { "Источник вернул другую песню" }
            val title = doc.selectFirst("#fr_title")?.text()?.trim().orEmpty()
            val translatedTitle = doc.selectFirst("#ru_title")?.text()?.trim().orEmpty()
            val artist = doc.selectFirst(".brArtistLink [itemprop=name]")?.text()?.trim().orEmpty()
            require(title.isNotBlank() && translatedTitle.isNotBlank() && artist.isNotBlank()) { "Нет метаданных песни Lyrsense" }
            val lines = doc.selectFirst("#songFlexLines") ?: error("Нет перевода Lyrsense")
            val blocks = lines.children().filter { it.hasClass("songTextLine") }.map { row ->
                fun text(css: String): String = row.selectFirst(css)?.clone()?.apply { select("sup").remove() }
                    ?.wholeText()?.trim().orEmpty()
                TranslationBlock(text(".songTextLine__original"), text(".songTextLine__translation"))
            }
            val meaningful = blocks.filter { it.original.isNotBlank() || it.translated.isNotBlank() }
            require(meaningful.isNotEmpty() && meaningful.all { it.original.isNotBlank() && it.translated.isNotBlank() }) {
                "Нарушено соответствие строк перевода Lyrsense"
            }
            val translator = doc.selectFirst("#author_var1 > span")?.text()?.trim().orEmpty()
            return SongTranslation(canonical, title, artist, translatedTitle, translator, blocks,
                language = "ru", sourceName = "Lyrsense", aligned = true).also { TranslationDocuments.encode(it) }
        }
    }

    suspend fun load(url: String): SongTranslation {
        val canonical = canonicalUrl(url)
        return parse(fetch(canonical) ?: error("Lyrsense временно недоступен"), canonical)
    }

    suspend fun find(title: String, artist: String, refresh: Boolean = false): TranslationSearchResult = gate.withLock {
        val artistKey = TranslationMetadata.artistKey(artist)
        val titleKey = TranslationMetadata.titleKey(title, artist)
        if (artistKey.isBlank() || artistKey in setOf("unknown", "unknownartist", "неизвестныйисполнитель") || titleKey.isBlank())
            return@withLock TranslationSearchResult.Missing
        val key = "$artistKey|$titleKey"
        val now = System.currentTimeMillis()
        if (now < unavailableUntil) return@withLock TranslationSearchResult.Missing
        if (!refresh && misses[key]?.let { now - it in 0..899_999L } == true) return@withLock TranslationSearchResult.Missing
        try {
            withTimeoutOrNull(15_000L) {
                val html = fetch(searchUrl(title, artist)) ?: run {
                    unavailableUntil = now + 60_000L
                    return@withTimeoutOrNull TranslationSearchResult.Missing
                }
                val candidates = searchCandidates(html, title, artist)
                if (candidates.isEmpty()) {
                    misses[key] = now
                    while (misses.size > 96) misses.remove(misses.keys.first())
                    return@withTimeoutOrNull TranslationSearchResult.Missing
                }
                if (candidates.size != 1) return@withTimeoutOrNull TranslationSearchResult.Candidates(candidates)
                val document = load(candidates.single().sourceUrl)
                if (TranslationMetadata.artistKey(document.artist) != artistKey || TranslationMetadata.titleKey(document.title) != titleKey)
                    TranslationSearchResult.Missing
                else TranslationSearchResult.Found(document)
            } ?: run { unavailableUntil = System.currentTimeMillis() + 60_000L; TranslationSearchResult.Missing }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            unavailableUntil = System.currentTimeMillis() + 60_000L
            TranslationSearchResult.Missing
        }
    }
}
