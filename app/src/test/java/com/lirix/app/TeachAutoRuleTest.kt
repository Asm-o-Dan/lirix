package com.lirix.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.lyrics.CustomRuleLyricsProvider
import com.lirix.app.feature.lyrics.RenderResult
import com.lirix.app.feature.lyrics.RuleFetchResult
import com.lirix.app.feature.lyrics.RuleFetcher
import com.lirix.app.feature.lyrics.RuleOutcome
import com.lirix.app.feature.lyrics.RuleRenderBudget
import com.lirix.app.feature.lyrics.RuleRenderer
import com.lirix.app.feature.lyrics.RuleRendererRegistry
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.AutoRuleFailure
import com.lirix.app.ui.AutoRuleRequest
import com.lirix.app.ui.AutoRuleResult
import com.lirix.app.ui.RuleWebViewRenderer
import com.lirix.app.ui.SearchFormDetector
import com.lirix.app.ui.TargetContentType
import com.lirix.app.ui.TeachAutoRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Automatic site rule after a manual binding (owner request 2026-11-13).
 *
 * The real rule engine runs end to end; only the transport and the page renderer are faked
 * with the golden fixtures. Expected texts and URLs are the hand-written `expected*.txt`.
 */
@RunWith(AndroidJUnit4::class)
class TeachAutoRuleTest {

    @After
    fun tearDown() {
        RuleRendererRegistry.factory = null
        RuleRenderBudget.reset()
    }

    private fun fixture(path: String): String =
        javaClass.classLoader?.getResourceAsStream("fixtures/rules/$path")?.bufferedReader()?.use { it.readText() }
            ?: error("Fixture not found: $path")

    private fun expectedText(site: String) = fixture("$site/expected.txt").replace("\r\n", "\n").trimEnd('\n')
    private fun expectedUrl(site: String) = fixture("$site/expected_url.txt").trim()

    private class FixtureFetcher(
        private val searchPrefix: String,
        private val searchHtml: String,
        private val pages: Map<String, String>
    ) : RuleFetcher {
        val requested = mutableListOf<String>()
        override suspend fun fetch(url: String, ruleDomain: String): RuleFetchResult {
            requested += url
            if (url.startsWith(searchPrefix)) return RuleFetchResult.Body(searchHtml, url)
            return pages[url]?.let { RuleFetchResult.Body(it, url) } ?: RuleFetchResult.HttpError(url, 404)
        }
    }

    private fun track(title: String, artist: String) =
        TrackEntity(trackKey = "${artist}_$title", title = title, artist = artist, sourcePackage = "com.example.player")

    private val amdmTrack = track("Огни над рекой", "Тихий Причал")
    private val amdmSearchVisit = "https://amdm.ru/search/?q=%D0%A2%D0%B8%D1%85%D0%B8%D0%B9+%D0%9F%D1%80%D0%B8%D1%87%D0%B0%D0%BB+%D0%9E%D0%B3%D0%BD%D0%B8+%D0%BD%D0%B0%D0%B4+%D1%80%D0%B5%D0%BA%D0%BE%D0%B9"

    private fun amdmRequest(boundText: String = expectedText("amdm"), visited: List<String> = listOf(amdmSearchVisit)) = AutoRuleRequest(
        domain = "amdm.ru",
        contentSelector = "pre.b-podbor__text",
        stripSelectors = listOf(".podbor-tip"),
        contentType = TargetContentType.CHORDS,
        track = amdmTrack,
        boundText = boundText,
        visitedUrls = visited,
        linkSelectorByPage = emptyMap(),
        pageHtml = null,
        pageUrl = expectedUrl("amdm")
    )

    private fun amdmFetcher() =
        FixtureFetcher("https://amdm.ru/search/", fixture("amdm/search.html"), mapOf(expectedUrl("amdm") to fixture("amdm/song.html")))

    private val modernTrack = track("Rain", "Echo Lane")
    private val modernPageWithSearchForm =
        """<html><body><form class="site-search" action="/search"><input type="search" name="q"></form><div id="app"></div></body></html>"""

    private fun modernRequest() = AutoRuleRequest(
        domain = "lyrics-hub.net",
        contentSelector = "div[data-lyrics-container=true]",
        stripSelectors = listOf(".SidebarAd"),
        contentType = TargetContentType.LYRICS,
        track = modernTrack,
        boundText = expectedText("modern"),
        // The user came from a search engine and never searched on the site itself.
        visitedUrls = listOf("https://www.google.com/search?q=echo+lane+rain+lyrics", expectedUrl("modern")),
        linkSelectorByPage = emptyMap(),
        pageHtml = modernPageWithSearchForm,
        pageUrl = expectedUrl("modern")
    )

