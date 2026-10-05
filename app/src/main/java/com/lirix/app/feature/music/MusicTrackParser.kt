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

    private val DASH_SEPARATORS = listOf(" — ", " – ", " - ", " // ")

    private val PLAYER_SUFFIX_REGEX = Regex(
        """\s*-\s*(spotify|yandex\s*music|яндекс\s*музыка|youtube(\s*music)?|vk(\s*музыка)?|apple\s*music|deezer|aimp|soundcloud)\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val TAG_CLEAN_REGEX = Regex(
        """\s*[\(\[](official\s*video|remix|lyrics|audio|hd|4k)[\)\]]\s*""",
        RegexOption.IGNORE_CASE
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
            val rawMediaTrack = mediaTrack?.trim().orEmpty()
            val rawMediaArtist = mediaArtist?.trim().orEmpty()
            val rawTitle = title?.trim().orEmpty()
            val rawText = text?.trim().orEmpty()

            var candidateArtist = rawMediaArtist
            var candidateTitle = if (rawTitle.isNotBlank()) rawTitle else rawMediaTrack
            var candidateAlbum = ""

            // 1. If mediaArtist is provided and valid
            if (candidateArtist.isNotBlank() && !isIgnored(candidateArtist)) {
                // Check if candidateTitle redundantly starts with "Artist - Track"
                val split = splitByDash(candidateTitle)
                if (split != null && split.first.equals(candidateArtist, ignoreCase = true)) {
                    candidateTitle = split.second
                } else if (rawMediaTrack.isNotBlank() && rawMediaTrack != candidateTitle) {
                    val mediaSplit = splitByDash(rawMediaTrack)
                    if (mediaSplit != null && mediaSplit.first.equals(candidateArtist, ignoreCase = true)) {
                        candidateTitle = mediaSplit.second
                    }
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

    private fun cleanTitle(raw: String): String {
        var t = raw.trim()
        // Strip player suffixes: " - Spotify", " - YouTube", etc.
        t = PLAYER_SUFFIX_REGEX.replace(t, "").trim()
        // Strip noise tags: "(Official Video)", "[Remix]", etc.
        t = TAG_CLEAN_REGEX.replace(t, "").trim()
        // Secondary pass for player suffixes in case tag was at end
        t = PLAYER_SUFFIX_REGEX.replace(t, "").trim()
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
        return null
    }

    private fun containsDash(str: String): Boolean =
        DASH_SEPARATORS.any { str.contains(it) }

    private fun isIgnored(word: String): Boolean {
        val lower = word.trim().lowercase()
        return IGNORED_STRINGS.contains(lower) ||
            IGNORED_STRINGS.any { lower.startsWith(it) && lower.length <= it.length + 3 }
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
