package com.lirix.app.feature

import android.content.Context
import com.lirix.app.domain.Event
import com.lirix.app.domain.EventType
import com.lirix.app.feature.blacklist.BlacklistMatcher
import com.lirix.app.feature.lyrics.AmDmChordParser
import com.lirix.app.feature.lyrics.LyricsNetworkPolicy
import com.lirix.app.feature.lyrics.NetworkConditionManager
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.LyricsDao
import com.lirix.app.storage.LyricsRejectionEntity
import com.lirix.app.storage.MusicDao
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.blacklist.BlacklistDao
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest

/**
 * Domain Feature Engine for Music & Listening Context (Sprint 4 Task 4.1).
 *
 * Responsibilities:
 * 1. Deterministic Track Identification via SHA-256 trackKey.
 * 2. Playback Session Aggregation: collapses rapid pauses (<= 30s) into continuous sessions.
 * 3. Idempotent Play Count Accounting: ensures push/metadata fluctuations don't falsely bump play counts.
 * 4. Automatic Favorite Detection: sets isFavorite = true when playCount >= 5 or totalDurationMs >= 15 min.
 * 5. Offline Lyrics & Personal Song Notes via OfflineLyricsAdapter.
 */
class MusicFeatureEngine(
    private val musicDao: MusicDao,
    private val lyricsDao: LyricsDao? = null,
    private val lyricsAdapter: LyricsProvider = AggregatedLyricsProvider(
        lyricsDao = lyricsDao,
        onLyricsDiscovered = { trackKey, plain, synced ->
        }
    ),
    private val blacklistDao: BlacklistDao? = null
) {

    private val activeBlacklistRules = MutableStateFlow<List<BlacklistRuleEntity>>(emptyList())
    private val _livePlaybackFlow = MutableStateFlow<LivePlaybackSnapshot?>(null)
    val livePlaybackFlow: StateFlow<LivePlaybackSnapshot?> = _livePlaybackFlow.asStateFlow()

    fun startBlacklistRuleSync(scope: CoroutineScope, dao: BlacklistDao? = blacklistDao) {
        val targetDao = dao ?: blacklistDao ?: return
        scope.launch {
            targetDao.getAllRulesFlow().collect { allRules ->
                activeBlacklistRules.value = allRules.filter { it.isEnabled }
            }
        }
    }

    fun startBlacklistSync(scope: CoroutineScope, db: com.lirix.app.storage.AppDatabase) {
        startBlacklistRuleSync(scope, db.blacklistDao())
    }

    fun updateActiveBlacklistRules(rules: List<BlacklistRuleEntity>) {
        activeBlacklistRules.value = rules.filter { it.isEnabled }
    }

    suspend fun getEffectiveActiveRules(): List<BlacklistRuleEntity> {
        return if (activeBlacklistRules.value.isNotEmpty()) {
            activeBlacklistRules.value
        } else if (blacklistDao != null) {
            blacklistDao.getActiveRules().also { activeBlacklistRules.value = it }
        } else {
            emptyList()
        }
    }

    /**
     * Ingests a direct playback signal without requiring the legacy Event object.
     * Metadata and confirmed playback states share one monotonic recorder per DAO.
     * Only observed PLAYING intervals contribute time; pause gaps and media positions do not.
     */
    suspend fun recordPlaybackSignal(
        title: String,
        artist: String,
        album: String = "",
        sourcePackage: String,
        playbackState: String = "PLAYING",
        durationDeltaMs: Long = 0L,
        timestamp: Long = System.currentTimeMillis(),
        albumArtUri: String? = null,
        monotonicMs: Long = android.os.SystemClock.elapsedRealtime()
    ): TrackEntity? {
        if (title.isBlank()) return null
        val cleanArtist = artist.ifBlank { "Unknown Artist" }
        val recorder = MonotonicSessionRecorder.forDao(musicDao)
        val blocked = BlacklistMatcher.isBlacklisted(title, cleanArtist, sourcePackage, getEffectiveActiveRules())
        if (blocked) {
            if (playbackState in setOf("PLAYING", "PAUSED", "STOPPED", "BUFFERING")) {
                recorder.progress(musicDao, sourcePackage.takeUnless { playbackState == "PLAYING" }, timestamp, monotonicMs, stop = true)
            }
            _livePlaybackFlow.value = LivePlaybackSnapshot(sourcePackage, title, cleanArtist, album,
                playbackState == "PLAYING", 0L, monotonicMs, timestamp = timestamp,
                albumArtUri = albumArtUri, isBlacklisted = true)
            com.lirix.app.ingestion.MediaSessionCollector.markCurrentTrackBlacklisted(true)
            return null
        }
        com.lirix.app.ingestion.MediaSessionCollector.markCurrentTrackBlacklisted(false)
        // durationDeltaMs is retained for source compatibility; unverified caller values are never credited.
        return recorder.record(musicDao, lyricsDao, title, cleanArtist, album, sourcePackage,
            playbackState, timestamp, monotonicMs, albumArtUri)
    }

    suspend fun processMusicEvent(
        event: Event,
        playbackState: String? = null,
        durationDeltaMs: Long = 0L,
        monotonicMs: Long = android.os.SystemClock.elapsedRealtime()
    ): TrackEntity? {
        val parsed = MusicTrackParser.parse(event)
        return recordPlaybackSignal(parsed.title, parsed.artist, parsed.album, event.sourcePackage,
            playbackState = playbackState ?: event.mediaPlaybackState ?: "UNKNOWN",
            timestamp = event.timestamp, monotonicMs = monotonicMs)
    }

    suspend fun getLyrics(trackKey: String): LyricsResult {
        val track = musicDao.getTrackByKey(trackKey)
            ?: return LyricsResult(false, "Трек не найден в локальной истории", "Система", sourceId = LyricsSourceIds.NONE)
        val rejected = lyricsDao?.getRejectedSourceIds(trackKey)?.toSet().orEmpty()
        return lyricsAdapter.getLyrics(track, rejectedSourceIds = rejected, forceNetwork = false)
    }

    suspend fun rejectCurrentLyrics(trackKey: String, currentSourceId: String): LyricsResult {
        if (currentSourceId.isNotBlank() && currentSourceId != LyricsSourceIds.NONE) {
            lyricsDao?.insertRejection(
                LyricsRejectionEntity(
                    trackKey = trackKey,
                    sourceId = currentSourceId,
                    rejectedAt = System.currentTimeMillis()
                )
            )
        }
        // 1. Очищаем неверный текст из кэша и трека
        musicDao.updateLyrics(trackKey, null, null)
        lyricsDao?.deleteCachedLyrics(trackKey)

        // 2. Ищем следующий доступный источник
        val track = musicDao.getTrackByKey(trackKey)
            ?: return LyricsResult(false, "Трек не найден", "Система", sourceId = LyricsSourceIds.NONE)

        val trackWithoutLyrics = track.copy(plainLyrics = null, syncedLyrics = null)
        val rejected = lyricsDao?.getRejectedSourceIds(trackKey)?.toSet().orEmpty()
        val nextResult = lyricsAdapter.getLyrics(trackWithoutLyrics, rejectedSourceIds = rejected, forceNetwork = true)

        // 3. Если следующий источник найден — сохраняем его в кэш
        if (nextResult.hasLyrics && nextResult.sourceId !in rejected) {
            val plain = nextResult.plainLyrics ?: nextResult.lyricsText
            val synced = nextResult.syncedLyrics
            lyricsDao?.saveAutomaticIfNotUser(
                LyricsCacheEntity(
                    trackKey = trackKey,
                    plainLyrics = plain,
                    syncedLyricsLrc = synced,
                    chordsAmDm = nextResult.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                    userNotes = track.userNotes,
                    provider = nextResult.sourceId.ifBlank { nextResult.source },
                    sourceAttributionJson = nextResult.sourceAttributionJson
                )
            )
        }
        return nextResult
    }

    suspend fun undoLyricsRejection(trackKey: String, sourceId: String): LyricsResult {
        lyricsDao?.deleteRejection(trackKey, sourceId)
        val track = musicDao.getTrackByKey(trackKey)
            ?: return LyricsResult(false, "Трек не найден", "Система", sourceId = LyricsSourceIds.NONE)
        val trackWithoutLyrics = track.copy(plainLyrics = null, syncedLyrics = null)
        val rejected = lyricsDao?.getRejectedSourceIds(trackKey)?.toSet().orEmpty()
        val result = lyricsAdapter.getLyrics(trackWithoutLyrics, rejectedSourceIds = rejected, forceNetwork = true)
        if (result.hasLyrics && result.sourceId !in rejected) {
            val plain = result.plainLyrics ?: result.lyricsText
            val synced = result.syncedLyrics
            lyricsDao?.saveAutomaticIfNotUser(
                LyricsCacheEntity(
                    trackKey = trackKey,
                    plainLyrics = plain,
                    syncedLyricsLrc = synced,
                    chordsAmDm = result.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                    userNotes = track.userNotes,
                    provider = result.sourceId.ifBlank { result.source },
                    sourceAttributionJson = result.sourceAttributionJson
                )
            )
        }
        return result
    }

    suspend fun clearAllRejections(trackKey: String): LyricsResult {
        lyricsDao?.clearRejectionsForTrack(trackKey)
        val track = musicDao.getTrackByKey(trackKey)
            ?: return LyricsResult(false, "Трек не найден", "Система", sourceId = LyricsSourceIds.NONE)
        val trackWithoutLyrics = track.copy(plainLyrics = null, syncedLyrics = null)
        val result = lyricsAdapter.getLyrics(trackWithoutLyrics, rejectedSourceIds = emptySet(), forceNetwork = true)
        if (result.hasLyrics) {
            val plain = result.plainLyrics ?: result.lyricsText
            val synced = result.syncedLyrics
            lyricsDao?.saveAutomaticIfNotUser(
                LyricsCacheEntity(
                    trackKey = trackKey,
                    plainLyrics = plain,
                    syncedLyricsLrc = synced,
                    chordsAmDm = result.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                    userNotes = track.userNotes,
                    provider = result.sourceId.ifBlank { result.source },
                    sourceAttributionJson = result.sourceAttributionJson
                )
            )
        }
        return result
    }

    fun prefetchLyricsAsync(context: Context, scope: CoroutineScope, track: TrackEntity) {
        if (!track.plainLyrics.isNullOrBlank() || !track.syncedLyrics.isNullOrBlank()) return
        // Preserve the implemented prefetch transport gate; foreground recovery also permits cellular.
        if (NetworkConditionManager.getNetworkPolicy(context) == LyricsNetworkPolicy.OFFLINE_ONLY) return
        scope.launch(Dispatchers.IO) {
            try {
                val db = com.lirix.app.storage.AppDatabase.getInstance(context.applicationContext)
                com.lirix.app.feature.lyrics.AutomaticLookupCoordinator.get(db).lookup(track.trackKey,
                    com.lirix.app.feature.lyrics.LookupTarget.LYRICS,
                    com.lirix.app.feature.lyrics.LookupNetworkMonitor.usable(context.applicationContext))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Timber.tag(TAG).w(e, "Background lyrics prefetch failed for %s", track.title) }
        }
    }

    /**
     * Commits elapsed PLAYING time since the previous monotonic checkpoint.
     * The legacy position argument is ignored, including after seek/restart.
     */
    suspend fun updateActiveSessionProgress(
        sourcePackage: String,
        currentPlaybackPositionMs: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
        monotonicMs: Long = android.os.SystemClock.elapsedRealtime()
    ) {
        // Position is deliberately ignored: seeking is not listening.
        MonotonicSessionRecorder.forDao(musicDao).progress(musicDao, sourcePackage, timestamp, monotonicMs)
    }

    suspend fun stopListeningProgress(
        sourcePackage: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        monotonicMs: Long = android.os.SystemClock.elapsedRealtime()
    ) {
        MonotonicSessionRecorder.forDao(musicDao).progress(musicDao, sourcePackage, timestamp, monotonicMs, stop = true)
    }

    suspend fun saveUserNotes(trackKey: String, notes: String) {
        musicDao.updateUserNotes(trackKey, notes)
    }

    suspend fun toggleFavorite(trackKey: String, isFavorite: Boolean) {
        musicDao.setFavorite(trackKey, isFavorite)
    }

    fun observeRecentTracks(): Flow<List<TrackEntity>> = musicDao.observeRecentTracks()

    fun observeFavoriteTracks(): Flow<List<TrackEntity>> = musicDao.observeFavoriteTracks()

    companion object {
        private const val TAG = "MusicFeatureEngine"

        const val SESSION_GAP_THRESHOLD_MS = 30_000L      // 30 seconds debounce window between pauses
        const val MAX_SINGLE_TRACK_GAP_MS = 30 * 60 * 1000L // 30 minutes protection cap
        const val PLAY_INCREMENT_COOLDOWN_MS = 60_000L    // 60 seconds cooldown for playCount increment
        const val FAVORITE_MIN_PLAY_COUNT = 5             // 5 plays to reach favorite
        const val FAVORITE_MIN_DURATION_MS = 15 * 60 * 1000L // 15 minutes cumulative duration

        fun recordPlayback(
            existingTrack: TrackEntity?,
            title: String,
            artist: String,
            album: String = "",
            sourcePackage: String,
            sessionDurationMs: Long = 0L,
            now: Long = System.currentTimeMillis()
        ): TrackEntity {
            val trackKey = computeTrackKey(title, artist, album)
            val updatedPlayCount = (existingTrack?.playCount ?: 0) + 1
            val updatedTotalDuration = (existingTrack?.totalDurationMs ?: 0L) + sessionDurationMs
            val isFavorite = (existingTrack?.isFavorite == true) ||
                    (updatedPlayCount >= FAVORITE_MIN_PLAY_COUNT) ||
                    (updatedTotalDuration >= FAVORITE_MIN_DURATION_MS)

            return TrackEntity(
                trackKey = trackKey,
                title = title,
                artist = artist,
                album = album,
                sourcePackage = sourcePackage,
                playCount = updatedPlayCount,
                totalDurationMs = updatedTotalDuration,
                firstPlayedAt = existingTrack?.firstPlayedAt ?: now,
                lastPlayedAt = now,
                userNotes = existingTrack?.userNotes.orEmpty(),
                isFavorite = isFavorite,
                albumArtUri = existingTrack?.albumArtUri,
                plainLyrics = existingTrack?.plainLyrics,
                syncedLyrics = existingTrack?.syncedLyrics
            )
        }

        fun computeTrackKey(title: String, artist: String, album: String = ""): String {
            return com.lirix.app.feature.music.MusicTrackParser.computeTrackKey(title, artist, album)
        }
    }
}
