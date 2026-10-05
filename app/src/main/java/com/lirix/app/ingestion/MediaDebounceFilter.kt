package com.lirix.app.ingestion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

/**
 * Sliding window debounce filter for media playback state changes.
 * Collapses rapid BUFFERING/UPDATE -> PLAYING sequences (within 600 ms)
 * to avoid spamming the database with transient states.
 */
class MediaDebounceFilter(
    private val scope: CoroutineScope,
    val debounceDelayMs: Long = 600L,
    private val onEventReady: suspend (packageName: String, title: String?, artist: String?, album: String?, stateName: String) -> Unit
) {

    private val pendingJobs = ConcurrentHashMap<String, Job>()

    fun onStateChange(
        packageName: String,
        title: String?,
        artist: String?,
        album: String?,
        stateName: String
    ) {
        val trackKey = "$packageName|$title|$artist"

        when (stateName) {
            "PLAYING" -> {
                // Cancel transient BUFFERING / UPDATE debounce for this track
                val pending = pendingJobs.remove(trackKey)
                if (pending != null) {
                    pending.cancel()
                    Timber.tag(TAG).d("Debounce collapsed BUFFERING/UPDATE into PLAYING for [%s] %s", packageName, title)
                }

                scope.launch {
                    onEventReady(packageName, title, artist, album, stateName)
                }
            }

            "BUFFERING", "UPDATE" -> {
                // Cancel existing pending job for this track and schedule new debounce window
                pendingJobs.remove(trackKey)?.cancel()

                val job = scope.launch {
                    delay(debounceDelayMs)
                    pendingJobs.remove(trackKey)
                    Timber.tag(TAG).d("Debounce window expired. Emitting %s for [%s] %s", stateName, packageName, title)
                    onEventReady(packageName, title, artist, album, stateName)
                }
                pendingJobs[trackKey] = job
            }

            else -> {
                // PAUSED, STOPPED, etc. -> Flush immediately and cancel pending
                pendingJobs.remove(trackKey)?.cancel()
                scope.launch {
                    onEventReady(packageName, title, artist, album, stateName)
                }
            }
        }
    }

    fun hasPendingDebounce(packageName: String, title: String?, artist: String?): Boolean {
        val trackKey = "$packageName|$title|$artist"
        return pendingJobs[trackKey]?.isActive == true
    }

    fun cancelAll() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
    }

    companion object {
        private const val TAG = "MediaDebounceFilter"
    }
}
