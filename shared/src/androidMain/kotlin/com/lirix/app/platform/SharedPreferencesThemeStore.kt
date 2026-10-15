package com.lirix.app.platform

import android.content.Context
import android.content.SharedPreferences
import com.lirix.app.ui.theme.AppThemeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesThemeStore(
    context: Context,
    prefsName: String = "lirix_preferences"
) : ThemePreferencesStore {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        prefsName,
        Context.MODE_PRIVATE
    )

    private val _selectedTheme = MutableStateFlow(
        AppThemeType.fromId(prefs.getString(KEY_SELECTED_THEME, AppThemeType.DEFAULT.id))
    )
    override val selectedTheme: StateFlow<AppThemeType> = _selectedTheme.asStateFlow()

    override fun setTheme(theme: AppThemeType) {
        prefs.edit().putString(KEY_SELECTED_THEME, theme.id).apply()
        _selectedTheme.value = theme
    }

    companion object {
        const val KEY_SELECTED_THEME = "selected_app_theme"
    }
}
