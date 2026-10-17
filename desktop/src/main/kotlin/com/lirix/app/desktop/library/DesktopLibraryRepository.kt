package com.lirix.app.desktop.library

import com.lirix.app.analytics.GamificationEngine
import com.lirix.app.analytics.AchievementCatalog
import com.lirix.app.feature.blacklist.BlacklistMatcher
import com.lirix.app.feature.music.MusicTrackParser
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.storage.*
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DesktopLibraryRepository(val database: AppDatabase, private val scope: CoroutineScope) {
    private val music = database.musicDao()
    private val mutex = Mutex()
    private val ledger = ListeningLedger()
    private var activeSession: ListeningSessionEntity? = null
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private fun <T> Flow<List<T>>.recover(label: String): Flow<List<T>> = catch { error ->
        if (error is CancellationException) throw error
        _error.value = "$label: ${error.message}"
        emit(emptyList())
    }
    val tracks = music.observeRecentTracks().recover("Медиатека недоступна").stateIn(scope, SharingStarted.Eagerly, emptyList())
    val sessions = music.observeAllSessions().recover("История недоступна").stateIn(scope, SharingStarted.Eagerly, emptyList())
    val achievements = database.achievementDao().observeAchievements().recover("Достижения недоступны").stateIn(scope, SharingStarted.Eagerly, emptyList())
    val rules = database.blacklistDao().getAllRulesFlow().recover("Чёрный список недоступен").stateIn(scope, SharingStarted.Eagerly, emptyList())
    val sourceRules = database.lyricsDao().observeAllRules().recover("Источники недоступны").stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch(Dispatchers.IO) {
            try { database.achievementDao().insertAll(AchievementCatalog.defaults()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = "Не удалось открыть базу данных: ${e.message}"; return@launch }
            combine(tracks, sessions) { t, s -> t to s }.collect { (t, s) ->
                try {
                    val dao = database.achievementDao()
                    val previous = achievements.value.ifEmpty { AchievementCatalog.defaults() }
                    val byId = previous.associateBy { it.id }
                    GamificationEngine.checkAchievements(previous, t, s).filter { it != byId[it.id] }.forEach { dao.insertOrUpdate(it) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _error.value = "Не удалось обновить достижения: ${e.message}" }
            }
        }
    }

    fun keyFor(playback: LivePlaybackSnapshot): String {
        val parsed = MusicTrackParser.parseTrackMetadata(playback.title, playback.artist, playback.title, playback.artist)
        return MusicTrackParser.computeTrackKey(parsed.title, parsed.artist, playback.album)
    }

    suspend fun record(playback: LivePlaybackSnapshot?, monotonicMs: Long = System.nanoTime() / 1_000_000) =
        withContext(Dispatchers.IO) { mutex.withLock {
            val now = System.currentTimeMillis()
            val activeRules = database.blacklistDao().getActiveRules()
            val allowed = playback?.takeIf { p -> p.title.isNotBlank() && !BlacklistMatcher.isBlacklisted(
                p.title, p.artist, p.packageName, activeRules
            ) }
            val key = allowed?.let(::keyFor)
            val tick = ledger.sample(allowed?.let { ListeningSignal(key!!, it.isPlaying, it.currentPositionMs(), it.durationMs) }, monotonicMs)
            if (tick.elapsedMs > 0 && tick.elapsedKey != null) {
                activeSession?.takeIf { it.trackKey == tick.elapsedKey }?.let { session ->
                    val updated = session.copy(endTimeMs = now, durationMs = session.durationMs + tick.elapsedMs)
                    music.updateSession(updated)
                    activeSession = updated
                    music.getTrackByKey(session.trackKey)?.let { track ->
                        music.insertOrUpdateTrack(track.copy(totalDurationMs = track.totalDurationMs + tick.elapsedMs))
                    }
                }
            }
            if (tick.startedKey != null && allowed != null) {
                activeSession?.let { music.updateSession(it.copy(isCompleted = true)) }
                val parsed = MusicTrackParser.parseTrackMetadata(allowed.title, allowed.artist, allowed.title, allowed.artist)
                val old = music.getTrackByKey(tick.startedKey)
                val track = old?.copy(playCount = old.playCount + 1, lastPlayedAt = now, albumArtUri = allowed.albumArtUri ?: old.albumArtUri)
                    ?: TrackEntity(tick.startedKey, parsed.title, parsed.artist, allowed.album, allowed.packageName,
                        firstPlayedAt = now, lastPlayedAt = now, albumArtUri = allowed.albumArtUri)
                music.insertOrUpdateTrack(track)
                val session = ListeningSessionEntity(trackKey = track.trackKey, sourcePackage = allowed.packageName,
                    startTimeMs = now, endTimeMs = now, durationMs = 0)
                activeSession = session.copy(id = music.insertSession(session))
            }
        } }

    suspend fun saveLyrics(key: String, plain: String?, lrc: String?, chords: String?, provider: String = "USER") =
        withContext(Dispatchers.IO) { mutex.withLock {
            val old = database.lyricsDao().getLyrics(key)
            database.lyricsDao().saveLyrics(LyricsCacheEntity(key, plain, lrc, chords, old?.userNotes, provider))
            music.updateLyrics(key, plain, lrc)
        } }

    suspend fun savedLyrics(key: String) = withContext(Dispatchers.IO) { database.lyricsDao().getLyrics(key) }
    fun editNotes(key: String, notes: String) = perform { music.updateUserNotes(key, notes) }
    fun favorite(key: String, favorite: Boolean) = perform { music.setFavorite(key, favorite) }
    fun saveRule(rule: BlacklistRuleEntity) = perform { database.blacklistDao().insertRule(rule) }
    fun deleteRule(id: String) = perform { database.blacklistDao().deleteRule(id) }
    fun setRuleEnabled(rule: BlacklistRuleEntity, enabled: Boolean) = perform { database.blacklistDao().updateRule(rule.copy(isEnabled = enabled)) }
    fun perform(block: suspend () -> Unit) = scope.launch(Dispatchers.IO) {
        try { mutex.withLock { block() }; _error.value = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Не удалось сохранить изменения" }
    }
    fun clearError() { _error.value = null }
}
