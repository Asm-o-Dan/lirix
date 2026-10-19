package com.lirix.app.translations

import com.lirix.app.platform.currentTimeMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TranslationCandidate(val sourceUrl: String, val title: String, val artist: String, val translator: String = "")

sealed interface TranslationSearchResult {
    data class Found(val document: SongTranslation) : TranslationSearchResult
    data class Candidates(val values: List<TranslationCandidate>) : TranslationSearchResult
    data object Missing : TranslationSearchResult
}

sealed interface AutomaticTranslationState {
    data object Searching : AutomaticTranslationState
    data object Ready : AutomaticTranslationState
    data object Missing : AutomaticTranslationState
    data class Candidates(val values: List<TranslationCandidate>) : AutomaticTranslationState
    data class Failed(val message: String) : AutomaticTranslationState
}

/** Application/screen-owned coordinator. Cancellation belongs to the active track, no background job. */
class AutomaticSongTranslations(
    private val dao: SongTranslationDao,
    private val search: suspend (title: String, artist: String, refresh: Boolean) -> TranslationSearchResult,
    private val now: () -> Long = ::currentTimeMillis,
    private val preferences: TranslationLanguagePreferences = MemoryTranslationLanguagePreferences(),
    private val searchLanguage: (suspend (String, String, String, Boolean) -> TranslationSearchResult)? = null
) {
    val language = preferences.language
    fun setLanguage(value: String) { require(value in TranslationLanguages.labels); preferences.setLanguage(value) }
    fun stateKey(key: String, language: String = this.language.value) = if (language == "ru") key else "$key\u0000$language"
    private val gate = Mutex()
    private val completed = linkedMapOf<String, Pair<String, Long>>()
    private val mutableStates = MutableStateFlow<Map<String, AutomaticTranslationState>>(emptyMap())
    val states = mutableStates.asStateFlow()

    private fun publish(key: String, state: AutomaticTranslationState?) {
        val values = mutableStates.value.toMutableMap()
        if (state == null) values.remove(key) else values[key] = state
        while (values.size > 128) values.remove(values.keys.first())
        mutableStates.value = values
    }

    suspend fun resolve(key: String, title: String, artist: String, refresh: Boolean = false, language: String = this.language.value) = gate.withLock {
        require(language in TranslationLanguages.labels)
        val stateKey = stateKey(key, language)
        if (key.isBlank() || title.isBlank() || artist.isBlank()) return@withLock
        val signature = "$title\u0000$artist"
        try {
            // Saved user selection always wins, including a concurrent manual import.
            if (withContext(Dispatchers.IO) { dao.get(key, language) }.isNotEmpty()) {
                publish(stateKey, AutomaticTranslationState.Ready)
                return@withLock
            }
            val old = completed[stateKey]
            val ttl = if (mutableStates.value[stateKey] is AutomaticTranslationState.Failed) 60_000L else 15 * 60_000L
            if (!refresh && old?.first == signature && now() - old.second in 0 until ttl && mutableStates.value[stateKey] != null) return@withLock
            publish(stateKey, AutomaticTranslationState.Searching)
            val state = when (val result = withTimeout(35_000) { withContext(Dispatchers.Default) { searchLanguage?.invoke(title, artist, language, refresh) ?: if (language == "ru") search(title, artist, refresh) else TranslationSearchResult.Missing } }) {
                is TranslationSearchResult.Found -> {
                    currentCoroutineContext().ensureActive()
                    val document = result.document
                    require(document.language == language) { "Источник вернул перевод на другом языке" }
                    val row = SongTranslationEntity(key, document.language, document.sourceUrl, TranslationDocuments.encode(document), now())
                    withContext(Dispatchers.IO) { dao.saveAutomaticIfEmpty(row) }
                    AutomaticTranslationState.Ready
                }
                is TranslationSearchResult.Candidates -> AutomaticTranslationState.Candidates(result.values.take(8))
                TranslationSearchResult.Missing -> AutomaticTranslationState.Missing
            }
            publish(stateKey, state)
            completed[stateKey] = signature to now()
        } catch (_: TimeoutCancellationException) {
            publish(stateKey, AutomaticTranslationState.Failed("Источник не ответил вовремя"))
            completed[stateKey] = signature to now()
        } catch (e: CancellationException) {
            publish(stateKey, null)
            throw e
        } catch (e: Exception) {
            publish(stateKey, AutomaticTranslationState.Failed(e.message ?: "Не удалось найти перевод"))
            completed[stateKey] = signature to now()
        }
        while (completed.size > 128) completed.remove(completed.keys.first())
    }
}