    // --- Search form discovery ---

    @Test
    fun search_form_of_a_real_page_becomes_a_template() {
        assertEquals(
            listOf("https://amdm.ru/search/?q={query}"),
            SearchFormDetector.detect(fixture("amdm/search.html"), "https://amdm.ru/search/?q=x", "amdm.ru")
        )
    }

    @Test
    fun hidden_fields_are_kept_and_relative_actions_resolved() {
        val html = """<form role="search" action="/find"><input type="hidden" name="type" value="song text"><input name="w"></form>"""
        assertEquals(
            listOf("https://x.com/find?type=song+text&w={query}"),
            SearchFormDetector.detect(html, "https://x.com/artist/song", "x.com")
        )
    }

    @Test
    fun forms_that_are_not_a_site_search_are_ignored() {
        fun detect(html: String) = SearchFormDetector.detect(html, "https://x.com/song", "x.com")
        assertEquals("POST", emptyList<String>(), detect("""<form method="post" action="/search"><input name="q"></form>"""))
        assertEquals("login", emptyList<String>(), detect("""<form action="/search"><input name="q"><input type="password" name="p"></form>"""))
        assertEquals("another site", emptyList<String>(), detect("""<form action="https://evil.example/search"><input name="q"></form>"""))
        assertEquals("not https", emptyList<String>(), detect("""<form action="http://x.com/search"><input name="q"></form>"""))
        assertEquals("newsletter", emptyList<String>(), detect("""<form action="/subscribe"><input type="email" name="email"></form>"""))
        assertEquals("unnamed text field in a non-search form", emptyList<String>(), detect("""<form action="/comment"><input name="body"></form>"""))
    }

    // --- Full synthesis ---

    @Test
    fun static_site_becomes_an_active_rule_that_the_cascade_can_repeat() = runBlocking {
        val fetcher = amdmFetcher()
        val result = TeachAutoRule.synthesize(amdmRequest(), existing = emptyList()) { draft ->
            CustomRuleLyricsProvider(draft, fetcher).resolve(amdmTrack, allowDraft = true)
        }

        if (result !is AutoRuleResult.Activated) fail("Expected an active rule, got $result") else {
            assertFalse(result.dynamic)
            assertTrue(result.rule.isEnabled)
            val config = CustomRuleLyricsProvider.parseConfig(result.rule.ruleJson, "", "")
            assertEquals("ACTIVE", config.status)
            assertEquals("https://amdm.ru/search/?q={query}", config.searchUrlTemplate)
            assertFalse(config.needsRenderer)
            // The stored rule, run the way the automatic cascade runs it, finds the same song.
            val cascade = CustomRuleLyricsProvider(result.rule, amdmFetcher()).getLyrics(amdmTrack)
            assertTrue(cascade.hasLyrics)
            assertEquals(expectedText("amdm"), cascade.lyricsText)
        }
    }

    @Test
    fun script_built_site_becomes_a_rendered_rule_with_a_measured_deadline() = runBlocking {
        val songUrl = expectedUrl("modern")
        fun fetcher() = FixtureFetcher("https://lyrics-hub.net/search", fixture("modern/search.html"), mapOf(songUrl to fixture("modern/song_dynamic.html")))
        val renderer = RuleRenderer { url, _ -> RenderResult.Html(fixture("modern/song.html"), url, elapsedMs = 3_000) }

        val result = TeachAutoRule.synthesize(modernRequest(), existing = emptyList()) { draft ->
            CustomRuleLyricsProvider(draft, fetcher(), renderer).resolve(modernTrack, allowDraft = true)
        }

        if (result !is AutoRuleResult.Activated) fail("Expected an active rule, got $result") else {
            assertTrue(result.dynamic)
            val config = CustomRuleLyricsProvider.parseConfig(result.rule.ruleJson, "", "")
            assertEquals("https://lyrics-hub.net/search?q={query}", config.searchUrlTemplate)
            assertTrue(config.needsRenderer)
            // 3 s observed -> twice that plus a 1.5 s margin.
            assertEquals(7_500L, config.renderTimeoutMs)

            // Cascade without a renderer (unit tests, Desktop): no text, no guess.
            assertFalse(CustomRuleLyricsProvider(result.rule, fetcher()).getLyrics(modernTrack).hasLyrics)

            // Cascade with the app's renderer installed: the rule works and gets its own deadline.
            val deadlines = mutableListOf<Long>()
            RuleRendererRegistry.factory = { _, timeoutMs -> deadlines += timeoutMs; renderer }
            val cascade = CustomRuleLyricsProvider(result.rule, fetcher()).getLyrics(modernTrack)
            assertTrue(cascade.hasLyrics)
            assertEquals(expectedText("modern"), cascade.lyricsText)
            assertEquals(listOf(7_500L), deadlines)
        }
    }

