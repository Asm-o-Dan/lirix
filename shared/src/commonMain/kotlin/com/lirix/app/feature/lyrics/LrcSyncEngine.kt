package com.lirix.app.feature.lyrics

import java.util.Locale

/**
 * Model representing an individual line of song lyrics during the tap-to-sync process.
 *
 * @param index 0-based sequential index of the lyric line.
 * @param text Trimmed line content.
 * @param timestampMs Assigned playback timestamp in milliseconds (null if not yet synced).
 */
data class SyncLineItem(
    val index: Int,
    val text: String,
    val timestampMs: Long? = null
)

/**
 * Synchronized lyric line with playback timestamp in milliseconds.
 */
data class KaraokeLine(
    val timestampMs: Long,
    val text: String
)

/**
 * Core engine for interactive LRC lyric synchronization (Tap-to-Sync Studio).
 *
 * Responsible for:
 * - Parsing raw plain lyrics into indexed line items.
 * - Recording timestamps per line with boundary checks.
 * - Undo/step-back mechanics.
 * - Monotonicity validation across timestamps.
 * - Generating standard-compliant LRC strings with format `[mm:ss.xx] Line`.
 * - Strict parsing of existing LRC content.
 *
 * Spec: TASK-LRC-01 / .sdd/specs/media-ui/lrc_tap_sync_studio.md
 */
object LrcSyncEngine {

    private val GLUED_WORD_CHORD_REGEX = Regex("""([а-яёa-zА-ЯЁA-Z0-9,\.\!\?])([A-H][#b]?(?:m|maj|min|dim|aug|sus|add|\d)+\b)""")
    private val GLUED_CHORD_WORD_REGEX = Regex("""\b([A-H](?:[#b](?:m|maj|min|dim|aug|sus|add|\d)*|(?:m|maj|min|dim|aug|sus|add|\d)+))([А-ЯЁа-яёA-Za-z])""")
    private val BARE_CHORD_CAP_REGEX = Regex("""\b([A-H])([А-ЯЁ])""")
    private val CHORD_INTERNAL_SPACE_REGEX = Regex("""\b([A-H][#b]?)\s+([m\d])\b""")
    private val SECTION_HEADER_SPLIT_REGEX = Regex("""(?<=\S)\s*(\|[^|\n]+\|:?|\[[^\]\n]+\])""", RegexOption.IGNORE_CASE)
    private val SECTION_HEADER_AFTER_REGEX = Regex("""(\|[^|\n]+\|:?|\[[^\]\n]+\])\s*(?=\S)""", RegexOption.IGNORE_CASE)
    private val PUNCTUATION_SPLIT_REGEX = Regex("""([.!?»”])\s+(?=[А-ЯЁA-Z]|(?:[A-H][#b]?))""")

    private const val CHORD_TOKEN = """(?:[A-H][#b]?(?:m|maj|min|dim|aug|sus|add|\d)*)"""
    private const val CHORD_CLUSTER = """(?:$CHORD_TOKEN(?:\s+$CHORD_TOKEN)*)"""
    private const val CAP_START = """(?:[А-ЯЁA-Z](?:\s+[а-яёa-z]+|[а-яёa-z]+))"""

    private val CHORD_BEFORE_CAP_REGEX = Regex("""($CHORD_CLUSTER)\s+($CAP_START)""")
    private val WORD_BEFORE_CHORD_REGEX = Regex("""([а-яёa-z,\.\!\?])\s+($CHORD_CLUSTER)""")

    private val CHORD_ONLY_LINE_REGEX = Regex("""^(?:[A-H][#b]?(?:m|maj|min|dim|aug|sus|add|\d)*(?:\s+|$))+$""")
    private val SECTION_HEADER_LINE_REGEX = Regex("""^(\|[^|\n]+\|:?|\[[^\]\n]+\])$""")

