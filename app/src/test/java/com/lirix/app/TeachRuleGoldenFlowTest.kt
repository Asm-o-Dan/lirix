package com.lirix.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.lyrics.BlockReason
import com.lirix.app.feature.lyrics.CustomRuleLyricsProvider
import com.lirix.app.feature.lyrics.InvalidRuleReason
import com.lirix.app.feature.lyrics.RenderResult
import com.lirix.app.feature.lyrics.RuleFetchResult
import com.lirix.app.feature.lyrics.RuleFetcher
import com.lirix.app.feature.lyrics.RuleOutcome
import com.lirix.app.feature.lyrics.RuleRenderer
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.net.URLDecoder

/**
 * Golden full-flow tests (SPEC-TEACH-06 R5, AGENTS §6):
 * track → search URL → search HTML → ranking → song URL → song HTML → DOM extraction → text.
 *
 * Only the transport is faked (HTML fixtures by URL). Expected URLs and texts live in
 * independent `expected_url.txt` / `expected.txt` files written by hand from the page as a
 * reader sees it; fixture lyrics are synthetic.
 */
@RunWith(AndroidJUnit4::class)
class TeachRuleGoldenFlowTest {

    private fun fixture(path: String): String =
        javaClass.classLoader?.getResourceAsStream("fixtures/rules/$path")?.bufferedReader()?.use { it.readText() }
            ?: error("Fixture not found: $path")

    private fun expectedText(site: String) = fixture("$site/expected.txt").replace("\r\n", "\n").trimEnd('\n')
    private fun expectedUrl(site: String) = fixture("$site/expected_url.txt").trim()

    /** Serves fixtures for exact song URLs and for any URL starting with the search prefix. */
    private class FixtureFetcher(
        private val searchPrefix: String,
        private val searchHtml: String?,
        private val pages: Map<String, String>,
        private val failures: Map<String, RuleFetchResult> = emptyMap()
    ) : RuleFetcher {
        val requested = mutableListOf<String>()
        override suspend fun fetch(url: String, ruleDomain: String): RuleFetchResult {
            requested += url
            failures[url]?.let { return it }
            if (url.startsWith(searchPrefix)) return searchHtml?.let { RuleFetchResult.Body(it, url) } ?: RuleFetchResult.HttpError(url, 404)
            return pages[url]?.let { RuleFetchResult.Body(it, url) } ?: RuleFetchResult.HttpError(url, 404)
        }
    }

    private fun rule(id: String, domain: String, json: String) =
        CustomLyricsRuleEntity(id = id, domain = domain, name = domain, ruleJson = json.trimIndent())

    private fun track(title: String, artist: String) =
        TrackEntity(trackKey = "${artist}_$title", title = title, artist = artist, sourcePackage = "com.example.player")

