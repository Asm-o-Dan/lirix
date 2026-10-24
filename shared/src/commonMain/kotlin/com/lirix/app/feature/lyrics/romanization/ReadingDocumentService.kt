package com.lirix.app.feature.lyrics.romanization

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Per-player service. Only background requests touch the cache/backend, guarded by one mutex. */
class ReadingDocumentService(
    private val backend: ReadingBackend = createPlatformReadingBackend(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val cacheLimit: Int = 512
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<ReadingCacheKey, ReadingResult>()
    suspend fun romanizeDocument(
        lines: List<ReadingSourceLine>, language: RomanizationLanguage,
        options: ReadingOptions = ReadingOptions()
    ): List<ReadingDocumentLine> = withContext(dispatcher) {
        val effective = resolveDocumentLanguage(lines.map { it.text }, language)
        mutex.withLock {
            lines.map { line ->
                coroutineContext.ensureActive()
                val result = when (effective) {
                    RomanizationLanguage.AUTO -> ReadingResult(line.text, null, ReadingStatus.NEEDS_LANGUAGE, effective)
                    RomanizationLanguage.NONE -> ReadingResult(line.text, null, ReadingStatus.NOT_APPLICABLE, effective)
                    else -> {
                        val key = ReadingCacheKey(line.text, effective, options, backend.version)
                        cache.remove(key)?.also { cache[key] = it } ?: readSafely(line.text, effective, options).also {
                            coroutineContext.ensureActive()
                            if (it.status != ReadingStatus.UNAVAILABLE) {
                                cache[key] = it
                                while (cache.size > cacheLimit.coerceAtLeast(0)) cache.remove(cache.keys.first())
                            }
                        }
                    }
                }
                ReadingDocumentLine(line, result)
            }
        }
    }
    private fun readSafely(text: String, language: RomanizationLanguage, options: ReadingOptions): ReadingResult = try {
        backend.read(text, language, options)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ReadingResult(text, null, ReadingStatus.UNAVAILABLE, language, backendId = backend.version)
    } catch (_: LinkageError) {
        ReadingResult(text, null, ReadingStatus.UNAVAILABLE, language, backendId = backend.version)
    }

}
