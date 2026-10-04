package com.eventengine.app

import com.eventengine.app.feature.lyrics.CustomRuleLyricsProvider
import com.eventengine.app.storage.CustomLyricsRuleEntity
import com.eventengine.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD Unit-tests for TASK-TEACH-02:
 * - Multi-selection of blocks in TeachMode.
 * - Composite CSS selectors separated by comma (e.g. "div.verse1, div.verse2").
 * - Backward compatibility with single selectors (#lyrics, .song-text, div.words, itemprop).
 * - Full execution flow of CustomRuleLyricsProvider with composite selectors.
 */
class TeachModeMultiSelectTest {

    @Test
    fun test_comma_separated_selector_extracts_all_blocks() {
        val html = """
            <html>
            <body>
                <div class="header"><h1>Название песни</h1></div>
                <div class="verse1">
                    Куплет первый: солнечный день,<br/>
                    На асфальте тени деревьев.
                </div>
                <div class="advertisement">Баннер спонсора</div>
                <div class="verse2">
                    Куплет второй: наступает вечер,<br/>
                    Мы зажигаем фонари.
                </div>
            </body>
            </html>
        """.trimIndent()

        // 1. Test extraction of composite selector
        val extractedRaw = CustomRuleLyricsProvider.extractContainerBySelector(html, "div.verse1, div.verse2")
        assertNotNull("Should extract content for composite selector", extractedRaw)

        // 2. Test full cleaning of composite content
        val cleaned = CustomRuleLyricsProvider.cleanHtmlToPlainLyrics(extractedRaw!!)
        assertTrue("Must contain verse 1", cleaned.contains("Куплет первый: солнечный день"))
        assertTrue("Must contain verse 2", cleaned.contains("Куплет второй: наступает вечер"))
        assertTrue("Must not contain advertisement", !cleaned.contains("Баннер спонсора"))

        // Both verses must be separated by paragraph breaks
        val expectedJoined = """
            Куплет первый: солнечный день,
            На асфальте тени деревьев.

            Куплет второй: наступает вечер,
            Мы зажигаем фонари.
        """.trimIndent()
        assertEquals(expectedJoined, cleaned)
    }

    @Test
    fun test_single_selector_remains_backward_compatible() {
        val htmlId = """<div id="lyrics">Текст песни в блоке id</div>"""
        val htmlClass = """<div class="song-text">Текст песни в блоке class</div>"""
        val htmlTagClass = """<p class="words">Текст песни в p.words</p>"""
        val htmlItemprop = """<div itemprop="lyrics">Текст песни в itemprop</div>"""

        val extractedId = CustomRuleLyricsProvider.extractContainerBySelector(htmlId, "#lyrics")
        assertNotNull(extractedId)
        assertTrue(extractedId!!.contains("Текст песни в блоке id"))

        val extractedClass = CustomRuleLyricsProvider.extractContainerBySelector(htmlClass, ".song-text")
        assertNotNull(extractedClass)
        assertTrue(extractedClass!!.contains("Текст песни в блоке class"))

        val extractedTagClass = CustomRuleLyricsProvider.extractContainerBySelector(htmlTagClass, "p.words")
        assertNotNull(extractedTagClass)
        assertTrue(extractedTagClass!!.contains("Текст песни в p.words"))

        val extractedItemprop = CustomRuleLyricsProvider.extractContainerBySelector(htmlItemprop, "itemprop=lyrics")
        assertNotNull(extractedItemprop)
        assertTrue(extractedItemprop!!.contains("Текст песни в itemprop"))
    }

    @Test
    fun test_custom_rule_with_multi_selector_execution() = runBlocking {
        val ruleEntity = CustomLyricsRuleEntity(
            id = "rule_multi_test",
            domain = "composite-lyrics.ru",
            name = "Composite Source",
            ruleJson = """
                {
                    "content": {
                        "selector": "div.verse, div.chorus",
                        "stripSelectors": ["span.note"]
                    }
                }
            """.trimIndent()
        )

        val pageHtml = """
            <html>
                <body>
                    <div class="verse">
                        <span class="note">Интро 4 такта</span>
                        Первый куплет песни звучит здесь.<br>
                        Слова первого куплета.
                    </div>
                    <div class="chorus">
                        Это громкий припев песни!<br>
                        Поём его вместе.
                    </div>
                </body>
            </html>
        """.trimIndent()

        val track = TrackEntity(
            trackKey = "track_composite",
            title = "Composite Song",
            artist = "Band",
            sourcePackage = "com.spotify.music"
        )

        val provider = CustomRuleLyricsProvider(ruleEntity, httpGet = { pageHtml })

        val result = provider.getLyrics(track)

        assertTrue("Provider should successfully extract lyrics", result.hasLyrics)
        assertNotNull(result.plainLyrics)
        val text = result.plainLyrics!!

        assertTrue("Must contain verse content", text.contains("Первый куплет песни звучит здесь."))
        assertTrue("Must contain chorus content", text.contains("Это громкий припев песни!"))
        assertTrue("Must strip unwanted span.note", !text.contains("Интро 4 такта"))
    }
}
