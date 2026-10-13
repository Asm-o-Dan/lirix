package com.lirix.app.storage

import android.content.Context
import android.content.SharedPreferences
import com.lirix.app.ui.theme.AppThemeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for persistent app theme selection.
 * Uses Android SharedPreferences to enable instant, zero-latency synchronous reading
 * on cold app startup, avoiding theme flickering or blank flashes.
 */
class ThemePreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _selectedTheme = MutableStateFlow(
        AppThemeType.fromId(prefs.getString(KEY_SELECTED_THEME, AppThemeType.DEFAULT.id))
    )
    val selectedTheme: StateFlow<AppThemeType> = _selectedTheme.asStateFlow()

    fun getTheme(): AppThemeType = _selectedTheme.value

    fun setTheme(theme: AppThemeType) {
        prefs.edit().putString(KEY_SELECTED_THEME, theme.id).apply()
        _selectedTheme.value = theme
    }

    companion object {
        const val PREFS_NAME = "lirix_preferences"
        const val KEY_SELECTED_THEME = "selected_app_theme"

        @Volatile
        private var instance: ThemePreferencesRepository? = null

        fun getInstance(context: Context): ThemePreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: ThemePreferencesRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
