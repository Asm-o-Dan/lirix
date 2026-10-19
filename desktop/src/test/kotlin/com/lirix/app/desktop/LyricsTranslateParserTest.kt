package com.lirix.app.desktop

import com.lirix.app.translations.*
import org.junit.Assert.*
import org.junit.Test

class LyricsTranslateParserTest {
    private val url = "https://lyricstranslate.com/en/example-translation.html"
    private fun html(language: String = "English") = """<meta charset="utf-8"><h1>Example - $language translation</h1>
        <div class="top__artists"><a class="top__tag">Artist</a></div>
        <div data-panel="original"><h2 class="translate__title">Example</h2><div id="original-lyrics">Original one<br>Original two</div></div>
        <div data-panel="translation"><h2 class="translate__title">Translated title</h2><div id="translation-body">Translated one</div></div>
        <div class="details__flex-block"><div class="details__title">Song details</div><a class="row-details__tag">Submitter</a></div>
        <div class="details__flex-block"><div class="details__title">Translation details</div><a class="row-details__tag">Translator</a></div>
        <div class="notice-warning"><span class="notice-warning__text">Proofreading requested</span></div>"""

    @Test fun preservesIndependentTextsAuthorAndWarnings() {
        val document = LyricsTranslateParser.parse(html().toByteArray(), url, "en")
        assertEquals("Translator", document.translator)
        assertEquals("LyricsTranslate", document.sourceName)
        assertFalse(document.aligned)
        assertEquals(1, document.blocks.size)
        assertTrue(document.blocks.single().original.contains("Original two"))
        assertEquals(listOf("Proofreading requested"), document.warnings)
        assertEquals(document, TranslationDocuments.decode(TranslationDocuments.encode(document)))
    }

    @Test fun supportsFiveLanguagesWithoutRelabeling() {
        mapOf("Russian" to "ru", "English" to "en", "Spanish" to "es", "Portuguese" to "pt", "Chinese" to "zh").forEach { (name, code) ->
            assertEquals(code, LyricsTranslateParser.parse(html(name).toByteArray(), url, code).language)
        }
        assertThrows(IllegalArgumentException::class.java) { LyricsTranslateParser.parse(html().toByteArray(), url, "ru") }
    }

    @Test fun rejectsSearchPagesMissingIdentityAndOversize() {
        assertThrows(IllegalStateException::class.java) { LyricsTranslateParser.parse("<h1>Search</h1>".toByteArray(), url, "en") }
        assertThrows(IllegalArgumentException::class.java) { LyricsTranslateParser.parse(html().replace("top__artists", "other").toByteArray(), url, "en") }
        assertThrows(IllegalArgumentException::class.java) { LyricsTranslateParser.parse(ByteArray(2_000_001), url, "en") }
    }

    @Test fun rejectsForeignAndNonSongUrls() {
        listOf("https://evil.example/en/example.html", "https://lyricstranslate.com@evil.example/en/example.html", "http://lyricstranslate.com/en/example.html", "https://lyricstranslate.com/en/songs", "$url?redirect=x").forEach {
            assertThrows(IllegalArgumentException::class.java) { LyricsTranslateParser.canonicalUrl(it) }
        }
    }

    @Test fun legacyRussianDocumentsRemainReadable() {
        val old = """{"sourceUrl":"https://example.test","title":"Example","artist":"Artist","translatedTitle":"","translator":"","blocks":[{"original":"One","translated":"Один"}]}"""
        val document = TranslationDocuments.decode(old)
        assertEquals("ru", document.language)
        assertEquals("Amalgama", document.sourceName)
        assertTrue(document.aligned)
    }
}
