package com.lirix.app.platform

import com.lirix.app.translations.MemoryTranslationLanguagePreferences
import com.lirix.app.translations.TranslationLanguagePreferences
import java.util.prefs.Preferences

class JvmTranslationLanguagePreferences(
    preferences: Preferences = Preferences.userRoot().node("com/lirix/translations")
) : TranslationLanguagePreferences by MemoryTranslationLanguagePreferences(
    preferences.get("target_language", "ru"), { preferences.put("target_language", it) }
)
