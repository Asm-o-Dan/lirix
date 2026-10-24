package com.lirix.app.desktop.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lirix.app.ui.i18n.InterfaceLanguage
import com.lirix.app.ui.i18n.LirixStrings
import java.util.Locale
import java.util.prefs.Preferences

enum class DesktopLanguage(val code: String) {
    SYSTEM(""), RUSSIAN("ru"), ENGLISH("en");
    companion object { fun fromCode(value: String?) = entries.firstOrNull { it.code == value } ?: SYSTEM }
}

/** Injectable persistence permits restart tests without writing the user's registry. */
class DesktopLanguagePreferences(
    private val read: () -> String = { Preferences.userRoot().node("com/lirix/interface").get("language", "") },
    private val write: (String) -> Unit = { Preferences.userRoot().node("com/lirix/interface").put("language", it) },
    private val systemLanguage: () -> String = { Locale.getDefault().language }
) {
    var selection by mutableStateOf(DesktopLanguage.fromCode(read()))
        private set
    init { refreshSystemLanguage() }
    fun setLanguage(language: DesktopLanguage) {
        write(language.code)
        selection = language
        refreshSystemLanguage()
    }
    fun refreshSystemLanguage() {
        LirixStrings.currentLanguage = when (selection) {
            DesktopLanguage.RUSSIAN -> InterfaceLanguage.RU
            DesktopLanguage.ENGLISH -> InterfaceLanguage.EN
            DesktopLanguage.SYSTEM -> if (systemLanguage().substringBefore('-').equals("ru", true)) InterfaceLanguage.RU else InterfaceLanguage.EN
        }
    }
}

object DesktopLanguageSettings { val preferences by lazy { DesktopLanguagePreferences() } }