    /**
     * Sanitizes plain lyric text:
     * 1. Unsticks glued chords in both directions (e.g. "войC7" -> "вой C7", "A#A ну" -> "A# А ну").
     * 2. Intelligently splits collapsed single-line or multi-line text at section headers, punctuation,
     *    and chord-preceded poetic lines so that every sung lyric line is on its own row.
     */
    fun sanitizePlainText(rawText: String): String {
        if (rawText.isBlank()) return rawText
        return rawText
            .replace(GLUED_WORD_CHORD_REGEX, "$1 $2")
            .replace(GLUED_CHORD_WORD_REGEX, "$1 $2")
            .replace(BARE_CHORD_CAP_REGEX, "$1 $2")
            .replace(CHORD_INTERNAL_SPACE_REGEX, "$1$2")
            .replace(SECTION_HEADER_SPLIT_REGEX, "\n\n$1\n")
            .replace(SECTION_HEADER_AFTER_REGEX, "$1\n")
            .replace(PUNCTUATION_SPLIT_REGEX, "$1\n")
            .replace(CHORD_BEFORE_CAP_REGEX, "$1\n$2")
            .replace(WORD_BEFORE_CHORD_REGEX, "$1\n$2")
            .replace(Regex("""\r\n"""), "\n")
            .replace(Regex("""\n\s*\n\s*\n+"""), "\n\n")
            .trim()
    }

