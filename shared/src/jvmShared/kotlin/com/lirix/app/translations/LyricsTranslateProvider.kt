package com.lirix.app.translations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class LyricsTranslateProvider(private val openConnection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }) {
    private data class Page(val bytes: ByteArray, val url: String, val charset: String?)
    private val gate = Mutex()
    private var blockedUntil = 0L
    companion object {
        const val MAX_BYTES = 2_000_000
        private const val SEARCH = "https://lyricstranslate.com/en/songs"
        private val names = mapOf("ru" to "Russian", "en" to "English", "es" to "Spanish", "pt" to "Portuguese", "zh" to "Chinese")
        private fun validateUrl(input: String): String {
            val uri = URI(input)
            require(uri.scheme == "https" && uri.host in setOf("lyricstranslate.com", "www.lyricstranslate.com") && uri.port == -1 && uri.rawUserInfo == null && uri.rawFragment == null) { "Недопустимый адрес LyricsTranslate" }
            if (uri.path == "/en/songs" || uri.rawPath.startsWith("/en/songs/")) {
                require(!uri.rawPath.contains("..") && !uri.rawPath.contains("%2f", true) && uri.rawPath.length < 2000 && uri.rawQuery.orEmpty().matches(Regex("(?:tlang=[0-9]+)?"))) { "Недопустимый адрес поиска" }
                return input
            }
            return LyricsTranslateParser.canonicalUrl(input)
        }
        fun candidates(html: ByteArray, url: String, title: String, artist: String): List<TranslationCandidate> {
            val doc = Jsoup.parse(ByteArrayInputStream(html), null, validateUrl(url))
            check(doc.selectFirst("#lyricstranslatesearch-searchsong-form") != null) { "Изменился формат поиска LyricsTranslate" }
            return doc.select(".table__trow").mapNotNull { row ->
                val link = row.selectFirst("a.title-link") ?: return@mapNotNull null
                val song = link.text().trim()
                val performer = row.selectFirst(".block-1-table__author .text")?.text().orEmpty()
                if (TranslationMetadata.titleKey(song) != TranslationMetadata.titleKey(title, artist) || TranslationMetadata.artistKey(performer) != TranslationMetadata.artistKey(artist)) return@mapNotNull null
                val target = runCatching { LyricsTranslateParser.canonicalUrl(URI(url).resolve(link.attr("href")).toString()) }.getOrNull() ?: return@mapNotNull null
                val translator = row.selectFirst("a[href*=/translator/] img")?.attr("alt").orEmpty()
                TranslationCandidate(target, song, performer, translator)
            }.distinctBy { it.sourceUrl }.take(8)
        }
    }
    suspend fun load(url: String, language: String? = null): SongTranslation {
        val page = readPage(LyricsTranslateParser.canonicalUrl(url))
        return LyricsTranslateParser.parse(page.bytes, page.url, language)
    }
    suspend fun find(title: String, artist: String, language: String, refresh: Boolean = false): TranslationSearchResult = gate.withLock {
        require(language in names)
        if (title.isBlank() || artist.isBlank()) return@withLock TranslationSearchResult.Missing
        check(System.currentTimeMillis() >= blockedUntil) { "LyricsTranslate временно ограничил запросы. Повторите позже." }
        try {
            val page = readPage(SEARCH)
            val dom = Jsoup.parse(ByteArrayInputStream(page.bytes), null, page.url)
            val form = dom.selectFirst("#lyricstranslatesearch-searchsong-form") ?: error("Изменился формат поиска LyricsTranslate")
            require(form.attr("method").equals("post", true) && URI(page.url).resolve(form.attr("action")).toString() == SEARCH) { "Изменился адрес формы поиска" }
            // Brackets in the actual HTML field name must not be interpreted as CSS syntax.
            val option = form.select("select").firstOrNull { it.attr("name") == "formtree[ltsearchtranslationlanguage][]" }
                ?.select("option")?.firstOrNull { it.text().trim() == names[language] } ?: error("Источник не поддерживает выбранный язык")
            val values = linkedMapOf<String, String>()
            form.select("input[type=hidden]").filter { it.attr("name") in setOf("form_build_id", "form_id", "order") }.forEach { values[it.attr("name")] = it.attr("value") }
            values["formtree[ltsearchtextsong]"] = TranslationMetadata.title(title, artist)
            values["formtree[ltsearchtextartist]"] = TranslationMetadata.primaryArtist(artist)
            values["formtree[ltsearchtranslationlanguage][]"] = option.attr("value")
            values["op"] = "Search"
            values["sort"] = "popularity"
            fun encodedBody() = values.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
            var results = readPage(SEARCH, encodedBody())
            var matches = candidates(results.bytes, results.url, title, artist)
            if (matches.isEmpty()) {
                // Browser channels can join artist words (OzzyOsbourneVEVO). A bounded
                // title-only query still requires exact artist identity in every result.
                values["formtree[ltsearchtextartist]"] = ""
                results = readPage(SEARCH, encodedBody())
                matches = candidates(results.bytes, results.url, title, artist)
            }
            if (matches.size != 1) return@withLock if (matches.isEmpty()) TranslationSearchResult.Missing else TranslationSearchResult.Candidates(matches)
            val document = load(matches.single().sourceUrl, language)
            if (TranslationMetadata.artistKey(document.artist) != TranslationMetadata.artistKey(artist) || TranslationMetadata.titleKey(document.title) != TranslationMetadata.titleKey(title, artist))
                return@withLock TranslationSearchResult.Candidates(matches)
            TranslationSearchResult.Found(document)
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("HTTP 403") || e.message.orEmpty().contains("HTTP 429")) blockedUntil = System.currentTimeMillis() + 60_000
            throw e
        }
    }
    private suspend fun readPage(url: String, body: String? = null): Page = runInterruptible(Dispatchers.IO) {
        var next = validateUrl(url)
        var payload = body
        repeat(4) {
            val connection = openConnection(URI(next))
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("User-Agent", "Lirix/1.4 song-translation-import")
                connection.setRequestProperty("Accept", "text/html")
                payload?.let {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    connection.outputStream.use { output -> output.write(it.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("Некорректное перенаправление")
                    val target = URI(next).resolve(location).toString()
                    require(payload == null || status in setOf(301, 302, 303)) { "Неожиданное перенаправление формы" }
                    payload = null
                    next = validateUrl(target)
                } else {
                    if (status == 403) error("LyricsTranslate ограничил доступ (HTTP 403). Повторите позже.")
                    if (status == 429) error("LyricsTranslate просит уменьшить частоту запросов (HTTP 429)")
                    require(status == 200) { "Источник недоступен (HTTP $status)" }
                    require(connection.contentType.orEmpty().contains("text/html", true)) { "Источник вернул не HTML-страницу" }
                    require(connection.contentLengthLong <= MAX_BYTES) { "Страница слишком большая" }
                    val bytes = connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= MAX_BYTES) { "Страница слишком большая" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val charset = Regex("charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
                        .find(connection.contentType.orEmpty())?.groupValues?.get(1)
                    return@runInterruptible Page(bytes, next, charset)
                }
            } finally { connection.disconnect() }
        }
        error("Слишком много перенаправлений")
    }
}
