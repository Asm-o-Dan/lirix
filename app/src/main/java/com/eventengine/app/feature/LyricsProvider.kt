package com.eventengine.app.feature

import com.eventengine.app.feature.lyrics.AmDmChordParser
import com.eventengine.app.feature.lyrics.CustomRuleLyricsProvider
import com.eventengine.app.storage.CustomLyricsRuleEntity
import com.eventengine.app.storage.LyricsDao
import com.eventengine.app.storage.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Result of lyrics lookup.
 * Contains both plain text lyrics and synchronized karaoke lyrics (LRC timestamps [mm:ss.xx]).
 */
data class LyricsResult(
    val hasLyrics: Boolean,
    val lyricsText: String,
    val source: String,
    val isUserNote: Boolean = false,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val sourceId: String = "",
    val chords: String? = null
)

object LyricsSourceIds {
    const val USER_NOTE = "note:user"
    const val ROOM_CACHE = "cache:room"
    const val LRCLIB = "builtin:lrclib"
    const val AMDM = "builtin:amdm"
    const val VSE_PESNI = "builtin:vse-pesni"
    const val NONE = "none"
}

/**
 * Common interface for lyrics retrieval.
 */
interface LyricsProvider {
    suspend fun getLyrics(track: TrackEntity): LyricsResult = getLyrics(track, emptySet(), false)
    suspend fun getLyrics(
        track: TrackEntity,
        rejectedSourceIds: Set<String> = emptySet(),
        forceNetwork: Boolean = false
    ): LyricsResult = getLyrics(track)
}

/**
 * 100% Offline fallback: uses user notes if available.
 */
class OfflineLyricsAdapter : LyricsProvider {

    override suspend fun getLyrics(track: TrackEntity): LyricsResult = getLyrics(track, emptySet(), false)

    override suspend fun getLyrics(
        track: TrackEntity,
        rejectedSourceIds: Set<String>,
        forceNetwork: Boolean
    ): LyricsResult {
        if (LyricsSourceIds.USER_NOTE in rejectedSourceIds) {
            return LyricsResult(
                hasLyrics = false,
                lyricsText = "Текст не добавлен или отклонен",
                source = "Пользовательские заметки",
                isUserNote = false,
                sourceId = LyricsSourceIds.NONE
            )
        }
        return if (track.userNotes.isNotBlank()) {
            LyricsResult(
                hasLyrics = true,
                lyricsText = track.userNotes,
                source = "Пользовательские заметки",
                isUserNote = true,
                plainLyrics = track.userNotes,
                sourceId = LyricsSourceIds.USER_NOTE
            )
        } else {
            LyricsResult(
                hasLyrics = false,
                lyricsText = "Текст для «${track.title}» не добавлен. Вы можете записать свои мысли или любимые строки в заметках к треку.",
                source = "Локальный офлайн-режим",
                isUserNote = false,
                sourceId = LyricsSourceIds.NONE
            )
        }
    }
}

/**
 * HTTP helper for lightweight scraping without heavy external dependencies.
 */
internal object HttpTextClient {
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 8000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Poco M7 Build/AP3A.240905.015) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    suspend fun get(urlStr: String, customHeaders: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8")
                setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                customHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                instanceFollowRedirects = true
            }

            val code = connection.responseCode
            if (code in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } else {
                Timber.tag("HttpTextClient").w("HTTP %d for URL: %s", code, urlStr)
                null
            }
        } catch (e: Exception) {
            Timber.tag("HttpTextClient").e(e, "Error fetching %s: %s", urlStr, e.message)
            null
        } finally {
            connection?.disconnect()
        }
    }
}

/**
 * Primary lyrics provider using the free, open LrcLib API (https://lrclib.net).
 * Returns both plain text and synced karaoke LRC lyrics.
 */
