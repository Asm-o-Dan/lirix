package com.lirix.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 4 design themes for Lirix:
 * 1. SPOTIFY_MOOD: Dark Scandinavian studio aesthetic
 * 2. YANDEX_MUSIC: Energetic high-contrast night style with "My Vibe"
 * 3. OBSIDIAN_PULSE: True 100% AMOLED black (#000000) with neon cyberpunk accents
 * 4. PASTEL_SERENITY: Soft warm pastel editorial light theme
 */
enum class AppThemeType(
    val id: String,
    val title: String,
    val subtitle: String,
    val previewPrimary: Color,
    val previewAccent: Color,
    val previewBackground: Color,
    val isDark: Boolean
) {
    SPOTIFY_MOOD(
        id = "spotify_mood",
        title = "Spotify Mood",
        subtitle = "Тёмная скандинавская эстетика",
        previewPrimary = Color(0xFF1DB954),
        previewAccent = Color(0xFF1DB954),
        previewBackground = Color(0xFF121212),
        isDark = true
    ),
    YANDEX_MUSIC(
        id = "yandex_music",
        title = "Yandex Music",
        subtitle = "Энергичный контрастный тёмный",
        previewPrimary = Color(0xFFFFCC00),
        previewAccent = Color(0xFFFF5500),
        previewBackground = Color(0xFF0E0F12),
        isDark = true
    ),
    OBSIDIAN_PULSE(
        id = "obsidian_pulse",
        title = "Original Lirix",
        subtitle = "Obsidian Pulse AMOLED",
        previewPrimary = Color(0xFFB388FF),
        previewAccent = Color(0xFF00E5FF),
        previewBackground = Color(0xFF000000),
        isDark = true
    ),
    PASTEL_SERENITY(
        id = "pastel_serenity",
        title = "Pastel Serenity",
        subtitle = "Мягкая светлая пастель",
        previewPrimary = Color(0xFFD9777F),
        previewAccent = Color(0xFF7A9A7B),
        previewBackground = Color(0xFFFBF9F5),
        isDark = false
    );

    companion object {
        val DEFAULT = OBSIDIAN_PULSE

        fun fromId(id: String?): AppThemeType {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}