    /**
     * Splits and sanitizes plain lyric text into [SyncLineItem] entries.
     * Empty lines, chord-only lines, and section markers are omitted so the user
     * only syncs actual vocal lyric lines. Falls back to all lines if no lyrics remain.
     */
    fun parsePlainToSyncLines(plainText: String): List<SyncLineItem> {
        if (plainText.isBlank()) return emptyList()
        val cleaned = sanitizePlainText(plainText)
        val filtered = cleaned.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !CHORD_ONLY_LINE_REGEX.matches(line) &&
                    !SECTION_HEADER_LINE_REGEX.matches(line)
            }
        val targetLines = if (filtered.isNotEmpty()) filtered else cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
        return targetLines.mapIndexed { idx, line ->
            SyncLineItem(index = idx, text = line)
        }
    }

    /**
     * Records the given [timestampMs] for the item at [index], subtracting [reactionLagMs]
     * to compensate for physiological tap latency.
     * Clamps negative timestamps to 0L. Returns an immutable copy of the list.
     */
    fun recordTimestamp(
        lines: List<SyncLineItem>,
        index: Int,
        timestampMs: Long,
        reactionLagMs: Long = 0L
    ): List<SyncLineItem> {
        if (index !in lines.indices) return lines
        val safeMs = maxOf(0L, timestampMs - reactionLagMs)
        return lines.mapIndexed { i, item ->
            if (i == index) item.copy(timestampMs = safeMs) else item
        }
    }

    /**
     * Adjusts the timestamp for the line at [index] by [deltaMs] (e.g. +100ms or -100ms).
     * If the line has no timestamp, it remains unchanged. Clamps to >= 0L.
     */
    fun adjustTimestamp(
        lines: List<SyncLineItem>,
        index: Int,
        deltaMs: Long
    ): List<SyncLineItem> {
        if (index !in lines.indices) return lines
        return lines.mapIndexed { i, item ->
            if (i == index && item.timestampMs != null) {
                item.copy(timestampMs = maxOf(0L, item.timestampMs + deltaMs))
            } else {
                item
            }
        }
    }

    /**
     * Steps back one synchronization step:
     * Clears the timestamp of the last recorded line and returns the updated list
     * alongside the target line index to focus next.
     */
    fun undoTimestamp(
        lines: List<SyncLineItem>,
        currentIndex: Int
    ): Pair<List<SyncLineItem>, Int> {
        if (lines.isEmpty()) return Pair(lines, 0)

        // If currently beyond bounds (all lines marked), undo the last line
        val targetIndex = if (currentIndex >= lines.size) {
            lines.size - 1
        } else if (lines.getOrNull(currentIndex)?.timestampMs != null) {
            currentIndex
        } else {
            (currentIndex - 1).coerceAtLeast(0)
        }

        val updated = lines.mapIndexed { i, item ->
            if (i == targetIndex) item.copy(timestampMs = null) else item
        }

        return Pair(updated, targetIndex)
    }

    /**
     * Formats milliseconds into standard LRC timestamp brackets `[mm:ss.xx]`.
     * `xx` represents hundredths of a second (00-99).
     */
    fun formatTimestamp(ms: Long): String {
        val safeMs = maxOf(0L, ms)
        val minutes = safeMs / 60000
        val seconds = (safeMs % 60000) / 1000
        val hundredths = ((safeMs % 1000) / 10).coerceIn(0, 99)
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
    }

    /**
     * Formats milliseconds into human-readable playback time `mm:ss`.
     */
    fun formatTimeLabel(ms: Long): String {
        val safeMs = maxOf(0L, ms)
        val minutes = safeMs / 60000
        val seconds = (safeMs % 60000) / 1000
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Generates a standard LRC file content string from synchronized line items.
     * Items without timestamps are omitted from the output.
     */
    fun buildLrcString(lines: List<SyncLineItem>): String {
        return lines
            .filter { it.timestampMs != null }
            .joinToString("\n") { item ->
                "${formatTimestamp(item.timestampMs!!)} ${item.text}"
            }
    }

    /**
     * Strict parser for LRC format with millisecond/centisecond precision.
     * Matches standard `[mm:ss.xx] Line` or `[mm:ss.xxx] Line` tags.
     */
    fun parseLrcLinesStrict(rawLrc: String): List<KaraokeLine> {
        if (rawLrc.isBlank()) return emptyList()
        val regex = Regex("""^\[(\d+):(\d{2})(?:[.:](\d{1,3}))?\](.*)$""")
        return rawLrc.lines().mapNotNull { line ->
            val trimmed = line.trim()
            val match = regex.find(trimmed) ?: return@mapNotNull null
            val m = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val s = match.groupValues[2].toLongOrNull() ?: 0L
            val fracStr = match.groupValues[3]
            val ms = when (fracStr.length) {
                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                3 -> fracStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            if (m > (Long.MAX_VALUE - s * 1000L - ms) / 60000L) return@mapNotNull null
            val text = match.groupValues[4].trim()
            if (text.isBlank()) null else KaraokeLine(m * 60000L + s * 1000L + ms, text)
        }.sortedBy { it.timestampMs }
    }

    /**
     * Checks whether all assigned timestamps are monotonically non-decreasing.
     */
    fun isMonotonic(lines: List<SyncLineItem>): Boolean {
        val timed = lines.filter { it.timestampMs != null }
        for (i in 0 until timed.size - 1) {
            if (timed[i].timestampMs!! > timed[i + 1].timestampMs!!) {
                return false
            }
        }
        return true
    }

    /**
     * Identifies line indices that violate chronological ordering.
     */
    fun findNonMonotonicIndices(lines: List<SyncLineItem>): List<Int> {
        val nonMonotonic = mutableListOf<Int>()
        var lastTs = -1L
        lines.forEachIndexed { idx, item ->
            val ts = item.timestampMs
            if (ts != null) {
                if (ts < lastTs) {
                    nonMonotonic.add(idx)
                } else {
                    lastTs = ts
                }
            }
        }
        return nonMonotonic
    }

    /**
     * Sanitizes timestamps to guarantee monotonic progression, adding [minGapMs]
     * if a timestamp would otherwise collide or regress.
     */
    fun validateAndSanitize(
        lines: List<SyncLineItem>,
        minGapMs: Long = 100L
    ): List<SyncLineItem> {
        var lastTs = 0L
        return lines.map { item ->
            if (item.timestampMs != null) {
                val safeMs = maxOf(item.timestampMs, lastTs)
                lastTs = safeMs + minGapMs
                item.copy(timestampMs = safeMs)
            } else {
                item
            }
        }
    }

    /**
     * Returns the count of lines with an assigned timestamp.
     */
    fun markedCount(lines: List<SyncLineItem>): Int {
        return lines.count { it.timestampMs != null }
    }

    /**
     * Returns progress ratio between 0.0f and 1.0f.
     */
    fun progress(lines: List<SyncLineItem>): Float {
        if (lines.isEmpty()) return 0f
        return markedCount(lines).toFloat() / lines.size.toFloat()
    }
}
