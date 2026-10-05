package com.lirix.app

import com.lirix.app.feature.AggregatedLyricsProvider
import com.lirix.app.feature.LyricsProvider
import com.lirix.app.feature.LyricsResult
import com.lirix.app.feature.lyrics.CustomRuleConfig
import com.lirix.app.feature.lyrics.CustomRuleLyricsProvider
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.LyricsDao
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * TDD Unit-tests for TASK-LYR-04-C:
 * - Declarative JSON rule deserialization (CustomRuleConfig).
 * - Content extraction by selector (#id, .class, tag, itemprop).
 * - Stripping advertising/script/unwanted tags via stripSelectors.
 * - Converting <br>, </p>, </div> to newlines, decoding HTML entities.
 * - Cascade integration: custom rule executes before LRCLIB/scrapers.
 * - Cascade integration: rejected custom rule is skipped.
 */
@RunWith(AndroidJUnit4::class)
class CustomRuleLyricsProviderTest {

    @Test
    fun test_parse_valid_json_config() {
        val json = """
            {
                "domain": "amalgama-lab.com",
                "name": "Амальгама (Русский перевод)",
                "search": {
                    "urlTemplate": "https://www.amalgama-lab.com/search.htm?q={query}",
                    "resultListSelector": ".search-results a"
                },
                "content": {
                    "selector": ".string_ru",
                    "stripSelectors": ["script", "style", ".adv-banner"],
                    "chordsSelector": null,
                    "lineBreakStrategy": "PRESERVE_BR"
                }
            }
        """.trimIndent()

        val config = CustomRuleLyricsProvider.parseConfig(json, "amalgama-lab.com", "Амальгама")

        assertEquals("amalgama-lab.com", config.domain)
        assertEquals("Амальгама (Русский перевод)", config.name)
        assertEquals("https://www.amalgama-lab.com/search.htm?q={query}", config.searchUrlTemplate)
        assertEquals(".search-results a", config.searchResultSelector)
        assertEquals(".string_ru", config.contentSelector)
        assertEquals(listOf("script", "style", ".adv-banner"), config.stripSelectors)
        assertEquals("PRESERVE_BR", config.lineBreakStrategy)
    }

    @Test
    fun test_extract_content_and_strip_ads() {
        val sampleHtml = """
            <html>
            <body>
                <header><h1>Amalgama</h1></header>
                <div id="content">
                    <div class="string_ru">
                        <script>console.log('tracker');</script>
                        <div class="banner">Реклама онлайн-курсов</div>
                        Строка перевода номер один<br/>
                        Вторая строка с &quot;кавычками&quot; &amp; спецсимволами<br>
                        Третья строка перевода.
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val extracted = CustomRuleLyricsProvider.extractContent(
            html = sampleHtml,
            contentSelector = ".string_ru",
            stripSelectors = listOf("script", "banner")
        )

        assertNotNull(extracted)
        val text = extracted!!
        assertFalse("Script contents must be stripped", text.contains("tracker"))
        assertFalse("Ad banner must be stripped", text.contains("Реклама онлайн-курсов"))
        assertTrue("Br tags converted to newline", text.contains("Строка перевода номер один\nВторая строка"))
        assertTrue("HTML entities decoded", text.contains("\"кавычками\" & спецсимволами"))
        assertTrue("Last line present", text.contains("Третья строка перевода."))
    }

    @Test
    fun test_custom_rule_provider_executes_search_and_extraction() = runBlocking {
        val ruleEntity = CustomLyricsRuleEntity(
            id = "rule_amalgama_test",
            domain = "amalgama-lab.com",
            name = "Амальгама",
            ruleJson = """
                {
                    "search": {
                        "urlTemplate": "https://amalgama-lab.com/search?q={query}"
                    },
                    "content": {
                        "selector": "#lyrics_block",
                        "stripSelectors": ["script"]
                    }
                }
            """.trimIndent()
        )

        val track = TrackEntity(
            trackKey = "eminem_mockingbird",
            title = "Mockingbird",
            artist = "Eminem",
            sourcePackage = "com.spotify.music"
        )

        val searchResponse = """
            <html>
                <body>
                    <div class="results">
                        <a href="https://amalgama-lab.com/songs/e/eminem/mockingbird.html">Eminem - Mockingbird (Перевод)</a>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val songPageResponse = """
            <html>
                <body>
                    <div id="lyrics_block">
                        <script>track();</script>
                        Yeah, I know sometimes things may not make sense now<br>
                        But hey, what daddy always tell you?<br>
                        Straighten up little soldier
                    </div>
                </body>
            </html>
        """.trimIndent()

        val fakeHttpGet: suspend (String) -> String? = { url ->
            when {
                url.contains("search") -> searchResponse
                url.contains("mockingbird.html") -> songPageResponse
                else -> null
            }
        }

        val provider = CustomRuleLyricsProvider(ruleEntity, httpGet = fakeHttpGet)
        val result = provider.getLyrics(track)

        assertTrue(result.hasLyrics)
        assertEquals("rule:rule_amalgama_test", result.sourceId)
        assertEquals("Амальгама", result.source)
        assertNotNull(result.plainLyrics)
        assertTrue(result.plainLyrics!!.contains("Yeah, I know sometimes"))
        assertFalse(result.plainLyrics!!.contains("track()"))
    }

    @Test
    fun test_cascade_executes_custom_rule_before_lrclib() = runBlocking {
        val ruleEntity = CustomLyricsRuleEntity(
            id = "rule_custom_1",
            domain = "custom.com",
            name = "Custom Community",
            ruleJson = """{"content":{"selector":"#lyrics"}}""",
            isEnabled = true,
            priority = 10
        )

        val mockLyricsDao = mock(LyricsDao::class.java)
        `when`(mockLyricsDao.getActiveRules()).thenReturn(listOf(ruleEntity))

        var lrclibCalled = false
        val mockLrcLib = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                lrclibCalled = true
                return LyricsResult(true, "LRCLIB text", "LRCLIB", sourceId = "builtin:lrclib")
            }
        }

        val track = TrackEntity(
            trackKey = "test_key",
            title = "Song",
            artist = "Artist",
            sourcePackage = "com.spotify.music"
        )

        // Custom provider returning valid text
        val mockCustomProvider = object : LyricsProvider {
            override suspend fun getLyrics(
                track: TrackEntity,
                rejectedSourceIds: Set<String>,
                forceNetwork: Boolean
            ): LyricsResult {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = "Text from Custom Rule",
                    source = "Custom Community",
                    plainLyrics = "Text from Custom Rule",
                    sourceId = "rule:rule_custom_1"
                )
            }
        }

        val aggregator = AggregatedLyricsProvider(
            lrcLibProvider = mockLrcLib,
            fallbackScraper = null,
            lyricsDao = mockLyricsDao,
            customRuleFactory = { mockCustomProvider }
        )

        val result = aggregator.getLyrics(track, emptySet(), false)

        assertTrue(result.hasLyrics)
        assertEquals("rule:rule_custom_1", result.sourceId)
        assertEquals("Text from Custom Rule", result.plainLyrics)
        assertFalse("LRCLIB must not be called when custom rule succeeds", lrclibCalled)
    }

    @Test
    fun test_rejected_custom_rule_is_skipped_in_cascade() = runBlocking {
        val ruleEntity = CustomLyricsRuleEntity(
            id = "rule_custom_1",
            domain = "custom.com",
            name = "Custom Community",
            ruleJson = """{"content":{"selector":"#lyrics"}}""",
            isEnabled = true,
            priority = 10
        )

        val mockLyricsDao = mock(LyricsDao::class.java)
        `when`(mockLyricsDao.getActiveRules()).thenReturn(listOf(ruleEntity))

        val mockLrcLib = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                return LyricsResult(true, "LRCLIB fallback text", "LRCLIB", sourceId = "builtin:lrclib")
            }
        }

        val track = TrackEntity(
            trackKey = "test_key",
            title = "Song",
            artist = "Artist",
            sourcePackage = "com.spotify.music"
        )

        val aggregator = AggregatedLyricsProvider(
            lrcLibProvider = mockLrcLib,
            fallbackScraper = null,
            lyricsDao = mockLyricsDao
        )

        // Rejection set contains the custom rule ID
        val rejections = setOf("rule:rule_custom_1")
        val result = aggregator.getLyrics(track, rejections, false)

        assertTrue(result.hasLyrics)
        assertEquals("builtin:lrclib", result.sourceId)
        assertEquals("LRCLIB fallback text", result.lyricsText)
    }
}
