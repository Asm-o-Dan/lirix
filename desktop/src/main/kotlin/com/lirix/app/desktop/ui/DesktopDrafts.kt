package com.lirix.app.desktop.ui

import androidx.compose.runtime.*
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.feature.lyrics.SyncLineItem
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.CancellationException

internal class DesktopDraft<T>(initial: T) {
    var value by mutableStateOf(initial)
    private var baseline by mutableStateOf(initial)
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val dirty get() = value != baseline

    fun initialize(transform: (T) -> T) {
        check(!saving)
        baseline = transform(baseline)
        value = transform(value)
    }

    suspend fun save(writer: suspend (T) -> Unit): Boolean {
        if (saving) return false
        val snapshot = value
        saving = true
        error = null
        return try {
            writer(snapshot)
            baseline = snapshot
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: com.lirix.app.ui.i18n.LirixStrings.message("Не удалось сохранить изменения")
            false
        } finally {
            saving = false
        }
    }
}

internal data class DesktopLyricsContent(val plain: String, val lrc: String, val chords: String)

internal class DesktopLyricsDraft(track: TrackEntity) {
    val content = DesktopDraft(DesktopLyricsContent(track.plainLyrics.orEmpty(), track.syncedLyrics.orEmpty(), ""))
    var chordsLoaded by mutableStateOf(false)
        private set
    fun loadChords(chords: String) {
        if (chordsLoaded) return
        content.initialize { it.copy(chords = chords) }
        chordsLoaded = true
    }
}

/** Owned by the application, rather than the disposable studio window. */
class DesktopDraftStore {
    internal val lyrics = mutableStateMapOf<String, DesktopLyricsDraft>()
    internal val sync = mutableStateMapOf<String, DesktopDraft<List<SyncLineItem>>>()
    internal val notes = mutableStateMapOf<String, String>()
    internal fun lyricsFor(track: TrackEntity) = lyrics.getOrPut(track.trackKey) { DesktopLyricsDraft(track) }
    internal fun syncFor(track: TrackEntity) = sync.getOrPut(track.trackKey) {
        DesktopDraft(LrcSyncEngine.parsePlainToSyncLines(track.plainLyrics ?: track.syncedLyrics?.let {
            LrcSyncEngine.parseLrcLinesStrict(it).joinToString("\n") { line -> line.text }
        }.orEmpty()))
    }
    val hasUnsavedChanges get() = notes.isNotEmpty() || lyrics.values.any { it.content.dirty || it.content.saving } ||
        sync.values.any { it.dirty || it.saving }
}
