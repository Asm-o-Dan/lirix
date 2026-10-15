package com.lirix.app

import com.lirix.app.feature.lyrics.ChordTransposer
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordTransposerTest {

    @Test
    fun test_transpose_single_major_chords() {
        assertEquals("D", ChordTransposer.transposeChord("C", 2))
        assertEquals("G#", ChordTransposer.transposeChord("G", 1))
        assertEquals("C", ChordTransposer.transposeChord("B", 1))
        assertEquals("D#", ChordTransposer.transposeChord("E", -1))
    }

    @Test
    fun test_transpose_minor_and_extensions() {
        assertEquals("Bm", ChordTransposer.transposeChord("Am", 2))
        assertEquals("Gm7", ChordTransposer.transposeChord("F#m7", 1))
        assertEquals("Esus4", ChordTransposer.transposeChord("Dsus4", 2))
        assertEquals("A7", ChordTransposer.transposeChord("G7", 2))
    }

    @Test
    fun test_transpose_slash_bass_chords() {
        assertEquals("Bm/A", ChordTransposer.transposeChord("Am/G", 2))
        assertEquals("C#/F", ChordTransposer.transposeChord("C/E", 1))
        assertEquals("E/G#", ChordTransposer.transposeChord("D/F#", 2))
    }

    @Test
    fun test_transpose_flat_notes_and_h_alias() {
        assertEquals("C", ChordTransposer.transposeChord("Bb", 2))
        assertEquals("C7", ChordTransposer.transposeChord("H7", 1))
    }

    @Test
    fun test_transpose_full_song_text_preserves_lyrics() {
        val input = """
            Am          C
            Надежда горит в нас
            F           E
            Пока не гаснет блеск
        """.trimIndent()

        val expected = """
            Bm          D
            Надежда горит в нас
            G           F#
            Пока не гаснет блеск
        """.trimIndent()

        val result = ChordTransposer.transposeText(input, 2)
        assertEquals(expected, result)
    }

    @Test
    fun test_transpose_zero_semitones_returns_original() {
        val input = "Am  C  Dm  E\nПесня без слов"
        assertEquals(input, ChordTransposer.transposeText(input, 0))
    }
}
