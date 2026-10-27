package com.lirix.app.ui.library

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A library-only exclusion; track, listening history, lyrics and audio remain untouched. */
class LibraryExclusions internal constructor(private val preferences: SharedPreferences) {
    private val mutex = Mutex()
    private val initial = runCatching { preferences.getStringSet(KEY, emptySet())?.toSet().orEmpty() }
    val initializationFailed: Boolean = initial.isFailure
    private val mutableRemoved = MutableStateFlow(initial.getOrDefault(emptySet()))
    val removed: StateFlow<Set<String>> = mutableRemoved.asStateFlow()
    private val generations = mutableMapOf<String, Long>()
    private var nextGeneration = 0L

    /** Returns a token identifying this removal, or null if it was not saved. */
    suspend fun remove(key: String): Long? = mutex.withLock {
        if (key.isBlank() || initializationFailed || key in mutableRemoved.value) return@withLock null
        if (!write(mutableRemoved.value + key)) return@withLock null
        val token = ++nextGeneration
        generations[key] = token
        token
    }

    /** A stale snackbar token is an intentional no-op, never an undo of a newer removal. */
    suspend fun restore(key: String, expectedToken: Long? = null): Boolean = mutex.withLock {
        if (key.isBlank() || initializationFailed) return@withLock false
        if (expectedToken != null && generations[key] != expectedToken) return@withLock true
        if (key !in mutableRemoved.value) return@withLock true
        if (!write(mutableRemoved.value - key)) return@withLock false
        generations.remove(key)
        true
    }

    // Caller holds mutex. Failed commit may still mutate the Android in-memory prefs map.
    // Always derive future operations from the last confirmed value and attempt rollback.
    private suspend fun write(next: Set<String>): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        val previous = mutableRemoved.value
        val saved = try {
            preferences.edit().putStringSet(KEY, next.toSet()).commit()
        } catch (e: CancellationException) {
            // Some storage implementations may update memory before throwing.
            // Preserve the cancellation, but compensate before exposing it to the caller.
            try { compensate(previous) } catch (_: Exception) { }
            throw e
        } catch (e: Exception) {
            false
        }
        if (saved) {
            mutableRemoved.value = next.toSet()
            true
        } else {
            compensate(previous)
            false
        }
    }

    private fun compensate(previous: Set<String>) {
        try { preferences.edit().putStringSet(KEY, previous.toSet()).commit() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* authoritative state remains the previous confirmed set */ }
    }

    companion object {
        private const val FILE = "lirix_library_exclusions"
        private const val KEY = "removed_track_keys_v1"
        @Volatile private var instance: LibraryExclusions? = null

        fun get(context: Context): LibraryExclusions = instance ?: synchronized(this) {
            instance ?: LibraryExclusions(
                context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
