package com.lirix.app.feature.share

import android.content.Context
import com.lirix.app.ui.i18n.InterfaceLanguage
import com.lirix.app.ui.i18n.UiMessageCatalog

/** Immutable export-language snapshot. Never translates song titles, artists, lyrics or notes. */
data class PosterTextSnapshot(val language: InterfaceLanguage) {
    fun text(source: String, vararg arguments: Any?): String = UiMessageCatalog.text(source, language, *arguments)
    fun quantity(value: Int, one: String, few: String, many: String): String = "$value ${unit(value, one, few, many)}"
    fun unit(value: Int, one: String, few: String, many: String): String {
        val absolute = kotlin.math.abs(value.toLong())
        val word = if (language == InterfaceLanguage.EN) {
            if (absolute == 1L) one else many
        } else when {
            absolute % 100 in 11L..14L -> many
            absolute % 10 == 1L -> one
            absolute % 10 in 2L..4L -> few
            else -> many
        }
        return text(word)
    }
    companion object {
        fun from(context: Context) = PosterTextSnapshot(
            if (context.resources.configuration.locales[0].language == "ru") InterfaceLanguage.RU else InterfaceLanguage.EN
        )
    }
}
