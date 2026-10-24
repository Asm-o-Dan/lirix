package com.lirix.app.ui.components

import android.content.Context
import java.security.MessageDigest
import com.lirix.app.feature.lyrics.romanization.RomanizationDisplayMode
import com.lirix.app.feature.lyrics.romanization.RomanizationLanguage

/** Independent of UI locale and original lyrics; per-track source language survives recreation. */
class LyricsReadingPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("lyrics_reading_preferences", Context.MODE_PRIVATE)
    private fun key(trackKey: String) = "language_" + MessageDigest.getInstance("SHA-256").digest(trackKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun language(trackKey: String?): RomanizationLanguage = trackKey?.let { id ->
        runCatching { RomanizationLanguage.valueOf(preferences.getString(key(id), "AUTO") ?: "AUTO") }.getOrDefault(RomanizationLanguage.AUTO)
    } ?: RomanizationLanguage.AUTO
    fun saveLanguage(trackKey: String?, language: RomanizationLanguage) {
        if (trackKey != null) preferences.edit().putString(key(trackKey), language.name).apply()
    }
    fun mode(): RomanizationDisplayMode = runCatching {
        RomanizationDisplayMode.valueOf(preferences.getString("display_mode", "ORIGINAL_ONLY") ?: "ORIGINAL_ONLY")
    }.getOrDefault(RomanizationDisplayMode.ORIGINAL_ONLY)
    fun saveMode(mode: RomanizationDisplayMode) { preferences.edit().putString("display_mode", mode.name).apply() }
}
