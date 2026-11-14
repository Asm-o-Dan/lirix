package com.lirix.app.storage

import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType

/**
 * Room v15 changes shared by the Android and Desktop migrations.
 * Spec: .sdd/specs/media-core/favorites_messenger_lookup_v1.md (D2, D4).
 */
object Schema15 {
    /** No backfill: existing rows start without a remembered user choice (user decision D2). */
    const val ADD_FAVORITE_USER_SET_AT = "ALTER TABLE music_tracks ADD COLUMN favoriteUserSetAt INTEGER DEFAULT NULL"

    const val BUILTIN_RULE_PREFIX = "builtin:"

    /** Messenger apps publish voice/video notes as media sessions with these fixed artist labels. */
    val messengerRules: List<BlacklistRuleEntity> = listOf(
        "messenger-voice-ru" to "Голосовое сообщение",
        "messenger-video-ru" to "Видеосообщение",
        "messenger-voice-en" to "Voice message",
        "messenger-video-en" to "Video message"
    ).map { (id, pattern) ->
        BlacklistRuleEntity(BUILTIN_RULE_PREFIX + id, BlacklistRuleType.ARTIST, pattern, BlacklistMatchMode.EXACT,
            isEnabled = true, createdAt = 0L)
    }

    fun isBuiltIn(rule: BlacklistRuleEntity): Boolean = rule.id.startsWith(BUILTIN_RULE_PREFIX)

    /** Insert-or-ignore keeps user changes to an existing rule with the same id. */
    fun seedMessengerRulesSql(createdAt: Long): List<String> = messengerRules.map { rule ->
        "INSERT OR IGNORE INTO blacklist_rules (id, ruleType, pattern, matchMode, isEnabled, createdAt) VALUES " +
            "('${rule.id}', '${rule.ruleType.name}', '${rule.pattern}', '${rule.matchMode.name}', 1, $createdAt)"
    }
}
