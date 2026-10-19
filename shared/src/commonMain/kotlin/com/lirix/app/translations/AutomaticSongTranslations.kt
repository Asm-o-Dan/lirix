package com.lirix.app.translations

import com.lirix.app.platform.currentTimeMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TranslationCandidate(val sourceUrl: String, val title: String, val artist: String)

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
    private val now: () -> Long = ::currentTimeMillis
) {
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

    suspend fun resolve(key: String, title: String, artist: String, refresh: Boolean = false) = gate.withLock {
        if (key.isBlank() || title.isBlank() || artist.isBlank()) return@withLock
        val signature = "$title\u0000$artist"
        try {
            // Saved user selection always wins, including a concurrent manual import.
            if (withContext(Dispatchers.IO) { dao.get(key) }.isNotEmpty()) {
                publish(key, AutomaticTranslationState.Ready)
                return@withLock
            }
            val old = completed[key]
            val ttl = if (mutableStates.value[key] is AutomaticTranslationState.Failed) 60_000L else 15 * 60_000L
            if (!refresh && old?.first == signature && now() - old.second in 0 until ttl && mutableStates.value[key] != null) return@withLock
            publish(key, AutomaticTranslationState.Searching)
            val state = when (val result = withTimeout(35_000) { withContext(Dispatchers.Default) { search(title, artist, refresh) } }) {
                is TranslationSearchResult.Found -> {
                    currentCoroutineContext().ensureActive()
                    val document = result.document
                    val row = SongTranslationEntity(key, document.language, document.sourceUrl, TranslationDocuments.encode(document), now())
                    withContext(Dispatchers.IO) { dao.saveAutomaticIfEmpty(row) }
                    AutomaticTranslationState.Ready
                }
                is TranslationSearchResult.Candidates -> AutomaticTranslationState.Candidates(result.values.take(8))
                TranslationSearchResult.Missing -> AutomaticTranslationState.Missing
            }
            publish(key, state)
            completed[key] = signature to now()
        } catch (_: TimeoutCancellationException) {
            publish(key, AutomaticTranslationState.Failed("Источник не ответил вовремя"))
            completed[key] = signature to now()
        } catch (e: CancellationException) {
            publish(key, null)
            throw e
        } catch (e: Exception) {
            publish(key, AutomaticTranslationState.Failed(e.message ?: "Не удалось найти перевод"))
            completed[key] = signature to now()
        }
        while (completed.size > 128) completed.remove(completed.keys.first())
    }
}
