package com.lirix.app

import com.lirix.app.feature.lyrics.BlockReason
import com.lirix.app.feature.lyrics.CandidateRanking
import com.lirix.app.feature.lyrics.CustomRuleConfig
import com.lirix.app.feature.lyrics.CustomRuleLyricsProvider
import com.lirix.app.feature.lyrics.CustomRuleLyricsProvider.Companion.ExtractResult
import com.lirix.app.feature.lyrics.RuleCandidateRanker
import com.lirix.app.feature.lyrics.RuleOutcome
import com.lirix.app.feature.lyrics.RuleUrlPolicy
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.SearchTemplateDetector
import com.lirix.app.ui.TeachBridgeGuard
import com.lirix.app.ui.TeachModeBindingHelper
import com.lirix.app.ui.TeachRuleFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/** Unit tests for the TeachMode rule engine pieces (SPEC-TEACH-06 R2, R3, R7, R9, R11). */
class TeachRuleEngineUnitTest {

    private fun track(title: String, artist: String) =
        TrackEntity(trackKey = "k", title = title, artist = artist, sourcePackage = "p")

    // --- URL policy (R11) ---

    @Test
    fun url_policy_allows_only_https_on_the_rule_domain() {
        assertNull(RuleUrlPolicy.check("https://amdm.ru/akkordi/x/", "amdm.ru"))
        assertNull(RuleUrlPolicy.check("https://www.amdm.ru/x", "amdm.ru"))
        assertNull(RuleUrlPolicy.check("https://search.intl-lyrics.com/search.php?q=a", "www.intl-lyrics.com"))
        assertEquals(BlockReason.UNSAFE_URL, RuleUrlPolicy.check("http://amdm.ru/x", "amdm.ru"))
        assertEquals(BlockReason.UNSAFE_URL, RuleUrlPolicy.check("file:///etc/hosts", "amdm.ru"))
        assertEquals(BlockReason.UNSAFE_URL, RuleUrlPolicy.check("https://127.0.0.1/x", "127.0.0.1"))
        assertEquals(BlockReason.UNSAFE_URL, RuleUrlPolicy.check("https://localhost/x", "localhost"))
        assertEquals(BlockReason.UNSAFE_URL, RuleUrlPolicy.check("https://[::1]/x", "amdm.ru"))
        assertEquals(BlockReason.UNSAFE_URL, RuleUrlPolicy.check("https://user:pw@amdm.ru/x", "amdm.ru"))
        assertEquals(BlockReason.UNSAFE_URL, RuleUrlPolicy.check("https://amdm.ru:8080/x", "amdm.ru"))
        assertEquals(BlockReason.UNSAFE_URL, RuleUrlPolicy.check("https://router.local/x", "router.local"))
        assertEquals(BlockReason.FOREIGN_HOST, RuleUrlPolicy.check("https://amdm.ru.evil.com/x", "amdm.ru"))
        assertEquals(BlockReason.FOREIGN_HOST, RuleUrlPolicy.check("https://notamdm.ru/x", "amdm.ru"))
    }

    @Test
    fun private_and_local_addresses_are_rejected_after_dns() {
        listOf("10.1.2.3", "192.168.0.10", "172.16.5.4", "127.0.0.1", "169.254.10.1", "100.64.1.1", "0.0.0.0", "::1", "fc00::1", "fe80::1")
            .forEach { assertTrue(it, RuleUrlPolicy.isPrivateAddress(InetAddress.getByName(it))) }
        listOf("93.184.216.34", "8.8.8.8", "2606:4700::1111")
            .forEach { assertFalse(it, RuleUrlPolicy.isPrivateAddress(InetAddress.getByName(it))) }
    }

    // --- Search URL building (R2) ---

