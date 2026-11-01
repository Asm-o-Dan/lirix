package com.lirix.app.feature.music

import com.lirix.app.domain.Event

/**
 * Parsed music metadata model.
 * Spec: TASK-ING-02 / .sdd/specs/media-ingress/overview.md#parseTrackMetadata (v1)
 */
data class ParsedTrackInfo(
    val title: String,
    val artist: String,
    val album: String = ""
)

/**
 * Normalization and extraction of music metadata from notifications and media sessions.
 * Cleans player suffixes (- Spotify, - YouTube), removes video/remix tags,
 * and handles "Artist - Title" splitting and fallbacks.
 */
object MusicTrackParser {

    private val DASH_SEPARATORS = listOf(" — ", " – ", " - ", " // ", " | ", " • ", " ~ ", " :: ", " : ")

    private val PLAYER_SUFFIX_REGEX = Regex(
        """\s*-\s*(spotify|yandex\s*music|яндекс\s*музыка|youtube(\s*music)?|vk(\s*музыка)?|apple\s*music|deezer|aimp|soundcloud)\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val TAG_CLEAN_REGEX = Regex(
        """\s*[\(\[](official\s*(music\s*)?video(\s*starring[^\)\]]*)?|official\s*audio|remix|lyrics|lyric\s*video|audio|hd|4k(\s*60fps)?|clip|visual|visualizer|live|teaser|clip\s*officiel|video\s*clip)[\)\]]\s*""",
        RegexOption.IGNORE_CASE
    )

    private val GENERIC_BRACKET_TAG_REGEX = Regex(
        """\s*[\(\[](official\s*[^)\]]*|video\s*clip|clip\s*officiel|visualizer?|4k|hd|hq|live\s*at[^)\]]*|live\s*in[^)\]]*)[\)\]]\s*""",
        RegexOption.IGNORE_CASE
    )

    private val CHANNEL_SUFFIX_REGEX = Regex(
        """(?i)\s*[-—–]?\s*(vevo|official|music|topic|channel|records|tv|media|entertainment|studios?|productions?|label|audio|video|hq|hd)$"""
    )

    private val CHANNEL_KEYWORD_REGEX = Regex(
        """(?i)\b(vevo|topic|records|studios|productions?|entertainment|channel|official|soundtrack)\b"""
    )

    private val IGNORED_STRINGS = setOf(
        "яндекс музыка", "яндекс.музыка", "yandex music", "yandex.music",
        "spotify", "vk музыка", "vk music", "вконтакте", "vk",
        "youtube music", "zvuk", "звук", "apple music", "deezer",
        "плеер", "player", "музыка", "music", "playing", "paused",
        "воспроизведение", "пауза", "сейчас играет", "новинка",
        "soundtrack", "радио", "radio", "audio", "аудио"
    )

    /**
     * Extracts and cleans music track metadata.
     * Guaranteed not to throw exceptions.
     */
    fun parseTrackMetadata(
        mediaTrack: String?,
        mediaArtist: String?,
        title: String?,
        text: String?
    ): ParsedTrackInfo {
        return runCatching {
            val rawMediaTrack = PLAYER_SUFFIX_REGEX.replace(mediaTrack?.trim().orEmpty(), "").trim()
            val rawMediaArtist = mediaArtist?.trim().orEmpty()
            val rawTitle = PLAYER_SUFFIX_REGEX.replace(title?.trim().orEmpty(), "").trim()
            val rawText = text?.trim().orEmpty()

            var candidateArtist = rawMediaArtist
            var candidateTitle = if (rawTitle.isNotBlank()) rawTitle else rawMediaTrack
            var candidateAlbum = ""

            // 1. If mediaArtist is provided and valid
            if (candidateArtist.isNotBlank() && !isIgnored(candidateArtist)) {
                val split = splitByDash(candidateTitle) ?: (if (rawMediaTrack.isNotBlank() && rawMediaTrack != candidateTitle) splitByDash(rawMediaTrack) else null)
                if (split != null) {
                    val (first, second) = split
                    val cleanFirst = cleanTitle(first)
                    val cleanSecond = cleanTitle(second)
                    when {
                        // Case A: "Artist - Title" (Artist matches candidateArtist)
                        cleanFirst.equals(candidateArtist, ignoreCase = true) || first.equals(candidateArtist, ignoreCase = true) -> {
                            candidateArtist = cleanFirst
                            candidateTitle = cleanSecond
                        }
                        // Case B: "Title - Artist" (Artist matches candidateArtist)
                        cleanSecond.equals(candidateArtist, ignoreCase = true) || second.equals(candidateArtist, ignoreCase = true) -> {
                            candidateArtist = cleanSecond
                            candidateTitle = cleanFirst
                        }
                        // Case C: candidateArtist is an acronym or channel for first (e.g. "5FDPVEVO" / "5FDP" for "Five Finger Death Punch", "LinkinParkVEVO" for "Linkin Park")
                        matchesAcronym(candidateArtist, cleanFirst) || matchesChannelName(candidateArtist, cleanFirst) -> {
                            candidateArtist = cleanFirst
                            candidateTitle = cleanSecond
                        }
                        // Case D: candidateArtist is an acronym or channel for second (Title - Artist format)
                        matchesAcronym(candidateArtist, cleanSecond) || matchesChannelName(candidateArtist, cleanSecond) -> {
                            candidateArtist = cleanSecond
                            candidateTitle = cleanFirst
                        }
                        // Case E: candidateArtist is a YouTube channel / uploader / broadcaster (e.g. "Century Media Records", "VEVO", etc.)
                        isLikelyChannelOrNonArtist(candidateArtist) -> {
                            candidateArtist = cleanFirst
                            candidateTitle = cleanSecond
                        }
                    }
                } else {
                    candidateArtist = stripChannelSuffix(candidateArtist)
                }
            } else {
                // 2. Artist is blank or ignored -> try splitting candidateTitle: "Artist - Title"
                val titleSplit = splitByDash(candidateTitle)
                if (titleSplit != null) {
                    candidateArtist = titleSplit.first
                    candidateTitle = titleSplit.second
                } else {
                    // Try splitting rawMediaTrack if distinct
                    val mediaSplit = splitByDash(rawMediaTrack)
                    if (mediaSplit != null) {
                        candidateArtist = mediaSplit.first
                        candidateTitle = mediaSplit.second
                    } else if (rawTitle.isNotBlank() && !isIgnored(rawTitle) && rawText.isNotBlank() && !isIgnored(rawText) && containsDash(rawText)) {
                        // Title is clean track, text is "Artist — Album"
                        val textSplit = splitByDash(rawText)
                        if (textSplit != null) {
                            candidateTitle = rawTitle
                            candidateArtist = textSplit.first
                            candidateAlbum = textSplit.second
                        }
                    } else if (rawText.isNotBlank() && !isIgnored(rawText) && containsDash(rawText)) {
                        // Title might be player name, text has "Artist - Title"
                        val textSplit = splitByDash(rawText)
                        if (textSplit != null) {
                            candidateArtist = textSplit.first
                            candidateTitle = textSplit.second
                        }
                    } else if (rawTitle.isNotBlank() && rawText.isNotBlank() && !isIgnored(rawText)) {
                        // MediaStyle standard: title = Title, text = Artist
                        candidateTitle = rawTitle
                        candidateArtist = rawText
                    }
                }
            }

            // Clean title of player suffixes and tags
            val cleanedTitle = cleanTitle(candidateTitle)
            val cleanedArtist = candidateArtist.trim()

            // 3. Fallback checks
            if (cleanedTitle.isBlank() && rawMediaTrack.isBlank() && rawTitle.isBlank()) {
                return ParsedTrackInfo(title = "", artist = "")
            }

            if (cleanedTitle.isNotBlank()) {
                val finalArtist = if (cleanedArtist.isBlank() || isIgnored(cleanedArtist)) {
                    "Unknown Artist"
                } else {
                    cleanedArtist
                }
                return ParsedTrackInfo(title = cleanedTitle, artist = finalArtist, album = candidateAlbum)
            }

            ParsedTrackInfo(title = "", artist = "")
        }.getOrDefault(ParsedTrackInfo(title = "", artist = ""))
    }

    /**
     * Overload for Event domain object.
     */
    fun parse(event: Event): ParsedTrackInfo {
        return parseTrackMetadata(
            mediaTrack = event.mediaTrack,
            mediaArtist = event.mediaArtist,
            title = event.title,
            text = event.text
        )
    }

    /**
     * Backward-compatible parse overload.
     */
    fun parse(
        mediaTrack: String? = null,
        mediaArtist: String? = null,
        title: String? = null,
        text: String? = null
    ): ParsedTrackInfo = parseTrackMetadata(mediaTrack, mediaArtist, title, text)

    fun cleanTitle(raw: String): String {
        var t = raw.trim()
        var changed = true
        var passes = 0
        while (changed && passes < 4) {
            val prev = t
            t = PLAYER_SUFFIX_REGEX.replace(t, "").trim()
            t = TAG_CLEAN_REGEX.replace(t, "").trim()
            t = GENERIC_BRACKET_TAG_REGEX.replace(t, "").trim()
            changed = (t != prev)
            passes++
        }
        return t
    }

    private fun splitByDash(str: String): Pair<String, String>? {
        for (sep in DASH_SEPARATORS) {
            val idx = str.indexOf(sep)
            if (idx > 0 && idx + sep.length < str.length) {
                val left = str.substring(0, idx).trim()
                val right = str.substring(idx + sep.length).trim()
                if (left.isNotBlank() && right.isNotBlank()) {
                    return Pair(left, right)
                }
            }
        }
        val match = Regex("""\s+[-—–]\s*|\s*[-—–]\s+""").find(str)
        if (match != null && match.range.first > 0 && match.range.last < str.length - 1) {
            val left = str.substring(0, match.range.first).trim()
            val right = str.substring(match.range.last + 1).trim()
            if (left.isNotBlank() && right.isNotBlank()) {
                return Pair(left, right)
            }
        }
        return null
    }

    fun stripChannelSuffix(artist: String): String {
        var a = artist.trim()
        val stripped = CHANNEL_SUFFIX_REGEX.replace(a, "").trim()
        if (stripped.isNotBlank()) {
            a = stripped
        }
        if (a.length >= 4 && !a.contains(" ") && a.any { it.isUpperCase() } && a.any { it.isLowerCase() }) {
            val withSpaces = a.replace(Regex("""(?<=[a-z])(?=[A-Z])|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])"""), " ")
            if (withSpaces.contains(" ")) {
                return withSpaces
            }
        }
        return a
    }

    fun isLikelyChannelOrNonArtist(artist: String): Boolean {
        val lower = artist.trim().lowercase()
        if (isIgnored(lower)) return true
        if (CHANNEL_SUFFIX_REGEX.containsMatchIn(lower) || CHANNEL_KEYWORD_REGEX.containsMatchIn(lower)) return true
        return false
    }

    fun matchesAcronym(acronymCandidate: String, phrase: String): Boolean {
        val cleanAcronym = stripChannelSuffix(acronymCandidate)
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .trim()
            .lowercase()
        if (cleanAcronym.length < 2) return false

        val words = phrase.trim()
            .replace(Regex("""[^\p{L}\p{Nd}\s]+"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
        if (words.size < 2) return false

        val wordInitials = words.map { word ->
            val firstChar = word.first().lowercaseChar()
            when (word.lowercase()) {
                "five" -> listOf('5', 'f')
                "one" -> listOf('1', 'o')
                "two" -> listOf('2', 't')
                "three" -> listOf('3', 't')
                "four" -> listOf('4', 'f')
                "six" -> listOf('6', 's')
                "seven" -> listOf('7', 's')
                "eight" -> listOf('8', 'e')
                "nine" -> listOf('9', 'n')
                "ten" -> listOf('1', 't')
                else -> listOf(firstChar)
            }
        }

        var combinations = listOf("")
        for (options in wordInitials) {
            combinations = combinations.flatMap { prefix -> options.map { opt -> "$prefix$opt" } }
        }

        return combinations.any { it.equals(cleanAcronym, ignoreCase = true) }
    }

    fun matchesChannelName(channel: String, artist: String): Boolean {
        val cleanChan = stripChannelSuffix(channel)
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .lowercase()
        val cleanArt = artist
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .lowercase()
        return cleanChan.isNotBlank() && cleanArt.isNotBlank() &&
            (cleanChan == cleanArt || cleanChan.contains(cleanArt) || cleanArt.contains(cleanChan))
    }

    private fun isSubtitleOrPart(str: String): Boolean {
        val lower = str.trim().lowercase()
        return lower.startsWith("part ") || lower.startsWith("pt.") || lower.startsWith("pt ") ||
            lower.startsWith("vol.") || lower.startsWith("vol ") || lower.startsWith("act ") ||
            lower.startsWith("live") || lower.startsWith("remix") || lower.startsWith("acoustic")
    }

    private fun containsDash(str: String): Boolean =
        DASH_SEPARATORS.any { str.contains(it) }

    private fun isIgnored(word: String): Boolean {
        val lower = word.trim().lowercase()
        return IGNORED_STRINGS.contains(lower) ||
            IGNORED_STRINGS.any { lower.startsWith(it) && lower.length <= it.length + 3 }
    }

    fun computeTrackKey(title: String, artist: String, album: String = ""): String {
        val normalized = "${title.trim().lowercase()}|${artist.trim().lowercase()}|${album.trim().lowercase()}"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Top-level signature per TASK-ING-02 contract specification.
 */
fun parseTrackMetadata(
    mediaTrack: String?,
    mediaArtist: String?,
    title: String?,
    text: String?
): ParsedTrackInfo = MusicTrackParser.parseTrackMetadata(mediaTrack, mediaArtist, title, text)
