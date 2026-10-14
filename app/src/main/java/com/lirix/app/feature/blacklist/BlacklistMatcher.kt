package com.lirix.app.feature.blacklist

import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType

object BlacklistMatcher {

    fun matches(
        title: String,
        artist: String,
        packageName: String,
        rule: BlacklistRuleEntity
    ): Boolean {
        if (!rule.isEnabled) return false
        val pattern = rule.pattern.trim()
        if (pattern.isEmpty()) return false

        val candidate = when (rule.ruleType) {
            BlacklistRuleType.TITLE -> title.trim()
            BlacklistRuleType.ARTIST -> artist.trim()
            BlacklistRuleType.PACKAGE_NAME -> packageName.trim()
        }

        return when (rule.matchMode) {
            BlacklistMatchMode.CONTAINS_IGNORE_CASE -> {
                candidate.contains(pattern, ignoreCase = true)
            }
            BlacklistMatchMode.EXACT -> {
                candidate.equals(pattern, ignoreCase = true)
            }
            BlacklistMatchMode.REGEX -> {
                try {
                    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(candidate)
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    fun isBlacklisted(
        title: String,
        artist: String,
        packageName: String,
        activeRules: List<BlacklistRuleEntity>
    ): Boolean {
        if (activeRules.isEmpty()) return false
        return activeRules.any { matches(title, artist, packageName, it) }
    }
}
