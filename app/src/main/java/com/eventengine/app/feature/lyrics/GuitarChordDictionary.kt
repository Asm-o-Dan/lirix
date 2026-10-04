package com.eventengine.app.feature.lyrics

/**
 * High-precision guitar chord fingering and diagram representation.
 * Strings are indexed 6 to 1:
 *  - Index 0: 6th string (Low E)
 *  - Index 1: 5th string (A)
 *  - Index 2: 4th string (D)
 *  - Index 3: 3rd string (G)
 *  - Index 4: 2nd string (B)
 *  - Index 5: 1st string (High e)
 *
 * Values in [frets]:
 *  - -1 : Muted string ('X')
 *   0 : Open string ('O')
 *  >0 : Fret number relative to fretboard
 */
data class GuitarChord(
    val name: String,
    val frets: List<Int>,
    val baseFret: Int = 1,
    val fingers: List<Int> = emptyList(),
    val barre: Int? = null
)

object GuitarChordDictionary {

    private val CHORD_MAP: Map<String, GuitarChord> = mapOf(
        // A
        "A" to GuitarChord("A", listOf(-1, 0, 2, 2, 2, 0), 1, listOf(0, 0, 1, 2, 3, 0)),
        "Am" to GuitarChord("Am", listOf(-1, 0, 2, 2, 1, 0), 1, listOf(0, 0, 2, 3, 1, 0)),
        "A7" to GuitarChord("A7", listOf(-1, 0, 2, 0, 2, 0), 1, listOf(0, 0, 2, 0, 3, 0)),
        "Am7" to GuitarChord("Am7", listOf(-1, 0, 2, 0, 1, 0), 1, listOf(0, 0, 2, 0, 1, 0)),
        "Amaj7" to GuitarChord("Amaj7", listOf(-1, 0, 2, 1, 2, 0), 1, listOf(0, 0, 2, 1, 3, 0)),
        "Asus2" to GuitarChord("Asus2", listOf(-1, 0, 2, 2, 0, 0), 1, listOf(0, 0, 2, 3, 0, 0)),
        "Asus4" to GuitarChord("Asus4", listOf(-1, 0, 2, 2, 3, 0), 1, listOf(0, 0, 1, 2, 4, 0)),

        // A# / Bb
        "A#" to GuitarChord("A#", listOf(-1, 1, 3, 3, 3, 1), 1, listOf(0, 1, 2, 3, 4, 1), barre = 1),
        "A#m" to GuitarChord("A#m", listOf(-1, 1, 3, 3, 2, 1), 1, listOf(0, 1, 3, 4, 2, 1), barre = 1),
        "A#7" to GuitarChord("A#7", listOf(-1, 1, 3, 1, 3, 1), 1, listOf(0, 1, 3, 1, 4, 1), barre = 1),
        "Bb" to GuitarChord("Bb", listOf(-1, 1, 3, 3, 3, 1), 1, listOf(0, 1, 2, 3, 4, 1), barre = 1),
        "Bbm" to GuitarChord("Bbm", listOf(-1, 1, 3, 3, 2, 1), 1, listOf(0, 1, 3, 4, 2, 1), barre = 1),
        "Bb7" to GuitarChord("Bb7", listOf(-1, 1, 3, 1, 3, 1), 1, listOf(0, 1, 3, 1, 4, 1), barre = 1),

        // B / H
        "B" to GuitarChord("B", listOf(-1, 2, 4, 4, 4, 2), 2, listOf(0, 1, 2, 3, 4, 1), barre = 2),
        "Bm" to GuitarChord("Bm", listOf(-1, 2, 4, 4, 3, 2), 2, listOf(0, 1, 3, 4, 2, 1), barre = 2),
        "B7" to GuitarChord("B7", listOf(-1, 2, 1, 2, 0, 2), 1, listOf(0, 2, 1, 3, 0, 4)),
        "Bm7" to GuitarChord("Bm7", listOf(-1, 2, 4, 2, 3, 2), 2, listOf(0, 1, 3, 1, 2, 1), barre = 2),
        "H" to GuitarChord("H", listOf(-1, 2, 4, 4, 4, 2), 2, listOf(0, 1, 2, 3, 4, 1), barre = 2),
        "Hm" to GuitarChord("Hm", listOf(-1, 2, 4, 4, 3, 2), 2, listOf(0, 1, 3, 4, 2, 1), barre = 2),
        "H7" to GuitarChord("H7", listOf(-1, 2, 1, 2, 0, 2), 1, listOf(0, 2, 1, 3, 0, 4)),
        "Hm7" to GuitarChord("Hm7", listOf(-1, 2, 4, 2, 3, 2), 2, listOf(0, 1, 3, 1, 2, 1), barre = 2),

        // C
        "C" to GuitarChord("C", listOf(-1, 3, 2, 0, 1, 0), 1, listOf(0, 3, 2, 0, 1, 0)),
        "Cm" to GuitarChord("Cm", listOf(-1, 3, 5, 5, 4, 3), 3, listOf(0, 1, 3, 4, 2, 1), barre = 3),
        "C7" to GuitarChord("C7", listOf(-1, 3, 2, 3, 1, 0), 1, listOf(0, 3, 2, 4, 1, 0)),
        "Cmaj7" to GuitarChord("Cmaj7", listOf(-1, 3, 2, 0, 0, 0), 1, listOf(0, 3, 2, 0, 0, 0)),
        "Csus2" to GuitarChord("Csus2", listOf(-1, 3, 0, 0, 1, 0), 1, listOf(0, 3, 0, 0, 1, 0)),
        "Csus4" to GuitarChord("Csus4", listOf(-1, 3, 3, 0, 1, 1), 1, listOf(0, 3, 4, 0, 1, 1)),

        // C# / Db
        "C#" to GuitarChord("C#", listOf(-1, 4, 6, 6, 6, 4), 4, listOf(0, 1, 2, 3, 4, 1), barre = 4),
        "C#m" to GuitarChord("C#m", listOf(-1, 4, 6, 6, 5, 4), 4, listOf(0, 1, 3, 4, 2, 1), barre = 4),
        "C#7" to GuitarChord("C#7", listOf(-1, 4, 6, 4, 6, 4), 4, listOf(0, 1, 3, 1, 4, 1), barre = 4),
        "Db" to GuitarChord("Db", listOf(-1, 4, 6, 6, 6, 4), 4, listOf(0, 1, 2, 3, 4, 1), barre = 4),
        "Dbm" to GuitarChord("Dbm", listOf(-1, 4, 6, 6, 5, 4), 4, listOf(0, 1, 3, 4, 2, 1), barre = 4),

        // D
        "D" to GuitarChord("D", listOf(-1, -1, 0, 2, 3, 2), 1, listOf(0, 0, 0, 1, 3, 2)),
        "Dm" to GuitarChord("Dm", listOf(-1, -1, 0, 2, 3, 1), 1, listOf(0, 0, 0, 2, 3, 1)),
        "D7" to GuitarChord("D7", listOf(-1, -1, 0, 2, 1, 2), 1, listOf(0, 0, 0, 2, 1, 3)),
        "Dm7" to GuitarChord("Dm7", listOf(-1, -1, 0, 2, 1, 1), 1, listOf(0, 0, 0, 2, 1, 1)),
        "Dmaj7" to GuitarChord("Dmaj7", listOf(-1, -1, 0, 2, 2, 2), 1, listOf(0, 0, 0, 1, 2, 3)),
        "Dsus2" to GuitarChord("Dsus2", listOf(-1, -1, 0, 2, 3, 0), 1, listOf(0, 0, 0, 1, 3, 0)),
        "Dsus4" to GuitarChord("Dsus4", listOf(-1, -1, 0, 2, 3, 3), 1, listOf(0, 0, 0, 1, 2, 4)),

        // D# / Eb
        "D#" to GuitarChord("D#", listOf(-1, 6, 8, 8, 8, 6), 6, listOf(0, 1, 2, 3, 4, 1), barre = 6),
        "D#m" to GuitarChord("D#m", listOf(-1, 6, 8, 8, 7, 6), 6, listOf(0, 1, 3, 4, 2, 1), barre = 6),
        "D#7" to GuitarChord("D#7", listOf(-1, 6, 8, 6, 8, 6), 6, listOf(0, 1, 3, 1, 4, 1), barre = 6),
        "Eb" to GuitarChord("Eb", listOf(-1, 6, 8, 8, 8, 6), 6, listOf(0, 1, 2, 3, 4, 1), barre = 6),
        "Ebm" to GuitarChord("Ebm", listOf(-1, 6, 8, 8, 7, 6), 6, listOf(0, 1, 3, 4, 2, 1), barre = 6),

        // E
        "E" to GuitarChord("E", listOf(0, 2, 2, 1, 0, 0), 1, listOf(0, 2, 3, 1, 0, 0)),
        "Em" to GuitarChord("Em", listOf(0, 2, 2, 0, 0, 0), 1, listOf(0, 2, 3, 0, 0, 0)),
        "E7" to GuitarChord("E7", listOf(0, 2, 0, 1, 0, 0), 1, listOf(0, 2, 0, 1, 0, 0)),
        "Em7" to GuitarChord("Em7", listOf(0, 2, 0, 0, 0, 0), 1, listOf(0, 2, 0, 0, 0, 0)),
        "Emaj7" to GuitarChord("Emaj7", listOf(0, 2, 1, 1, 0, 0), 1, listOf(0, 3, 1, 2, 0, 0)),
        "Esus4" to GuitarChord("Esus4", listOf(0, 2, 2, 2, 0, 0), 1, listOf(0, 2, 3, 4, 0, 0)),

        // F
        "F" to GuitarChord("F", listOf(1, 3, 3, 2, 1, 1), 1, listOf(1, 3, 4, 2, 1, 1), barre = 1),
        "Fm" to GuitarChord("Fm", listOf(1, 3, 3, 1, 1, 1), 1, listOf(1, 3, 4, 1, 1, 1), barre = 1),
        "F7" to GuitarChord("F7", listOf(1, 3, 1, 2, 1, 1), 1, listOf(1, 3, 1, 2, 1, 1), barre = 1),
        "Fmaj7" to GuitarChord("Fmaj7", listOf(-1, -1, 3, 2, 1, 0), 1, listOf(0, 0, 3, 2, 1, 0)),

        // F# / Gb
        "F#" to GuitarChord("F#", listOf(2, 4, 4, 3, 2, 2), 2, listOf(1, 3, 4, 2, 1, 1), barre = 2),
        "F#m" to GuitarChord("F#m", listOf(2, 4, 4, 2, 2, 2), 2, listOf(1, 3, 4, 1, 1, 1), barre = 2),
        "F#7" to GuitarChord("F#7", listOf(2, 4, 2, 3, 2, 2), 2, listOf(1, 3, 1, 2, 1, 1), barre = 2),
        "Gb" to GuitarChord("Gb", listOf(2, 4, 4, 3, 2, 2), 2, listOf(1, 3, 4, 2, 1, 1), barre = 2),
        "Gbm" to GuitarChord("Gbm", listOf(2, 4, 4, 2, 2, 2), 2, listOf(1, 3, 4, 1, 1, 1), barre = 2),

        // G
        "G" to GuitarChord("G", listOf(3, 2, 0, 0, 0, 3), 1, listOf(2, 1, 0, 0, 0, 3)),
        "Gm" to GuitarChord("Gm", listOf(3, 5, 5, 3, 3, 3), 3, listOf(1, 3, 4, 1, 1, 1), barre = 3),
        "G7" to GuitarChord("G7", listOf(3, 2, 0, 0, 0, 1), 1, listOf(3, 2, 0, 0, 0, 1)),
        "Gm7" to GuitarChord("Gm7", listOf(3, 5, 3, 3, 3, 3), 3, listOf(1, 3, 1, 1, 1, 1), barre = 3),
        "Gmaj7" to GuitarChord("Gmaj7", listOf(3, 2, 0, 0, 0, 2), 1, listOf(3, 2, 0, 0, 0, 1)),
        "Gsus4" to GuitarChord("Gsus4", listOf(3, -1, 0, 0, 1, 3), 1, listOf(3, 0, 0, 0, 1, 4)),

        // G# / Ab
        "G#" to GuitarChord("G#", listOf(4, 6, 6, 5, 4, 4), 4, listOf(1, 3, 4, 2, 1, 1), barre = 4),
        "G#m" to GuitarChord("G#m", listOf(4, 6, 6, 4, 4, 4), 4, listOf(1, 3, 4, 1, 1, 1), barre = 4),
        "G#7" to GuitarChord("G#7", listOf(4, 6, 4, 5, 4, 4), 4, listOf(1, 3, 1, 2, 1, 1), barre = 4),
        "Ab" to GuitarChord("Ab", listOf(4, 6, 6, 5, 4, 4), 4, listOf(1, 3, 4, 2, 1, 1), barre = 4),
        "Abm" to GuitarChord("Abm", listOf(4, 6, 6, 4, 4, 4), 4, listOf(1, 3, 4, 1, 1, 1), barre = 4)
    )