    @Test
    fun query_placeholder_is_encoded_for_its_url_component() {
        val t = track("Rain & Fire", "Echo Lane")
        assertEquals("https://x.com/search?q=Echo+Lane+Rain+%26+Fire", CustomRuleLyricsProvider.buildSearchUrl("https://x.com/search?q={query}", t))
        assertEquals("https://x.com/search/Echo%20Lane%20Rain%20%26%20Fire/", CustomRuleLyricsProvider.buildSearchUrl("https://x.com/search/{query}/", t))
        assertEquals("https://x.com/a/Echo%20Lane?t=Rain+%26+Fire", CustomRuleLyricsProvider.buildSearchUrl("https://x.com/a/{artist}?t={title}", t))
        assertEquals("Rain", CustomRuleLyricsProvider.buildQuery(track("Rain", "Unknown Artist")))
    }

    // --- Search template from navigation (R2) ---

    @Test
    fun template_is_derived_from_the_users_site_search_not_invented() {
        val visited = listOf(
            "https://www.google.com/search?q=echo+lane+rain",
            "https://lyrics-hub.net/",
            "https://lyrics-hub.net/search?q=Echo+Lane+Rain&src=header",
            "https://lyrics-hub.net/echo-lane/rain-lyrics"
        )
        val detected = SearchTemplateDetector.detect(visited, "lyrics-hub.net", listOf("Echo Lane Rain", "Rain", "Echo Lane"))
        assertEquals("https://lyrics-hub.net/search?q={query}&src=header", detected?.template)
        assertEquals("https://lyrics-hub.net/search?q=Echo+Lane+Rain&src=header", detected?.sourceUrl)
    }

    @Test
    fun template_detection_handles_path_queries_cyrillic_and_generic_parameter_names() {
        assertEquals(
            "https://lyrics-hub.net/search/{query}",
            SearchTemplateDetector.detect(listOf("https://lyrics-hub.net/search/echo%20lane%20rain"), "lyrics-hub.net", listOf("Echo Lane Rain"))?.template
        )
        assertEquals(
            "https://amdm.ru/search/?q={query}",
            SearchTemplateDetector.detect(
                listOf("https://amdm.ru/search/?q=%D0%9E%D0%B3%D0%BD%D0%B8+%D0%BD%D0%B0%D0%B4+%D1%80%D0%B5%D0%BA%D0%BE%D0%B9"),
                "amdm.ru",
                listOf("Тихий Причал Огни над рекой", "Огни над рекой")
            )?.template
        )
        // The user typed something else: a well-known search parameter is still recognised.
        assertEquals(
            "https://site.org/find?s={query}&page=1",
            SearchTemplateDetector.detect(listOf("https://site.org/find?s=whatever&page=1"), "site.org", listOf("Rain"))?.template
        )
    }

    @Test
    fun no_template_without_a_search_on_the_rule_domain() {
        assertNull(SearchTemplateDetector.detect(listOf("https://lyrics-hub.net/echo-lane/rain-lyrics"), "lyrics-hub.net", listOf("Rain")))
        assertNull(SearchTemplateDetector.detect(listOf("https://other.net/search?q=Rain"), "lyrics-hub.net", listOf("Rain")))
        assertNull(SearchTemplateDetector.detect(listOf("http://lyrics-hub.net/search?q=Rain"), "lyrics-hub.net", listOf("Rain")))
        assertNull(SearchTemplateDetector.detect(emptyList(), "lyrics-hub.net", listOf("Rain")))
    }

    // --- Ranking (R7) ---

    private val searchPage = "https://songs.example.org/search?q=x"

    @Test
    fun ranking_resolves_relative_links_against_the_actual_page_and_skips_foreign_hosts() {
        val html = """
            <ul class="r">
              <li><a href="https://evil.example.net/rain">Rain - Echo Lane</a></li>
              <li><a href="https://127.0.0.1/rain">Rain - Echo Lane</a></li>
              <li><a href="javascript:void(0)">Rain - Echo Lane</a></li>
              <li><a href="lyrics/echo-lane-rain.html">Rain - Echo Lane</a></li>
            </ul>
        """
        val ranking = RuleCandidateRanker.rank(html, "https://songs.example.org/find/index.php?q=x", "ul.r a", "Rain", "Echo Lane", "songs.example.org")
        assertTrue("Got $ranking", ranking is CandidateRanking.Selected)
        assertEquals("https://songs.example.org/find/lyrics/echo-lane-rain.html", (ranking as CandidateRanking.Selected).best.url)
    }

