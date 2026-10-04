package com.eventengine.app.feature

import android.content.Context
import com.eventengine.app.domain.Event
import com.eventengine.app.domain.EventType
import com.eventengine.app.feature.lyrics.AmDmChordParser
import com.eventengine.app.feature.lyrics.LyricsNetworkPolicy
import com.eventengine.app.feature.lyrics.NetworkConditionManager
import com.eventengine.app.storage.ListeningSessionEntity
import com.eventengine.app.storage.LyricsCacheEntity
import com.eventengine.app.storage.LyricsDao
import com.eventengine.app.storage.LyricsRejectionEntity
import com.eventengine.app.storage.MusicDao
import com.eventengine.app.storage.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
        onLyricsDiscovered = { trackKey, plain, synced ->
            musicDao.updateLyrics(trackKey, plain, synced)
        }
    )
) {
    private val lastIncrementTimeMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

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
                isCompleted = (playbackState == "PAUSED" || playbackState == "STOPPED")
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
                isCompleted = (playbackState == "PAUSED" || playbackState == "STOPPED")
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

    fun prefetchLyricsAsync(
        context: Context,
        scope: CoroutineScope,
        track: TrackEntity
    ) {
        // Если текст уже есть — повторный запрос не нужен
        if (!track.plainLyrics.isNullOrBlank() || !track.syncedLyrics.isNullOrBlank()) {
            return
        }

        val policy = NetworkConditionManager.getNetworkPolicy(context)
        if (policy == LyricsNetworkPolicy.OFFLINE_ONLY) {
            Timber.tag(TAG).d("Skipping lyrics prefetch: OFFLINE_ONLY")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val rejected = lyricsDao?.getRejectedSourceIds(track.trackKey)?.toSet().orEmpty()

                // Проверяем кэш Room
                val cached = lyricsDao?.getLyrics(track.trackKey)
                if (cached != null && (!cached.plainLyrics.isNullOrBlank() || !cached.syncedLyricsLrc.isNullOrBlank())) {
                    if (cached.provider !in rejected) {
                        musicDao.updateLyrics(track.trackKey, cached.plainLyrics, cached.syncedLyricsLrc)
                        return@launch
                    }
                }

                // Загружаем через агрегатор (LrcLib -> Scrapers)
                val fetched = lyricsAdapter.getLyrics(track, rejectedSourceIds = rejected, forceNetwork = true)
                if (fetched.hasLyrics && fetched.sourceId !in rejected) {
                    val plain = fetched.plainLyrics ?: fetched.lyricsText
                    val synced = fetched.syncedLyrics

                    // 1. Dual-write в lyrics_cache
                    lyricsDao?.saveLyrics(
                        LyricsCacheEntity(
                            trackKey = track.trackKey,
                            plainLyrics = plain,
                            syncedLyricsLrc = synced,
                            chordsAmDm = fetched.chords ?: AmDmChordParser.parseAmDmHtml(plain),
                            userNotes = "",
                            provider = fetched.sourceId.ifBlank { fetched.source }
                        )
                    )

                    // 2. Dual-write в music_tracks
                    musicDao.updateLyrics(track.trackKey, plain, synced)
                    Timber.tag(TAG).i("Successfully prefetched lyrics for: %s", track.title)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Background lyrics prefetch failed for %s", track.title)
            }
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
            val normalized = "${title.trim().lowercase()}|${artist.trim().lowercase()}|${album.trim().lowercase()}"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
