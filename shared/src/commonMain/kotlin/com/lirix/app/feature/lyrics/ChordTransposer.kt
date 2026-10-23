package com.lirix.app.feature.lyrics

/** Conservative chart transposition: explicit chord rows or square-bracket inline markup only. */
object ChordTransposer {
    private val NOTES_SHARP = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val NOTES_FLAT = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val naturalPitch = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11, 'H' to 11)
    private val notePattern = Regex("""^([A-H])([#b♯♭]?)$""", RegexOption.IGNORE_CASE)
    private val chordRoot = Regex("""^([A-H][#b♯♭]?)(.*)$""", RegexOption.IGNORE_CASE)
    private val slashBass = Regex("""/([A-H][#b♯♭]?)$""", RegexOption.IGNORE_CASE)
    // Explicit musical grammar; arbitrary alphabetic tails must never turn prose into chords.
    private val suffixPattern = Regex(
        """^(?:maj|min|dim|aug|sus[24]?|m|M|\+|°|ø|-)?(?:2|4|5|6|7|9|11|13)?(?:maj(?:7|9|11|13)|M(?:7|9|11|13))?(?:sus[24]|add(?:2|4|6|9|11|13))?(?:[#b♯♭](?:5|9|11|13))*(?:\((?:maj7|M7|[#b♯♭]?(?:5|9|11|13)(?:,[#b♯♭]?(?:5|9|11|13))*)\))?$""",
        RegexOption.IGNORE_CASE
    )
    private val labels = "Intro|Verse|Chorus|Bridge|Outro|Instrumental|Solo|Pre[- ]Chorus|Вступление|Куплет|Припев|Бридж|Проигрыш|Соло|Кода"
    private val header = Regex("""^\s*(?:\[\s*(?:$labels)(?:\s*\d+)?\s*\]\s*:?|(?:$labels)(?:\s*\d+)?\s*:)""", RegexOption.IGNORE_CASE)
    private val tokens = Regex("""\[[^\]\r\n]*\]|\{[^}\r\n]*\}|\((?:[^()\r\n]|\([^()\r\n]*\))*\)|[|:]+|[^\s|:]+""")
    private val repeat = Regex("""[}|:]*(?:[xх×]\d+|\d+[xх×])[|:]*""", RegexOption.IGNORE_CASE)
    private val inlineChord = Regex("""\[[^\]\r\n]*\]""")

    private data class ParsedChord(val prefix: String, val root: String, val suffix: String,
        val bass: String?, val postfix: String)

    fun transposeNote(rawNote: String, semitones: Int): String {
        if (semitones.mod(12) == 0) return rawNote
        val value = rawNote.trim()
        val match = notePattern.matchEntire(value) ?: return rawNote
        val natural = naturalPitch[match.groupValues[1][0].uppercaseChar()] ?: return rawNote
        val accidental = match.groupValues[2]
        val adjustment = when (accidental) { "#", "♯" -> 1; "b", "B", "♭" -> -1; else -> 0 }
        val target = (natural + adjustment + semitones.mod(12)).mod(12)
        return if (adjustment < 0) NOTES_FLAT[target] else NOTES_SHARP[target]
    }

    private fun parseChordToken(raw: String): ParsedChord? {
        var start = raw.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return null
        var end = raw.indexOfLast { !it.isWhitespace() } + 1
        while (end - start >= 2 && when (raw[start]) {
            '(' -> raw[end - 1] == ')'; '[' -> raw[end - 1] == ']'; '{' -> raw[end - 1] == '}'; else -> false
        }) {
            start++; end--
            while (start < end && raw[start].isWhitespace()) start++
            while (end > start && raw[end - 1].isWhitespace()) end--
        }
        if (start >= end) return null
        val core = raw.substring(start, end)
        val match = chordRoot.matchEntire(core) ?: return null
        val root = match.groupValues[1]
        var suffix = match.groupValues[2]
        val bassMatch = slashBass.find(suffix)
        val bass = bassMatch?.groupValues?.get(1)
        if (bassMatch != null) suffix = suffix.substring(0, bassMatch.range.first)
        // 6/9 is a numeric chord extension, not slash bass.
        val valid = suffixPattern.matches(suffix) || (suffix.endsWith("6/9") && suffixPattern.matches(suffix.dropLast(2)))
        if (!valid) return null
        return ParsedChord(raw.substring(0, start), root, suffix, bass, raw.substring(end))
    }

    fun transposeChord(chord: String, semitones: Int): String {
        if (semitones.mod(12) == 0) return chord
        val parsed = parseChordToken(chord) ?: return chord
        return buildString {
            append(parsed.prefix); append(transposeNote(parsed.root, semitones)); append(parsed.suffix)
            parsed.bass?.let { append('/'); append(transposeNote(it, semitones)) }
            append(parsed.postfix)
        }
    }

    fun transposeText(text: String, semitones: Int): String {
        if (semitones.mod(12) == 0) return text
        // Replacing content spans rather than splitting lines preserves CRLF, CR, LF and final newlines.
        return Regex("""[^\r\n]+""").replace(text) { match ->
            val line = match.value
            if (isChordLine(line)) transposeChordLine(line, semitones) else transposeBracketedLine(line, semitones)
        }
    }

    private fun transposeBracketedLine(line: String, semitones: Int): String =
        inlineChord.replace(line) { transposeChord(it.value, semitones) }

    private fun isControl(token: String): Boolean =
        token.isNotEmpty() && (token.all { it in "|:,-}" } || repeat.matches(token))

    private fun isChordLine(line: String): Boolean {
        val content = line.substring(header.find(line)?.range?.last?.plus(1) ?: 0)
        var foundChord = false
        for (match in tokens.findAll(content)) {
            if (isControl(match.value)) continue
            val chord = parseChordToken(match.value) ?: return false
            if (!chord.root[0].isUpperCase()) return false
            foundChord = true
        }
        return foundChord
    }

    private fun transposeChordLine(line: String, semitones: Int): String {
        val contentStart = header.find(line)?.range?.last?.plus(1) ?: 0
        return line.substring(0, contentStart) + tokens.replace(line.substring(contentStart)) {
            if (isControl(it.value)) it.value else transposeChord(it.value, semitones)
        }
    }
}
