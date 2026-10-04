package com.eventengine.app.feature.lyrics

object ChordTransposer {
    private val NOTES_SHARP = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val NOTES_FLAT  = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    private val NOTE_TO_SEMITONE = mapOf(
        "C" to 0, "B#" to 0,
        "C#" to 1, "DB" to 1,
        "D" to 2,
        "D#" to 3, "EB" to 3,
        "E" to 4, "FB" to 4,
        "F" to 5, "E#" to 5,
        "F#" to 6, "GB" to 6,
        "G" to 7,
        "G#" to 8, "AB" to 8,
        "A" to 9,
        "A#" to 10, "BB" to 10,
        "B" to 11, "H" to 11, "CB" to 11
    )

    private val CHORD_REGEX = Regex(
        """^([A-H][#b]?)(m|maj|min|dim|aug|sus2|sus4|add9|5|6|7|maj7|m7|mM7)?(?:/([A-H][#b]?))?$""",
        RegexOption.IGNORE_CASE
    )

    fun transposeNote(rawNote: String, semitones: Int): String {
        val trimmed = rawNote.trim()
        val key = when {
            trimmed.length == 1 -> trimmed.uppercase()
            trimmed.length >= 2 && trimmed[1] == '#' -> "${trimmed[0].uppercase()}#"
            trimmed.length >= 2 && (trimmed[1] == 'b' || trimmed[1] == 'B') -> "${trimmed[0].uppercase()}B"
            else -> trimmed.uppercase()
        }

        val baseSemitone = NOTE_TO_SEMITONE[key] ?: return rawNote
        val targetSemitone = (baseSemitone + semitones).mod(12)

        val preferFlats = trimmed.contains("b", ignoreCase = true) && !trimmed.equals("b", ignoreCase = true)
        return if (preferFlats) {
            NOTES_FLAT[targetSemitone]
        } else {
            NOTES_SHARP[targetSemitone]
        }
    }

    fun transposeChord(chord: String, semitones: Int): String {
        if (semitones == 0) return chord
        val match = CHORD_REGEX.matchEntire(chord.trim()) ?: return chord

        val root = match.groupValues[1]
        val suffix = match.groupValues[2]
        val bass = match.groupValues[3]

        val newRoot = transposeNote(root, semitones)
        val newBass = if (bass.isNotEmpty()) transposeNote(bass, semitones) else ""

        return if (newBass.isNotEmpty()) {
            "$newRoot$suffix/$newBass"
        } else {
            "$newRoot$suffix"
        }
    }

    fun transposeText(text: String, semitones: Int): String {
        if (semitones == 0) return text

        return text.lines().joinToString("\n") { line ->
            if (isChordLine(line)) {
                transposeChordLine(line, semitones)
            } else {
                line
            }
        }
    }

    private fun isChordLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val words = trimmed.split(Regex("""\s+"""))
        if (words.isEmpty()) return false

        val chordCount = words.count { CHORD_REGEX.matches(it) }
        return chordCount.toFloat() / words.size >= 0.6f
    }

    private fun transposeChordLine(line: String, semitones: Int): String {
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            if (line[i].isWhitespace()) {
                sb.append(line[i])
                i++
            } else {
                val start = i
                while (i < line.length && !line[i].isWhitespace()) {
                    i++
                }
                val word = line.substring(start, i)
                val transposed = if (CHORD_REGEX.matches(word)) {
                    transposeChord(word, semitones)
                } else {
                    word
                }
                sb.append(transposed)
            }
        }
        return sb.toString()
    }
}
