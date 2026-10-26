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
            musicDao.updateLyrics(trackKey, plain, synced)
        }
    ),
    private val blacklistDao: BlacklistDao? = null
) {

    private val lastIncrementTimeMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

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
     * Records or updates the track in Room, collapses rapid sessions (<= 30s),
     * and updates play counts idempotently.
     * Spec: TASK-BUG-01 / .sdd/tasks/TASK-BUG-01.md
     */
    suspend fun recordPlaybackSignal(
        title: String,
        artist: String,
        album: String = "",
        sourcePackage: String,
        playbackState: String = "PLAYING",
        durationDeltaMs: Long = 0L,
        timestamp: Long = System.currentTimeMillis(),
        albumArtUri: String? = null
    ): TrackEntity? {
        if (title.isBlank()) return null

        val cleanArtist = artist.ifBlank { "Unknown Artist" }

        val rules = getEffectiveActiveRules()
        val isBlocked = BlacklistMatcher.isBlacklisted(
            title = title,
            artist = cleanArtist,
            packageName = sourcePackage,
            activeRules = rules
        )

        if (isBlocked) {
            Timber.tag(TAG).d("MusicFeatureEngine: Track '%s - %s' (%s) is blacklisted. Skipping persistence.", title, cleanArtist, sourcePackage)
            val isPlayingSignal = (playbackState != "PAUSED" && playbackState != "STOPPED")
            val current = _livePlaybackFlow.value
            _livePlaybackFlow.value = current?.copy(
                packageName = sourcePackage,
                title = title,
                artist = cleanArtist,
                album = album,
                isPlaying = isPlayingSignal,
                isBlacklisted = true
            ) ?: LivePlaybackSnapshot(
                packageName = sourcePackage,
                title = title,
                artist = cleanArtist,
                album = album,
                isPlaying = isPlayingSignal,
                basePositionMs = 0L,
                lastPositionUpdateTimeMs = timestamp,
                albumArtUri = albumArtUri,
                isBlacklisted = true
            )
            try {
                com.lirix.app.ingestion.MediaSessionCollector.markCurrentTrackBlacklisted(true)
            } catch (_: Throwable) {}
            return null
        }

        try {
            com.lirix.app.ingestion.MediaSessionCollector.markCurrentTrackBlacklisted(false)
        } catch (_: Throwable) {}

        val trackKey = computeTrackKey(title, cleanArtist, album)

        val existingTrack = musicDao.getTrackByKey(trackKey)
        val lastSession = musicDao.getLastSessionForPackage(sourcePackage)

        val isNewTrack = lastSession == null || lastSession.trackKey != trackKey
        val isGapLarge = lastSession != null && (timestamp - lastSession.endTimeMs) > SESSION_GAP_THRESHOLD_MS

        if (isNewTrack && lastSession != null && !lastSession.isCompleted) {
            val finalDuration = if (lastSession.durationMs > 0L) {
                val delta = (timestamp - lastSession.endTimeMs).coerceIn(0L, MAX_SINGLE_TRACK_GAP_MS)
                lastSession.durationMs + delta
            } else {
                (timestamp - lastSession.startTimeMs).coerceIn(0L, MAX_SINGLE_TRACK_GAP_MS)
            }

            val closedSession = lastSession.copy(
                endTimeMs = timestamp,
                durationMs = finalDuration,
                isCompleted = true
            )
            musicDao.updateSession(closedSession)

            val previousTrack = musicDao.getTrackByKey(lastSession.trackKey)
            if (previousTrack != null) {
                val durationDelta = (finalDuration - lastSession.durationMs).coerceAtLeast(0L)
                if (durationDelta > 0L) {
                    musicDao.insertOrUpdateTrack(
                        previousTrack.copy(
                            totalDurationMs = previousTrack.totalDurationMs + durationDelta,
                            lastPlayedAt = timestamp
                        )
                    )
                }
            }
        }

        val effectiveDuration = if (durationDeltaMs > 0L) {
            durationDeltaMs
        } else if (lastSession != null && !isNewTrack && !isGapLarge) {
            (timestamp - lastSession.endTimeMs).coerceAtLeast(0L)
        } else {
            0L
        }

        if (lastSession != null && !isNewTrack && !isGapLarge) {
            val updated = lastSession.copy(
                endTimeMs = timestamp,
                durationMs = lastSession.durationMs + effectiveDuration,
                isCompleted = (playbackState == "STOPPED")
            )
            musicDao.updateSession(updated)
        } else {
            val newSession = ListeningSessionEntity(
                trackKey = trackKey,
                sourcePackage = sourcePackage,
                startTimeMs = timestamp,
                endTimeMs = timestamp + effectiveDuration,
                durationMs = effectiveDuration,
                isCompleted = false
            )
            musicDao.insertSession(newSession)
        }

        val lastIncrement = lastIncrementTimeMap[trackKey] ?: 0L
        val isCooldownPassed = (timestamp - lastIncrement) >= PLAY_INCREMENT_COOLDOWN_MS

        val isFirstTrackOccurrence = (existingTrack == null)
        val isTrackSwitch = (lastSession == null || lastSession.trackKey != trackKey)
        val isTrackReplayAfterCompletion = (lastSession != null && lastSession.trackKey == trackKey && lastSession.isCompleted && isCooldownPassed)

        val isPlayingSignal = (playbackState != "PAUSED" && playbackState != "STOPPED")
        val shouldIncrement = isPlayingSignal && isCooldownPassed && (isFirstTrackOccurrence || isTrackSwitch || isTrackReplayAfterCompletion)

        if (shouldIncrement) {
            lastIncrementTimeMap[trackKey] = timestamp
        }

        val updatedPlayCount = (existingTrack?.playCount ?: 0) + (if (shouldIncrement) 1 else 0)
        val updatedTotalDuration = (existingTrack?.totalDurationMs ?: 0L) + effectiveDuration
        val isFavorite = (existingTrack?.isFavorite == true) ||
            (updatedPlayCount >= FAVORITE_MIN_PLAY_COUNT) ||
            (updatedTotalDuration >= FAVORITE_MIN_DURATION_MS)

        val effectiveAlbumArtUri = albumArtUri ?: existingTrack?.albumArtUri
        val cachedLyrics = if (existingTrack?.plainLyrics == null && existingTrack?.syncedLyrics == null) {
            lyricsDao?.getLyrics(trackKey)
        } else {
            null
        }
        val effectivePlainLyrics = existingTrack?.plainLyrics ?: cachedLyrics?.plainLyrics
        val effectiveSyncedLyrics = existingTrack?.syncedLyrics ?: cachedLyrics?.syncedLyricsLrc

        val track = TrackEntity(
            trackKey = trackKey,
            title = title,
            artist = cleanArtist,
            album = album,
            sourcePackage = sourcePackage,
            playCount = updatedPlayCount,
            totalDurationMs = updatedTotalDuration,
            firstPlayedAt = existingTrack?.firstPlayedAt ?: timestamp,
            lastPlayedAt = timestamp,
            userNotes = existingTrack?.userNotes.orEmpty(),
            isFavorite = isFavorite,
            albumArtUri = effectiveAlbumArtUri,
            plainLyrics = effectivePlainLyrics,
            syncedLyrics = effectiveSyncedLyrics
        )
        musicDao.insertOrUpdateTrack(track)
        Timber.tag(TAG).d("Recorded playback signal for %s: count=%d, fav=%b, art=%s", trackKey, updatedPlayCount, isFavorite, effectiveAlbumArtUri)
        return track
    }

    /**
     * Ingests a music event (MediaSession or Notification).
     * Returns the updated TrackEntity, or null if event is not a valid music playback signal.
     */
    suspend fun processMusicEvent(
        event: Event,
        playbackState: String? = null,
        durationDeltaMs: Long = 0L
    ): TrackEntity? {
        val parsed = MusicTrackParser.parse(event)
        if (parsed.title.isBlank()) return null

        val title = parsed.title
        val artist = parsed.artist.ifBlank { "Unknown Artist" }
        val album = parsed.album
        val pkg = event.sourcePackage

        val rules = getEffectiveActiveRules()
        val isBlocked = BlacklistMatcher.isBlacklisted(
            title = title,
            artist = artist,
            packageName = pkg,
            activeRules = rules
        )
        if (isBlocked) {
            Timber.tag(TAG).d("MusicFeatureEngine: Track '%s - %s' (%s) is blacklisted. Skipping persistence.", title, artist, pkg)
            return null
        }

        val trackKey = computeTrackKey(title, artist, album)
        val now = event.timestamp

        // Retrieve existing track or initialize new one
        val existingTrack = musicDao.getTrackByKey(trackKey)

        // Session management: check last session for this package
        val lastSession = musicDao.getLastSessionForPackage(pkg)
        val isNewTrack = lastSession == null || lastSession.trackKey != trackKey
        val isGapLarge = lastSession != null && (now - lastSession.endTimeMs) > SESSION_GAP_THRESHOLD_MS

        val effectiveDuration = if (durationDeltaMs > 0L) {
            durationDeltaMs
        } else if (lastSession != null && !isNewTrack && !isGapLarge) {
            (now - lastSession.endTimeMs).coerceAtLeast(0L)
        } else {
            0L
        }

        val lastIncrement = lastIncrementTimeMap[trackKey] ?: 0L
        val isCooldownPassed = (now - lastIncrement) >= PLAY_INCREMENT_COOLDOWN_MS

        val isFirstTrackOccurrence = (existingTrack == null)
        val isTrackSwitch = (lastSession == null || lastSession.trackKey != trackKey)
        val isTrackReplayAfterCompletion = (lastSession != null && lastSession.trackKey == trackKey && lastSession.isCompleted && isCooldownPassed)

        val isPlayingSignal = (playbackState != "PAUSED" && playbackState != "STOPPED")
        val shouldIncrementPlayCount = isPlayingSignal && isCooldownPassed && (isFirstTrackOccurrence || isTrackSwitch || isTrackReplayAfterCompletion)

        if (shouldIncrementPlayCount) {
            lastIncrementTimeMap[trackKey] = now
        }

        // Update active or new session
        if (lastSession != null && !isNewTrack && !isGapLarge) {
            val updatedSession = lastSession.copy(
                endTimeMs = now,
                durationMs = lastSession.durationMs + effectiveDuration,
                isCompleted = (playbackState == "STOPPED")
            )
            musicDao.updateSession(updatedSession)
        } else {
            val newSession = ListeningSessionEntity(
                trackKey = trackKey,
                sourcePackage = pkg,
                startTimeMs = now,
                endTimeMs = now + effectiveDuration,
                durationMs = effectiveDuration,
                isCompleted = false
            )
            musicDao.insertSession(newSession)
        }

        val updatedPlayCount = (existingTrack?.playCount ?: 0) + (if (shouldIncrementPlayCount) 1 else 0)
        val updatedTotalDuration = (existingTrack?.totalDurationMs ?: 0L) + effectiveDuration

        // Automatic favorite detection rule: playCount >= 5 OR totalDurationMs >= 15 minutes (900,000 ms)
        val isFavorite = (existingTrack?.isFavorite == true) ||
                (updatedPlayCount >= FAVORITE_MIN_PLAY_COUNT) ||
                (updatedTotalDuration >= FAVORITE_MIN_DURATION_MS)

        val cachedLyrics = if (existingTrack?.plainLyrics == null && existingTrack?.syncedLyrics == null) {
            lyricsDao?.getLyrics(trackKey)
        } else {
            null
        }
        val effectivePlainLyrics = existingTrack?.plainLyrics ?: cachedLyrics?.plainLyrics
        val effectiveSyncedLyrics = existingTrack?.syncedLyrics ?: cachedLyrics?.syncedLyricsLrc

        val updatedTrack = TrackEntity(
            trackKey = trackKey,
            title = title,
            artist = artist,
            album = album,
            sourcePackage = pkg,
            playCount = updatedPlayCount,
            totalDurationMs = updatedTotalDuration,
            firstPlayedAt = existingTrack?.firstPlayedAt ?: now,
            lastPlayedAt = now,
            userNotes = existingTrack?.userNotes.orEmpty(),
            isFavorite = isFavorite,
            albumArtUri = existingTrack?.albumArtUri,
            plainLyrics = effectivePlainLyrics,
            syncedLyrics = effectiveSyncedLyrics
        )

        musicDao.insertOrUpdateTrack(updatedTrack)
        Timber.tag(TAG).d("Processed track %s: playCount=%d, fav=%b", trackKey, updatedPlayCount, isFavorite)
        return updatedTrack
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
            lyricsDao?.saveLyrics(
                LyricsCacheEntity(
                    trackKey = trackKey,
                    plainLyrics = plain,
                    syncedLyricsLrc = synced,
                    chordsAmDm = nextResult.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                    userNotes = track.userNotes,
                    provider = nextResult.sourceId.ifBlank { nextResult.source }
                )
            )
            musicDao.updateLyrics(trackKey, plain, synced)
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
            lyricsDao?.saveLyrics(
                LyricsCacheEntity(
                    trackKey = trackKey,
                    plainLyrics = plain,
                    syncedLyricsLrc = synced,
                    chordsAmDm = result.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                    userNotes = track.userNotes,
                    provider = result.sourceId.ifBlank { result.source }
                )
            )
            musicDao.updateLyrics(trackKey, plain, synced)
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
            lyricsDao?.saveLyrics(
                LyricsCacheEntity(
                    trackKey = trackKey,
                    plainLyrics = plain,
                    syncedLyricsLrc = synced,
                    chordsAmDm = result.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                    userNotes = track.userNotes,
                    provider = result.sourceId.ifBlank { result.source }
                )
            )
            musicDao.updateLyrics(trackKey, plain, synced)
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
     * Commits incremental playback progress to the currently active uncompleted session
     * and credits the progress delta to the corresponding track in Room.
     * Spec: TASK-BUG-03 / .sdd/tasks/TASK-BUG-03.md
     */
    suspend fun updateActiveSessionProgress(
        sourcePackage: String,
        currentPlaybackPositionMs: Long? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val lastSession = musicDao.getLastSessionForPackage(sourcePackage) ?: return
        if (lastSession.isCompleted) return

        val effectiveDuration = if (currentPlaybackPositionMs != null && currentPlaybackPositionMs > 0L) {
            currentPlaybackPositionMs
        } else {
            (timestamp - lastSession.startTimeMs).coerceIn(0L, MAX_SINGLE_TRACK_GAP_MS)
        }

        if (effectiveDuration > lastSession.durationMs) {
            val durationDelta = effectiveDuration - lastSession.durationMs
            val updatedSession = lastSession.copy(
                endTimeMs = timestamp,
                durationMs = effectiveDuration
            )
            musicDao.updateSession(updatedSession)

            val track = musicDao.getTrackByKey(lastSession.trackKey)
            if (track != null) {
                musicDao.insertOrUpdateTrack(
                    track.copy(
                        totalDurationMs = track.totalDurationMs + durationDelta,
                        lastPlayedAt = timestamp
                    )
                )
            }
        }
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