    /**
     * Looks up a chord fingering diagram by name.
     * Handles brackets, slash bass stripping (Am/G -> Am), and aliases.
     */
    fun getChord(chordName: String): GuitarChord? {
        val clean = chordName.trim()
            .removePrefix("[").removeSuffix("]")
            .removePrefix("(").removeSuffix(")")
        if (clean.isEmpty()) return null

        // Direct hit
        CHORD_MAP[clean]?.let { return it }

        // Strip slash bass e.g. Am/G -> Am
        if (clean.contains("/")) {
            val rootPart = clean.substringBefore("/")
            CHORD_MAP[rootPart]?.let { return it }
        }

        // Russian H aliases: Hm -> Bm, H -> B, H7 -> B7
        if (clean.startsWith("H", ignoreCase = false)) {
            val alias = "B" + clean.substring(1)
            CHORD_MAP[alias]?.let { return it }
        }

        return null
    }

    /**
     * Extracts unique guitar chords found in a song text in sequential order.
     */
    fun extractChordsFromText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val regex = Regex("""\b([A-H][b#]?(?:m|maj7|maj|min|7|sus2|sus4|dim|aug|add9|5)?(?:/[A-H][b#]?)?)\b""")
        val found = LinkedHashSet<String>()
        for (match in regex.findAll(text)) {
            val chord = match.value
            // Only add if it's a known guitar chord or can be resolved
            if (getChord(chord) != null) {
                found.add(chord)
            }
        }
        return found.toList()
    }
}