    @Test
    fun static_rules_never_ask_for_a_renderer() = runBlocking {
        var asked = false
        RuleRendererRegistry.factory = { _, _ -> asked = true; RuleRenderer { _, _ -> RenderResult.Failed } }
        val result = TeachAutoRule.synthesize(amdmRequest(), emptyList()) { CustomRuleLyricsProvider(it, amdmFetcher()).resolve(amdmTrack, allowDraft = true) }
        val rule = (result as AutoRuleResult.Activated).rule
        CustomRuleLyricsProvider(rule, amdmFetcher()).getLyrics(amdmTrack)
        assertFalse("A plain-HTTP rule must not create a WebView", asked)
    }

    @Test
    fun rule_that_finds_a_different_text_stays_a_draft() = runBlocking {
        val other = (1..12).joinToString("\n") { "completely unrelated synthetic line number $it" }
        val result = TeachAutoRule.synthesize(amdmRequest(boundText = other), emptyList()) {
            CustomRuleLyricsProvider(it, amdmFetcher()).resolve(amdmTrack, allowDraft = true)
        }
        if (result !is AutoRuleResult.NotActivated) fail("Expected no activation, got $result") else {
            assertEquals(AutoRuleFailure.DIFFERENT_TEXT, result.reason)
            val draft = result.draft!!
            assertFalse(draft.isEnabled)
            assertEquals(RuleOutcome.Draft, CustomRuleLyricsProvider(draft, amdmFetcher()).resolve(amdmTrack))
        }
    }

    @Test
    fun without_any_search_nothing_is_requested_and_nothing_is_guessed() = runBlocking {
        var checks = 0
        val result = TeachAutoRule.synthesize(amdmRequest(visited = listOf(expectedUrl("amdm"))), emptyList()) {
            checks++
            RuleOutcome.Cancelled
        }
        assertEquals(0, checks)
        assertEquals(AutoRuleFailure.NO_SEARCH, (result as AutoRuleResult.NotActivated).reason)
        assertFalse(result.draft!!.isEnabled)
    }

    @Test
    fun song_not_found_on_the_site_is_reported_as_such() = runBlocking {
        val empty = FixtureFetcher("https://amdm.ru/search/", "<html><body><p>Ничего не найдено</p></body></html>", emptyMap())
        val result = TeachAutoRule.synthesize(amdmRequest(), emptyList()) { CustomRuleLyricsProvider(it, empty).resolve(amdmTrack, allowDraft = true) }
        assertEquals(AutoRuleFailure.SONG_NOT_FOUND, (result as AutoRuleResult.NotActivated).reason)
    }

    @Test
    fun existing_rule_is_updated_in_place_on_success_and_left_alone_on_failure() = runBlocking {
        val old = CustomLyricsRuleEntity(
            id = "rule_amdm_old", domain = "amdm.ru", name = "amdm.ru", priority = 7, createdAt = 111L,
            ruleJson = """{"schemaVersion":2,"status":"ACTIVE","site":{"domain":"amdm.ru","name":"amdm.ru"},"search":{"urlTemplate":"https://amdm.ru/search/?q={query}"},"content":{"mode":"CHORDS","contentSelector":"pre.old","stripSelectors":[]}}"""
        )
        val ok = TeachAutoRule.synthesize(amdmRequest(), listOf(old)) { CustomRuleLyricsProvider(it, amdmFetcher()).resolve(amdmTrack, allowDraft = true) }
        val updated = (ok as AutoRuleResult.Activated).rule
        assertEquals("rule_amdm_old", updated.id)
        assertEquals(111L, updated.createdAt)
        assertEquals(7, updated.priority)
        assertEquals("pre.b-podbor__text", CustomRuleLyricsProvider.parseConfig(updated.ruleJson, "", "").contentSelector)

        val failed = TeachAutoRule.synthesize(amdmRequest(boundText = "one two three four five six seven"), listOf(old)) {
            CustomRuleLyricsProvider(it, amdmFetcher()).resolve(amdmTrack, allowDraft = true)
        }
        assertNull("A working rule is not replaced by an unchecked draft", (failed as AutoRuleResult.NotActivated).draft)
    }