class LrcLibLyricsProvider(
    private val httpGet: suspend (String) -> String? = { url -> HttpTextClient.get(url) }
) : LyricsProvider {

    override suspend fun getLyrics(track: TrackEntity): LyricsResult = getLyrics(track, emptySet(), false)

    override suspend fun getLyrics(
        track: TrackEntity,
        rejectedSourceIds: Set<String>,
        forceNetwork: Boolean
    ): LyricsResult {
        if (LyricsSourceIds.LRCLIB in rejectedSourceIds) {
            return LyricsResult(
                hasLyrics = false,
                lyricsText = "Источник LRCLIB отклонен",
                source = "LrcLib",
                sourceId = LyricsSourceIds.NONE
            )
        }

        val cleanTitle = cleanTrackName(track.title)
        val cleanArtist = cleanArtistName(track.artist)

        if (cleanTitle.isBlank() && cleanArtist.isBlank()) {
            return LyricsResult(
                hasLyrics = false,
                lyricsText = "Название трека и исполнитель не указаны",
                source = "LrcLib",
                sourceId = LyricsSourceIds.NONE
            )
        }

        val encodedTrack = URLEncoder.encode(cleanTitle, "UTF-8")
        val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")

        // 1. Direct match endpoint: /api/get (requires both artist and track title)
        if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
            val directUrl = "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTrack"
            try {
                val response = httpGet(directUrl)
                if (!response.isNullOrBlank()) {
                    val synced = JsonHelper.extractStringField(response, "syncedLyrics")
                    val plain = JsonHelper.extractStringField(response, "plainLyrics")

                    if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
                        val textToDisplay = (synced ?: plain).orEmpty()
                        return LyricsResult(
                            hasLyrics = true,
                            lyricsText = textToDisplay,
                            source = "LrcLib",
                            isUserNote = false,
                            syncedLyrics = synced,
                            plainLyrics = plain ?: cleanLrcToPlain(synced),
                            sourceId = LyricsSourceIds.LRCLIB
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "LrcLib direct fetch failed for %s - %s", cleanArtist, cleanTitle)
            }
        }

        // 2. Search fallback endpoint: /api/search?q=
        val query = listOf(cleanArtist, cleanTitle).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isNotBlank()) {
            val queryUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8")
            try {
                val searchResponse = httpGet(queryUrl)
                if (!searchResponse.isNullOrBlank()) {
                    val items = JsonHelper.extractJsonArrayObjects(searchResponse)
                    for (item in items) {
                        val synced = JsonHelper.extractStringField(item, "syncedLyrics")
                        val plain = JsonHelper.extractStringField(item, "plainLyrics")
                        if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
                            return LyricsResult(
                                hasLyrics = true,
                                lyricsText = (synced ?: plain).orEmpty(),
                                source = "LrcLib (Search)",
                                isUserNote = false,
                                syncedLyrics = synced,
                                plainLyrics = plain ?: cleanLrcToPlain(synced),
                                sourceId = LyricsSourceIds.LRCLIB
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "LrcLib search fallback failed for %s", query)
            }
        }

        return LyricsResult(
            hasLyrics = false,
            lyricsText = "Текст не найден на LrcLib",
            source = "LrcLib",
            sourceId = LyricsSourceIds.NONE
        )
    }

    companion object {
        private const val TAG = "LrcLibLyricsProvider"

        fun cleanTrackName(title: String): String {
            var current = title.trim()
            val suffixRegexes = listOf(
                Regex("""(?i)\s*\([^)]*?(remix|edit|mix|version|prod|feat|ft|live|ost|bonus|deluxe|official)[^)]*?\)$"""),
                Regex("""(?i)\s*\[[^\]]*?(remix|edit|mix|version|prod|feat|ft|live|ost|bonus|deluxe|official)[^\]]*?\]$"""),
                Regex("""(?i)\s*-\s*.*?(remix|edit|mix|version|live|radio edit|feat|prod).*?$""")
            )
            var changed = true
            while (changed) {
                changed = false
                for (regex in suffixRegexes) {
                    val replaced = current.replace(regex, "").trim()
                    if (replaced != current && replaced.isNotBlank()) {
                        current = replaced
                        changed = true
                    }
                }
            }
            return current
        }

        fun cleanArtistName(artist: String): String {
            return artist
                .replace(Regex("""(?i)\s*(feat\.|ft\.|,|&).*?$"""), "")
                .trim()
        }

        fun cleanLrcToPlain(lrc: String?): String? {
            if (lrc == null) return null
            return lrc.lines()
                .map { line -> line.replace(Regex("""^\[\d{2}:\d{2}\.\d{2,3}\]"""), "").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }
    }
}

/**
 * Scraped content from AmDm.ru containing plain lyrics and formatted chords.
 */
data class AmDmScrapeResult(
    val plainLyrics: String,
    val chords: String?
)

/**
 * Fallback Web Scraper for Russian & Foreign tracks (Genius, Amalgama-Lab, Textpesni).
 */
class FallbackLyricsScraper(
    private val httpGet: suspend (String) -> String? = { url -> HttpTextClient.get(url) }
) : LyricsProvider {

    override suspend fun getLyrics(track: TrackEntity): LyricsResult = getLyrics(track, emptySet(), false)

    override suspend fun getLyrics(
        track: TrackEntity,
        rejectedSourceIds: Set<String>,
        forceNetwork: Boolean
    ): LyricsResult {
        val cleanTitle = LrcLibLyricsProvider.cleanTrackName(track.title)
        val cleanArtist = LrcLibLyricsProvider.cleanArtistName(track.artist)

        // 1. Textpesni.com (Russian hits, lyrics, translations)
        if ("builtin:textpesni" !in rejectedSourceIds) {
            val textpesniResult = scrapeTextpesni(cleanArtist, cleanTitle)
            if (textpesniResult != null) {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = textpesniResult,
                    source = "Textpesni",
                    plainLyrics = textpesniResult,
                    sourceId = "builtin:textpesni"
                )
            }
        }

        // 2. AmDm.ru (Russian rock, indie, niche songs, chords & lyrics)
        if (LyricsSourceIds.AMDM !in rejectedSourceIds) {
            val amdmResult = scrapeAmDmDetails(cleanArtist, cleanTitle)
            if (amdmResult != null) {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = amdmResult.plainLyrics,
                    source = "AmDm",
                    plainLyrics = amdmResult.plainLyrics,
                    sourceId = LyricsSourceIds.AMDM,
                    chords = amdmResult.chords
                )
            }
        }

        // 3. Vse-Pesni.com (Extensive niche & Russian catalog)
        if (LyricsSourceIds.VSE_PESNI !in rejectedSourceIds) {
            val vsePesniResult = scrapeVsePesni(cleanArtist, cleanTitle)
            if (vsePesniResult != null) {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = vsePesniResult,
                    source = "Vse-Pesni",
                    plainLyrics = vsePesniResult,
                    sourceId = LyricsSourceIds.VSE_PESNI
                )
            }
        }

        // 4. LyricFind (Global international & multi-language lyrics catalog)
        if ("builtin:lyricfind" !in rejectedSourceIds) {
            val lyricFindResult = scrapeLyricFind(cleanArtist, cleanTitle)
            if (lyricFindResult != null) {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = lyricFindResult,
                    source = "LyricFind",
                    plainLyrics = lyricFindResult,
                    sourceId = "builtin:lyricfind"
                )
            }
        }

        // 5. Amalgama-Lab (Foreign lyrics and translations, requires both artist and title)
        if ("builtin:amalgama" !in rejectedSourceIds && cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
            val amalgamaResult = scrapeAmalgama(cleanArtist, cleanTitle)
            if (amalgamaResult != null) {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = amalgamaResult,
                    source = "Amalgama",
                    plainLyrics = amalgamaResult,
                    sourceId = "builtin:amalgama"
                )
            }
        }

        // 6. Genius (Public web search & lyrics scraping)
        if ("builtin:genius" !in rejectedSourceIds) {
            val geniusResult = scrapeGenius(cleanArtist, cleanTitle)
            if (geniusResult != null) {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = geniusResult,
                    source = "Genius",
                    plainLyrics = geniusResult,
                    sourceId = "builtin:genius"
                )
            }
        }

        return LyricsResult(
            hasLyrics = false,
            lyricsText = "Текст не найден в резервных источниках",
            source = "FallbackScraper",
            sourceId = LyricsSourceIds.NONE
        )
    }

    suspend fun scrapeTextpesni(artist: String, title: String): String? {
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://textpesni.com/search?q=$q"
            val html = httpGet(searchUrl) ?: return null

            val songPathMatch = Regex("""<a\s+[^>]*href=["'](/song/[^"']+)["'][^>]*>(.*?)</a>""").find(html)
                ?: Regex("""<a\s+[^>]*href=["'](https?://textpesni\.com/[^"']+)["'][^>]*>(.*?)</a>""").find(html)
            val songUrl = songPathMatch?.let {
                val candidateText = sanitizeHtml(it.groupValues[2])
                if (!matchesSearchQuery(candidateText, "", title, artist)) return@let null
                val path = it.groupValues[1]
                if (path.startsWith("http")) path else "https://textpesni.com$path"
            } ?: return null

            val songHtml = httpGet(songUrl) ?: return null
            val rawContainers = extractBalancedTags(songHtml, "class=\"song-text\"")
                .ifEmpty { extractBalancedTags(songHtml, "id=\"song-text\"") }
                .ifEmpty { extractBalancedTags(songHtml, "song-text") }

            val rawLyrics = rawContainers.firstOrNull() ?: return null
            val clean = sanitizeHtml(rawLyrics)
            if (clean.length > 50) return clean
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Textpesni scrape error: %s", e.message)
        }
        return null
    }

    suspend fun scrapeAmDmDetails(artist: String, title: String): AmDmScrapeResult? {
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://amdm.ru/search/?q=$q"
            val searchHtml = httpGet(searchUrl) ?: return null

            // 1. Strictly isolate search results table (prevents grabbing popular songs from the sidebar)
            val itemsTable = Regex("""(?s)<table[^>]*class=["'][^"']*items[^"']*["'][^>]*>(.*?)</table>""")
                .find(searchHtml)?.groupValues?.get(1) ?: return null

            // 2. Each result row contains artist and song links
            val rowMatches = Regex("""(?s)<td[^>]*>(.*?)</td>""")
                .findAll(itemsTable)

            var matchedSongUrl: String? = null
            for (match in rowMatches) {
                val tdContent = match.groupValues[1]
                val links = Regex("""<a\s+[^>]*href=["']((?:https?://[^/]*amdm\.ru)?/akkordi/[^"']+)["'][^>]*>(.*?)</a>""")
                    .findAll(tdContent)
                    .toList()
                if (links.isEmpty()) continue

                // Find song link (usually second link with more path segments, or with digits)
                val songLink = links.find { it.groupValues[1].contains(Regex("""/akkordi/[^/]+/\d+/|/akkordi/[^/]+/[^/]+/?$""")) && it != links.firstOrNull() }
                    ?: links.lastOrNull()
                    ?: continue

                val candidateUrl = songLink.groupValues[1]
                val candidateTitle = sanitizeHtml(songLink.groupValues[2])
                val candidateArtist = if (links.size > 1 && songLink != links.first()) {
                    sanitizeHtml(links.first().groupValues[2])
                } else {
                    ""
                }

                if (matchesSearchQuery(candidateTitle, candidateArtist, title, artist)) {
                    matchedSongUrl = if (candidateUrl.startsWith("http")) candidateUrl else "https://amdm.ru$candidateUrl"
                    break
                }
            }

            val songUrl = matchedSongUrl ?: return null
            val songHtml = httpGet(songUrl) ?: return null

            val rawContainers = extractBalancedTags(songHtml, "itemprop=\"chordsBlock\"", "pre")
                .ifEmpty { extractBalancedTags(songHtml, "chordsBlock", "pre") }
                .ifEmpty { extractBalancedTags(songHtml, "podbor__text", "pre") }
                .ifEmpty {
                    Regex("""(?s)<pre[^>]*itemprop=["']chordsBlock["'][^>]*>(.*?)</pre>""").find(songHtml)?.let { listOf(it.groupValues[1]) } ?: emptyList()
                }

            val rawLyrics = rawContainers.firstOrNull() ?: return null

            // Strip chords: <div class="podbor__chord"...>...</div>
            val withoutChords = rawLyrics
                .replace(Regex("""(?s)<div[^>]*class=["'][^"']*podbor__chord[^"']*["'][^>]*>.*?</div>"""), "")
                .replace(Regex("""(?s)<div[^>]*class=["'][^"']*podbor__keyword[^"']*["'][^>]*>(.*?)</div>"""), "$1\n")

            val clean = sanitizeHtml(withoutChords)
            if (clean.length < 25) return null

            val chords = AmDmChordParser.parseAmDmHtml(songHtml)
                ?: AmDmChordParser.parseAmDmHtml("<pre itemprop=\"chordsBlock\">$rawLyrics</pre>")

            return AmDmScrapeResult(
                plainLyrics = clean,
                chords = chords
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "AmDm scrape error: %s", e.message)
        }
        return null
    }

    suspend fun scrapeAmDm(artist: String, title: String): String? {
        return scrapeAmDmDetails(artist, title)?.plainLyrics
    }

    suspend fun scrapeVsePesni(artist: String, title: String): String? {
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://vse-pesni.com/?s=$q"
            val searchHtml = httpGet(searchUrl) ?: return null

            // 1. Strictly isolate search results list (prevents grabbing popular songs from the sidebar)
            val searchList = Regex("""(?s)<ul[^>]*class=["'][^"']*search-results-list[^"']*["'][^>]*>(.*?)</ul>""")
                .find(searchHtml)?.groupValues?.get(1) ?: return null

            val itemMatches = Regex("""<a\s+[^>]*href=["']((?:https?://vse-pesni\.com)?/song/[^"']+)["'][^>]*>(.*?)</a>""")
                .findAll(searchList)

            var matchedSongUrl: String? = null
            for (item in itemMatches) {
                val candidateUrl = item.groupValues[1]
                val candidateText = sanitizeHtml(item.groupValues[2])
                if (matchesSearchQuery(candidateText, "", title, artist)) {
                    matchedSongUrl = if (candidateUrl.startsWith("http")) candidateUrl else "https://vse-pesni.com$candidateUrl"
                    break
                }
            }

            val songUrl = matchedSongUrl ?: return null
            val songHtml = httpGet(songUrl) ?: return null

            val rawContainers = extractBalancedTags(songHtml, "itemprop=\"lyrics\"", "div")
                .ifEmpty { extractBalancedTags(songHtml, "class=\"can_copy\"", "div") }
                .ifEmpty { extractBalancedTags(songHtml, "song_text", "div") }
                .ifEmpty {
                    Regex("""(?s)<div[^>]*class=["'][^"']*can_copy[^"']*["'][^>]*>(.*?)</div>""").find(songHtml)?.let { listOf(it.groupValues[1]) } ?: emptyList()
                }

            val rawLyrics = rawContainers.firstOrNull() ?: return null

            val withoutWidgets = rawLyrics
                .replace(Regex("""(?s)<div[^>]*class=["'][^"']*custom-html-widget[^"']*["'][^>]*>.*?</div>"""), "")
                .replace(Regex("""(?s)<div[^>]*class=["'][^"']*textwidget[^"']*["'][^>]*>.*?</div>"""), "")
                .replace(Regex("""(?s)<button[^>]*class=["'][^"']*copy-button[^"']*["'][^>]*>.*?</button>"""), "")

            val clean = sanitizeHtml(withoutWidgets)
            if (clean.length > 50) return clean
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "VsePesni scrape error: %s", e.message)
        }
        return null
    }

    suspend fun scrapeLyricFind(artist: String, title: String): String? {
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://lyrics.lyricfind.com/search?q=$q"
            val searchHtml = httpGet(searchUrl)

            var songUrl: String? = null

            if (searchHtml != null) {
                // Find candidate links like: <a href="/lyrics/artist-slug-title-slug">...</a>
                val linkMatches = Regex("""<a\s+[^>]*href=["']((?:https?://lyrics\.lyricfind\.com)?/lyrics/[^"']+)["'][^>]*>(.*?)</a>""")
                    .findAll(searchHtml)

                for (match in linkMatches) {
                    val candidateUrl = match.groupValues[1]
                    val candidateText = sanitizeHtml(match.groupValues[2])
                    if (matchesSearchQuery(candidateText, "", title, artist)) {
                        songUrl = if (candidateUrl.startsWith("http")) candidateUrl else "https://lyrics.lyricfind.com$candidateUrl"
                        break
                    }
                }
            }

            // Direct slug fallback if search HTML did not yield an exact result
            if (songUrl == null && artist.isNotBlank() && title.isNotBlank()) {
                val artistSlug = transliterateSlug(artist)
                val titleSlug = transliterateSlug(title)
                songUrl = "https://lyrics.lyricfind.com/lyrics/$artistSlug-$titleSlug"
            }

            val targetUrl = songUrl ?: return null
            val songHtml = httpGet(targetUrl) ?: return null

            val rawContainers = extractBalancedTags(songHtml, "class=\"track-lyrics\"", "div")
                .ifEmpty { extractBalancedTags(songHtml, "class=\"lyrics\"", "div") }
                .ifEmpty { extractBalancedTags(songHtml, "class=\"lf-lyrics\"", "div") }
                .ifEmpty { extractBalancedTags(songHtml, "id=\"lyrics\"", "div") }
                .ifEmpty {
                    Regex("""(?s)<(?:div|p)[^>]*class=["'][^"']*(?:track-lyrics|lyrics|lf-lyrics|song-lyrics)[^"']*["'][^>]*>(.*?)</(?:div|p)>""")
                        .findAll(songHtml)
                        .map { it.groupValues[1] }
                        .toList()
                }

            val rawLyrics = rawContainers.firstOrNull() ?: return null
            val clean = sanitizeHtml(rawLyrics)
            if (clean.length > 50) return clean
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "LyricFind scrape error: %s", e.message)
        }
        return null
    }

    private fun transliterateSlug(str: String): String {
        return str.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
    }

    private fun matchesSearchQuery(
        candidateTitle: String,
        candidateArtist: String,
        queryTitle: String,
        queryArtist: String
    ): Boolean {
        if (queryTitle.isBlank() && queryArtist.isBlank()) return true

        fun tokenize(str: String): Set<String> {
            return str.lowercase()
                .replace(Regex("""[^\p{L}\p{Nd}]+"""), " ")
                .split(" ")
                .map { it.trim() }
                .filter { it.length >= 2 }
                .toSet()
        }

        val titleTokens = tokenize(queryTitle)
        val artistTokens = tokenize(queryArtist)
        val candidateTokens = tokenize("$candidateTitle $candidateArtist")

        if (titleTokens.isNotEmpty()) {
            val matchesTitle = titleTokens.any { it in candidateTokens }
            if (!matchesTitle) return false
        }

        if (artistTokens.isNotEmpty() && candidateArtist.isNotBlank()) {
            val matchesArtist = artistTokens.any { it in candidateTokens }
            if (!matchesArtist && titleTokens.isEmpty()) return false
        }

        return true
    }

    suspend fun scrapeAmalgama(artist: String, title: String): String? {
        if (artist.isBlank() || title.isBlank()) return null
        try {
            val translitArtist = transliterateForUrl(artist)
            val translitTitle = transliterateForUrl(title)
            val firstLetter = translitArtist.firstOrNull()?.toString() ?: "a"

            val url = "https://www.amalgama-lab.com/songs/$firstLetter/$translitArtist/$translitTitle.html"
            val html = httpGet(url) ?: return null

            val originalLines = Regex("""(?s)<div class=["']original["']>(.*?)</div>""").findAll(html)
                .map { sanitizeHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()

            if (originalLines.isNotEmpty()) {
                val fullLyrics = originalLines.joinToString("\n")
                if (fullLyrics.length > 50) return fullLyrics
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Amalgama scrape error: %s", e.message)
        }
        return null
    }

    suspend fun scrapeGenius(artist: String, title: String): String? {
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://genius.com/api/search/multi?per_page=3&q=$q"
            val jsonStr = httpGet(searchUrl) ?: return null

            // Try extracting song URL via regex/JsonHelper for robust execution on JVM tests and Android ART
            val urlRegex = Regex(""""url"\s*:\s*"(https://genius\.com/[^"]+)"""")
            val songUrl = urlRegex.find(jsonStr)?.groupValues?.get(1)
                ?: run {
                    try {
                        val root = JSONObject(jsonStr)
                        val sections = root.optJSONObject("response")?.optJSONArray("sections")
                        if (sections != null) {
                            for (i in 0 until sections.length()) {
                                val section = sections.getJSONObject(i)
                                if (section.optString("type") == "song") {
                                    val hits = section.optJSONArray("hits")
                                    if (hits != null && hits.length() > 0) {
                                        return@run hits.getJSONObject(0).optJSONObject("result")?.optString("url")
                                    }
                                }
                            }
                        }
                        null
                    } catch (e: Throwable) {
                        null
                    }
                }

            if (songUrl.isNullOrBlank()) return null

            val pageHtml = httpGet(songUrl) ?: return null

            // Extract all balanced data-lyrics-container divs without truncation from internal divs
            val rawContainers = extractBalancedTags(pageHtml, "data-lyrics-container=\"true\"")
                .ifEmpty {
                    // Fallback regex if data-lyrics-container has alternative formatting
                    Regex("""(?s)<div[^>]*data-lyrics-container=["']true["'][^>]*>(.*?)</div>""")
                        .findAll(pageHtml)
                        .map { it.groupValues[1] }
                        .toList()
                }

            val containers = rawContainers
                .map { sanitizeHtml(it) }
                .filter { it.isNotBlank() }

            if (containers.isNotEmpty()) {
                val full = containers.joinToString("\n\n")
                if (full.length > 50) return full
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Genius scrape error: %s", e.message)
        }
        return null
    }

    private fun sanitizeHtml(html: String): String {
        val textWithoutTags = html
            // Remove Genius contributor headers / excluded sections
            .replace(Regex("""(?s)<div[^>]*data-exclude-from-selection=["']true["'][^>]*>.*?</div>"""), "")
            .replace(Regex("""(?s)<script.*?</script>"""), "")
            .replace(Regex("""(?s)<style.*?</style>"""), "")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</p>"""), "\n\n")
            .replace(Regex("""<[^>]+>"""), "")

        return decodeHtmlEntities(textWithoutTags)
            .lines()
            .map { it.trimEnd() }
            .filterNot { line ->
                // Filter out non-lyric Genius artifacts
                val trimmed = line.trim()
                trimmed.contains("Contributors") || trimmed.contains("Translations")
            }
            .fold(mutableListOf<String>()) { acc, line ->
                if (line.isNotBlank() || (acc.isNotEmpty() && acc.last().isNotBlank())) {
                    acc.add(line)
                }
                acc
            }
            .joinToString("\n")
            .trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""&#(\d+);""")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null && code in 1..0x10FFFF) {
                    try {
                        String(Character.toChars(code))
                    } catch (e: Exception) {
                        match.value
                    }
                } else match.value
            }
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null && code in 1..0x10FFFF) {
                    try {
                        String(Character.toChars(code))
                    } catch (e: Exception) {
                        match.value
                    }
                } else match.value
            }
    }

    private fun transliterateForUrl(str: String): String {
        return str.lowercase()
            .replace(Regex("""[^a-z0-9_]+"""), "_")
            .trim('_')
    }

    companion object {
        private const val TAG = "FallbackLyricsScraper"

        /**
         * Extracts full content of HTML tags matching an attribute marker,
         * correctly tracking tag nesting depth so nested tags don't prematurely close the container.
         */
        fun extractBalancedTags(
            html: String,
            attributeMarker: String,
            tagName: String = "div"
        ): List<String> {
            val results = mutableListOf<String>()
            val openPrefix = "<$tagName"
            val closeTag = "</$tagName>"
            var idx = 0

            while (idx < html.length) {
                val markerIdx = html.indexOf(attributeMarker, idx)
                if (markerIdx == -1) break

                val tagStart = html.lastIndexOf(openPrefix, markerIdx)
                if (tagStart == -1) {
                    idx = markerIdx + attributeMarker.length
                    continue
                }

                val contentStart = html.indexOf('>', markerIdx)
                if (contentStart == -1) break
                val innerStart = contentStart + 1

                var depth = 1
                var curr = innerStart

                while (depth > 0 && curr < html.length) {
                    val nextOpen = html.indexOf(openPrefix, curr)
                    val nextClose = html.indexOf(closeTag, curr)

                    if (nextClose == -1) break

                    if (nextOpen != -1 && nextOpen < nextClose) {
                        depth++
                        curr = nextOpen + openPrefix.length
                    } else {
                        depth--
                        if (depth == 0) {
                            results.add(html.substring(innerStart, nextClose))
                        }
                        curr = nextClose + closeTag.length
                    }
                }
                idx = curr.coerceAtLeast(markerIdx + attributeMarker.length)
            }
            return results
        }
    }
}

