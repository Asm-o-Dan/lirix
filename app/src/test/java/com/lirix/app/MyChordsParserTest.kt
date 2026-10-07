package com.lirix.app

import com.lirix.app.feature.lyrics.MyChordsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MyChordsParser (TASK-MYCHORDS-01).
 * Validates extraction of w-words__text, conversion of <span class="subline"> and <div class="pline">,
 * preservation of chord spacing, stripping of HTML tags and entities, and separation of plain lyrics.
 */
class MyChordsParserTest {

    @Test
    fun test_parse_valid_mychords_html() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
            <div class="b-words__centering">
                <div class="w-words__text">
                    <div class="pline">
                        <span class="subline">Am          C</span>
                        <span class="subline">Белый снег, серый лед</span>
                    </div>
                    <div class="pline">
                        <span class="subline">Dm          G</span>
                        <span class="subline">На растрескавшейся земле</span>
                    </div>
                    <div class="b-words__text-actions">
                        <button>Копировать</button>
                    </div>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent()

        val result = MyChordsParser.parseMyChordsHtml(html)
        assertNotNull("Result should not be null", result)

        val chords = result!!.chords
        assertNotNull("Chords should not be null", chords)
        assertTrue("Chords should contain Am", chords!!.contains("Am"))
        assertTrue("Chords should contain C", chords.contains("C"))
        assertTrue("Chords should contain lyrics line", chords.contains("Белый снег, серый лед"))

        val plain = result.plainLyrics
        assertTrue("Plain lyrics should contain song text", plain.contains("Белый снег, серый лед"))
        assertTrue("Plain lyrics should contain second line", plain.contains("На растрескавшейся земле"))
    }

    @Test
    fun test_strips_html_tags_and_decodes_entities() {
        val html = """
            <div class="w-words__text">
                <span class="subline"><a href="/akkord/am">Am</a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="/akkord/c">C</a></span>
                <span class="subline">Текст &quot;песни&quot; &amp; аккорды</span>
            </div>
        """.trimIndent()

        val result = MyChordsParser.parseMyChordsHtml(html)
        assertNotNull(result)
        val chords = result!!.chords
        assertNotNull(chords)
        assertTrue("Should unescape quotes and ampersand", chords!!.contains("\"песни\" & аккорды"))
        assertTrue("Should not contain HTML anchor tag", !chords.contains("<a") && !chords.contains("</a>"))
    }

    @Test
    fun test_empty_or_blank_html_returns_null() {
        assertNull(MyChordsParser.parseMyChordsHtml(null))
        assertNull(MyChordsParser.parseMyChordsHtml(""))
        assertNull(MyChordsParser.parseMyChordsHtml("   "))
        assertNull(MyChordsParser.parseMyChordsHtml("<html><body>No lyrics here</body></html>"))
    }

    @Test
    fun test_day_darogu_supersession_structure() {
        // Typical structure from mychords.net/ru/dai_darogu/275290-day-darogu-supersession.html
        val html = """
            <div class="w-words__text" itemprop="lyrics">
                <span class="subline">Em              C</span>
                <span class="subline">Суперсессия, суперсессия!</span>
                <span class="subline">G               D</span>
                <span class="subline">В нашей голове депрессия!</span>
            </div>
        """.trimIndent()

        val result = MyChordsParser.parseMyChordsHtml(html)
        assertNotNull(result)
        assertTrue(result!!.chords!!.contains("Em              C"))
        assertTrue(result.chords!!.contains("Суперсессия, суперсессия!"))
        assertTrue(result.plainLyrics.contains("Суперсессия, суперсессия!"))
        assertTrue(result.plainLyrics.contains("В нашей голове депрессия!"))
    }
}