    @Test
    fun version_markers_absent_from_the_track_are_penalised() {
        val html = """
            <a class="s" href="/rain-live">Echo Lane - Rain (Live)</a>
            <a class="s" href="/rain">Echo Lane - Rain</a>
        """
        val selected = RuleCandidateRanker.rank(html, searchPage, "a.s", "Rain", "Echo Lane", "songs.example.org") as CandidateRanking.Selected
        assertEquals("https://songs.example.org/rain", selected.best.url)

        val live = RuleCandidateRanker.rank(html, searchPage, "a.s", "Rain (Live)", "Echo Lane", "songs.example.org") as CandidateRanking.Selected
        assertEquals("https://songs.example.org/rain-live", live.best.url)
    }

    @Test
    fun several_versions_of_the_same_song_follow_the_sites_order_instead_of_being_ambiguous() {
        val html = """
            <table class="t">
              <tr><td><a href="/akkordi/1/">Тихий Причал - Огни над рекой</a></td></tr>
              <tr><td><a href="/akkordi/2/">Тихий Причал - Огни над рекой</a></td></tr>
            </table>
        """
        val selected = RuleCandidateRanker.rank(html, searchPage, "table.t a", "Огни над рекой", "Тихий Причал", "songs.example.org")
        assertEquals("https://songs.example.org/akkordi/1/", (selected as CandidateRanking.Selected).best.url)
    }

    @Test
    fun ranking_normalises_case_yo_diacritics_and_featuring_markers() {
        val html = """<a class="s" href="/x">ЁЛКИ — Звёздный ВЕТЕР</a><a class="s" href="/y">Other band — Другая песня</a>"""
        val selected = RuleCandidateRanker.rank(html, searchPage, "a.s", "Звездный ветер (feat. Гость)", "Елки", "songs.example.org")
        assertEquals("https://songs.example.org/x", (selected as CandidateRanking.Selected).best.url)
    }

    // AGENTS 7.4: an uncertain match is never treated as confirmed.

    @Test
    fun same_title_by_another_artist_only_is_low_confidence() {
        val html = """<a class="s" href="/night-owls/rain">Night Owls - Rain</a>"""
        val ranking = RuleCandidateRanker.rank(html, searchPage, "a.s", "Rain", "Echo Lane", "songs.example.org")
        assertTrue("Got $ranking", ranking is CandidateRanking.LowConfidence)
    }

    @Test
    fun longer_titles_containing_the_track_title_are_other_songs() {
        val html = """
            <a class="s" href="/echo-lane/rain-dance">Echo Lane - Rain Dance</a>
            <a class="s" href="/echo-lane/after-the-rain">Echo Lane - After the Rain</a>
        """
        val ranking = RuleCandidateRanker.rank(html, searchPage, "a.s", "Rain", "Echo Lane", "songs.example.org")
        assertTrue("Got $ranking", ranking is CandidateRanking.LowConfidence)

        // The exact title next to them is still selected.
        val withExact = html + """<a class="s" href="/echo-lane/rain">Echo Lane - Rain</a>"""
        val selected = RuleCandidateRanker.rank(withExact, searchPage, "a.s", "Rain", "Echo Lane", "songs.example.org")
        assertEquals("https://songs.example.org/echo-lane/rain", (selected as CandidateRanking.Selected).best.url)
    }

    @Test
    fun artist_printed_next_to_a_neighbouring_result_is_not_borrowed() {
        val html = """
            <div class="results">
              <a href="/1">Rain</a> Night Owls
              <a href="/2">Storm</a> Echo Lane
            </div>
        """
        val ranking = RuleCandidateRanker.rank(html, searchPage, "div.results a", "Rain", "Echo Lane", "songs.example.org")
        assertTrue("Got $ranking", ranking is CandidateRanking.LowConfidence)

        // One result per row: the row's own artist confirms the match.
        val rows = """
            <ul class="r">
              <li><a href="/1">Rain</a> Night Owls</li>
              <li><a href="/2">Rain</a> Echo Lane</li>
            </ul>
        """
        val selected = RuleCandidateRanker.rank(rows, searchPage, "ul.r a", "Rain", "Echo Lane", "songs.example.org")
        assertEquals("https://songs.example.org/2", (selected as CandidateRanking.Selected).best.url)
    }

