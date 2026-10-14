package com.lirix.app

import com.lirix.app.feature.blacklist.BlacklistMatcher
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD Unit-tests for BlacklistMatcher engine (TASK-BLK-QA / SPEC-BLK-01):
 * - Verifies TITLE matching with CONTAINS_IGNORE_CASE, EXACT, and REGEX modes.
 * - Verifies ARTIST and PACKAGE_NAME matching across all modes.
 * - Verifies disabled rules (isEnabled = false) are always ignored.
 * - Verifies error resilience: invalid regex patterns must safely return false without crashing.
 * - Verifies blank or empty patterns safely return false.
 * - Verifies multi-rule aggregation via isBlacklisted.
 */
class BlacklistEngineTest {

    // ------------------------------------------------------------------------
    // 1. TITLE Matching Scenarios
    // ------------------------------------------------------------------------

    @Test
    fun testTitleContainsIgnoreCase() {
        val rule = BlacklistRuleEntity(
            id = "title_contains_1",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "реклама",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )

        assertTrue(
            "Should match lowercase substring",
            BlacklistMatcher.matches("Встроенная реклама трека", "Артист", "com.music", rule)
        )
        assertTrue(
            "Should match uppercase substring",
            BlacklistMatcher.matches("Встроенная РЕКЛАМА трека", "Артист", "com.music", rule)
        )
        assertTrue(
            "Should match with untrimmed candidate",
            BlacklistMatcher.matches("   РЕКЛАМА   ", "Артист", "com.music", rule)
        )
        assertFalse(
            "Should not match unrelated title",
            BlacklistMatcher.matches("Обычный трек", "Артист", "com.music", rule)
        )
    }

    @Test
    fun testTitleExactMatch() {
        val rule = BlacklistRuleEntity(
            id = "title_exact_1",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "Intro",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = true
        )

        assertTrue(
            "Exact match should succeed (same case)",
            BlacklistMatcher.matches("Intro", "Artist", "com.music", rule)
        )
        assertTrue(
            "Exact match should be case-insensitive",
            BlacklistMatcher.matches("intro", "Artist", "com.music", rule)
        )
        assertTrue(
            "Exact match should ignore outer whitespace",
            BlacklistMatcher.matches("  INTRO  ", "Artist", "com.music", rule)
        )
        assertFalse(
            "Exact match should fail for substring / longer title",
            BlacklistMatcher.matches("Intro - Live at Wembley", "Artist", "com.music", rule)
        )
    }

    @Test
    fun testTitleRegexMatch() {
        val rule = BlacklistRuleEntity(
            id = "title_regex_1",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "^Track\\s+\\d+$",
            matchMode = BlacklistMatchMode.REGEX,
            isEnabled = true
        )

        assertTrue(
            "Should match regex 'Track 01'",
            BlacklistMatcher.matches("Track 01", "Artist", "com.music", rule)
        )
        assertTrue(
            "Should match regex case-insensitively 'track 999'",
            BlacklistMatcher.matches("track 999", "Artist", "com.music", rule)
        )
        assertFalse(
            "Should not match non-digits",
            BlacklistMatcher.matches("Track Bonus", "Artist", "com.music", rule)
        )
    }

    // ------------------------------------------------------------------------
    // 2. ARTIST Matching Scenarios
    // ------------------------------------------------------------------------

    @Test
    fun testArtistContainsIgnoreCase() {
        val rule = BlacklistRuleEntity(
            id = "artist_contains_1",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "Инстасамка",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )

        assertTrue(
            "Should match artist in collaboration",
            BlacklistMatcher.matches("За деньги да", "DJ Remix & ИНСТАСАМКА", "com.music", rule)
        )
        assertFalse(
            "Should not match another artist",
            BlacklistMatcher.matches("За деньги да", "Linkin Park", "com.music", rule)
        )
    }

    @Test
    fun testArtistExactMatch() {
        val rule = BlacklistRuleEntity(
            id = "artist_exact_1",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "Моргенштерн",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = true
        )

        assertTrue(
            "Exact match case-insensitive should succeed",
            BlacklistMatcher.matches("Cadillac", "моргенштерн", "com.music", rule)
        )
        assertFalse(
            "Exact match should not match duo / collab",
            BlacklistMatcher.matches("Cadillac", "Моргенштерн & Элджей", "com.music", rule)
        )
    }

    @Test
    fun testArtistRegexMatch() {
        val rule = BlacklistRuleEntity(
            id = "artist_regex_1",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = ".*(feat\\.|ft\\.).*",
            matchMode = BlacklistMatchMode.REGEX,
            isEnabled = true
        )

        assertTrue(
            "Should match artist featuring notation",
            BlacklistMatcher.matches("Numb / Encore", "Jay-Z feat. Linkin Park", "com.music", rule)
        )
        assertTrue(
            "Should match 'ft.' notation case-insensitively",
            BlacklistMatcher.matches("Song", "Artist FT. Guest", "com.music", rule)
        )
        assertFalse(
            "Should not match solo artist without featuring",
            BlacklistMatcher.matches("In The End", "Linkin Park", "com.music", rule)
        )
    }

    // ------------------------------------------------------------------------
    // 3. PACKAGE_NAME Matching Scenarios
    // ------------------------------------------------------------------------

