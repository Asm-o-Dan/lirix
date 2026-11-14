package com.lirix.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for Music tracks and listening sessions.
 */
@Dao
interface MusicDao {

    @Query("UPDATE music_tracks SET totalDurationMs = totalDurationMs + :deltaMs, lastPlayedAt = MAX(lastPlayedAt, :timestamp), isFavorite = CASE WHEN favoriteUserSetAt IS NULL AND totalDurationMs + :deltaMs >= 900000 THEN 1 ELSE isFavorite END WHERE trackKey = :trackKey")
    suspend fun addListeningCredit(trackKey: String, deltaMs: Long, timestamp: Long)

    @Transaction
    suspend fun commitListeningProgress(session: ListeningSessionEntity, deltaMs: Long, timestamp: Long) {
        updateSession(session)
        addListeningCredit(session.trackKey, deltaMs, timestamp)
    }

    @Transaction
    suspend fun commitPlaybackState(track: TrackEntity, session: ListeningSessionEntity, completed: ListeningSessionEntity?): Long {
        completed?.let { updateSession(it) }
        insertOrUpdateTrack(track)
        return if (session.id == 0L) insertSession(session) else { updateSession(session); session.id }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTrack(track: TrackEntity)

    /** Tracks with this title and artist under any album or player (the key also includes the album). */
    @Query("SELECT * FROM music_tracks WHERE title = :title COLLATE NOCASE AND artist = :artist COLLATE NOCASE LIMIT 8")
    suspend fun getTracksByTitleAndArtist(title: String, artist: String): List<TrackEntity>

    @Query("SELECT * FROM music_tracks WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getTrackByKey(trackKey: String): TrackEntity?

    @Query("SELECT * FROM music_tracks ORDER BY lastPlayedAt DESC")
    fun observeRecentTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM music_tracks WHERE isFavorite = 1 ORDER BY playCount DESC")
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM music_tracks WHERE isFavorite = 1 ORDER BY playCount DESC")
    suspend fun getFavoriteTracks(): List<TrackEntity>

    @Query("SELECT * FROM music_tracks ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentTracks(limit: Int = 20): List<TrackEntity>

    @Query("UPDATE music_tracks SET userNotes = :notes WHERE trackKey = :trackKey")
    suspend fun updateUserNotes(trackKey: String, notes: String)

    @Query("UPDATE music_tracks SET plainLyrics = :plainLyrics, syncedLyrics = :syncedLyrics WHERE trackKey = :trackKey")
    suspend fun updateLyrics(trackKey: String, plainLyrics: String?, syncedLyrics: String?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackIfMissing(track: TrackEntity)

    @Query("UPDATE music_tracks SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END, favoriteUserSetAt = :userSetAt WHERE trackKey = :trackKey")
    suspend fun invertFavoriteByUser(trackKey: String, userSetAt: Long)

    /** Explicit user toggle of an existing track (library row and action sheet). */
    suspend fun invertFavorite(trackKey: String) = invertFavoriteByUser(trackKey, System.currentTimeMillis())

    /** Explicit user toggle. The choice is remembered so automatic rules never override it. */
    @Transaction
    suspend fun toggleFavorite(track: TrackEntity): Boolean {
        insertTrackIfMissing(track.copy(isFavorite = false, favoriteUserSetAt = null))
        invertFavoriteByUser(track.trackKey, System.currentTimeMillis())
        return getTrackByKey(track.trackKey)?.isFavorite == true
    }

    @Query("UPDATE music_tracks SET isFavorite = :favorite, favoriteUserSetAt = :userSetAt WHERE trackKey = :trackKey")
    suspend fun setFavoriteByUser(trackKey: String, favorite: Boolean, userSetAt: Long)

    /** Explicit user choice. The choice is remembered so automatic rules never override it. */
    suspend fun setFavorite(trackKey: String, favorite: Boolean) =
        setFavoriteByUser(trackKey, favorite, System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ListeningSessionEntity): Long

    @Query("SELECT * FROM music_listening_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ListeningSessionEntity?

    @Query("SELECT * FROM music_listening_sessions ORDER BY id DESC, endTimeMs DESC LIMIT 1")
    suspend fun getActiveOrLastSession(): ListeningSessionEntity?

    @Query("SELECT * FROM music_listening_sessions WHERE sourcePackage = :pkg ORDER BY id DESC, endTimeMs DESC LIMIT 1")
    suspend fun getLastSessionForPackage(pkg: String): ListeningSessionEntity?

    @Update
    suspend fun updateSession(session: ListeningSessionEntity)

    @Query("SELECT * FROM music_listening_sessions ORDER BY startTimeMs ASC")
    fun observeAllSessions(): Flow<List<ListeningSessionEntity>>

    @Query("SELECT * FROM music_listening_sessions ORDER BY startTimeMs ASC")
    suspend fun getAllSessions(): List<ListeningSessionEntity>

    @Query("SELECT * FROM music_tracks")
    suspend fun getAllTracks(): List<TrackEntity>

    @Query("DELETE FROM music_tracks WHERE trackKey IN (:trackKeys)")
    suspend fun deleteTracksByKeys(trackKeys: List<String>): Int

    @Query("DELETE FROM music_listening_sessions WHERE trackKey IN (:trackKeys)")
    suspend fun deleteSessionsByTrackKeys(trackKeys: List<String>): Int

    @Query("SELECT * FROM music_listening_sessions WHERE trackKey IN (:trackKeys)")
    suspend fun getSessionsByTrackKeys(trackKeys: List<String>): List<ListeningSessionEntity>

    /** Restores removed sessions with their original ids; an id taken meanwhile is left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreSessions(sessions: List<ListeningSessionEntity>): List<Long>

    @Query("DELETE FROM music_tracks")
    suspend fun clearAllTracks()

    @Query("DELETE FROM music_listening_sessions")
    suspend fun clearAllSessions()

    @Query("SELECT DISTINCT sourcePackage FROM music_listening_sessions WHERE sourcePackage IS NOT NULL AND sourcePackage != '' ORDER BY id DESC LIMIT :limit")
    suspend fun getDistinctSourcePackages(limit: Int = 20): List<String>

    @Query("SELECT DISTINCT artist FROM music_tracks WHERE artist IS NOT NULL AND artist != '' ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    suspend fun getTopArtists(limit: Int = 20): List<String>

    @Query("SELECT DISTINCT title FROM music_tracks WHERE title IS NOT NULL AND title != '' ORDER BY lastPlayedAt DESC, playCount DESC LIMIT :limit")
    suspend fun getRecentTrackTitles(limit: Int = 20): List<String>
}
