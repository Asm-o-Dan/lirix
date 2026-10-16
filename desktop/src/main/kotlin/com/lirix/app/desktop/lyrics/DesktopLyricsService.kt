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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
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
                val knownLyricsForVerification = foundLrc ?: foundPlain
                for (pair in searchPairs) {
                    val (art, tit) = pair
                    if (art.isNotBlank() && tit.isNotBlank()) {
                        val chordsHit = fetchChords(art, tit, knownLyricsForVerification)
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

    internal fun cleanNoise(str: String): String {
        return str
            .replace(Regex("""(?i)\s*[\(\[](official\s*(music\s*)?video|video|audio|lyrics|hd|4k|remix|clip|live)[\)\]]"""), "")
            .replace(Regex("""(?i)\s*-\s*(youtube(\s*music)?|spotify|яндекс\s*музыка)\s*$"""), "")
            .trim()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private data class LrcLibDto(val syncedLyrics: String?, val plainLyrics: String?)

    private suspend fun fetchFromLrcLib(artist: String, title: String): LrcLibDto? = withContext(Dispatchers.IO) {
        if (artist.isBlank() || title.isBlank()) return@withContext null
        try {
            val encArt = URLEncoder.encode(artist, "UTF-8")
            val encTit = URLEncoder.encode(title, "UTF-8")
            val url = "https://lrclib.net/api/get?artist_name=$encArt&track_name=$encTit"
            val response = httpGet(url) ?: return@withContext null

            val root = json.parseToJsonElement(response)
            if (root is JsonObject) {
                val synced = root["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                val plain = root["plainLyrics"]?.jsonPrimitive?.contentOrNull

                if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
                    return@withContext LrcLibDto(synced, plain)
                }
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

            val root = json.parseToJsonElement(response)
            if (root is JsonArray) {
                for (item in root) {
                    if (item is JsonObject) {
                        val synced = item["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                        val plain = item["plainLyrics"]?.jsonPrimitive?.contentOrNull
                        if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
                            return@withContext LrcLibDto(synced, plain)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logDebug(TAG, "LrcLib query search failed: ${e.message}")
        }
        null
    }

    private suspend fun fetchChords(
        artist: String,
        title: String,
        knownLyrics: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val queries = buildList {
                val combined = "$artist $title".trim()
                if (combined.isNotBlank()) add(combined)
                val cleanT = cleanNoise(title)
                if (cleanT.isNotBlank() && cleanT != combined) add(cleanT)
            }.distinct()

            // 1. AmDm.ru (search with artist+title, fallback to title)
            for (q in queries) {
                val enc = URLEncoder.encode(q, "UTF-8")
                val amdmHtml = httpGet("https://amdm.ru/search/?q=$enc")
                if (!amdmHtml.isNullOrBlank()) {
                    val songUrl = extractAmDmSongUrl(amdmHtml, expectedTitle = title, expectedArtist = artist)
                    if (songUrl != null) {
                        val pageHtml = httpGet(songUrl)
                        if (pageHtml != null) {
                            val chords = AmDmChordParser.parseAmDmHtml(pageHtml)
                            if (!chords.isNullOrBlank()) {
                                if (isChordsPlausible(chords, expectedArtist = artist, expectedTitle = title, knownLyrics = knownLyrics, candidateUrl = songUrl)) {
                                    logInfo(TAG, "AmDm chords verified for '$artist' - '$title' via query '$q' ($songUrl)")
                                    return@withContext chords
                                } else {
                                    logWarn(TAG, "AmDm chords rejected: content does not match song '$artist' - '$title' ($songUrl)")
                                }
                            }
                        }
                    }
                }
            }

            // 2. MyChords.net (search with artist+title, fallback to title)
            for (q in queries) {
                val enc = URLEncoder.encode(q, "UTF-8")
                val myChordsJson = httpGetWithHeaders(
                    "https://mychords.net/ru/ajax/autocomplete?q=$enc",
                    mapOf("X-Requested-With" to "XMLHttpRequest")
                )
                if (!myChordsJson.isNullOrBlank()) {
                    val targetUrl = extractMyChordsSongUrl(myChordsJson, expectedTitle = title, expectedArtist = artist)
                    if (targetUrl != null) {
                        val pageHtml = httpGet(targetUrl)
                        if (pageHtml != null) {
                            val parsed = MyChordsParser.parseMyChordsHtml(pageHtml)
                            val chords = parsed?.chords
                            if (!chords.isNullOrBlank()) {
                                if (isChordsPlausible(chords, expectedArtist = artist, expectedTitle = title, knownLyrics = knownLyrics, candidateUrl = targetUrl)) {
                                    logInfo(TAG, "MyChords chords verified for '$artist' - '$title' via query '$q' ($targetUrl)")
                                    return@withContext chords
                                } else {
                                    logWarn(TAG, "MyChords chords rejected: content does not match song '$artist' - '$title' ($targetUrl)")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logDebug(TAG, "Chords search failed: ${e.message}")
        }
        null
    }

    internal fun isChordsPlausible(
        chords: String,
        expectedArtist: String,
        expectedTitle: String,
        knownLyrics: String?,
        candidateUrl: String
    ): Boolean {
        // If known lyrics are available, verify that chord sheet lyrics actually match known lyrics!
        if (!knownLyrics.isNullOrBlank()) {
            return verifyChordsAgainstLyrics(chords, knownLyrics)
        }

        // If known lyrics are not available, candidate must correlate with expected artist or title
        val normExpectedArtist = cleanNoise(expectedArtist).lowercase()
        val expArtistTokens = significantTokens(normExpectedArtist)
        val normCandidate = candidateUrl.lowercase() + " " + chords.take(400).lowercase()
        val candTokens = significantTokens(normCandidate)

        if (expArtistTokens.isNotEmpty() && expArtistTokens.any { it in candTokens }) {
            return true
        }

        val normExpectedTitle = cleanNoise(expectedTitle).lowercase()
        val expTitleTokens = significantTokens(normExpectedTitle)
        return expTitleTokens.isNotEmpty() && expTitleTokens.all { it in candTokens }
    }

    internal fun verifyChordsAgainstLyrics(chordsText: String, knownLyrics: String): Boolean {
        val normChords = chordsText.lowercase().replace(Regex("""[^\p{L}\p{Nd}\s]+"""), " ")
        val lines = knownLyrics.lines()
            .map { it.replace(Regex("""\[\d+:\d+(?:\.\d+)?\]"""), "").trim() }
            .filter { it.length >= 8 }

        if (lines.isEmpty()) return true

        var matchCount = 0
        for (line in lines) {
            val tokens = line.lowercase()
                .replace(Regex("""[^\p{L}\p{Nd}\s]+"""), " ")
                .split(Regex("""\s+"""))
                .filter { it.length >= 3 && it !in STOP_WORDS }

            if (tokens.size >= 2) {
                val phrase = tokens.take(2).joinToString(" ")
                if (normChords.contains(phrase)) {
                    matchCount++
                    if (matchCount >= 2) return true
                }
            }
        }
        return matchCount >= 1
    }

    internal fun extractAmDmSongUrl(searchHtml: String, expectedTitle: String, expectedArtist: String): String? {
        // 1. Strictly isolate search results table (never look outside table.items!)
        val itemsTable = Regex("""(?s)<table[^>]*class=["'][^"']*items[^"']*["'][^>]*>(.*?)</table>""")
            .find(searchHtml)?.groupValues?.get(1) ?: return null

        val rowMatches = Regex("""(?s)<td[^>]*>(.*?)</td>""")
            .findAll(itemsTable)

        var bestFallbackUrl: String? = null

        for (match in rowMatches) {
            val tdContent = match.groupValues[1]
            val links = Regex("""<a\s+[^>]*href=["']((?:https?://[^/]*amdm\.ru)?/akkordi/[^"']+)["'][^>]*>(.*?)</a>""")
                .findAll(tdContent)
                .toList()
            if (links.isEmpty()) continue

            // Song link is usually the one with song ID or the second link in the pair (artist link - song link)
            val songLink = links.find {
                it.groupValues[1].contains(Regex("""/akkordi/[^/]+/\d+/|/akkordi/[^/]+/[^/]+/?$""")) && it != links.firstOrNull()
            } ?: if (links.size > 1) links.last() else links.first()

            val candidateUrl = songLink.groupValues[1]
            val candidateTitle = cleanHtml(songLink.groupValues[2])
            val candidateArtist = if (links.size > 1 && songLink != links.first()) cleanHtml(links.first().groupValues[2]) else ""

            val fullUrl = if (candidateUrl.startsWith("http")) candidateUrl else "https://amdm.ru$candidateUrl"

            val titleMatch = matchesTitle(candidateTitle, expectedTitle)
            val artistMatch = matchesArtist(candidateArtist, expectedArtist)

            if (titleMatch && artistMatch) {
                // High confidence match (both artist and title)
                return fullUrl
            } else if (titleMatch && bestFallbackUrl == null) {
                // Title matches, store as potential fallback
                bestFallbackUrl = fullUrl
            }
        }
        return bestFallbackUrl
    }

    internal fun extractMyChordsSongUrl(jsonStr: String, expectedTitle: String, expectedArtist: String): String? {
        try {
            val root = json.parseToJsonElement(jsonStr)
            val items: List<JsonObject> = when (root) {
                is JsonArray -> root.filterIsInstance<JsonObject>()
                is JsonObject -> {
                    (root["suggestions"] as? JsonArray)?.filterIsInstance<JsonObject>()
                        ?: listOf(root)
                }
                else -> emptyList()
            }

            var bestFallbackUrl: String? = null

            for (item in items) {
                val candidateValue = item["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val url = item["url"]?.jsonPrimitive?.contentOrNull
                    ?: (item["data"] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: continue

                if (!url.contains("/ru/") || !url.endsWith(".html")) continue

                val fullUrl = if (url.startsWith("http")) url else "https://mychords.net$url"
                val dashIdx = candidateValue.indexOf(" - ")
                val (candArtist, candTitle) = if (dashIdx > 0) {
                    candidateValue.substring(0, dashIdx).trim() to candidateValue.substring(dashIdx + 3).trim()
                } else {
                    "" to candidateValue
                }

                val matchesT = matchesTitle(candTitle, expectedTitle)
                val matchesA = if (candArtist.isNotBlank()) matchesArtist(candArtist, expectedArtist) else matchesArtist(candidateValue, expectedArtist)

                if (matchesT && matchesA) {
                    return fullUrl
                } else if (matchesT && bestFallbackUrl == null) {
                    bestFallbackUrl = fullUrl
                }
            }
            return bestFallbackUrl
        } catch (_: Exception) {
            return null
        }
    }

    internal fun significantTokens(str: String): Set<String> {
        return str.lowercase()
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()
    }

    internal fun matchesTitle(candidateTitle: String, expectedTitle: String): Boolean {
        val cleanCand = cleanNoise(candidateTitle).trim().lowercase()
        val cleanExp = cleanNoise(expectedTitle).trim().lowercase()
        if (cleanCand == cleanExp) return true
        if (cleanCand.contains(cleanExp) || cleanExp.contains(cleanCand)) return true

        val expTokens = significantTokens(cleanExp)
        val candTokens = significantTokens(cleanCand)

        if (expTokens.isEmpty() || candTokens.isEmpty()) {
            return cleanCand.contains(cleanExp) || cleanExp.contains(cleanCand)
        }

        val common = expTokens.intersect(candTokens)
        val minSize = minOf(expTokens.size, candTokens.size)
        return if (minSize == 1) {
            common.size >= 1
        } else {
            (common.size.toDouble() / minSize) >= 0.7
        }
    }

    internal fun matchesArtist(candidateArtist: String, expectedArtist: String): Boolean {
        val cleanCand = cleanNoise(candidateArtist).trim().lowercase()
        val cleanExp = cleanNoise(expectedArtist).trim().lowercase()
        if (cleanCand.isBlank() || cleanExp.isBlank()) return false
        if (cleanCand == cleanExp || cleanCand.contains(cleanExp) || cleanExp.contains(cleanCand)) return true

        val expTokens = significantTokens(cleanExp)
        val candTokens = significantTokens(cleanCand)
        if (expTokens.isEmpty() || candTokens.isEmpty()) return false

        val common = expTokens.intersect(candTokens)
        val minSize = minOf(expTokens.size, candTokens.size)
        return if (minSize == 1) {
            common.size >= 1
        } else {
            (common.size.toDouble() / minSize) >= 0.5
        }
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
            val url = URI.create(urlStr).toURL()
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

    companion object {
        private const val TAG = "DesktopLyricsService"

        internal val STOP_WORDS = setOf(
            // EN
            "the", "a", "an", "and", "or", "in", "on", "at", "to", "for", "of", "with", "by", "from",
            "is", "are", "was", "were", "it", "its", "as", "be", "this", "that", "my", "your", "his", "her",
            "me", "you", "them", "we", "us", "our", "so", "but", "not", "too",
            // RU
            "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то", "все", "она",
            "так", "его", "но", "да", "ты", "к", "ко", "у", "же", "вы", "за", "бы", "по", "только",
            "ее", "мне", "было", "вот", "от", "меня", "еще", "о", "об", "обо", "из", "ему", "теперь",
            "когда", "даже", "ну", "вдруг", "ли", "если", "уже", "или", "ни", "быть", "был", "него",
            "до", "вас", "там", "потом", "себя", "ничего", "ей", "может", "они", "тут", "где", "есть",
            "надо", "ней", "для", "мы", "тебя", "их", "чем", "была", "сам", "чтоб", "без", "раз",
            "тоже", "себе", "под", "будет", "ж", "тогда", "кто", "этот", "того", "этого", "какой",
            "здесь", "этом", "один", "почти", "мой", "про", "всего"
        )
    }
}