    @Test
    fun invalid_result_selector_and_empty_pages_are_reported() {
        assertEquals(CandidateRanking.InvalidSelector("a[href"), RuleCandidateRanker.rank("<a href='/x'>x</a>", searchPage, "a[href", "x", "y", "songs.example.org"))
        assertEquals(CandidateRanking.NoCandidates, RuleCandidateRanker.rank("<p>Nothing found</p>", searchPage, null, "Rain", "Echo Lane", "songs.example.org"))
    }

    // --- Extraction modes and DOM edge cases (R3, R9) ---

    private val chordHtml = """
        <div class="song">
          <p><span class="ch">Am</span>&nbsp;&nbsp;&nbsp;&nbsp;<span class="ch">F</span><br>First line here<br><span class="ch">C</span>&nbsp;&nbsp;<span class="ch">G</span><br>Second line here</p>
        </div>
    """

    @Test
    fun lyrics_mode_drops_chord_annotations_while_chords_mode_keeps_alignment() {
        val config = CustomRuleConfig(domain = "s.org", name = "s", contentSelector = ".song", chordsSelector = ".ch", mode = "LYRICS")
        assertEquals(ExtractResult.Text("First line here\nSecond line here"), CustomRuleLyricsProvider.extract(chordHtml, config))

        val chords = config.copy(mode = "MIXED")
        assertEquals(ExtractResult.Text("Am    F\nFirst line here\nC  G\nSecond line here"), CustomRuleLyricsProvider.extract(chordHtml, chords))
    }

    @Test
    fun overlapping_and_repeated_matches_are_extracted_once_in_document_order() {
        val html = """
            <div class="lyrics"><p class="verse">One<br>Two</p><p class="verse">Three<br>Four</p></div>
            <div class="chorus">Chorus line</div>
        """
        assertEquals(
            ExtractResult.Text("One\nTwo\n\nThree\nFour\n\nChorus line"),
            CustomRuleLyricsProvider.extract(html, ".lyrics, .lyrics p.verse, .chorus", emptyList(), "LYRICS")
        )
    }

    @Test
    fun strip_selector_never_removes_the_matched_container_itself() {
        val html = """<div class="lyrics ad-free">Keep<br><span class="lyrics-note">note</span>me</div>"""
        assertEquals(
            ExtractResult.Text("Keep\nme"),
            CustomRuleLyricsProvider.extract(html, "div.lyrics", listOf(".lyrics, .lyrics-note"), "LYRICS")
        )
    }

    @Test
    fun nested_verse_blocks_keep_boundaries_and_source_whitespace_collapses_in_lyrics_mode() {
        val html = """
            <article class="text">
              <div class="v"><div>Line   one
                 continues here</div><div>Line two</div></div>
              <div class="v"><div>Line three</div></div>
            </article>
        """
        assertEquals(
            ExtractResult.Text("Line one continues here\nLine two\nLine three"),
            CustomRuleLyricsProvider.extract(html, "article.text", emptyList(), "LYRICS")
        )
    }

    @Test
    fun extraction_distinguishes_no_match_empty_and_invalid_selector() {
        assertEquals(ExtractResult.NoMatch, CustomRuleLyricsProvider.extract("<div>x</div>", ".missing", emptyList(), "LYRICS"))
        assertEquals(ExtractResult.Empty, CustomRuleLyricsProvider.extract("<div class='l'> <script>x()</script> </div>", ".l", emptyList(), "LYRICS"))
        assertEquals(ExtractResult.InvalidSelector("div[x"), CustomRuleLyricsProvider.extract("<div>x</div>", "div[x", emptyList(), "LYRICS"))
    }

