package com.lirix.app.translations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI

class AmalgamaTranslationProvider(private val openConnection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }) {
    private data class Page(val bytes: ByteArray, val url: String, val charset: String?)
    private data class Link(val name: String, val url: String)
    private val catalogGate = Mutex()
    private val catalogs = linkedMapOf<String, Pair<Long, List<Link>>>()
    private class CatalogMissing : Exception()
    companion object {
        const val MAX_BYTES = 2_000_000
        fun canonicalUrl(input: String): String {
            val uri = try { URI(input.trim()) } catch (_: Exception) { throw IllegalArgumentException("Некорректная ссылка") }
            require(uri.scheme.equals("https", true) && uri.host?.lowercase() in setOf("www.amalgama-lab.com", "amalgama-lab.com") &&
                uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null &&
                uri.rawPath?.matches(Regex("/songs/[a-zA-Z0-9_/-]+\\.html")) == true && !uri.path.contains("//")) {
                "Нужна HTTPS-ссылка на страницу песни amalgama-lab.com/songs/…"
            }
            return "https://www.amalgama-lab.com${uri.rawPath}"
        }

        fun parse(bytes: ByteArray, url: String, charset: String? = null): SongTranslation {
            require(bytes.size <= MAX_BYTES) { "Страница слишком большая" }
            val canonical = canonicalUrl(url)
            val doc = Jsoup.parse(ByteArrayInputStream(bytes), charset, canonical)
            val originalHeading = doc.selectFirst("h2.original") ?: error("На странице не найден заголовок песни")
            val translatedHeading = doc.selectFirst("h2.translate") ?: error("На странице не найден перевод")
            val title = originalHeading.ownText().trim()
            val artist = originalHeading.selectFirst("span")?.text().orEmpty().removePrefix("(").removeSuffix(")").removePrefix("оригинал ").trim()
            val translator = translatedHeading.selectFirst("a")?.text()?.trim().orEmpty().ifBlank {
                translatedHeading.selectFirst("span")?.text().orEmpty().removePrefix("(").removeSuffix(")").removePrefix("перевод ").trim()
            }
            val blocks = doc.select("#click_area .string_container").map { row ->
                val original = row.selectFirst(".original")?.wholeText()?.trim().orEmpty()
                val translated = row.selectFirst(".translate")?.wholeText()?.trim().orEmpty()
                TranslationBlock(original, translated, original.startsWith("[") && original.endsWith("]"))
            }.filter { it.original.isNotBlank() || it.translated.isNotBlank() }
            return SongTranslation(canonical, title, artist, translatedHeading.ownText().trim(), translator, blocks)
                .also { TranslationDocuments.encode(it) }
        }
    }

    suspend fun load(url: String): SongTranslation {
        val page = readPage(canonicalUrl(url), false)
        return parse(page.bytes, page.url, page.charset)
    }

    private fun catalogUrl(input: String): String {
        val uri = URI(input)
        require(uri.scheme == "https" && uri.host in setOf("www.amalgama-lab.com", "amalgama-lab.com") &&
            uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.matches(Regex("/songs/(?:[a-z0-9]/(?:[a-zA-Z0-9_-]+/)?)?"))) { "Некорректная ссылка каталога" }
        return "https://www.amalgama-lab.com${uri.rawPath}"
    }

    private suspend fun catalog(url: String, artists: Boolean, refresh: Boolean): List<Link> = catalogGate.withLock {
        val canonical = catalogUrl(url)
        val cached = catalogs[canonical]
        if (!refresh && cached != null && System.currentTimeMillis() - cached.first in 0..86_400_000L) return@withLock cached.second
        val page = readPage(canonical, true)
        val dom = Jsoup.parse(ByteArrayInputStream(page.bytes), page.charset, page.url)
        val lists = if (URI(canonical).path == "/songs/") dom.select(".bands_list") else dom.select("#songs_nav")
        check(lists.isNotEmpty()) { "Изменился формат каталога Amalgama" }
        val links = lists.select("a[href]").mapNotNull { link ->
            val name = link.text().trim()
            if (name.isBlank()) return@mapNotNull null
            val target = URI(page.url).resolve(link.attr("href")).toString()
            val valid = runCatching { if (artists) catalogUrl(target) else canonicalUrl(target) }.getOrNull() ?: return@mapNotNull null
            val path = URI(valid).path
            if (artists && !path.matches(Regex("/songs/[a-z0-9]/[a-zA-Z0-9_-]+/"))) return@mapNotNull null
            if (!artists && (!valid.startsWith(canonical) || path.removePrefix(URI(canonical).path).contains('/'))) return@mapNotNull null
            Link(name, valid)
        }.distinctBy { it.url }
        catalogs[canonical] = System.currentTimeMillis() to links
        while (catalogs.size > 96) catalogs.remove(catalogs.keys.first())
        links
    }

    suspend fun find(title: String, artist: String, refresh: Boolean = false): TranslationSearchResult {
        val artistKey = TranslationMetadata.artistKey(artist)
        val titleKey = TranslationMetadata.titleKey(title, artist)
        if (artistKey.isBlank() || artistKey in setOf("unknownartist", "unknown", "неизвестныйисполнитель") || titleKey.isBlank()) return TranslationSearchResult.Missing
        val bucket = TranslationMetadata.bucket(artist) ?: return TranslationSearchResult.Missing
        return try {
            val direct = catalog("https://www.amalgama-lab.com/songs/$bucket/", true, refresh)
                .filter { TranslationMetadata.artistKey(it.name) == artistKey }
            // Follow the source's own popular-artist links when spelling and bucket differ
            // (e.g. Thirty Seconds To Mars is listed under 3). Never invent an artist slug.
            val artists = direct.ifEmpty { catalog("https://www.amalgama-lab.com/songs/", true, refresh)
                .filter { TranslationMetadata.artistKey(it.name) == artistKey } }
            if (artists.isEmpty()) return TranslationSearchResult.Missing
            val possible = artists.take(3).flatMap { performer ->
                catalog(performer.url, false, refresh).map { song -> TranslationCandidate(song.url, song.name, performer.name) }
            }.distinctBy { it.sourceUrl }
            val exact = possible.filter { TranslationMetadata.titleKey(it.title) == titleKey }
            if (exact.size == 1 && artists.size == 1) {
                val found = load(exact.single().sourceUrl)
                if (TranslationMetadata.artistHeadingMatches(artist, found.artist) && TranslationMetadata.titleKey(found.title) == titleKey)
                    TranslationSearchResult.Found(found)
                else TranslationSearchResult.Candidates(listOf(TranslationCandidate(found.sourceUrl, found.title, found.artist)))
            } else {
                val candidates = exact.ifEmpty { possible.filter { TranslationMetadata.baseTitleKey(it.title) == TranslationMetadata.baseTitleKey(TranslationMetadata.title(title, artist)) } }
                if (candidates.isEmpty()) TranslationSearchResult.Missing else TranslationSearchResult.Candidates(candidates.take(8))
            }
        } catch (_: CatalogMissing) { TranslationSearchResult.Missing }
    }

    private suspend fun readPage(url: String, directory: Boolean): Page = runInterruptible(Dispatchers.IO) {
        var next = if (directory) catalogUrl(url) else canonicalUrl(url)
        repeat(4) {
            val connection = openConnection(URI(next))
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("User-Agent", "Lirix/1.4 song-translation-import")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("Некорректное перенаправление")
                    val target = URI(next).resolve(location).toString()
                    next = if (directory) catalogUrl(target) else canonicalUrl(target)
                } else {
                    if (directory && status == 404) throw CatalogMissing()
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
