package com.lirix.app

import androidx.compose.ui.graphics.Color
import com.lirix.app.ui.player.buildChordAnnotatedString
import com.lirix.app.ui.player.wrapSingleLine
import com.lirix.app.ui.player.wrapSyllabicPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordsTabContentTest {

    @Test
    fun testWrapSyllabicPair_ShortLine_NoWrap() {
        val chord = "Am   C"
        val lyric = "Hello world"
        val result = wrapSyllabicPair(chord, lyric, maxChars = 30)
        assertEquals(1, result.size)
        assertEquals("Am   C", result[0].first)
        assertEquals("Hello world", result[0].second)
    }

    @Test
    fun testWrapSyllabicPair_LongLine_SplitsAtWordBoundary() {
        val chord = "C#m                     B               F#m"
        val lyric = "Я помню белые обои, черная посуда, нас в хрущевке двое"
        val result = wrapSyllabicPair(chord, lyric, maxChars = 36)

        assertTrue(result.size >= 2)
        for (pair in result) {
            assertTrue("Chord line exceeds maxChars: ${pair.first.length}", pair.first.length <= 36)
            assertTrue("Lyric line exceeds maxChars: ${pair.second.length}", pair.second.length <= 36)
        }
        // First chunk contains C#m and B, second chunk contains F#m
        assertTrue(result[0].first.contains("C#m"))
        assertTrue(result[0].first.contains("B"))
        assertTrue(result[1].first.contains("F#m"))
        assertTrue(result[1].second.contains("хрущевке"))
    }

    @Test
    fun testWrapSingleLine_SplitsAtWordBoundary() {
        val longLine = "Это очень длинная строка текста которая должна переноситься на несколько строк"
        val wrapped = wrapSingleLine(longLine, maxChars = 30)

        assertTrue(wrapped.size >= 3)
        for (chunk in wrapped) {
            assertTrue("Chunk '${chunk}' exceeds maxChars: ${chunk.length}", chunk.length <= 30)
        }
        assertEquals(longLine, wrapped.joinToString(" "))
    }

    @Test
    fun testBuildChordAnnotatedString_RunonText_AutomaticallyNormalized() {
        val runonText = "[C#m]Белые обои, [B]черная посуда. [F#m]Нас в хрущевке двое, [C#m]кто мы и откуда? [B]Задвигаем шторы..."
        val annotated = buildChordAnnotatedString(
            text = runonText,
            accentColor = Color.Yellow,
            textColor = Color.White,
            isWrapEnabled = true,
            maxChars = 36
        )

        // Must contain newlines rather than a single giant line
        val text = annotated.text
        assertTrue("Text must contain linebreaks", text.contains("\n"))
        val lines = text.lines()
        assertTrue("Should have multiple verses and chords lines: ${lines.size}", lines.size >= 4)
    }
}
