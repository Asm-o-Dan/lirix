package com.lirix.app.desktop.lyrics

import com.lirix.app.feature.lyrics.AmDmChordParser
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.feature.lyrics.MyChordsParser
import com.lirix.app.feature.music.MusicTrackParser
import com.lirix.app.platform.logDebug
import com.lirix.app.platform.logError
import com.lirix.app.platform.logInfo
import com.lirix.app.platform.logWarn
import com.lirix.app.ui.player.LrcLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class DesktopLyricsState(
    val isLoading: Boolean = false,
    val trackKey: String = "",
    val displayTitle: String = "",
    val displayArtist: String = "",
    val lrcLines: List<LrcLine> = emptyList(),
    val plainLyrics: String? = null,
    val chords: String? = null,
    val source: String? = null,
    val hasLyrics: Boolean = false,
    val errorMessage: String? = null
)

class DesktopLyricsService(
    private val scope: CoroutineScope
) {
    private val _lyricsState = MutableStateFlow(DesktopLyricsState())
    val lyricsState: StateFlow<DesktopLyricsState> = _lyricsState.asStateFlow()

    private val memoryCache = ConcurrentHashMap<String, DesktopLyricsState>()
    private var activeJob: Job? = null
    private var currentTrackKey: String = ""

    private val diskCacheDir: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + File.separator + ".lirix")
        File(base, "Lirix" + File.separator + "cache" + File.separator + "lyrics").apply {
            mkdirs()
        }
    }

    fun onTrackChanged(rawTitle: String?, rawArtist: String?, album: String? = null) {
        val title = rawTitle?.trim().orEmpty()
        val artist = rawArtist?.trim().orEmpty()

        if (title.isBlank()) {
            activeJob?.cancel()
            _lyricsState.value = DesktopLyricsState()
            currentTrackKey = ""
            return
        }

        // Clean and detect candidate artist/title (e.g. for YouTube titles "Artist - Track")
        val parsed = MusicTrackParser.parseTrackMetadata(title, artist, title, artist)
        val cleanTitle = if (parsed.title.isNotBlank()) parsed.title else title
        val cleanArtist = if (parsed.artist.isNotBlank() && parsed.artist != "Unknown Artist") parsed.artist else artist

        val trackKey = MusicTrackParser.computeTrackKey(cleanTitle, cleanArtist, album.orEmpty())
        if (trackKey == currentTrackKey && _lyricsState.value.hasLyrics) {
            return
        }

        currentTrackKey = trackKey

        // Check memory cache
        memoryCache[trackKey]?.let { cached ->
            logInfo(TAG, "Loaded lyrics from memory cache for $cleanArtist - $cleanTitle")
            _lyricsState.value = cached
            return
        }

        activeJob?.cancel()
        _lyricsState.value = DesktopLyricsState(
            isLoading = true,
            trackKey = trackKey,
            displayTitle = cleanTitle,
            displayArtist = cleanArtist
        )

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                // Check disk cache first
                val fromDisk = loadFromDiskCache(trackKey, cleanTitle, cleanArtist)
                if (fromDisk != null) {
                    memoryCache[trackKey] = fromDisk
                    _lyricsState.value = fromDisk
                    logInfo(TAG, "Loaded lyrics from disk cache for $cleanArtist - $cleanTitle")
                    return@launch
                }

                logInfo(TAG, "Searching online lyrics for: '$cleanArtist' - '$cleanTitle' (raw title: '$title')")

                // Generate smart search pairs
                val searchPairs = buildSearchCandidates(title, artist, cleanTitle, cleanArtist)

                var foundLrc: String? = null
                var foundPlain: String? = null
                var foundSource: String? = null
                var foundChords: String? = null

                // 1. Query LrcLib
                for (pair in searchPairs) {
                    val (art, tit) = pair
                    val lrcLibResult = fetchFromLrcLib(art, tit)
                    if (lrcLibResult != null) {
                        foundLrc = lrcLibResult.syncedLyrics
                        foundPlain = lrcLibResult.plainLyrics
                        foundSource = "LrcLib"
                        logInfo(TAG, "LrcLib match found using '$art' - '$tit'")
                        break
                    }
                }

                // If direct / search pairs didn't hit, try raw search query on LrcLib
                if (foundLrc == null && foundPlain == null) {
                    val rawQueries = listOf(
                        "$cleanArtist $cleanTitle",
                        title,
                        title.replace(Regex("""\s*[-—–]\s*"""), " ")
                    ).distinct()

                    for (query in rawQueries) {
                        val searchHit = searchLrcLibByQuery(query)
                        if (searchHit != null) {
                            foundLrc = searchHit.syncedLyrics
                            foundPlain = searchHit.plainLyrics
                            foundSource = "LrcLib (Search)"
                            logInfo(TAG, "LrcLib search hit with query '$query'")
                            break
                        }
                    }
                }

                // 2. Query Chords (AmDm.ru & MyChords.net)
                for (pair in searchPairs) {
                    val (art, tit) = pair
                    if (art.isNotBlank() && tit.isNotBlank()) {
                        val chordsHit = fetchChords(art, tit)
                        if (chordsHit != null) {
                            foundChords = chordsHit
                            if (foundPlain == null && foundLrc == null) {
                                foundPlain = chordsHit
                                foundSource = "AmDm / MyChords"
                            }
                            logInfo(TAG, "Chords found for '$art' - '$tit'")
                            break
                        }
                    }
                }

                // 3. Fallback Scrapers (Genius / Textpesni) if still no lyrics
                if (foundLrc == null && foundPlain == null) {
                    for (pair in searchPairs) {
                        val (art, tit) = pair
                        val scrapedText = fetchFallbackLyrics(art, tit)
                        if (!scrapedText.isNullOrBlank()) {
                            foundPlain = scrapedText
                            foundSource = "Web Scraper"
                            break
                        }
                    }
                }

                val parsedLrcLines = if (!foundLrc.isNullOrBlank()) {
                    LrcSyncEngine.parseLrcLinesStrict(foundLrc).map {
                        LrcLine(it.timestampMs, it.text)
                    }
                } else if (!foundPlain.isNullOrBlank()) {
                    // Create simulated line items for clean viewing
                    foundPlain.lines().filter { it.isNotBlank() }.mapIndexed { idx, line ->
                        LrcLine(idx * 5000L, line)
                    }
                } else {
                    emptyList()
                }

                val hasContent = parsedLrcLines.isNotEmpty() || !foundPlain.isNullOrBlank() || !foundChords.isNullOrBlank()

                val resultState = DesktopLyricsState(
                    isLoading = false,
                    trackKey = trackKey,
                    displayTitle = cleanTitle,
                    displayArtist = cleanArtist,
                    lrcLines = parsedLrcLines,
                    plainLyrics = foundPlain,
                    chords = foundChords,
                    source = foundSource,
                    hasLyrics = hasContent,
                    errorMessage = if (!hasContent) "Текст не найден в открытых базах" else null
                )

                if (hasContent) {
                    memoryCache[trackKey] = resultState
                    saveToDiskCache(trackKey, foundLrc, foundPlain, foundChords, foundSource)
                }

                _lyricsState.value = resultState
            } catch (e: Exception) {
                logError(TAG, "Error fetching lyrics for $title", e)
                _lyricsState.value = DesktopLyricsState(
                    isLoading = false,
                    trackKey = trackKey,
                    displayTitle = cleanTitle,
                    displayArtist = cleanArtist,
                    hasLyrics = false,
                    errorMessage = "Ошибка поиска текста: ${e.message}"
                )
            }
        }
    }

    private fun buildSearchCandidates(
        rawTitle: String,
        rawArtist: String,
        cleanTitle: String,
        cleanArtist: String
    ): List<Pair<String, String>> {
        val candidates = mutableListOf<Pair<String, String>>()

        // If rawTitle contains "Artist - Title" (common in YouTube and browser players)
        val dashSeparators = listOf(" — ", " – ", " - ", " // ")
        for (sep in dashSeparators) {
            val idx = rawTitle.indexOf(sep)
            if (idx > 0 && idx + sep.length < rawTitle.length) {
                val left = cleanNoise(rawTitle.substring(0, idx))
                val right = cleanNoise(rawTitle.substring(idx + sep.length))
                if (left.isNotBlank() && right.isNotBlank()) {
                    candidates.add(left to right)
                }
            }
        }

        // Standard clean pair
        if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
            candidates.add(cleanArtist to cleanTitle)
        }

        // Raw pair
        if (rawArtist.isNotBlank() && rawTitle.isNotBlank() && (rawArtist != cleanArtist || rawTitle != cleanTitle)) {
            candidates.add(cleanArtist to cleanNoise(rawTitle))
        }

        return candidates.distinct()
    }

    private fun cleanNoise(str: String): String {
        return str
            .replace(Regex("""(?i)\s*[\(\[](official\s*(music\s*)?video|video|audio|lyrics|hd|4k|remix|clip|live)[\)\]]"""), "")
            .replace(Regex("""(?i)\s*-\s*(youtube(\s*music)?|spotify|яндекс\s*музыка)\s*$"""), "")
            .trim()
    }

    private data class LrcLibDto(val syncedLyrics: String?, val plainLyrics: String?)

    private suspend fun fetchFromLrcLib(artist: String, title: String): LrcLibDto? = withContext(Dispatchers.IO) {
        if (artist.isBlank() || title.isBlank()) return@withContext null
        try {
            val encArt = URLEncoder.encode(artist, "UTF-8")
            val encTit = URLEncoder.encode(title, "UTF-8")
            val url = "https://lrclib.net/api/get?artist_name=$encArt&track_name=$encTit"
            val response = httpGet(url) ?: return@withContext null

            val synced = extractJsonField(response, "syncedLyrics")
            val plain = extractJsonField(response, "plainLyrics")

            if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
                return@withContext LrcLibDto(synced, plain)
            }
        } catch (e: Exception) {
            logDebug(TAG, "LrcLib direct fetch failed: ${e.message}")
        }
        null
    }

    private suspend fun searchLrcLibByQuery(query: String): LrcLibDto? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        try {
            val enc = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://lrclib.net/api/search?q=$enc"
            val response = httpGet(url) ?: return@withContext null

            // Search results are an array of JSON objects
            val items = extractJsonArrayItems(response)
            for (item in items) {
                val synced = extractJsonField(item, "syncedLyrics")
                val plain = extractJsonField(item, "plainLyrics")
                if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
                    return@withContext LrcLibDto(synced, plain)
                }
            }
        } catch (e: Exception) {
            logDebug(TAG, "LrcLib query search failed: ${e.message}")
        }
        null
    }

    private suspend fun fetchChords(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "$artist $title".trim()
            val enc = URLEncoder.encode(query, "UTF-8")

            // 1. AmDm.ru
            val amdmHtml = httpGet("https://amdm.ru/search/?q=$enc")
            if (amdmHtml != null) {
                val songUrl = Regex("""<a\s+[^>]*href=["']((?:https?://[^/]*amdm\.ru)?/akkordi/[^"']+)["']""").find(amdmHtml)?.groupValues?.get(1)
                if (songUrl != null) {
                    val fullUrl = if (songUrl.startsWith("http")) songUrl else "https://amdm.ru$songUrl"
                    val pageHtml = httpGet(fullUrl)
                    if (pageHtml != null) {
                        val chords = AmDmChordParser.parseAmDmHtml(pageHtml)
                        if (!chords.isNullOrBlank()) return@withContext chords
                    }
                }
            }

            // 2. MyChords.net
            val myChordsJson = httpGetWithHeaders("https://mychords.net/ru/ajax/autocomplete?q=$enc", mapOf("X-Requested-With" to "XMLHttpRequest"))
            if (myChordsJson != null) {
                val targetUrl = Regex(""""url"\s*:\s*"([^"]+)"""").find(myChordsJson)?.groupValues?.get(1)
                if (targetUrl != null) {
                    val fullUrl = if (targetUrl.startsWith("http")) targetUrl else "https://mychords.net$targetUrl"
                    val pageHtml = httpGet(fullUrl)
                    if (pageHtml != null) {
                        val parsed = MyChordsParser.parseMyChordsHtml(pageHtml)
                        if (parsed?.chords?.isNotBlank() == true) return@withContext parsed.chords
                    }
                }
            }
        } catch (e: Exception) {
            logDebug(TAG, "Chords search failed: ${e.message}")
        }
        null
    }

    private suspend fun fetchFallbackLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode("$artist $title", "UTF-8")
            // Textpesni.com
            val searchHtml = httpGet("https://textpesni.com/search?q=$q")
            if (searchHtml != null) {
                val songPath = Regex("""<a\s+[^>]*href=["'](/song/[^"']+)["']""").find(searchHtml)?.groupValues?.get(1)
                if (songPath != null) {
                    val songHtml = httpGet("https://textpesni.com$songPath")
                    if (songHtml != null) {
                        val rawBlock = Regex("""(?s)<div[^>]*class=["'][^"']*song-text[^"']*["'][^>]*>(.*?)</div>""").find(songHtml)?.groupValues?.get(1)
                        if (!rawBlock.isNullOrBlank()) {
                            return@withContext cleanHtml(rawBlock)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</p>"""), "\n\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            .trim()
    }

    private fun saveToDiskCache(trackKey: String, lrc: String?, plain: String?, chords: String?, source: String?) {
        try {
            val file = File(diskCacheDir, "$trackKey.cache")
            val content = buildString {
                appendLine("[SOURCE]")
                appendLine(source.orEmpty())
                appendLine("[CHORDS]")
                appendLine(chords.orEmpty())
                appendLine("[SYNCED_LRC]")
                appendLine(lrc.orEmpty())
                appendLine("[PLAIN_LYRICS]")
                appendLine(plain.orEmpty())
            }
            file.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            logDebug(TAG, "Failed to save disk cache: ${e.message}")
        }
    }

    private fun loadFromDiskCache(trackKey: String, title: String, artist: String): DesktopLyricsState? {
        try {
            val file = File(diskCacheDir, "$trackKey.cache")
            if (!file.exists() || file.length() == 0L) return null

            val text = file.readText(Charsets.UTF_8)
            val source = text.substringAfter("[SOURCE]\n").substringBefore("\n[CHORDS]").trim()
            val chords = text.substringAfter("[CHORDS]\n").substringBefore("\n[SYNCED_LRC]").trim().ifBlank { null }
            val lrc = text.substringAfter("[SYNCED_LRC]\n").substringBefore("\n[PLAIN_LYRICS]").trim().ifBlank { null }
            val plain = text.substringAfter("[PLAIN_LYRICS]\n").trim().ifBlank { null }

            val lrcLines = if (!lrc.isNullOrBlank()) {
                LrcSyncEngine.parseLrcLinesStrict(lrc).map { LrcLine(it.timestampMs, it.text) }
            } else if (!plain.isNullOrBlank()) {
                plain.lines().filter { it.isNotBlank() }.mapIndexed { idx, line -> LrcLine(idx * 5000L, line) }
            } else {
                emptyList()
            }

            return DesktopLyricsState(
                isLoading = false,
                trackKey = trackKey,
                displayTitle = title,
                displayArtist = artist,
                lrcLines = lrcLines,
                plainLyrics = plain,
                chords = chords,
                source = source,
                hasLyrics = lrcLines.isNotEmpty() || plain != null || chords != null
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun httpGet(urlStr: String): String? = httpGetWithHeaders(urlStr, emptyMap())

    private fun httpGetWithHeaders(urlStr: String, headers: Map<String, String>): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "*/*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    return it.readText()
                }
            }
        } catch (_: Exception) {
            return null
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = Regex(""""$fieldName"\s*:\s*("(?:\\.|[^"\\])*"|null)""")
        val match = pattern.find(json) ?: return null
        val rawValue = match.groupValues[1]
        if (rawValue == "null") return null

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

    private fun extractJsonArrayItems(json: String): List<String> {
        val items = mutableListOf<String>()
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
                        items.add(json.substring(startIdx, i + 1))
                        startIdx = -1
                    }
                }
            }
        }
        return items
    }

    companion object {
        private const val TAG = "DesktopLyricsService"
    }
}
