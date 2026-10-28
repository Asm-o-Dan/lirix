package com.lirix.app.translations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.text.Normalizer
import java.util.Locale

/** Rustoeng contains English translations only; original lyrics are never inferred. */
class RustoengTranslationProvider(
    private val openConnection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }
) {
    private data class Identity(val artist: String, val title: String)
    private data class Heading(val element: Element, val title: String, val translatedTitle: String)
    private data class ArtistPage(val artist: String, val content: Element, val headings: List<Heading>)
    private data class Page(val bytes: ByteArray, val url: String)
    @Volatile private var blockedUntil = 0L

    companion object {
        private const val MAX_BYTES = 2_000_000
        private const val MAX_PAGES = 8
        private const val PREFIX = "lirix-v1="
        private val json = Json { ignoreUnknownKeys = false }
        private val validPath = Regex("/[A-Za-z0-9_-]+/")
        private val headings = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        private val bilingualHeading = Regex("^(.+?)\\s*\\(([^()]+)\\)\\s*$")

        // Preserve version and collaborator words: only normalize presentation punctuation/case.
        private fun strictKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        // A cross-script search hit can be offered for confirmation, never auto-selected.
        private fun possibleScriptAlias(requested: String, candidate: String): Boolean {
            fun scripts(value: String) = value.asSequence().filter { it.isLetter() }
                .map { Character.UnicodeScript.of(it.code) }.toSet()
            val left = scripts(requested); val right = scripts(candidate)
            return (Character.UnicodeScript.CYRILLIC in left && Character.UnicodeScript.LATIN in right &&
                Character.UnicodeScript.LATIN !in left && Character.UnicodeScript.CYRILLIC !in right) ||
                (Character.UnicodeScript.LATIN in left && Character.UnicodeScript.CYRILLIC in right &&
                    Character.UnicodeScript.CYRILLIC !in left && Character.UnicodeScript.LATIN !in right)
        }

        private fun validValue(value: String): Boolean = value.isNotBlank() && value == value.trim() &&
            value.codePointCount(0, value.length) <= 200 && value.none { Character.isISOControl(it) }

        private fun pageUrl(input: String): String {
            val uri = try { URI(input) } catch (_: Exception) { throw IllegalArgumentException("Некорректная ссылка Rustoeng") }
            require(uri.scheme == "https" && uri.host?.lowercase(Locale.ROOT) in setOf("rustoeng.com", "www.rustoeng.com") &&
                uri.rawUserInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath != null && validPath.matches(uri.rawPath) && !uri.rawPath.contains("..")) {
                "Недопустимый адрес страницы Rustoeng"
            }
            return "https://rustoeng.com${uri.rawPath}"
        }

        private fun encoded(identity: Identity): String {
            require(validValue(identity.artist) && validValue(identity.title)) { "Недопустимые метаданные песни" }
            val data = json.encodeToString(listOf(identity.artist, identity.title)).toByteArray(Charsets.UTF_8)
            require(data.size <= 512) { "Слишком длинные метаданные" }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        }

        private fun decode(fragment: String?): Identity {
            require(fragment != null && fragment.length <= 1024 && fragment.startsWith(PREFIX)) { "Нет идентификатора песни" }
            val raw = fragment.removePrefix(PREFIX)
            require(raw.isNotEmpty() && raw.matches(Regex("[A-Za-z0-9_-]+"))) { "Некорректный идентификатор песни" }
            val bytes = try { Base64.getUrlDecoder().decode(raw) } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Некорректный идентификатор песни")
            }
            require(bytes.size <= 512 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == raw) { "Некорректный идентификатор песни" }
            val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
                catch (_: Exception) { throw IllegalArgumentException("Некорректный UTF-8") }
            val values = try { json.decodeFromString<List<String>>(text) } catch (_: Exception) {
                throw IllegalArgumentException("Некорректные метаданные песни")
            }
            require(values.size == 2 && values.all(::validValue)) { "Некорректные метаданные песни" }
            val identity = Identity(values[0], values[1])
            require(encoded(identity) == fragment) { "Неканонический идентификатор песни" }
            return identity
        }

        private fun ref(input: String): Pair<String, Identity> {
            val uri = try { URI(input) } catch (_: Exception) { throw IllegalArgumentException("Некорректная ссылка Rustoeng") }
            val fragment = uri.rawFragment
            val page = URI(uri.scheme, uri.rawAuthority, uri.rawPath, uri.rawQuery, null).toASCIIString()
            return pageUrl(page) to decode(fragment)
        }

        private fun reference(page: String, artist: String, title: String): String =
            pageUrl(page) + "#" + encoded(Identity(artist, title))

        private fun isUtility(element: Element): Boolean = element.parents().any {
            it.hasClass("sd-sharing") || it.hasClass("sharedaddy") || it.hasClass("robots-nocontent") ||
                it.hasClass("wp-block-embed") || it.tagName() in setOf("figure", "nav", "aside")
        } || element.hasClass("sd-sharing") || element.hasClass("sharedaddy") || element.hasClass("robots-nocontent")

        private fun artistPage(bytes: ByteArray, url: String): ArtistPage {
            require(bytes.size <= MAX_BYTES) { "Страница слишком большая" }
            val doc = Jsoup.parse(ByteArrayInputStream(bytes), "UTF-8", url)
            val articles = doc.select("main#main article").filter { it.select("header.entry-header h1.entry-title").isNotEmpty() && it.select(".entry-content").isNotEmpty() }
            require(articles.size == 1) { "Неоднозначная страница Rustoeng" }
            val article = articles.single()
            val artistHeaders = article.select("header.entry-header h1.entry-title")
            val contents = article.select(".entry-content")
            require(artistHeaders.size == 1 && contents.size == 1) { "Нет однозначного заголовка Rustoeng" }
            val artist = artistHeaders.single().text().trim()
            require(validValue(artist)) { "Нет исполнителя Rustoeng" }
            val content = contents.single()
            val songHeadings = content.select("h2, h3").filterNot(::isUtility).mapNotNull { element ->
                val matched = bilingualHeading.matchEntire(element.text().trim()) ?: return@mapNotNull null
                val translated = matched.groupValues[1].trim()
                val title = matched.groupValues[2].trim()
                if (!validValue(title) || !validValue(translated)) return@mapNotNull null
                Heading(element, title, translated)
            }
            val normalized = songHeadings.map { strictKey(it.title) }
            require(normalized.all { it.isNotBlank() } && normalized.distinct().size == normalized.size) { "Неоднозначные заголовки песен" }
            return ArtistPage(artist, content, songHeadings)
        }

        private fun parseSection(bytes: ByteArray, pageUrl: String, identity: Identity): SongTranslation {
            val parsed = artistPage(bytes, pageUrl)
            require(strictKey(parsed.artist) == strictKey(identity.artist)) { "Исполнитель страницы изменился" }
            val matches = parsed.headings.filter { strictKey(it.title) == strictKey(identity.title) }
            require(matches.size == 1) { "Песня отсутствует или неоднозначна" }
            val selected = matches.single()
            val elements = parsed.content.getAllElements().drop(1)
            val begin = elements.indexOf(selected.element)
            require(begin >= 0) { "Заголовок песни отсутствует" }
            val pieces = mutableListOf<String>()
            for (element in elements.drop(begin + 1)) {
                // A sharing widget ends the lyrics even when it has no nested heading.
                if (element.hasClass("sd-sharing") || element.hasClass("sharedaddy")) break
                if (element.tagName() in headings) {
                    val knownSong = parsed.headings.any { it.element === element }
                    require(knownSong || isUtility(element)) { "Неизвестная граница песни" }
                    break
                }
                if (isUtility(element)) continue
                if (element.tagName() != "p") {
                    if (element.parents().any { it.tagName() == "p" }) continue
                    require(element.ownText().isBlank()) { "Неизвестное содержимое песни" }
                    continue
                }
                val content = element.wholeText().trim()
                // Video metadata is not a lyric stanza; normal inline links are text, not a reason to drop the line.
                if (element.selectFirst("a[href], iframe, video") != null &&
                    content.matches(Regex("(?is)^(video|video of english translation|youtube|watch video)\\s*:.*"))) continue
                require(element.selectFirst("iframe, video") == null) { "Неоднозначное содержимое песни" }
                if (content.isNotBlank()) pieces += content
            }
            val translated = pieces.joinToString("\n\n").trim()
            require(translated.isNotBlank()) { "В выбранной песне нет перевода" }
            return SongTranslation(reference(pageUrl, parsed.artist, selected.title), selected.title, parsed.artist,
                selected.translatedTitle, "", listOf(TranslationBlock("", translated)), "en", "Rustoeng", false)
                .also { TranslationDocuments.encode(it) }
        }

        private fun searchLinks(bytes: ByteArray, url: String, requestedArtist: String): List<Triple<String, String, Boolean>> {
            require(bytes.size <= MAX_BYTES) { "Страница слишком большая" }
            val doc = Jsoup.parse(ByteArrayInputStream(bytes), "UTF-8", url)
            val main = doc.select("main#main")
            require(main.size == 1 && doc.selectFirst("body.search-results, body.search-no-results") != null) { "Изменился формат поиска Rustoeng" }
            val articles = main.single().select("article")
            val results = main.single().select("article header.entry-header h1.entry-title a[href]")
            require(articles.isEmpty() || results.isNotEmpty()) { "Изменился формат результатов Rustoeng" }
            val matches = results.mapNotNull { a ->
                val exact = strictKey(a.text()) == strictKey(requestedArtist)
                if (!exact && !possibleScriptAlias(requestedArtist, a.text())) return@mapNotNull null
                val target = try { URI(url).resolve(a.attr("href")).toString() } catch (_: Exception) { return@mapNotNull null }
                runCatching { Triple(pageUrl(target), a.text().trim(), exact) }.getOrNull()
            }.distinct()
            require(matches.size <= MAX_PAGES) { "Слишком много результатов Rustoeng" }
            return matches
        }
    }

    suspend fun load(url: String): SongTranslation {
        val (pageUrl, identity) = ref(url)
        val page = readPage(pageUrl, false)
        return parseSection(page.bytes, page.url, identity)
    }

    suspend fun find(title: String, artist: String, refresh: Boolean = false): TranslationSearchResult {
        if (title.isBlank() || artist.isBlank()) return TranslationSearchResult.Missing
        check(System.currentTimeMillis() >= blockedUntil) { "Rustoeng временно ограничил доступ" }
        val query = "https://rustoeng.com/?s=" + URLEncoder.encode(title.trim(), "UTF-8")
        val search = readPage(query, true)
        val pages = searchLinks(search.bytes, search.url, artist)
        val candidates = mutableListOf<TranslationCandidate>()
        var hasUncertainArtist = false
        for ((url, searchArtist, searchExact) in pages) {
            val page = readPage(url, false)
            val parsed = artistPage(page.bytes, page.url)
            require(strictKey(parsed.artist) == strictKey(searchArtist)) { "Исполнитель результата поиска изменился" }
            // Search result and loaded article must identify the same artist, even for manual candidates.
            val articleMatchesRequest = strictKey(parsed.artist) == strictKey(artist)
            if (!articleMatchesRequest && !possibleScriptAlias(artist, parsed.artist)) continue
            for (heading in parsed.headings) {
                if (strictKey(heading.title) != strictKey(title)) continue
                candidates += TranslationCandidate(reference(page.url, parsed.artist, heading.title), heading.title, parsed.artist)
                if (!searchExact || !articleMatchesRequest) hasUncertainArtist = true
            }
        }
        val unique = candidates.distinctBy { it.sourceUrl }
        if (unique.isEmpty()) return TranslationSearchResult.Missing
        if (unique.size > 1 || hasUncertainArtist) return TranslationSearchResult.Candidates(unique)
        val doc = load(unique.single().sourceUrl)
        require(strictKey(doc.artist) == strictKey(artist) && strictKey(doc.title) == strictKey(title)) { "Сведения о песне изменились" }
        return TranslationSearchResult.Found(doc)
    }

    private fun networkUrl(input: String, search: Boolean): String {
        if (!search) return pageUrl(input)
        val uri = URI(input)
        require(uri.scheme == "https" && uri.host == "rustoeng.com" && uri.port == -1 && uri.rawUserInfo == null &&
            uri.rawPath == "/" && uri.rawFragment == null && uri.rawQuery?.matches(Regex("s=[A-Za-z0-9%+._~-]{1,1200}")) == true) { "Недопустимый адрес поиска" }
        return uri.toASCIIString()
    }

    private suspend fun readPage(url: String, search: Boolean): Page = runInterruptible(Dispatchers.IO) {
        var next = networkUrl(url, search)
        repeat(4) {
            check(System.currentTimeMillis() >= blockedUntil) { "Rustoeng временно ограничил доступ" }
            val connection = openConnection(URI(next))
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("User-Agent", "Lirix/1.4 song-translation-import")
                connection.setRequestProperty("Accept", "text/html")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("Некорректное перенаправление")
                    next = networkUrl(URI(next).resolve(location).toString(), search)
                } else {
                    if (status == 403 || status == 429) {
                        blockedUntil = System.currentTimeMillis() + 60_000
                        error("Rustoeng ограничил доступ (HTTP $status)")
                    }
                    check(status == 200) { "Rustoeng недоступен (HTTP $status)" }
                    val contentType = connection.contentType.orEmpty()
                    require(contentType.substringBefore(';').trim().equals("text/html", ignoreCase = true)) { "Источник вернул не HTML" }
                    val charset = Regex("(?i)(?:^|;)\\s*charset\\s*=\\s*([^;]+)").find(contentType)
                        ?.groupValues?.get(1)?.trim()?.trim('\"', '\'')
                    require(charset == null || charset.equals("utf-8", ignoreCase = true)) { "Неподдерживаемая кодировка страницы" }
                    require(connection.contentLengthLong <= MAX_BYTES) { "Страница слишком большая" }
                    val bytes = connection.inputStream.use { stream ->
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= MAX_BYTES) { "Страница слишком большая" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    try {
                        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
                    } catch (_: Exception) { throw IllegalArgumentException("Некорректный UTF-8 страницы") }
                    return@runInterruptible Page(bytes, next)
                }
            } finally { connection.disconnect() }
        }
        error("Слишком много перенаправлений")
    }
}
