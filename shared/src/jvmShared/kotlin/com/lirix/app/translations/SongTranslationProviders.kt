package com.lirix.app.translations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

class SongTranslationProviders(
    private val amalgama: AmalgamaTranslationProvider = AmalgamaTranslationProvider(),
    private val multilingual: LyricsTranslateProvider = LyricsTranslateProvider(),
    private val rustoeng: RustoengTranslationProvider = RustoengTranslationProvider(),
    private val englishPrimary: suspend (String, String, Boolean) -> TranslationSearchResult = { title, artist, refresh -> multilingual.find(title, artist, "en", refresh) },
    private val englishFallback: suspend (String, String, Boolean) -> TranslationSearchResult = { title, artist, refresh -> rustoeng.find(title, artist, refresh) },
    private val englishSourceTimeoutMs: Long = 14_000L
) {
    suspend fun load(url: String): SongTranslation = when (URI(url.trim()).host?.lowercase()) {
        "lyricstranslate.com", "www.lyricstranslate.com" -> multilingual.load(url)
        "rustoeng.com", "www.rustoeng.com" -> rustoeng.load(url)
        else -> amalgama.load(url)
    }

    suspend fun find(title: String, artist: String, refresh: Boolean = false): TranslationSearchResult = findLanguage(title, artist, "ru", refresh)

    suspend fun findLanguage(title: String, artist: String, language: String, refresh: Boolean): TranslationSearchResult {
        if (language == "en") return findEnglish(title, artist, refresh)
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
    /** Null means this provider failed, including its own timeout. Parent cancellation still propagates. */
    private suspend fun englishAttempt(
        source: suspend (String, String, Boolean) -> TranslationSearchResult,
        title: String, artist: String, refresh: Boolean
    ): TranslationSearchResult? {
        require(englishSourceTimeoutMs in 1..14_000L) { "Некорректный лимит ожидания источника" }
        return try {
            val result = withTimeoutOrNull(englishSourceTimeoutMs) { source(title, artist, refresh) }
            if (result is TranslationSearchResult.Found) require(result.document.language == "en") { "Источник вернул перевод на другом языке" }
            result
        } catch (e: TimeoutCancellationException) {
            // A source-created timeout is failure; a cancelled parent must not continue to fallback.
            currentCoroutineContext().ensureActive()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun findEnglish(title: String, artist: String, refresh: Boolean): TranslationSearchResult {
        val primary = englishAttempt(englishPrimary, title, artist, refresh)
        // Preserve the pre-existing priority and automatic save without waiting for the fallback.
        if (primary is TranslationSearchResult.Found || primary is TranslationSearchResult.Candidates) return primary
        val fallback = englishAttempt(englishFallback, title, artist, refresh)
        if (primary == TranslationSearchResult.Missing) {
            return fallback ?: throw IllegalStateException("Не удалось получить перевод: источник недоступен")
        }
        // A partial result must always be presented for explicit user confirmation.
        return when (fallback) {
            is TranslationSearchResult.Found -> {
                val doc = fallback.document
                TranslationSearchResult.Candidates(listOf(TranslationCandidate(doc.sourceUrl, doc.title, doc.artist, doc.translator)), listOf("LyricsTranslate"))
            }
            is TranslationSearchResult.Candidates -> if (fallback.values.isNotEmpty())
                TranslationSearchResult.Candidates(fallback.values, listOf("LyricsTranslate"))
            else throw IllegalStateException("Не удалось получить перевод: источник недоступен")
            else -> throw IllegalStateException("Не удалось получить перевод: источник недоступен")
        }
    }

}