    private fun queryParam(url: String, name: String): String? =
        URI(url).rawQuery?.split('&')?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it[0] == name }?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }

    private fun assertCleanText(text: String) {
        assertFalse("No unparsed HTML tags", Regex("""<[a-zA-Z/!][^>]*>""").containsMatchIn(text))
        assertFalse("No HTML entities left", Regex("""&(#x?[0-9a-fA-F]+|[a-z]+);""").containsMatchIn(text))
    }

    private val amdmRule = rule(
        "rule_amdm_golden", "amdm.ru", """{
            "schemaVersion": 2, "status": "ACTIVE",
            "site": { "domain": "amdm.ru", "name": "AmDm" },
            "search": { "urlTemplate": "https://amdm.ru/search/?q={query}", "resultListSelector": "table.artist_list a.artist" },
            "content": { "mode": "CHORDS", "contentSelector": "pre.b-podbor__text", "chordsSelector": "pre.b-podbor__text", "stripSelectors": [".podbor-tip"] }
        }"""
    )

    private val modernRule = rule(
        "rule_modern_golden", "lyrics-hub.net", """{
            "schemaVersion": 2, "status": "ACTIVE",
            "site": { "domain": "lyrics-hub.net", "name": "LyricsHub" },
            "search": { "urlTemplate": "https://lyrics-hub.net/search?q={query}", "resultListSelector": "ul.search-results a.result-card__link" },
            "content": { "mode": "LYRICS", "contentSelector": "div[data-lyrics-container=true]", "stripSelectors": [".SidebarAd"] }
        }"""
    )

    @Test
    fun amdm_chords_site_full_flow_preserves_alignment_and_all_lines() = runBlocking {
        val expectedUrl = expectedUrl("amdm")
        val fetcher = FixtureFetcher("https://amdm.ru/search/", fixture("amdm/search.html"), mapOf(expectedUrl to fixture("amdm/song.html")))
        val provider = CustomRuleLyricsProvider(amdmRule, fetcher)
        val track = track("Огни над рекой", "Тихий Причал")

        val outcome = provider.resolve(track)

        if (outcome !is RuleOutcome.Success) fail("Expected success, got $outcome") else {
            assertEquals(2, fetcher.requested.size)
            assertEquals("Тихий Причал Огни над рекой", queryParam(fetcher.requested[0], "q"))
            assertTrue("Query must be URL-encoded", fetcher.requested[0].none { it == ' ' || it in 'А'..'я' })
            assertEquals(expectedUrl, fetcher.requested[1])
            assertEquals(expectedUrl, outcome.songUrl)
            val expected = expectedText("amdm")
            assertTrue("Fixture deliberately has more than 20 lines", expected.lines().count { it.isNotBlank() } > 20)
            assertEquals(expected, outcome.text)
            assertEquals("CHORDS", outcome.mode)
            assertFalse(outcome.usedWebView)
            assertCleanText(outcome.text)
        }

        // Cascade contract: chords mode fills both chords and plain text.
        val result = CustomRuleLyricsProvider(amdmRule, FixtureFetcher("https://amdm.ru/search/", fixture("amdm/search.html"), mapOf(expectedUrl to fixture("amdm/song.html")))).getLyrics(track)
        assertTrue(result.hasLyrics)
        assertEquals("rule:rule_amdm_golden", result.sourceId)
        assertEquals(expectedText("amdm"), result.chords)
        assertEquals(expectedText("amdm"), result.plainLyrics)
    }

    @Test
    fun modern_responsive_site_full_flow_joins_containers_and_drops_ads() = runBlocking {
        val expectedUrl = expectedUrl("modern")
        val fetcher = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), mapOf(expectedUrl to fixture("modern/song.html")))

        val outcome = CustomRuleLyricsProvider(modernRule, fetcher).resolve(track("Rain", "Echo Lane"))

        if (outcome !is RuleOutcome.Success) fail("Expected success, got $outcome") else {
            assertEquals("Echo Lane Rain", queryParam(fetcher.requested[0], "q"))
            assertEquals(expectedUrl, outcome.songUrl)
            assertEquals(expectedText("modern"), outcome.text)
            assertEquals("LYRICS", outcome.mode)
            assertCleanText(outcome.text)
            assertFalse("Ads between containers are not part of the lyrics", outcome.text.contains("Advertisement"))
            assertFalse(outcome.text.contains("Sponsored"))
            assertFalse("Footer metadata is excluded", outcome.text.contains("Written by"))
        }
    }

    @Test
    fun international_site_full_flow_matches_accents_and_artist_outside_link() = runBlocking {
        val expectedUrl = expectedUrl("international")
        val rule = rule(
            "rule_intl_golden", "intl-lyrics.com", """{
                "schemaVersion": 2, "status": "ACTIVE",
                "site": { "domain": "intl-lyrics.com", "name": "Intl Lyrics" },
                "search": { "urlTemplate": "https://search.intl-lyrics.com/search.php?q={query}", "resultListSelector": "table.table-condensed td a" },
                "content": { "mode": "LYRICS", "contentSelector": "div.col-xs-12.col-lg-8 > div:not([class])" }
            }"""
        )
        val fetcher = FixtureFetcher("https://search.intl-lyrics.com/", fixture("international/search.html"), mapOf(expectedUrl to fixture("international/song.html")))

        val outcome = CustomRuleLyricsProvider(rule, fetcher).resolve(track("Canción del Faro", "Mira Sol"))

        if (outcome !is RuleOutcome.Success) fail("Expected success, got $outcome") else {
            assertEquals("Mira Sol Canción del Faro", queryParam(fetcher.requested[0], "q"))
            assertEquals(expectedUrl, outcome.songUrl)
            assertEquals(expectedText("international"), outcome.text)
            assertCleanText(outcome.text)
            assertFalse(outcome.text.contains("Ringtone"))
            assertFalse(outcome.text.contains("Submit Corrections"))
            assertFalse("HTML comments are not text", outcome.text.contains("prohibited"))
        }
    }

    @Test
    fun translation_site_full_flow_reads_translation_column_with_verse_break() = runBlocking {
        val expectedUrl = expectedUrl("amalgama")
        val rule = rule(
            "rule_amalgama_golden", "amalgama-lab.com", """{
                "schemaVersion": 2, "status": "ACTIVE",
                "site": { "domain": "amalgama-lab.com", "name": "Amalgama" },
                "search": { "urlTemplate": "https://www.amalgama-lab.com/search?q={query}", "resultListSelector": ".search-results a" },
                "content": { "mode": "LYRICS", "contentSelector": "#click_area", "stripSelectors": [".original"] }
            }"""
        )
        val fetcher = FixtureFetcher("https://www.amalgama-lab.com/search", fixture("amalgama/search.html"), mapOf(expectedUrl to fixture("amalgama/song.html")))

        val outcome = CustomRuleLyricsProvider(rule, fetcher).resolve(track("Paper Boats", "Harbor Lights"))

        if (outcome !is RuleOutcome.Success) fail("Expected success, got $outcome") else {
            assertEquals(expectedUrl, outcome.songUrl)
            assertEquals(expectedText("amalgama"), outcome.text)
            assertFalse("Original column is stripped", outcome.text.contains("paper into boats"))
            assertFalse("Scripts are not text", outcome.text.contains("renderAds"))
        }
    }

    @Test
    fun script_rendered_page_without_renderer_reports_unsupported_dynamic_content() = runBlocking {
        val songUrl = expectedUrl("modern")
        val fetcher = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), mapOf(songUrl to fixture("modern/song_dynamic.html")))

        val outcome = CustomRuleLyricsProvider(modernRule, fetcher).resolve(track("Rain", "Echo Lane"))

        assertEquals(RuleOutcome.UnsupportedDynamicContent(songUrl), outcome)
        assertFalse(CustomRuleLyricsProvider(modernRule, fetcher).getLyrics(track("Rain", "Echo Lane")).hasLyrics)
    }

    @Test
    fun script_rendered_page_uses_webview_fallback_once_with_the_content_selector() = runBlocking {
        val songUrl = expectedUrl("modern")
        val fetcher = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), mapOf(songUrl to fixture("modern/song_dynamic.html")))
        val renderCalls = mutableListOf<Pair<String, String>>()
        val renderer = RuleRenderer { url, selector ->
            renderCalls += url to selector
            RenderResult.Html(fixture("modern/song.html"), url)
        }

        val outcome = CustomRuleLyricsProvider(modernRule, fetcher, renderer).resolve(track("Rain", "Echo Lane"))

        assertEquals(listOf(songUrl to "div[data-lyrics-container=true]"), renderCalls)
        if (outcome !is RuleOutcome.Success) fail("Expected success, got $outcome") else {
            assertTrue(outcome.usedWebView)
            assertEquals(expectedText("modern"), outcome.text)
        }
    }

    @Test
    fun search_results_found_only_through_webview_mark_the_success_as_webview_dependent() = runBlocking {
        val songUrl = expectedUrl("modern")
        // Static search page has no result links; they appear only in the rendered page.
        val fetcher = FixtureFetcher("https://lyrics-hub.net/search", "<html><body><div id='app'></div></body></html>", mapOf(songUrl to fixture("modern/song.html")))
        val renderCalls = mutableListOf<String>()
        val renderer = RuleRenderer { url, _ ->
            renderCalls += url
            RenderResult.Html(fixture("modern/search.html"), url)
        }

        val outcome = CustomRuleLyricsProvider(modernRule, fetcher, renderer).resolve(track("Rain", "Echo Lane"))

        assertEquals(1, renderCalls.size)
        if (outcome !is RuleOutcome.Success) fail("Expected success, got $outcome") else {
            assertTrue("The cascade could not repeat this without a renderer", outcome.usedWebView)
            assertEquals(expectedText("modern"), outcome.text)
        }
        // Without a renderer the same rule finds nothing instead of guessing.
        assertTrue(CustomRuleLyricsProvider(modernRule, fetcher).resolve(track("Rain", "Echo Lane")) is RuleOutcome.NotFound)
    }

    @Test
    fun webview_timeout_and_unavailability_are_typed_failures() = runBlocking {
        val songUrl = expectedUrl("modern")
        fun fetcher() = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), mapOf(songUrl to fixture("modern/song_dynamic.html")))
        val t = track("Rain", "Echo Lane")

        assertEquals(RuleOutcome.Timeout(songUrl), CustomRuleLyricsProvider(modernRule, fetcher()) { _, _ -> RenderResult.Timeout }.resolve(t))
        assertEquals(RuleOutcome.WebViewUnavailable(songUrl), CustomRuleLyricsProvider(modernRule, fetcher()) { _, _ -> RenderResult.Unavailable }.resolve(t))
        assertEquals(RuleOutcome.SelectorNotMatched(songUrl, usedWebView = true), CustomRuleLyricsProvider(modernRule, fetcher()) { _, _ -> RenderResult.NotReady(null) }.resolve(t))
        assertEquals(RuleOutcome.Cancelled, CustomRuleLyricsProvider(modernRule, fetcher()) { _, _ -> RenderResult.Cancelled }.resolve(t))
    }

    @Test
    fun network_failures_do_not_trigger_webview_fallback() = runBlocking {
        val songUrl = expectedUrl("modern")
        var rendered = false
        val renderer = RuleRenderer { _, _ -> rendered = true; RenderResult.Failed }

        val timeout = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), emptyMap(), mapOf(songUrl to RuleFetchResult.Timeout(songUrl)))
        assertEquals(RuleOutcome.Timeout(songUrl), CustomRuleLyricsProvider(modernRule, timeout, renderer).resolve(track("Rain", "Echo Lane")))

        val limited = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), emptyMap(), mapOf(songUrl to RuleFetchResult.Blocked(songUrl, BlockReason.RATE_LIMITED, 429)))
        assertEquals(RuleOutcome.Blocked(songUrl, BlockReason.RATE_LIMITED, 429), CustomRuleLyricsProvider(modernRule, limited, renderer).resolve(track("Rain", "Echo Lane")))
        assertEquals("No retry after HTTP 429", 2, limited.requested.size)

        assertFalse(rendered)
    }

    @Test
    fun unknown_artist_with_two_songs_of_the_same_title_is_ambiguous_and_fetches_no_song() = runBlocking {
        val fetcher = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), emptyMap())

        val outcome = CustomRuleLyricsProvider(modernRule, fetcher).resolve(track("Rain", "Unknown Artist"))

        assertTrue("Expected AmbiguousMatch, got $outcome", outcome is RuleOutcome.AmbiguousMatch)
        val urls = (outcome as RuleOutcome.AmbiguousMatch).candidates.map { it.url }.toSet()
        assertTrue(urls.contains("https://lyrics-hub.net/echo-lane/rain-lyrics"))
        assertTrue(urls.contains("https://lyrics-hub.net/marlow-kids/rain-lyrics"))
        assertEquals("Rain", queryParam(fetcher.requested[0], "q"))
        assertEquals("No song page may be fetched for an ambiguous match", 1, fetcher.requested.size)
    }

    @Test
    fun unrelated_results_are_low_confidence_and_existing_lyrics_are_not_replaced() = runBlocking {
        val fetcher = FixtureFetcher("https://amdm.ru/search/", fixture("amdm/search.html"), emptyMap())

        val outcome = CustomRuleLyricsProvider(amdmRule, fetcher).resolve(track("Enter Sandman", "Metallica"))

        assertTrue("Expected LowConfidence, got $outcome", outcome is RuleOutcome.LowConfidence)
        assertEquals(1, fetcher.requested.size)
        val cascade = CustomRuleLyricsProvider(amdmRule, FixtureFetcher("https://amdm.ru/search/", fixture("amdm/search.html"), emptyMap()))
            .getLyrics(track("Enter Sandman", "Metallica"))
        assertFalse(cascade.hasLyrics)
        assertNull(cascade.plainLyrics)
    }

    @Test
    fun empty_search_page_is_not_found() = runBlocking {
        val fetcher = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search_empty.html"), emptyMap())

        val outcome = CustomRuleLyricsProvider(modernRule, fetcher).resolve(track("Nonexistent Song", "Nobody"))

        assertTrue("Expected NotFound, got $outcome", outcome is RuleOutcome.NotFound)
    }

    @Test
    fun draft_never_runs_in_cascade_but_can_be_previewed() = runBlocking {
        val draft = rule("rule_draft", "lyrics-hub.net", modernRule.ruleJson.replace("\"ACTIVE\"", "\"DRAFT\""))
        val fetcher = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), mapOf(expectedUrl("modern") to fixture("modern/song.html")))
        val provider = CustomRuleLyricsProvider(draft, fetcher)

        assertEquals(RuleOutcome.Draft, provider.resolve(track("Rain", "Echo Lane")))
        val cascade = provider.getLyrics(track("Rain", "Echo Lane"))
        assertFalse(cascade.hasLyrics)
        assertTrue(cascade.lyricsText.contains("DRAFT"))
        assertTrue("A draft makes no network requests", fetcher.requested.isEmpty())

        val preview = provider.resolve(track("Rain", "Echo Lane"), allowDraft = true)
        assertTrue("Preview validates the draft, got $preview", preview is RuleOutcome.Success)
    }

    @Test
    fun invalid_rules_fail_before_any_request() = runBlocking {
        val fetcher = FixtureFetcher("https://", "<html></html>", emptyMap())
        fun outcome(json: String) = runBlocking { CustomRuleLyricsProvider(rule("r", "lyrics-hub.net", json), fetcher).resolve(track("Rain", "Echo Lane")) }

        assertEquals(
            RuleOutcome.InvalidRule(InvalidRuleReason.UNSUPPORTED_SCHEMA),
            outcome("""{"schemaVersion":3,"status":"ACTIVE","search":{"urlTemplate":"https://lyrics-hub.net/s?q={query}"},"content":{"contentSelector":".l"}}""")
        )
        assertEquals(
            RuleOutcome.InvalidRule(InvalidRuleReason.MISSING_SEARCH_TEMPLATE),
            outcome("""{"schemaVersion":2,"status":"ACTIVE","content":{"contentSelector":".l"}}""")
        )
        assertEquals(
            RuleOutcome.InvalidRule(InvalidRuleReason.INVALID_SEARCH_TEMPLATE),
            outcome("""{"schemaVersion":2,"status":"ACTIVE","search":{"urlTemplate":"https://evil.example.com/s?q={query}"},"content":{"contentSelector":".l"}}""")
        )
        assertEquals(
            RuleOutcome.InvalidRule(InvalidRuleReason.INVALID_SEARCH_TEMPLATE),
            outcome("""{"schemaVersion":2,"status":"ACTIVE","search":{"urlTemplate":"http://lyrics-hub.net/s?q={query}"},"content":{"contentSelector":".l"}}""")
        )
        assertEquals(
            RuleOutcome.InvalidSelector("div[data-x"),
            outcome("""{"schemaVersion":2,"status":"ACTIVE","search":{"urlTemplate":"https://lyrics-hub.net/s?q={query}"},"content":{"contentSelector":"div[data-x"}}""")
        )
        assertTrue(fetcher.requested.isEmpty())
    }
}
