package com.lirix.app.translations

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TranslationLanguages {
    val labels = linkedMapOf("ru" to "Русский", "en" to "English", "es" to "Español", "pt" to "Português", "zh" to "中文")
    fun validated(value: String) = value.takeIf { it in labels } ?: "ru"
}

interface TranslationLanguagePreferences {
    val language: StateFlow<String>
    fun setLanguage(value: String)
}

class MemoryTranslationLanguagePreferences(initial: String = "ru", private val persist: (String) -> Unit = {}) : TranslationLanguagePreferences {
    private val state = MutableStateFlow(TranslationLanguages.validated(initial))
    override val language = state.asStateFlow()
    override fun setLanguage(value: String) {
        require(value in TranslationLanguages.labels)
        persist(value)
        state.value = value
    }
}
