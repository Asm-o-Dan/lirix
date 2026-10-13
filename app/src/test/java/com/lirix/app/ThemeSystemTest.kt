package com.lirix.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.storage.ThemePreferencesRepository
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSystemTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ThemePreferencesRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun testAppThemeType_fromId() {
        assertEquals(AppThemeType.SPOTIFY_MOOD, AppThemeType.fromId("spotify_mood"))
        assertEquals(AppThemeType.YANDEX_MUSIC, AppThemeType.fromId("yandex_music"))
        assertEquals(AppThemeType.OBSIDIAN_PULSE, AppThemeType.fromId("obsidian_pulse"))
        assertEquals(AppThemeType.PASTEL_SERENITY, AppThemeType.fromId("pastel_serenity"))
        assertEquals(AppThemeType.DEFAULT, AppThemeType.fromId(null))
        assertEquals(AppThemeType.DEFAULT, AppThemeType.fromId("invalid_theme_id"))
        assertEquals(AppThemeType.OBSIDIAN_PULSE, AppThemeType.DEFAULT)
    }

    @Test
    fun testAppThemeType_isDarkFlag() {
        assertTrue(AppThemeType.SPOTIFY_MOOD.isDark)
        assertTrue(AppThemeType.YANDEX_MUSIC.isDark)
        assertTrue(AppThemeType.OBSIDIAN_PULSE.isDark)
        assertFalse(AppThemeType.PASTEL_SERENITY.isDark)
    }

    @Test
    fun testColorPalettes_obsidianPulsePureBlack() {
        val tokens = ColorPalettes.getTokens(AppThemeType.OBSIDIAN_PULSE)
        assertEquals(Color(0xFF000000), tokens.appBackground)
        assertEquals(Color(0xFFB388FF), tokens.accentPrimary)
        assertEquals(Color(0xFF00E5FF), tokens.accentSecondary)
        assertTrue(tokens.isDark)
        assertTrue(tokens.cardCornerRadius > 0.dp)
        assertTrue(tokens.pillCornerRadius > 0.dp)
    }

    @Test
    fun testColorPalettes_spotifyMood() {
        val tokens = ColorPalettes.getTokens(AppThemeType.SPOTIFY_MOOD)
        assertEquals(Color(0xFF121212), tokens.appBackground)
        assertEquals(Color(0xFF1DB954), tokens.accentPrimary)
        assertTrue(tokens.isDark)
        assertEquals(6.dp, tokens.cardCornerRadius)
    }

    @Test
    fun testColorPalettes_yandexMusic() {
        val tokens = ColorPalettes.getTokens(AppThemeType.YANDEX_MUSIC)
        assertEquals(Color(0xFF0E0F12), tokens.appBackground)
        assertEquals(Color(0xFFFFCC00), tokens.accentPrimary)
        assertEquals(Color(0xFFFF5500), tokens.accentSecondary)
        assertTrue(tokens.isDark)
        assertEquals(14.dp, tokens.cardCornerRadius)
    }

    @Test
    fun testColorPalettes_pastelSerenity() {
        val tokens = ColorPalettes.getTokens(AppThemeType.PASTEL_SERENITY)
        assertEquals(Color(0xFFFBF9F5), tokens.appBackground)
        assertEquals(Color(0xFFD9777F), tokens.accentPrimary)
        assertFalse(tokens.isDark)
        assertEquals(20.dp, tokens.cardCornerRadius)
    }

    @Test
    fun testThemePreferencesRepository_persistence() {
        val repo = ThemePreferencesRepository(context)
        assertEquals(AppThemeType.DEFAULT, repo.getTheme())
        assertEquals(AppThemeType.DEFAULT, repo.selectedTheme.value)

        repo.setTheme(AppThemeType.SPOTIFY_MOOD)
        assertEquals(AppThemeType.SPOTIFY_MOOD, repo.getTheme())
        assertEquals(AppThemeType.SPOTIFY_MOOD, repo.selectedTheme.value)

        // Re-read with a fresh repository instance to verify persistent storage
        val repo2 = ThemePreferencesRepository(context)
        assertEquals(AppThemeType.SPOTIFY_MOOD, repo2.getTheme())
        assertEquals(AppThemeType.SPOTIFY_MOOD, repo2.selectedTheme.value)

        // Switch to Pastel Serenity
        repo2.setTheme(AppThemeType.PASTEL_SERENITY)
        assertEquals(AppThemeType.PASTEL_SERENITY, repo2.getTheme())
    }
}
