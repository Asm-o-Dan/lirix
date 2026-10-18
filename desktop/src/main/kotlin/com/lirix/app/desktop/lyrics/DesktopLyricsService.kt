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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import com.lirix.app.desktop.library.DesktopLibraryRepository
import com.lirix.app.storage.LyricsRejectionEntity
import com.lirix.app.storage.LyricsCacheEntity
import kotlinx.coroutines.cancelAndJoin
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
    val errorMessage: String? = null,
    val syncedLyricsLrc: String? = null
)

class DesktopLyricsService(
    private val scope: CoroutineScope,
    private val repository: DesktopLibraryRepository? = null,
    private val cacheDirectory: File? = null,
    private val savedLookup: (suspend (String) -> LyricsCacheEntity?)? = null,
    private val persistLyrics: (suspend (String, String?, String?, String?, String) -> Unit)? = null,
    private val automaticLookup: (suspend (String, String, String, String, Set<String>) -> DesktopLyricsState)? = null
) {
    private val _lyricsState = MutableStateFlow(DesktopLyricsState())
    val lyricsState: StateFlow<DesktopLyricsState> = _lyricsState.asStateFlow()

    private val memoryCache = ConcurrentHashMap<String, DesktopLyricsState>()
    private var activeJob: Job? = null
    private var currentTrackKey: String = ""

    private val diskCacheDir: File by lazy {
        cacheDirectory?.let { return@lazy it.apply { mkdirs() } }
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + File.separator + ".lirix")
        File(base, "Lirix" + File.separator + "cache" + File.separator + "lyrics").apply {
            mkdirs()
        }
    }

    fun onTrackChanged(rawTitle: String?, rawArtist: String?, album: String? = null, forceRefresh: Boolean = false) {
        val title = rawTitle?.trim().orEmpty()
        val artist = rawArtist?.trim().orEmpty()
        if (title.isBlank()) {
            activeJob?.cancel()
            currentTrackKey = ""
            _lyricsState.value = DesktopLyricsState()
            return
        }
        val parsed = MusicTrackParser.parseTrackMetadata(title, artist, title, artist)
        val cleanTitle = parsed.title.ifBlank { title }
        val cleanArtist = parsed.artist.takeIf { it.isNotBlank() && it != "Unknown Artist" } ?: artist
        val trackKey = MusicTrackParser.computeTrackKey(cleanTitle, cleanArtist, album.orEmpty())
        if (!forceRefresh && trackKey == currentTrackKey && _lyricsState.value.hasLyrics) return

        activeJob?.cancel()
        currentTrackKey = trackKey
        if (forceRefresh) memoryCache.remove(trackKey)
        _lyricsState.value = DesktopLyricsState(isLoading = true, trackKey = trackKey,
            displayTitle = cleanTitle, displayArtist = cleanArtist)
        activeJob = scope.launch(Dispatchers.IO) {
            fun isCurrent(): Boolean {
                coroutineContext.ensureActive()
                return currentTrackKey == trackKey
            }
            fun savedState(saved: LyricsCacheEntity) = stateFromSaved(trackKey, cleanTitle, cleanArtist,
                saved.plainLyrics, saved.syncedLyricsLrc, saved.chordsAmDm,
                if (saved.provider == "USER") "Сохранено вами" else saved.provider)
            try {
                val saved = savedLookup?.invoke(trackKey) ?: repository?.savedLyrics(trackKey)
                val rejected = repository?.database?.lyricsDao()?.getRejectedSourceIds(trackKey)
                    .orEmpty().map(::canonicalSourceId).toSet()
                if (!isCurrent()) return@launch
                // Explicit user edits, including clearing, always win over automatic results.
                if (saved?.provider == "USER" || (!forceRefresh && saved != null &&
                    isUsableAutomatic(savedState(saved), rejected))) {
                    _lyricsState.value = savedState(saved!!)
                    return@launch
                }
                if (!forceRefresh) {
                    memoryCache[trackKey]?.takeIf { isUsableAutomatic(it, rejected) }?.let {
                        if (isCurrent()) _lyricsState.value = it
                        return@launch
                    }
                }

                // Save first; a cancelled/stale write must never populate a later cache hit.
                suspend fun acceptAutomatic(candidate: DesktopLyricsState) {
                    if (!isCurrent()) return
                    var effective = candidate.copy(trackKey = trackKey, displayTitle = cleanTitle,
                        displayArtist = cleanArtist, isLoading = false)
                    if (candidate.hasLyrics) {
                        writeLyrics(trackKey, candidate.plainLyrics, candidate.syncedLyricsLrc,
                            candidate.chords, candidate.source ?: "NONE")
                        if (!isCurrent()) return
                        // The repository also protects USER against other automatic callers.
                        val stored = repository?.savedLyrics(trackKey)
                        if (!isCurrent()) return
                        if (stored?.provider == "USER") effective = savedState(stored)
                        memoryCache[trackKey] = effective
                        if (stored?.provider != "USER") saveToDiskCache(trackKey, effective.syncedLyricsLrc,
                            effective.plainLyrics, effective.chords, effective.source)
                    }
                    if (isCurrent()) _lyricsState.value = effective
                }

                for (rule in repository?.database?.lyricsDao()?.getActiveRules().orEmpty()) {
                    if (!isCurrent()) return@launch
                    if ("rule:${rule.id}" in rejected) continue
                    val text = try { DesktopCustomSources.resolve(rule, cleanArtist, cleanTitle) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (!text.isNullOrBlank()) {
                        acceptAutomatic(stateFromSaved(trackKey, cleanTitle, cleanArtist, text, null, null, "rule:${rule.id}"))
                        return@launch
                    }
                }
                val fromDisk = if (!forceRefresh) loadFromDiskCache(trackKey, cleanTitle, cleanArtist)
                    ?.takeIf { isUsableAutomatic(it, rejected) } else null
                if (fromDisk != null) {
                    if (!isCurrent()) return@launch
                    memoryCache[trackKey] = fromDisk
                    _lyricsState.value = fromDisk
                    return@launch
                }
                if (!isCurrent()) return@launch
                val found = automaticLookup?.invoke(title, artist, cleanTitle, cleanArtist, rejected)
                    ?: lookupAutomatic(title, artist, cleanTitle, cleanArtist, rejected)
                if (!isCurrent()) return@launch
                // Normalize injected and production results through the same parser/content check.
                val normalized = stateFromSaved(trackKey, cleanTitle, cleanArtist, found.plainLyrics,
                    found.syncedLyricsLrc, found.chords, found.source ?: "NONE")
                    .copy(errorMessage = found.errorMessage)
                val result = if (isUsableAutomatic(normalized, rejected)) normalized else
                    DesktopLyricsState(trackKey = trackKey, displayTitle = cleanTitle, displayArtist = cleanArtist,
                        errorMessage = found.errorMessage ?: "Текст не найден в открытых базах")
                acceptAutomatic(result)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!isCurrent()) return@launch
                logError(TAG, "Error fetching lyrics for $title", e)
                _lyricsState.value = DesktopLyricsState(trackKey = trackKey, displayTitle = cleanTitle,
                    displayArtist = cleanArtist, errorMessage = "Ошибка поиска текста: ${e.message}")
            }
        }
    }

    suspend fun saveUserLyrics(key: String, title: String, artist: String, plain: String?, lrc: String?, chords: String?) {
        val lookupToCancel = activeJob.takeIf { currentTrackKey == key }
        lookupToCancel?.cancelAndJoin()
        writeLyrics(key, plain, lrc, chords, "USER")
        val state = stateFromSaved(key, title, artist, plain, lrc, chords, "Сохранено вами")
        memoryCache[key] = state
        if (currentTrackKey == key) _lyricsState.value = state
    }

    private suspend fun writeLyrics(key: String, plain: String?, lrc: String?, chords: String?, provider: String) {
        if (persistLyrics != null) persistLyrics.invoke(key, plain, lrc, chords, provider)
        else repository?.saveLyrics(key, plain, lrc, chords, provider)
    }

    fun rejectCurrentSource(title: String, artist: String, album: String) {
        val state = _lyricsState.value
        val id = canonicalSourceId(state.source.orEmpty())
        if (id.isBlank() || id == "USER" || id == "NONE" || state.trackKey.isBlank()) return
        memoryCache.remove(state.trackKey)
        scope.launch {
            withContext(Dispatchers.IO) { repository?.database?.lyricsDao()?.insertRejection(LyricsRejectionEntity(state.trackKey, id)) }
            if (currentTrackKey == state.trackKey) onTrackChanged(title, artist, album, forceRefresh = true)
        }
    }

    private fun canonicalSourceId(source: String): String {
        val name = source.trim()
        return when {
            name.startsWith("rule:") -> name
            name.equals("USER", true) || name == "Сохранено вами" -> "USER"
            name.startsWith("LrcLib", true) || name.equals("builtin:lrclib", true) -> "builtin:lrclib"
            name.equals("AMDM", true) || name.contains("AmDm", true) || name.contains("MyChords", true) -> "builtin:amdm"
            name.equals("Web Scraper", true) || name.equals("builtin:web", true) -> "builtin:web"
            else -> name
        }
    }

    private fun isUsableAutomatic(state: DesktopLyricsState, rejected: Set<String>): Boolean {
        val provider = canonicalSourceId(state.source.orEmpty())
        return state.hasLyrics && provider.isNotBlank() && !provider.equals("NONE", true) && provider !in rejected
    }

    private suspend fun lookupAutomatic(title: String, artist: String, cleanTitle: String, cleanArtist: String, rejected: Set<String>): DesktopLyricsState {
        // Generate smart search pairs
        val searchPairs = buildSearchCandidates(title, artist, cleanTitle, cleanArtist)

        var foundLrc: String? = null
        var foundPlain: String? = null
        var foundSource: String? = null
        var foundChords: String? = null

        // 1. Query LrcLib
        for (pair in searchPairs.takeIf { "builtin:lrclib" !in rejected }.orEmpty()) {
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
        if (foundLrc == null && foundPlain == null && "builtin:lrclib" !in rejected) {
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
        for (pair in searchPairs.takeIf { "builtin:amdm" !in rejected }.orEmpty()) {
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
        if (foundLrc == null && foundPlain == null && "builtin:web" !in rejected) {
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
        } else {
            emptyList()
        }

        val hasContent = parsedLrcLines.isNotEmpty() || !foundPlain.isNullOrBlank() || !foundChords.isNullOrBlank()

        return DesktopLyricsState(
            isLoading = false,
            trackKey = "",
            displayTitle = cleanTitle,
            displayArtist = cleanArtist,
            lrcLines = parsedLrcLines,
            plainLyrics = foundPlain,
            chords = foundChords,
            source = foundSource,
            hasLyrics = hasContent,
            errorMessage = if (!hasContent) "Текст не найден в открытых базах" else null,
            syncedLyricsLrc = foundLrc
        )

    }

    private fun stateFromSaved(key: String, title: String, artist: String, plain: String?, lrc: String?, chords: String?, source: String) =
        DesktopLyricsState(trackKey = key, displayTitle = title, displayArtist = artist,
            lrcLines = lrc?.let { LrcSyncEngine.parseLrcLinesStrict(it).map { line -> LrcLine(line.timestampMs, line.text) } }.orEmpty(),
            plainLyrics = plain, chords = chords, source = source,
            hasLyrics = !plain.isNullOrBlank() || !lrc.isNullOrBlank() || !chords.isNullOrBlank(), syncedLyricsLrc = lrc)

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
                hasLyrics = lrcLines.isNotEmpty() || plain != null || chords != null,
                syncedLyricsLrc = lrc
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
