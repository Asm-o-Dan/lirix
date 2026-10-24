package com.lirix.app.ui.i18n

import android.content.Context
import android.content.SharedPreferences
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.lirix.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(
    val code: String,
    @StringRes val titleRes: Int,
    val nativeName: String
) {
    SYSTEM("", R.string.language_system_default, "Системный / System Default"),
    RUSSIAN("ru", R.string.language_ru, "Русский"),
    ENGLISH("en", R.string.language_en, "English");

    companion object {
        fun fromCode(code: String?): AppLanguage = when (code?.lowercase()?.trim()) {
            "ru" -> RUSSIAN
            "en" -> ENGLISH
            else -> SYSTEM
        }
    }
}

/** Framework locale access is injectable so restart/external changes can be tested independently. */
internal interface AppLocalePlatform {
    fun readLanguageCode(): String
    fun writeLanguageCode(value: String)
}

internal class AndroidLanguageController(
    private val preferences: SharedPreferences,
    legacyPreferences: SharedPreferences? = null,
    private val platform: AppLocalePlatform? = null
) {
    private val state = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = state.asStateFlow()

    init {
        if (!preferences.contains(KEY_LANGUAGE) && legacyPreferences?.contains(KEY_LANGUAGE) == true) {
            preferences.edit().putString(KEY_LANGUAGE, legacyPreferences.getString(KEY_LANGUAGE, "")).apply()
        }
        if (platform != null && !preferences.getBoolean(KEY_MIGRATED, false)) {
            val existing = platform.readLanguageCode()
            val saved = AppLanguage.fromCode(preferences.getString(KEY_LANGUAGE, ""))
            if (existing.isEmpty() && saved != AppLanguage.SYSTEM) platform.writeLanguageCode(saved.code)
            preferences.edit().putBoolean(KEY_MIGRATED, true).apply()
        }
        refresh()
    }

    fun refresh() {
        val selected = AppLanguage.fromCode(platform?.readLanguageCode() ?: preferences.getString(KEY_LANGUAGE, ""))
        state.value = selected
        if (preferences.getString(KEY_LANGUAGE, null) != selected.code) {
            preferences.edit().putString(KEY_LANGUAGE, selected.code).apply()
        }
    }

    fun setLanguage(language: AppLanguage) {
        if (platform != null && platform.readLanguageCode() != language.code) platform.writeLanguageCode(language.code)
        preferences.edit().putString(KEY_LANGUAGE, language.code).apply()
        state.value = language
    }

    companion object {
        private const val KEY_LANGUAGE = "pref_app_language"
        private const val KEY_MIGRATED = "pref_framework_locale_migrated"
    }
}

object LanguageManager {
    // Shared with the existing, explicitly backed-up theme/user settings; never Connect credentials.
    private const val PREFS_NAME = "lirix_preferences"
    private const val LEGACY_STORE = "lirix_prefs"
    private var instance: AndroidLanguageController? = null

    @Synchronized
    private fun controller(context: Context): AndroidLanguageController = instance ?: run {
        val app = context.applicationContext
        val platform = if (Build.VERSION.SDK_INT >= 33) {
            val manager = app.getSystemService(LocaleManager::class.java)
            object : AppLocalePlatform {
                override fun readLanguageCode(): String = manager.applicationLocales.let { if (it.isEmpty) "" else it[0].language }
                override fun writeLanguageCode(value: String) { manager.applicationLocales = LocaleList.forLanguageTags(value) }
            }
        } else null
        AndroidLanguageController(
            app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            app.getSharedPreferences(LEGACY_STORE, Context.MODE_PRIVATE), platform
        ).also { instance = it }
    }

    fun language(context: Context): StateFlow<AppLanguage> = controller(context).language
    fun refresh(context: Context) = controller(context).refresh()
    fun setLanguage(context: Context, language: AppLanguage) = controller(context).setLanguage(language)
    fun getCurrentLanguage(context: Context): AppLanguage = controller(context).run { refresh(); language.value }
}