    @Test
    fun testPackageNameExactMatch() {
        val rule = BlacklistRuleEntity(
            id = "pkg_exact_1",
            ruleType = BlacklistRuleType.PACKAGE_NAME,
            pattern = "org.telegram.messenger",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = true
        )

        assertTrue(
            "Should match exact Telegram package",
            BlacklistMatcher.matches("Voice Note", "Voice", "org.telegram.messenger", rule)
        )
        assertFalse(
            "Should not match Spotify package",
            BlacklistMatcher.matches("Song", "Singer", "com.spotify.music", rule)
        )
    }

    @Test
    fun testPackageNameContainsIgnoreCase() {
        val rule = BlacklistRuleEntity(
            id = "pkg_contains_1",
            ruleType = BlacklistRuleType.PACKAGE_NAME,
            pattern = "telegram",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )

        assertTrue(
            "Should match Telegram beta/plus package variants",
            BlacklistMatcher.matches("Audio", "Unknown", "org.telegram.messenger.beta", rule)
        )
        assertTrue(
            "Should match uppercase input",
            BlacklistMatcher.matches("Audio", "Unknown", "ORG.TELEGRAM.PLUS", rule)
        )
        assertFalse(
            "Should not match unrelated package",
            BlacklistMatcher.matches("Song", "Singer", "com.apple.android.music", rule)
        )
    }

    @Test
    fun testPackageNameRegexMatch() {
        val rule = BlacklistRuleEntity(
            id = "pkg_regex_1",
            ruleType = BlacklistRuleType.PACKAGE_NAME,
            pattern = "^com\\.(spotify|apple|vk)\\..*",
            matchMode = BlacklistMatchMode.REGEX,
            isEnabled = true
        )

        assertTrue(
            "Should match Spotify package via regex",
            BlacklistMatcher.matches("Song", "Artist", "com.spotify.music", rule)
        )
        assertTrue(
            "Should match Apple Music package via regex",
            BlacklistMatcher.matches("Song", "Artist", "com.apple.android.music", rule)
        )
        assertTrue(
            "Should match VK package via regex",
            BlacklistMatcher.matches("Song", "Artist", "com.vk.music", rule)
        )
        assertFalse(
            "Should not match Telegram package",
            BlacklistMatcher.matches("Voice", "Speaker", "org.telegram.messenger", rule)
        )
    }

    // ------------------------------------------------------------------------
    // 4. Inactive Rule & Pattern Resilience
    // ------------------------------------------------------------------------

    @Test
    fun testDisabledRuleDoesNotMatch() {
        val rule = BlacklistRuleEntity(
            id = "disabled_rule_1",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "Test Song",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = false
        )

        assertFalse(
            "Disabled rule must never match even with exact hit",
            BlacklistMatcher.matches("Test Song", "Artist", "pkg", rule)
        )
    }

    @Test
    fun testInvalidRegexDoesNotThrow() {
        val invalidPatterns = listOf(
            "[invalid(regex",
            "[a-z",
            "(?i*",
            "++",
            "*invalid",
            "(unclosed group"
        )

        for (pattern in invalidPatterns) {
            val rule = BlacklistRuleEntity(
                id = "invalid_regex",
                ruleType = BlacklistRuleType.TITLE,
                pattern = pattern,
                matchMode = BlacklistMatchMode.REGEX,
                isEnabled = true
            )

            // Must safely return false without throwing PatternSyntaxException
            assertFalse(
                "Invalid regex '$pattern' must return false without throwing",
                BlacklistMatcher.matches("Any Title", "Artist", "pkg", rule)
            )
        }
    }

    @Test
    fun testEmptyOrBlankPatternDoesNotMatch() {
        val emptyRule = BlacklistRuleEntity(
            id = "empty_pattern",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "   ",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )

        assertFalse(
            "Empty/blank pattern must never match",
            BlacklistMatcher.matches("Some Song", "Artist", "pkg", emptyRule)
        )
    }

    // ------------------------------------------------------------------------
    // 5. Multi-Rule Aggregation (isBlacklisted)
    // ------------------------------------------------------------------------

    @Test
    fun testIsBlacklisted_withMultipleRules() {
        val rules = listOf(
            BlacklistRuleEntity(
                id = "rule_title",
                ruleType = BlacklistRuleType.TITLE,
                pattern = "ad_break",
                matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
                isEnabled = true
            ),
            BlacklistRuleEntity(
                id = "rule_artist_disabled",
                ruleType = BlacklistRuleType.ARTIST,
                pattern = "Blocked Artist",
                matchMode = BlacklistMatchMode.EXACT,
                isEnabled = false // disabled!
            ),
            BlacklistRuleEntity(
                id = "rule_pkg",
                ruleType = BlacklistRuleType.PACKAGE_NAME,
                pattern = "org.telegram.messenger",
                matchMode = BlacklistMatchMode.EXACT,
                isEnabled = true
            )
        )

        // Empty rules -> false
        assertFalse(BlacklistMatcher.isBlacklisted("Title", "Artist", "pkg", emptyList()))

        // Matches title rule -> true
        assertTrue(BlacklistMatcher.isBlacklisted("Podcast ad_break segment", "Host", "com.podcast", rules))

        // Matches pkg rule -> true
        assertTrue(BlacklistMatcher.isBlacklisted("Voice Message", "Friend", "org.telegram.messenger", rules))

        // Matches disabled artist rule -> false
        assertFalse(BlacklistMatcher.isBlacklisted("Clean Song", "Blocked Artist", "com.spotify.music", rules))

        // Clean track -> false
        assertFalse(BlacklistMatcher.isBlacklisted("Numb", "Linkin Park", "com.spotify.music", rules))
    }
}
