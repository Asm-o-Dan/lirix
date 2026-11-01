package com.lirix.app.feature

import com.lirix.app.analytics.ListeningIntervals
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.LyricsDao
import com.lirix.app.storage.MusicDao
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.WeakHashMap

/** One serial playback clock per database DAO, shared by ingestion and heartbeat engine instances. */
internal class MonotonicSessionRecorder private constructor() {
    private val mutex = Mutex()
    private var session: ListeningSessionEntity? = null
    private var playing = false
    private var countedPlay = false
    private var lastMono = Long.MIN_VALUE
    private var lastStateMono = Long.MIN_VALUE
    private var lastWall = 0L
    private val lastIncrement = mutableMapOf<String, Long>()

    private suspend fun advance(dao: MusicDao, wall: Long, mono: Long) {
        val current = session
        var nextWall = wall
        if (current != null) {
            val interval = ListeningIntervals.confirmedSpan(lastMono, mono, lastWall, wall, playing)
            if (interval != null) {
                val updated = ListeningIntervals.append(current, interval)
                dao.commitListeningProgress(updated, interval.durationMs, wall)
                session = updated
                nextWall = interval.endMs
            }
        }
        lastMono = mono
        lastWall = nextWall
    }

    suspend fun record(dao: MusicDao, lyrics: LyricsDao?, title: String, artist: String, album: String,
        pkg: String, state: String, wall: Long, mono: Long, artwork: String?): TrackEntity = mutex.withLock {
        withContext(NonCancellable) {
            val key = MusicFeatureEngine.computeTrackKey(title, artist, album)
            if (session == null && mono >= lastMono && state in setOf("PLAYING", "PAUSED", "STOPPED", "BUFFERING")) {
                val persisted = dao.getLastSessionForPackage(pkg)
                if (persisted?.trackKey == key && !persisted.isCompleted && persisted.listeningIntervalsJson.isNotBlank()) {
                    // Reuse confirmed history after process death, with a fresh non-playing anchor.
                    session = persisted
                    countedPlay = true
                    playing = false
                    lastMono = mono
                    lastWall = wall
                }
            }
            val confirmed = state in setOf("PLAYING", "PAUSED", "STOPPED", "BUFFERING") &&
                (session == null || session?.sourcePackage == pkg || state == "PLAYING")
            if (confirmed && mono < lastStateMono) return@withContext dao.getTrackByKey(key)
                ?: TrackEntity(key, title, artist, album, pkg, playCount = 0)
            if (confirmed && mono < lastMono) {
                // A late pause can follow a slightly newer heartbeat. Never let that race count the pause gap.
                if (session?.trackKey == key && session?.sourcePackage == pkg) {
                    playing = state == "PLAYING"
                    lastStateMono = mono
                    if (state == "STOPPED") {
                        val stopped = session!!.copy(isCompleted = true)
                        dao.updateSession(stopped)
                        session = stopped
                    }
                }
                return@withContext dao.getTrackByKey(key) ?: TrackEntity(key, title, artist, album, pkg, playCount = 0)
            }
            if (confirmed) advance(dao, wall, mono)
            val oldTrack = dao.getTrackByKey(key)
            val oldSession = session
            val same = oldSession?.trackKey == key && oldSession.sourcePackage == pkg && !oldSession.isCompleted
            val wantsPlay = state == "PLAYING"
            val increment = confirmed && wantsPlay && (!same || !countedPlay) &&
                (oldTrack == null || mono - (lastIncrement[key] ?: Long.MIN_VALUE / 2) >= 60_000L)
            val cached = if (oldTrack?.plainLyrics == null && oldTrack?.syncedLyrics == null) lyrics?.getLyrics(key) else null
            val track = (oldTrack ?: TrackEntity(key, title, artist, album, pkg, playCount = 0,
                firstPlayedAt = wall, lastPlayedAt = wall)).copy(
                title = title, artist = artist, album = album, sourcePackage = pkg,
                playCount = (oldTrack?.playCount ?: 0) + if (increment) 1 else 0,
                lastPlayedAt = wall, albumArtUri = artwork ?: oldTrack?.albumArtUri,
                plainLyrics = oldTrack?.plainLyrics ?: cached?.plainLyrics,
                syncedLyrics = oldTrack?.syncedLyrics ?: cached?.syncedLyricsLrc,
                isFavorite = oldTrack?.isFavorite == true || (oldTrack?.playCount ?: 0) + (if (increment) 1 else 0) >= 5
            )
            // An ongoing notification/metadata update is not proof that audio was actually playing.
            if (!confirmed) { dao.insertOrUpdateTrack(track); return@withContext track }
            if (!same && !wantsPlay) {
                // Discovering a paused/buffering player must not create a listening session.
                if (oldSession != null) {
                    dao.commitPlaybackState(track, oldSession.copy(isCompleted = true), null)
                    session = null
                } else dao.insertOrUpdateTrack(track)
                playing = false
                countedPlay = false
                lastStateMono = mono
                return@withContext track
            }
            val current = if (same) session!! else ListeningSessionEntity(trackKey = key, sourcePackage = pkg,
                startTimeMs = wall, endTimeMs = wall, durationMs = 0, listeningIntervalsJson = "[]")
            val updated = current.copy(endTimeMs = maxOf(current.endTimeMs, wall), isCompleted = state == "STOPPED")
            val completed = if (!same) oldSession?.copy(isCompleted = true)
                ?: dao.getLastSessionForPackage(pkg)?.takeIf { !it.isCompleted }?.copy(isCompleted = true) else null
            val id = dao.commitPlaybackState(track, updated, completed)
            session = updated.copy(id = id)
            playing = wantsPlay
            lastStateMono = mono
            countedPlay = (same && countedPlay) || wantsPlay
            if (increment) lastIncrement[key] = mono
            track
        }
    }

    suspend fun progress(dao: MusicDao, pkg: String?, wall: Long, mono: Long, stop: Boolean = false) = mutex.withLock {
        withContext(NonCancellable) {
            if (session == null || (pkg != null && session?.sourcePackage != pkg) || mono < lastMono) return@withContext
            advance(dao, wall, mono)
            if (stop) {
                session?.let { dao.updateSession(it.copy(isCompleted = true)) }
                session = null
                playing = false
                countedPlay = false
                lastStateMono = mono
            }
        }
    }

    companion object {
        private val recorders = WeakHashMap<MusicDao, MonotonicSessionRecorder>()
        fun forDao(dao: MusicDao): MonotonicSessionRecorder = synchronized(recorders) {
            recorders.getOrPut(dao) { MonotonicSessionRecorder() }
        }
    }
}