    @Test
    fun a_rule_of_the_other_content_type_is_not_touched() = runBlocking {
        val lyricsRule = CustomLyricsRuleEntity(
            id = "rule_amdm_lyrics", domain = "amdm.ru", name = "amdm.ru",
            ruleJson = """{"schemaVersion":2,"status":"ACTIVE","site":{"domain":"amdm.ru","name":"amdm.ru"},"search":{"urlTemplate":"https://amdm.ru/search/?q={query}"},"content":{"mode":"LYRICS","contentSelector":"div.text","stripSelectors":[]}}"""
        )
        val result = TeachAutoRule.synthesize(amdmRequest(), listOf(lyricsRule)) { CustomRuleLyricsProvider(it, amdmFetcher()).resolve(amdmTrack, allowDraft = true) }
        assertFalse("rule_amdm_lyrics" == (result as AutoRuleResult.Activated).rule.id)
    }

    @Test
    fun blank_site_or_selector_is_skipped() = runBlocking {
        val noSite = amdmRequest().copy(domain = "")
        val noSelector = amdmRequest().copy(contentSelector = "")
        assertEquals(AutoRuleResult.Skipped, TeachAutoRule.synthesize(noSite, emptyList()) { fail("must not run"); RuleOutcome.Cancelled })
        assertEquals(AutoRuleResult.Skipped, TeachAutoRule.synthesize(noSelector, emptyList()) { fail("must not run"); RuleOutcome.Cancelled })
    }

    // --- Same-song check ---

    @Test
    fun same_song_ignores_layout_but_not_content() {
        val bound = "Am        C\nGrey water folding under paper skies\n\nG\nI count the windows where the morning lies"
        val sameOtherLayout = "Am C\nGrey water folding under paper skies\nG\nI count the windows   where the morning lies\n"
        assertTrue(TeachAutoRule.sameSong(bound, sameOtherLayout))
        assertFalse("Half of the text", TeachAutoRule.sameSong(bound + "\n" + bound.replace("water", "stone").replace("windows", "doors") + "\nand a long extra verse of other words entirely here", "Am C\nGrey water folding under paper skies"))
        assertFalse(TeachAutoRule.sameSong(bound, "Another song with other words about something else altogether"))
        assertFalse(TeachAutoRule.sameSong(bound, ""))
    }

    // --- Cost control for hidden rendering ---

    @Test
    fun render_deadline_follows_the_measured_time_within_bounds() {
        assertEquals(4_000L, RuleRendererRegistry.timeoutFor(0))
        assertEquals(4_000L, RuleRendererRegistry.timeoutFor(900))
        assertEquals(7_500L, RuleRendererRegistry.timeoutFor(3_000))
        assertEquals(20_000L, RuleRendererRegistry.timeoutFor(60 * 60_000L))
    }

    @Test
    fun content_is_ready_only_when_present_and_no_longer_growing() {
        assertFalse(RuleWebViewRenderer.isSettled(0, 0))
        assertFalse("first appearance", RuleWebViewRenderer.isSettled(0, 400))
        assertFalse("still loading in parts", RuleWebViewRenderer.isSettled(400, 900))
        assertTrue(RuleWebViewRenderer.isSettled(900, 900))
    }

    @Test
    fun background_rendering_is_limited_per_window() {
        RuleRenderBudget.reset()
        repeat(6) { assertTrue("session ${it + 1}", RuleRenderBudget.tryAcquire("site$it.com", now = 1_000L + it)) }
        assertFalse("Seventh session inside ten minutes", RuleRenderBudget.tryAcquire("other.com", now = 2_000L))
        assertTrue("Window passed", RuleRenderBudget.tryAcquire("other.com", now = 1_000L + 10 * 60_000L))
    }

    @Test
    fun a_site_that_keeps_failing_is_paused_for_an_hour() {
        RuleRenderBudget.reset()
        repeat(2) { RuleRenderBudget.report("slow.com", success = false, now = 0L) }
        assertTrue("Two failures are tolerated", RuleRenderBudget.tryAcquire("slow.com", now = 1L))
        RuleRenderBudget.report("slow.com", success = false, now = 2L)
        assertFalse(RuleRenderBudget.tryAcquire("slow.com", now = 3L))
        assertTrue("Other sites are not affected", RuleRenderBudget.tryAcquire("fine.com", now = 3L))
        assertFalse(RuleRenderBudget.tryAcquire("slow.com", now = 2L + 60 * 60_000L - 1))
        assertTrue(RuleRenderBudget.tryAcquire("slow.com", now = 2L + 60 * 60_000L))

        RuleRenderBudget.reset()
        repeat(2) { RuleRenderBudget.report("flaky.com", success = false, now = 0L) }
        RuleRenderBudget.report("flaky.com", success = true, now = 0L)
        repeat(2) { RuleRenderBudget.report("flaky.com", success = false, now = 0L) }
        assertTrue("A success clears the failure count", RuleRenderBudget.tryAcquire("flaky.com", now = 1L))
    }
}
