package com.lirix.app

import com.lirix.app.feature.lyrics.ChordTransposer
import org.junit.Assert.*
import org.junit.Test

class ChordTransposerRepairTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/fixtures/chords/$name"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test fun goldStructuredCorpusTransposesEveryChordAndPreservesProse() {
        assertEquals(fixture("gold-structure-plus2.txt"), ChordTransposer.transposeText(fixture("gold-structure.txt"), 2))
    }

    @Test fun outOfVocabularyExtensionsWrappersAndBassHaveIndependentExpectedValues() {
        val cases = mapOf(
            " (F#m7b5) " to " (G#m7b5) ", "{Bb7(#11)/F}" to "{C7(#11)/G}",
            "C6/9" to "D6/9", "Dm9/G" to "Em9/A", "G13(b9)" to "A13(b9)",
            "[ C/G ]" to "[ D/A ]", "Am(maj7)" to "Bm(maj7)",
            "Dbadd11/Ab" to "Ebadd11/Bb", "G7sus4" to "A7sus4", "Hdim7/F#" to "C#dim7/G#",
            "E♭maj9/B♭" to "Fmaj9/C", "F♯m7/C♯" to "G#m7/D#"
        )
        for ((source, expected) in cases) assertEquals(source, expected, ChordTransposer.transposeChord(source, 2))
    }

    @Test fun proseUnknownTokensAndMalformedChordsAreUntouched() {
        val negatives = listOf("A cup of tea in C", "Am I a chord?", "(Am) explains the example", "Cfoobar Am",
            "[notes] [Verse 1]", "Am/C/invalid", "(Am]", "Abandoned", "Cmajorly", "hello Am G")
        for (line in negatives) assertEquals(line, line, ChordTransposer.transposeText(line, 5))
        for (token in listOf("Am/C/invalid", "(Am]", "Cfoobar", "C##", "C/", "C7(bad)")) {
            assertEquals(token, token, ChordTransposer.transposeChord(token, 5))
        }
    }

    @Test fun explicitInlineMarkupDoesNotRequireTheSurroundingProseToBeChordRow() {
        assertEquals("Keep [ Bm/F# ] and [annotation] (Am) here.",
            ChordTransposer.transposeText("Keep [ Am/E ] and [annotation] (Am) here.", 2))
    }

    @Test fun layoutNewlinesAndOctaveIdentityAreExact() {
        val source = "Intro:\tAm  (Em) }x2\r\n| F | C/E |\rPlain A prose\n[ Am ]"
        val expected = "Intro:\tBm  (F#m) }x2\r\n| G | D/F# |\rPlain A prose\n[ Bm ]"
        assertEquals(expected, ChordTransposer.transposeText(source, 2))
        for (shift in listOf(0, -12, 12, 24, -24)) {
            assertEquals(source, ChordTransposer.transposeText(source, shift))
            assertEquals(" {Bbmaj7/F} ", ChordTransposer.transposeChord(" {Bbmaj7/F} ", shift))
            assertEquals("Cb", ChordTransposer.transposeNote("Cb", shift))
        }
    }

    private val pitch = mapOf("C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3,
        "E" to 4, "F" to 5, "F#" to 6, "Gb" to 6, "G" to 7, "G#" to 8, "Ab" to 8,
        "A" to 9, "A#" to 10, "Bb" to 10, "B" to 11, "H" to 11, "Cb" to 11)

    @Test fun everyRootAndBassUsesIndependentPitchOracleAndRoundTrips() {
        for ((root, rootPitch) in pitch) for ((bass, bassPitch) in pitch) for (shift in -12..12) {
            val input = "$root" + "m7/$bass"
            val result = ChordTransposer.transposeChord(input, shift)
            val parts = result.split("m7/")
            assertEquals(input, 2, parts.size)
            assertEquals(input, (rootPitch + shift).mod(12), pitch[parts[0]])
            assertEquals(input, (bassPitch + shift).mod(12), pitch[parts[1]])
            val reversed = ChordTransposer.transposeChord(result, -shift).split("m7/")
            assertEquals(input, rootPitch, pitch[reversed[0]])
            assertEquals(input, bassPitch, pitch[reversed[1]])
        }
    }

    @Test fun extremeShiftCannotOverflowAndInvalidNoteIsNotPrefixConsumed() {
        for (shift in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals((11L + shift).mod(12).toInt(), pitch[ChordTransposer.transposeNote("B", shift)])
        }
        for (invalid in listOf("C#oops", "Ebmore", " C# trailing ", "C##"))
            assertEquals(invalid, ChordTransposer.transposeNote(invalid, 3))
    }
}
