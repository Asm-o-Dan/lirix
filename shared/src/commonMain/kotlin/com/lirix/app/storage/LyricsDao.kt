package com.lirix.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lirix.app.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for LyricsCacheEntity and CustomLyricsRuleEntity.
 * Spec: TASK-DB-01 / TASK-LYR-04-A
 */
@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache")
    suspend fun getAllLyrics(): List<LyricsCacheEntity>

    @Query("SELECT * FROM lyrics_rejections")
    suspend fun getAllRejections(): List<LyricsRejectionEntity>

    @Query("SELECT * FROM custom_lyrics_rules ORDER BY priority ASC, createdAt DESC")
    suspend fun getAllRules(): List<CustomLyricsRuleEntity>

    @Query("SELECT * FROM lyrics_cache WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getLyrics(trackKey: String): LyricsCacheEntity?

    @Query("SELECT * FROM lyrics_cache WHERE trackKey = :trackKey LIMIT 1")
    fun observeLyrics(trackKey: String): Flow<LyricsCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyrics(entity: LyricsCacheEntity)

    @Query("SELECT * FROM music_tracks WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getAutomaticLookupTrack(trackKey: String): TrackEntity?

    @Query("UPDATE music_tracks SET plainLyrics = :plain, syncedLyrics = :synced WHERE trackKey = :key")
    suspend fun fillAutomaticLookupTrack(key: String, plain: String?, synced: String?)

    /** Source authorization and both writes share the same Room transaction. */
    @Transaction
    suspend fun fillMissingAutomaticLookup(trackKey: String, candidate: AutomaticLookupCandidate): LyricsCacheEntity? {
        val old = getLyrics(trackKey)
        val track = getAutomaticLookupTrack(trackKey) ?: return old
        val provider = old?.provider.orEmpty()
        if (provider.equals("USER", true) || provider.startsWith("USER:", true)) return old
        val rejected = getRejectedSourceIds(trackKey).toSet()
        val noteFallback = provider == "note:user"
        val oldPlain = old?.plainLyrics.takeUnless { noteFallback }?.takeIf { it.isNotBlank() }
            ?: automaticLookupAcceptedTrackField(track.plainLyrics, track, old)
        val oldSynced = old?.syncedLyricsLrc.takeUnless { noteFallback }?.takeIf { it.isNotBlank() }
            ?: automaticLookupAcceptedTrackField(track.syncedLyrics, track, old)
        val plain = candidate.plain?.takeIf { oldPlain == null && it.authorized(rejected) }
        val synced = candidate.synced?.takeIf { oldSynced == null && it.authorized(rejected) }
        val chords = candidate.chords?.takeIf { old?.chordsAmDm.isNullOrBlank() && it.authorized(rejected) }
        if (plain == null && synced == null && chords == null) return old
        // A newly filled sync field cannot reattribute an already retained plain lyric.
        val lyricOrigin = (plain ?: synced).takeIf { noteFallback || oldPlain == null && oldSynced == null }
        // Chord-only recovery leaves the note-origin evidence intact until actual words arrive.
        val keepNoteOrigin = noteFallback && plain == null && synced == null
        val saved = LyricsCacheEntity(trackKey,
            if (keepNoteOrigin) old?.plainLyrics else oldPlain ?: plain?.text,
            if (keepNoteOrigin) old?.syncedLyricsLrc else oldSynced ?: synced?.text,
            old?.chordsAmDm?.takeIf { it.isNotBlank() } ?: chords?.text,
            old?.userNotes ?: track.userNotes,
            if (keepNoteOrigin) provider
                else if (noteFallback) (plain ?: synced)!!.sourceId
                else if (oldPlain != null || oldSynced != null) provider.ifBlank { "cache:room" }
                else (plain ?: synced ?: chords)!!.sourceId,
            sourceAttributionJson = mergeAutomaticLookupAttribution(old?.sourceAttributionJson, lyricOrigin, chords))
        saveLyrics(saved)
        if (plain != null || synced != null) fillAutomaticLookupTrack(trackKey,
            automaticLookupAcceptedTrackField(track.plainLyrics, track, old) ?: plain?.text,
            automaticLookupAcceptedTrackField(track.syncedLyrics, track, old) ?: synced?.text)
        return saved
    }

    @Query("DELETE FROM lyrics_cache WHERE trackKey = :trackKey")
    suspend fun deleteCachedLyrics(trackKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRejection(rejection: LyricsRejectionEntity)

    @Query("SELECT sourceId FROM lyrics_rejections WHERE trackKey = :trackKey")
    suspend fun getRejectedSourceIds(trackKey: String): List<String>

    @Query("DELETE FROM lyrics_rejections WHERE trackKey = :trackKey AND sourceId = :sourceId")
    suspend fun deleteRejection(trackKey: String, sourceId: String): Int

    @Query("DELETE FROM lyrics_rejections WHERE trackKey = :trackKey")
    suspend fun clearRejectionsForTrack(trackKey: String): Int

    @Query("SELECT COUNT(*) FROM lyrics_rejections WHERE trackKey = :trackKey")
    suspend fun getRejectionsCountForTrack(trackKey: String): Int

    // --- Custom Lyrics Rules (TASK-LYR-04-A) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: CustomLyricsRuleEntity)

    @Query("SELECT * FROM custom_lyrics_rules WHERE isEnabled = 1 ORDER BY priority ASC, createdAt DESC")
    suspend fun getActiveRules(): List<CustomLyricsRuleEntity>

    @Query("SELECT * FROM custom_lyrics_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: String): CustomLyricsRuleEntity?

    @Query("SELECT * FROM custom_lyrics_rules WHERE domain = :domain")
    suspend fun getRulesForDomain(domain: String): List<CustomLyricsRuleEntity>

    @Query("SELECT * FROM custom_lyrics_rules ORDER BY priority ASC, createdAt DESC")
    fun observeAllRules(): Flow<List<CustomLyricsRuleEntity>>

    @Query("UPDATE custom_lyrics_rules SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRuleEnabled(id: String, isEnabled: Boolean, updatedAt: Long = currentTimeMillis()): Int

    @Query("DELETE FROM custom_lyrics_rules WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteRule(id: String): Int
}
