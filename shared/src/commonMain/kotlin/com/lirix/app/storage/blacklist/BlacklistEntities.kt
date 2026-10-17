package com.lirix.app.storage.blacklist

import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Serializable
enum class BlacklistRuleType {
    TITLE,
    ARTIST,
    PACKAGE_NAME
}

@Serializable
enum class BlacklistMatchMode {
    CONTAINS_IGNORE_CASE,
    EXACT,
    REGEX
}

@Entity(
    tableName = "blacklist_rules",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["ruleType"])
    ]
)
@Serializable
data class BlacklistRuleEntity(
    @PrimaryKey
    val id: String,
    val ruleType: BlacklistRuleType,
    val pattern: String,
    val matchMode: BlacklistMatchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
