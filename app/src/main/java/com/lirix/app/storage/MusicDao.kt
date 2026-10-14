package com.lirix.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for Music tracks and listening sessions.
 */
@Dao
interface MusicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTrack(track: TrackEntity)

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

    @Query("UPDATE music_tracks SET isFavorite = :favorite WHERE trackKey = :trackKey")
    suspend fun setFavorite(trackKey: String, favorite: Boolean)

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
