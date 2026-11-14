package com.lirix.app.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Extra facts about a track found in open catalogues (Room v16).
 * Spec: .sdd/specs/media-core/track_metadata_and_avatar_v1.md
 *
 * A re-fetchable cache, never user content: it is not part of the library archive and holds no
 * manual edits. A field is null when no source gave it with a confirmed match; nothing is guessed.
 * Audio features are fractions 0..1 as published by the source.
 */
@Entity(tableName = "track_metadata")
data class TrackMetadataEntity(
    @PrimaryKey
    val trackKey: String,
    /** [TrackMetadataStatus] name. */
    val status: String,
    /** Genres joined with [TrackMetadataEntity.SEPARATOR], most general first. */
    val genres: String? = null,
    val releaseYear: Int? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val bpm: Double? = null,
    /** Pitch class 0..11 (0 = C). */
    val musicalKey: Int? = null,
    /** 1 = major, 0 = minor. */
    val musicalMode: Int? = null,
    val energy: Double? = null,
    val valence: Double? = null,
    val danceability: Double? = null,
    val acousticness: Double? = null,
    val instrumentalness: Double? = null,
    val liveness: Double? = null,
    val speechiness: Double? = null,
    val loudnessDb: Double? = null,
    /** Listener tags joined with [TrackMetadataEntity.SEPARATOR]. */
    val tags: String? = null,
    /** Which source gave which group, e.g. "genre=itunes;features=reccobeats". */
    val sources: String = "",
    val fetchedAt: Long
) {
    fun genreList(): List<String> = split(genres)
    fun tagList(): List<String> = split(tags)

    companion object {
        const val SEPARATOR = "|"
        private fun split(value: String?) = value.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        fun join(values: List<String>): String? =
            values.map { it.replace(SEPARATOR, "/").trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
                .takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
    }
}

enum class TrackMetadataStatus {
    /** At least one field comes from a confirmed match. */
    FOUND,
    /** Sources answered, the song is not in them. */
    NOT_FOUND,
    /** Several different recordings fit and nothing tells them apart; no field was taken. */
    AMBIGUOUS
}

@Dao
interface TrackMetadataDao {
    @Query("SELECT * FROM track_metadata WHERE trackKey = :trackKey")
    suspend fun get(trackKey: String): TrackMetadataEntity?

    @Query("SELECT * FROM track_metadata WHERE trackKey = :trackKey")
    fun observe(trackKey: String): Flow<TrackMetadataEntity?>

    @Query("SELECT * FROM track_metadata WHERE status = 'FOUND'")
    suspend fun getAllFound(): List<TrackMetadataEntity>

    @Query("SELECT * FROM track_metadata WHERE status = 'FOUND'")
    fun observeAllFound(): Flow<List<TrackMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: TrackMetadataEntity)

    @Query("DELETE FROM track_metadata WHERE trackKey IN (:trackKeys)")
    suspend fun deleteByKeys(trackKeys: List<String>): Int

    @Query("DELETE FROM track_metadata")
    suspend fun clear(): Int
}

/** Room v16 changes shared by the Android and Desktop migrations. */
object Schema16 {
    const val CREATE_TRACK_METADATA = "CREATE TABLE IF NOT EXISTS `track_metadata` (" +
        "`trackKey` TEXT NOT NULL, `status` TEXT NOT NULL, `genres` TEXT, `releaseYear` INTEGER, `durationMs` INTEGER, " +
        "`isrc` TEXT, `bpm` REAL, `musicalKey` INTEGER, `musicalMode` INTEGER, `energy` REAL, `valence` REAL, " +
        "`danceability` REAL, `acousticness` REAL, `instrumentalness` REAL, `liveness` REAL, `speechiness` REAL, " +
        "`loudnessDb` REAL, `tags` TEXT, `sources` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`trackKey`))"
}
