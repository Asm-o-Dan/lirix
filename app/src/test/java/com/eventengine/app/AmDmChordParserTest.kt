package com.eventengine.app

import com.eventengine.app.feature.lyrics.AmDmChordParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD Unit-tests for AmDmChordParser (TASK-LYR-02).
 * Validates extraction of chords block, stripping of HTML tags,
 * unescaping entities, and preservation of chord spacing/alignment.
 */
class AmDmChordParserTest {

    @Test
    fun test_parse_valid_html() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
            <div class="b-podbor__text">
            <pre itemprop="chordsBlock">
            [Am]            [C]
            Белый снег, серый лед,
            [Dm]            [G]
            На растрескавшейся земле.
            </pre>
            </div>
            </body>
            </html>
        """.trimIndent()

        val parsed = AmDmChordParser.parseAmDmHtml(html)
        assertNotNull("Parsed chords must not be null for valid html", parsed)
        assertTrue(parsed!!.contains("[Am]"))
        assertTrue(parsed.contains("Белый снег"))
        assertTrue(parsed.contains("[Dm]"))
    }

    @Test
    fun test_parse_strips_html_anchor_tags_and_unescapes() {
        val html = """
            <pre itemprop="chordsBlock">
            <a href="/akkordi/am/">Am</a> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; <a href="/akkordi/c/">C</a>
            Текст &quot;песни&quot; &amp; аккорды
            </pre>
        """.trimIndent()

        val parsed = AmDmChordParser.parseAmDmHtml(html)
        assertNotNull(parsed)
        assertFalseHtmlTags(parsed!!)
        assertTrue(parsed.contains("Am"))
        assertTrue(parsed.contains("C"))
        assertTrue(parsed.contains("\"песни\" & аккорды"))
    }

    @Test
    fun test_preserve_chord_alignment() {
        val rawChords = "   Am         C          Em\nСлово над слогом выровнено точно"
        val html = "<pre>$rawChords</pre>"

        val parsed = AmDmChordParser.parseAmDmHtml(html)
        assertNotNull(parsed)
        assertTrue("Indentation must be preserved for chord tabs", parsed!!.startsWith("   Am"))
    }

    @Test
    fun test_parse_empty_or_blank_html_returnsNull() {
        assertNull(AmDmChordParser.parseAmDmHtml(""))
        assertNull(AmDmChordParser.parseAmDmHtml("   "))
        assertNull(AmDmChordParser.parseAmDmHtml("<html><body>No chords here</body></html>"))
    }

    // ------------------------------------------------------------------------
    // TASK-BUG-06: AmDm Chords Parser, Inline Detection & Transposition
    // ------------------------------------------------------------------------

    @Test
    fun test_extractChordsWithLyrics_preservesChordsAndLyrics() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
            <div class="b-podbor__text">
            <pre itemprop="chordsBlock">
            <div class="podbor__chord">Am</div>                   <div class="podbor__chord">C</div>
            Группа крови на рукаве,
            <div class="podbor__chord">Dm</div>                   <div class="podbor__chord">E</div>
            Мой порядковый номер на рукаве.
            </pre>
            </div>
            </body>
            </html>
        """.trimIndent()

        val extracted = AmDmChordParser.extractChordsWithLyrics(html)
        assertNotNull("extractChordsWithLyrics must not return null for valid HTML", extracted)
        assertTrue("Extracted chords must contain Am", extracted!!.contains("Am"))
        assertTrue("Extracted chords must contain C", extracted.contains("C"))
        assertTrue("Extracted chords must contain lyrics text", extracted.contains("Группа крови на рукаве"))
        assertFalseHtmlTags(extracted)
    }

    @Test
    fun test_detectAndFormatInlineChords() {
        val textWithChords = """
            Am          C
            Я помню чудное мгновенье
            Dm          E
            Передо мной явилась ты
        """.trimIndent()

        val formatted = AmDmChordParser.detectAndFormatInlineChords(textWithChords)
        assertNotNull("detectAndFormatInlineChords must recognize inline chord sequences", formatted)
        assertTrue(formatted!!.contains("Am"))
        assertTrue(formatted.contains("Dm"))
        assertTrue(formatted.contains("Я помню чудное мгновенье"))
    }

    @Test
    fun test_transpose_chords_up_and_down() {
        val original = "Am   C   Dm   E   G"

        // +2 semitones: Am -> Bm (or Hm), C -> D, Dm -> Em, E -> F#, G -> A
        val transposedPlus2 = AmDmChordParser.transpose(original, 2)
        assertTrue("Am + 2 must become Bm or Hm, got: $transposedPlus2", transposedPlus2.contains("Bm") || transposedPlus2.contains("Hm"))
        assertTrue("C + 2 must become D, got: $transposedPlus2", transposedPlus2.contains("D"))
        assertTrue("Dm + 2 must become Em, got: $transposedPlus2", transposedPlus2.contains("Em"))

        // -2 semitones: Am -> Gm, C -> Bb (or A#), Dm -> Cm
        val transposedMinus2 = AmDmChordParser.transpose(original, -2)
        assertTrue("Am - 2 must become Gm, got: $transposedMinus2", transposedMinus2.contains("Gm"))
        assertTrue("Dm - 2 must become Cm, got: $transposedMinus2", transposedMinus2.contains("Cm"))
    }

    private fun assertFalseHtmlTags(text: String) {
        val hasHtmlTag = Regex("""<[^>]+>""").containsMatchIn(text)
        org.junit.Assert.assertFalse("Result must not contain raw HTML tags: $text", hasHtmlTag)
    }
}

