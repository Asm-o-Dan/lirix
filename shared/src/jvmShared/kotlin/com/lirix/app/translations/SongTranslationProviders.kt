package com.lirix.app.translations

import kotlinx.coroutines.CancellationException
import java.net.URI

class SongTranslationProviders(
    private val amalgama: AmalgamaTranslationProvider = AmalgamaTranslationProvider(),
    private val multilingual: LyricsTranslateProvider = LyricsTranslateProvider()
) {
    suspend fun load(url: String): SongTranslation = when (URI(url.trim()).host?.lowercase()) {
        "lyricstranslate.com", "www.lyricstranslate.com" -> multilingual.load(url)
        else -> amalgama.load(url)
    }

    suspend fun find(title: String, artist: String, refresh: Boolean = false): TranslationSearchResult = findLanguage(title, artist, "ru", refresh)

    suspend fun findLanguage(title: String, artist: String, language: String, refresh: Boolean): TranslationSearchResult {
        var firstError: Exception? = null
        if (language == "ru") {
            try {
                val result = amalgama.find(title, artist, refresh)
                if (result != TranslationSearchResult.Missing) return result
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { firstError = e }
        }
        val result = multilingual.find(title, artist, language, refresh)
        if (result == TranslationSearchResult.Missing && firstError != null) throw firstError
        return result
    }
}
