package com.lirix.app.platform

import com.lirix.app.ui.theme.AppThemeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

class JvmPreferencesThemeStore(
    private val preferences: Preferences = Preferences.userNodeForPackage(ThemePreferencesStore::class.java)
) : ThemePreferencesStore {

    private val _selectedTheme = MutableStateFlow(
        AppThemeType.fromId(preferences.get(KEY_SELECTED_THEME, AppThemeType.DEFAULT.id))
    )
    override val selectedTheme: StateFlow<AppThemeType> = _selectedTheme.asStateFlow()

    override fun setTheme(theme: AppThemeType) {
        preferences.put(KEY_SELECTED_THEME, theme.id)
        _selectedTheme.value = theme
    }

    companion object {
        private const val KEY_SELECTED_THEME = "selected_app_theme"
    }
}
