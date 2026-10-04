package com.eventengine.app.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

typealias MusicTrackEntity = TrackEntity
typealias MusicListeningSessionEntity = ListeningSessionEntity
/**
 * Room entity representing a unique track in music listening history.
 * TrackKey is a deterministic SHA-256 fingerprint of normalized title, artist, and album.
 */
@Entity(
    tableName = "music_tracks",
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["playCount"]),
        Index(value = ["lastPlayedAt"])
    ]
)
data class TrackEntity(
    @PrimaryKey
    val trackKey: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val sourcePackage: String,
    val playCount: Int = 1,
    val totalDurationMs: Long = 0L,
    val firstPlayedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val userNotes: String = "",       // Local offline user notes to the song
    val isFavorite: Boolean = false,  // playCount >= 5 or totalDurationMs >= 15 minutes
    val syncedLyrics: String? = null, // Текст с таймкодами караоке [01:23.45]
    val plainLyrics: String? = null,  // Чистый текст песни
    val albumArtUri: String? = null   // Новый столбец Room v7
)

/**
 * Room entity representing an active or completed listening session.
 */
@Entity(
    tableName = "music_listening_sessions",
    indices = [
        Index(value = ["trackKey"]),
        Index(value = ["startTimeMs"])
    ]
)
data class ListeningSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackKey: String,
    val sourcePackage: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean = false
)

/**
 * Room entity for the per-track lyrics cache.
 * Spec: TASK-DB-01 / .sdd/specs/media-core/overview.md v1
 *
 * trackKey — SHA-256("lowercase_artist||lowercase_title"), foreign key to music_tracks.
 * provider  — source identifier: "LRCLIB" | "AMDM" | "USER" | "NONE"
 */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey
    val trackKey: String,
    val plainLyrics: String?,
    val syncedLyricsLrc: String?,
    val chordsAmDm: String?,
    val userNotes: String?,
    val provider: String,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for gamification achievements.
 * Spec: TASK-DB-01 / .sdd/specs/media-core/overview.md v1
 *
 * id          — stable string key, e.g. "FIRST_TRACK", "NIGHT_OWL", "CENTURY_CLUB"
 * iconRes     — drawable resource name (resolved at runtime via Resources)
 * maxProgress — threshold at which the achievement unlocks
 */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val iconRes: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val currentProgress: Int = 0,
    val maxProgress: Int = 1,
    val tier: String = "BRONZE",       // "BRONZE", "SILVER", "GOLD", "PLATINUM"
    val category: String = "LISTENING" // "LISTENING", "EXPLORATION", "LYRICS", "SPECIAL"
)

/**
 * Room entity representing an explicit user rejection of a lyrics source for a specific track.
 * Spec: TASK-LYR-03-A / .sdd/architecture_lyr_community.md
 *
 * trackKey   — SHA-256 fingerprint of normalized track (title + artist + album)
 * sourceId   — Canonical identifier of rejected provider (e.g. "builtin:lrclib", "builtin:amdm")
 * rejectedAt — Epoch timestamp in milliseconds when user clicked "Not this lyrics"
 */
@Entity(
    tableName = "lyrics_rejections",
    primaryKeys = ["trackKey", "sourceId"],
    indices = [
        Index(value = ["trackKey"]),
        Index(value = ["sourceId"])
    ]
)
data class LyricsRejectionEntity(
    val trackKey: String,
    val sourceId: String,
    val rejectedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity representing a declarative scraper rule for a specific lyrics domain.
 * Spec: TASK-LYR-04-A / .sdd/architecture_lyr_community.md
 *
 * id         — Unique slug or UUID (e.g. "rule_amalgama_v1")
 * domain     — Root web domain (e.g. "amalgama-lab.com")
 * name       — Human-readable source name (e.g. "Амальгама (Русский перевод)")
 * ruleJson   — Declarative JSON string describing selectors and search patterns
 * isEnabled  — Active toggle for cascade execution
 * priority   — Ordering priority (lower value = higher priority, default 100)
 * isBuiltIn  — Protection flag against accidental deletion
 * author     — Author identifier ("local" or community handle)
 * version    — Schema version of the rule JSON
 * createdAt  — Epoch timestamp of creation
 * updatedAt  — Epoch timestamp of last update
 */
@Entity(
    tableName = "custom_lyrics_rules",
    indices = [
        Index(value = ["domain"]),
        Index(value = ["isEnabled"]),
        Index(value = ["priority"])
    ]
)
data class CustomLyricsRuleEntity(
    @PrimaryKey
    val id: String,
    val domain: String,
    val name: String,
    val ruleJson: String,
    val isEnabled: Boolean = true,
    val priority: Int = 100,
    val isBuiltIn: Boolean = false,
    val author: String = "local",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
