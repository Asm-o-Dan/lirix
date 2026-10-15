package com.lirix.app.platform

import com.lirix.app.ui.theme.AppThemeType
import kotlinx.coroutines.flow.StateFlow

interface ThemePreferencesStore {
    val selectedTheme: StateFlow<AppThemeType>
    fun setTheme(theme: AppThemeType)
}