/**
 * Aggregated lyrics engine that chains:
 * 1. Room Cache (plainLyrics / syncedLyrics from TrackEntity)
 * 2. User Notes
 * 3. LrcLib Provider (Fast API with synced LRC karaoke)
 * 4. Fallback Scrapers (Genius / Amalgama / Textpesni)
 *
 * Automatically saves retrieved lyrics back to Room via onLyricsDiscovered callback.
 */
class AggregatedLyricsProvider(
    private val lrcLibProvider: LyricsProvider? = LrcLibLyricsProvider(),
    private val fallbackScraper: LyricsProvider? = FallbackLyricsScraper(),
    private val offlineAdapter: LyricsProvider = OfflineLyricsAdapter(),
    private val lyricsDao: LyricsDao? = null,
    private val customRuleFactory: ((CustomLyricsRuleEntity) -> LyricsProvider)? = null,
    private val onLyricsDiscovered: (suspend (trackKey: String, plain: String?, synced: String?) -> Unit)? = null
) : LyricsProvider {

    override suspend fun getLyrics(track: TrackEntity): LyricsResult {
        return getLyrics(track, emptySet(), false)
    }

    override suspend fun getLyrics(
        track: TrackEntity,
        rejectedSourceIds: Set<String>,
        forceNetwork: Boolean
    ): LyricsResult {
        // 1. Check local Room cache first (100% offline instant return)
        if (!forceNetwork && LyricsSourceIds.ROOM_CACHE !in rejectedSourceIds) {
            if (!track.syncedLyrics.isNullOrBlank() || !track.plainLyrics.isNullOrBlank()) {
                val textToDisplay = track.syncedLyrics ?: track.plainLyrics ?: ""
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = textToDisplay,
                    source = "Локальный кэш Room",
                    isUserNote = false,
                    syncedLyrics = track.syncedLyrics,
                    plainLyrics = track.plainLyrics,
                    sourceId = LyricsSourceIds.ROOM_CACHE
                )
            }
        }

        // 2. Check user notes
        if (LyricsSourceIds.USER_NOTE !in rejectedSourceIds && track.userNotes.isNotBlank()) {
            val userNoteResult = offlineAdapter.getLyrics(track, rejectedSourceIds, forceNetwork)
            if (userNoteResult.hasLyrics && userNoteResult.sourceId !in rejectedSourceIds) {
                return userNoteResult
            }
        }

        // 2.5. Query custom user/community rules (TASK-LYR-04-C)
        val activeRules = lyricsDao?.getActiveRules().orEmpty()
        for (rule in activeRules) {
            val ruleSourceId = "rule:${rule.id}"
            if (ruleSourceId !in rejectedSourceIds) {
                val factory = customRuleFactory ?: { r: CustomLyricsRuleEntity -> CustomRuleLyricsProvider(r) }
                val ruleProvider = factory(rule)
                val ruleResult = ruleProvider.getLyrics(track, rejectedSourceIds, forceNetwork)
                if (ruleResult.hasLyrics && ruleResult.sourceId !in rejectedSourceIds) {
                    var effectiveResult = if (ruleResult.sourceId.isBlank()) {
                        ruleResult.copy(sourceId = ruleSourceId)
                    } else {
                        ruleResult
                    }
                    if (effectiveResult.chords.isNullOrBlank() && LyricsSourceIds.AMDM !in rejectedSourceIds) {
                        val chords = fetchAmDmChords(track, rejectedSourceIds, forceNetwork)
                        if (!chords.isNullOrBlank()) {
                            effectiveResult = effectiveResult.copy(chords = chords)
                        }
                    }
                    onLyricsDiscovered?.invoke(track.trackKey, effectiveResult.plainLyrics, effectiveResult.syncedLyrics)
                    return effectiveResult
                }
            }
        }

        // 3. Query primary source: LrcLib API (synced & plain lyrics)
        if (lrcLibProvider != null && LyricsSourceIds.LRCLIB !in rejectedSourceIds) {
            val lrcResult = lrcLibProvider.getLyrics(track, rejectedSourceIds, forceNetwork)
            if (lrcResult.hasLyrics && lrcResult.sourceId !in rejectedSourceIds) {
                var effectiveResult = if (lrcResult.sourceId.isBlank()) {
                    lrcResult.copy(sourceId = LyricsSourceIds.LRCLIB)
                } else {
                    lrcResult
                }
                // Hybrid mode: fetch chords from AmDm if not provided by LRCLIB
                if (effectiveResult.chords.isNullOrBlank() && LyricsSourceIds.AMDM !in rejectedSourceIds) {
                    val chords = fetchAmDmChords(track, rejectedSourceIds, forceNetwork)
                    if (!chords.isNullOrBlank()) {
                        effectiveResult = effectiveResult.copy(chords = chords)
                    }
                }
                onLyricsDiscovered?.invoke(track.trackKey, effectiveResult.plainLyrics, effectiveResult.syncedLyrics)
                return effectiveResult
            }
        }

        // 4. Query fallback scrapers
        if (fallbackScraper != null) {
            val fallbackResult = fallbackScraper.getLyrics(track, rejectedSourceIds, forceNetwork)
            if (fallbackResult.hasLyrics && fallbackResult.sourceId !in rejectedSourceIds && (fallbackResult.sourceId.isNotBlank() || LyricsSourceIds.AMDM !in rejectedSourceIds)) {
                var effectiveResult = if (fallbackResult.sourceId.isBlank()) {
                    fallbackResult.copy(sourceId = LyricsSourceIds.AMDM)
                } else {
                    fallbackResult
                }
                if (effectiveResult.chords.isNullOrBlank() && effectiveResult.sourceId != LyricsSourceIds.AMDM && LyricsSourceIds.AMDM !in rejectedSourceIds) {
                    val chords = fetchAmDmChords(track, rejectedSourceIds, forceNetwork)
                    if (!chords.isNullOrBlank()) {
                        effectiveResult = effectiveResult.copy(chords = chords)
                    }
                }
                onLyricsDiscovered?.invoke(track.trackKey, effectiveResult.plainLyrics, effectiveResult.syncedLyrics)
                return effectiveResult
            }
        }

        // 5. Final fallback: offline helper
        if (LyricsSourceIds.USER_NOTE !in rejectedSourceIds) {
            return offlineAdapter.getLyrics(track, rejectedSourceIds, forceNetwork)
        }

        return LyricsResult(
            hasLyrics = false,
            lyricsText = "Текст не найден в доступных базах",
            source = "Система",
            sourceId = LyricsSourceIds.NONE
        )
    }

    private suspend fun fetchAmDmChords(
        track: TrackEntity,
        rejectedSourceIds: Set<String>,
        forceNetwork: Boolean
    ): String? {
        if (LyricsSourceIds.AMDM in rejectedSourceIds || fallbackScraper == null) return null
        return try {
            if (fallbackScraper is FallbackLyricsScraper) {
                val cleanArtist = LrcLibLyricsProvider.cleanArtistName(track.artist)
                val cleanTitle = LrcLibLyricsProvider.cleanTrackName(track.title)
                fallbackScraper.scrapeAmDmDetails(cleanArtist, cleanTitle)?.chords
            } else {
                val res = fallbackScraper.getLyrics(track, rejectedSourceIds, forceNetwork)
                res.chords
            }
        } catch (e: Exception) {
            Timber.w(e, "Error fetching fallback AmDm chords for %s", track.title)
            null
        }
    }
}

/**
 * Lightweight zero-dependency JSON parser helper for strings and objects.
 * Guarantees correct execution on both Android ART and JVM unit test runners.
 */
internal object JsonHelper {

    fun extractStringField(json: String, fieldName: String): String? {
        val pattern = Regex(""""$fieldName"\s*:\s*("(?:\\.|[^"\\])*"|null)""")
        val match = pattern.find(json) ?: return null
        val rawValue = match.groupValues[1]
        if (rawValue == "null") return null

        // Unescape standard JSON escape sequences
        val unquoted = rawValue.substring(1, rawValue.length - 1)
        return unquoted
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace(Regex("""\\u([0-9a-fA-F]{4})""")) { m ->
                m.groupValues[1].toInt(16).toChar().toString()
            }
    }

    fun extractJsonArrayObjects(json: String): List<String> {
        val list = mutableListOf<String>()
        var depth = 0
        var inQuotes = false
        var isEscaped = false
        var startIdx = -1

        for (i in json.indices) {
            val c = json[i]
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
                continue
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) startIdx = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && startIdx != -1) {
                        list.add(json.substring(startIdx, i + 1))
                        startIdx = -1
                    }
                }
            }
        }
        return list
    }
}