    @Test
    fun legacy_itemprop_selector_is_normalised_to_css() {
        assertEquals("[itemprop=\"lyrics\"]", CustomRuleLyricsProvider.normalizeSelector("itemprop=lyrics"))
        assertEquals(
            ExtractResult.Text("Text in itemprop"),
            CustomRuleLyricsProvider.extract("<div itemprop=\"lyrics\">Text in itemprop</div>", "itemprop=lyrics", emptyList(), "LYRICS")
        )
    }

    // --- Activation and bridge guards (review items 6, 12) ---

    @Test
    fun only_a_successful_check_can_be_activated_and_a_rendered_one_stores_its_deadline() {
        // Contract changed 2026-11-13 (owner request): a success that needed the page renderer may
        // be activated, because the rule now records `render.engine = WEBVIEW` for the cascade.
        fun success(usedWebView: Boolean) = RuleOutcome.Success("text", "https://x.com/s", "https://x.com/q", "q", 0.9f, "LYRICS", usedWebView, renderMs = 2_000)
        assertTrue(TeachRuleFactory.canActivate(success(usedWebView = false)))
        assertTrue(TeachRuleFactory.canActivate(success(usedWebView = true)))
        assertNull("Plain HTTP was enough", TeachRuleFactory.renderTimeoutFor(success(usedWebView = false)))
        assertEquals(5_500L, TeachRuleFactory.renderTimeoutFor(success(usedWebView = true)))
        assertNull(TeachRuleFactory.renderTimeoutFor(RuleOutcome.NotFound("https://x.com/q", "q")))
        assertFalse(TeachRuleFactory.canActivate(RuleOutcome.NotFound("https://x.com/q", "q")))
        assertFalse(TeachRuleFactory.canActivate(null))
    }

    @Test
    fun bridge_ignores_selections_outside_selection_mode_and_oversized_values() {
        assertTrue(TeachBridgeGuard.acceptsSelection(true, "div.lyrics", "[]", "Line"))
        assertFalse("Page script calling the bridge while browsing", TeachBridgeGuard.acceptsSelection(false, "div.lyrics", "[]", "Line"))
        assertFalse(TeachBridgeGuard.acceptsSelection(true, " ", "[]", "Line"))
        assertFalse(TeachBridgeGuard.acceptsSelection(true, "a".repeat(TeachBridgeGuard.MAX_SELECTOR_LENGTH + 1), "[]", "Line"))
        assertFalse(TeachBridgeGuard.acceptsSelection(true, "div", "x".repeat(TeachBridgeGuard.MAX_STRIP_JSON_LENGTH + 1), "Line"))
        assertFalse(TeachBridgeGuard.acceptsSelection(true, "div", "[]", "x".repeat(TeachBridgeGuard.MAX_TEXT_LENGTH + 1)))
        assertTrue(TeachBridgeGuard.acceptsSelection(true, "div", "[]", "x".repeat(TeachBridgeGuard.MAX_TEXT_LENGTH)))
    }

    // --- Replacement guard (UI review S2) ---

    @Test
    fun replacement_guard_triggers_only_for_noticeably_shorter_text() {
        val song = (1..20).joinToString("\n") { "Line number $it" }
        assertFalse("Nothing stored → one-tap binding", TeachModeBindingHelper.isSignificantShrink("", "One line"))
        assertTrue(TeachModeBindingHelper.isSignificantShrink(song, "Line number 7"))
        assertFalse(TeachModeBindingHelper.isSignificantShrink(song, song))
        assertFalse(TeachModeBindingHelper.isSignificantShrink(song, song.lines().drop(4).joinToString("\n")))
        assertFalse("Longer text replaces silently", TeachModeBindingHelper.isSignificantShrink(song, song + "\nExtra"))
        assertTrue("Same line count but most characters gone", TeachModeBindingHelper.isSignificantShrink(song, (1..20).joinToString("\n") { "L$it" }))
    }
}
