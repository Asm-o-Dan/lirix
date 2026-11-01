package com.lirix.app.feature.lyrics

import com.lirix.app.translations.TranslationMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

/** Chord sheets with independent page identity checks and separate original lyrics. */
class AkkordsProProvider(
    private val fetch: suspend (String) -> String? = { LyricsSourceHttp.get(it, setOf("akkords.pro")) }
) {
    data class Candidate(val title: String, val artist: String, val url: String)
    internal data class Document(val title: String, val artist: String, val result: AdditionalLyricsResult)
    private val gate = Mutex()
    private val misses = LinkedHashMap<String, Long>()
    private var unavailableUntil = 0L

    companion object {
        private const val ROOT = "https://akkords.pro"
        private const val MAX_CHARS = 2_000_000
        private val chord = Regex("[A-H](?:#|b)?(?:(?:maj|min|dim|aug|sus|add|no|m)[0-9]*|[0-9]+)*(?:/[A-H](?:#|b)?)?")
        private fun isChordLine(value: String): Boolean = value.trim().split(Regex("\\s+"))
            .let { it.isNotEmpty() && it.all { token -> chord.matches(token) } }
        private fun cleanTitle(value: String): String = value.removeSuffix(": аккорды для гитары").trim()

        fun canonicalUrl(input: String): String {
            val uri = URI(input.trim())
            require(uri.scheme == "https" && uri.host == "akkords.pro" && uri.port == -1 &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.orEmpty().matches(Regex("/songs/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/"))) {
                "Нужна HTTPS-ссылка на песню Akkords.pro"
            }
            return "$ROOT${uri.rawPath}"
        }

        fun searchUrl(title: String, artist: String): String = "$ROOT/?s=" + URLEncoder.encode(
            "${TranslationMetadata.primaryArtist(artist)} ${TranslationMetadata.title(title, artist)}", "UTF-8"
        )

        fun candidates(html: String, title: String, artist: String): List<Candidate> {
            require(html.length <= MAX_CHARS) { "Страница слишком большая" }
            val doc = Jsoup.parse(html, ROOT)
            require(doc.selectFirst("form.search-form input[name=s]") != null) { "Изменился поиск Akkords.pro" }
            return doc.select(".apx-song-card").mapNotNull { card ->
                val link = card.selectFirst(".apx-song-card__post-link[href]") ?: return@mapNotNull null
                val name = cleanTitle(link.text())
                val performer = card.selectFirst(".apx-song-card__artist")?.text().orEmpty()
                if (TranslationMetadata.artistKey(performer) != TranslationMetadata.artistKey(artist) ||
                    TranslationMetadata.titleKey(name) != TranslationMetadata.titleKey(title, artist)) return@mapNotNull null
                val url = runCatching { canonicalUrl(URI(ROOT).resolve(link.attr("href")).toString()) }
                    .getOrNull() ?: return@mapNotNull null
                Candidate(name, performer, url)
            }.distinctBy { it.url }.take(8)
        }

        internal fun document(html: String, url: String): Document {
            require(html.length <= MAX_CHARS) { "Страница слишком большая" }
            val requested = canonicalUrl(url)
            val doc = Jsoup.parse(html, requested)
            val canonical = canonicalUrl(doc.selectFirst("link[rel=canonical]")?.absUrl("href")
                ?: error("Нет адреса песни Akkords.pro"))
            require(canonical == requested) { "Источник вернул другую песню" }
            val title = cleanTitle(doc.selectFirst("h1.entry-title")?.text().orEmpty())
            val artistPath = URI(canonical).path.substringBeforeLast('/', "").substringBeforeLast('/') + "/"
            val artist = doc.select("#kadence-breadcrumbs a[href]").firstOrNull {
                runCatching { URI(it.absUrl("href")).let { u -> u.scheme == "https" && u.host == "akkords.pro" && u.path == artistPath } }.getOrDefault(false)
            }?.text()?.trim().orEmpty()
            require(title.isNotBlank() && artist.isNotBlank()) { "Нет метаданных песни Akkords.pro" }
            val sheets = doc.select("article .entry-content p.chords")
            require(sheets.isNotEmpty()) { "Нет аккордов Akkords.pro" }
            fun normalize(value: String): String = value.replace('\u00a0', ' ').replace("\r\n", "\n")
                .lines().joinToString("\n") { it.trimEnd() }.trim('\n', '\r')
            val chordText = normalize(sheets.joinToString("\n\n") { it.wholeText() })
            val chordSpans = sheets.select("span").count { isChordLine(it.text()) }
            require(chordSpans > 0 && chordText.isNotBlank()) { "Не найдены обозначения аккордов" }
            val plain = normalize(sheets.joinToString("\n\n") { sheet ->
                sheet.clone().apply { select("span").filter { isChordLine(it.text()) }.forEach { it.remove() } }.wholeText()
            }).lines().filterNot { isChordLine(it) }.joinToString("\n").trim()
            val author = doc.selectFirst(".entry-author-name")?.text()?.trim()?.takeIf { it.isNotBlank() }
            return Document(title, artist, AdditionalLyricsResult(
                sourceId = "builtin:akkords-pro", sourceName = "Akkords.pro", plainLyrics = plain.takeIf { it.isNotBlank() },
                chordsText = chordText, sourceUrl = canonical, author = author
            ))
        }

        fun parse(html: String, url: String): AdditionalLyricsResult = document(html, url).result
    }

    suspend fun find(title: String, artist: String): AdditionalLyricsResult? = gate.withLock {
        val artistKey = TranslationMetadata.artistKey(artist)
        val titleKey = TranslationMetadata.titleKey(title, artist)
        if (artistKey.isBlank() || artistKey in setOf("unknown", "unknownartist", "неизвестныйисполнитель") || titleKey.isBlank()) return@withLock null
        val key = "$artistKey|$titleKey"
        val now = System.currentTimeMillis()
        if (now < unavailableUntil) return@withLock null
        if (misses[key]?.let { now - it in 0..899_999L } == true) return@withLock null
        try {
            withTimeoutOrNull(15_000L) {
                val html = fetch(searchUrl(title, artist)) ?: run {
                    unavailableUntil = now + 60_000L
                    return@withTimeoutOrNull null
                }
                val matches = candidates(html, title, artist)
                if (matches.isEmpty()) {
                    misses[key] = now
                    while (misses.size > 96) misses.remove(misses.keys.first())
                    return@withTimeoutOrNull null
                }
                if (matches.size != 1) return@withTimeoutOrNull null
                val url = matches.single().url
                val page = fetch(url) ?: return@withTimeoutOrNull null
                val found = document(page, url)
                if (TranslationMetadata.artistKey(found.artist) == artistKey && TranslationMetadata.titleKey(found.title) == titleKey)
                    found.result else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            unavailableUntil = System.currentTimeMillis() + 60_000L
            null
        }
    }
}
