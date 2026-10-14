package com.lirix.app.feature.blacklist

import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlacklistMatcherTest {

    @Test
    fun test_title_contains_ignore_case() {
        val rule = BlacklistRuleEntity(
            id = "1",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "podcast",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )

        assertTrue(BlacklistMatcher.matches("Tech PODCAST Episode 1", "Host", "com.app", rule))
        assertTrue(BlacklistMatcher.matches("podcast", "Host", "com.app", rule))
        assertFalse(BlacklistMatcher.matches("Music Song", "Host", "com.app", rule))
    }

    @Test
    fun test_title_exact() {
        val rule = BlacklistRuleEntity(
            id = "2",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "News Today",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = true
        )

        assertTrue(BlacklistMatcher.matches("News Today", "CNN", "com.app", rule))
        assertTrue(BlacklistMatcher.matches("  news today  ", "CNN", "com.app", rule)) // trims candidate and pattern
        assertFalse(BlacklistMatcher.matches("News Today Extra", "CNN", "com.app", rule))
    }

    @Test
    fun test_artist_regex() {
        val rule = BlacklistRuleEntity(
            id = "3",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "^(Radio|BBC).*$",
            matchMode = BlacklistMatchMode.REGEX,
            isEnabled = true
        )

        assertTrue(BlacklistMatcher.matches("Show", "Radio 1 News", "com.app", rule))
        assertTrue(BlacklistMatcher.matches("Broadcast", "BBC World", "com.app", rule))
        assertFalse(BlacklistMatcher.matches("Song", "Unknown Radio", "com.app", rule))
    }

    @Test
    fun test_invalid_regex_does_not_throw_and_returns_false() {
        val rule = BlacklistRuleEntity(
            id = "4",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "[invalid(regex",
            matchMode = BlacklistMatchMode.REGEX,
            isEnabled = true
        )

        assertFalse(BlacklistMatcher.matches("Any Title", "Artist", "com.pkg", rule))
    }

    @Test
    fun test_package_name_match() {
        val rule = BlacklistRuleEntity(
            id = "5",
            ruleType = BlacklistRuleType.PACKAGE_NAME,
            pattern = "org.telegram.messenger",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = true
        )

        assertTrue(BlacklistMatcher.matches("Voice Note", "Sender", "org.telegram.messenger", rule))
        assertFalse(BlacklistMatcher.matches("Track", "Artist", "com.spotify.music", rule))
    }

    @Test
    fun test_disabled_rule_never_matches() {
        val rule = BlacklistRuleEntity(
            id = "6",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "Blocked",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = false
        )

        assertFalse(BlacklistMatcher.matches("Blocked Song", "Artist", "com.pkg", rule))
    }

    @Test
    fun test_empty_pattern_never_matches() {
        val rule = BlacklistRuleEntity(
            id = "7",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "   ",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )

        assertFalse(BlacklistMatcher.matches("Some Song", "Artist", "com.pkg", rule))
    }

    @Test
    fun test_is_blacklisted_any_matching_active_rule() {
        val rules = listOf(
            BlacklistRuleEntity("1", BlacklistRuleType.ARTIST, "SpamBot", BlacklistMatchMode.EXACT, isEnabled = true),
            BlacklistRuleEntity("2", BlacklistRuleType.TITLE, "Audio Ad", BlacklistMatchMode.CONTAINS_IGNORE_CASE, isEnabled = true),
            BlacklistRuleEntity("3", BlacklistRuleType.PACKAGE_NAME, "com.ad.network", BlacklistMatchMode.EXACT, isEnabled = false)
        )

        assertTrue(BlacklistMatcher.isBlacklisted("Great Song", "SpamBot", "com.spotify", rules))
        assertTrue(BlacklistMatcher.isBlacklisted("Free Audio Ad 123", "Radio", "com.spotify", rules))
        assertFalse(BlacklistMatcher.isBlacklisted("Ad Track", "Band", "com.ad.network", rules)) // Disabled rule
        assertFalse(BlacklistMatcher.isBlacklisted("Numb", "Linkin Park", "com.spotify", rules))
    }
}
