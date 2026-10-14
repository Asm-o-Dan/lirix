package com.lirix.app.feature.blacklist

import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PurgeReport(
    val deletedTracksCount: Int,
    val deletedSessionsCount: Int
)

class BlacklistPurgeEngine(private val db: AppDatabase) {

    /**
     * Возвращает список треков (до limit), попадающих под указанное правило.
     */
    suspend fun findMatchingTracks(rule: BlacklistRuleEntity, limit: Int = 20): List<TrackEntity> = withContext(Dispatchers.IO) {
        val allTracks = db.musicDao().getAllTracks()
        allTracks.filter { track ->
            BlacklistMatcher.matches(
                title = track.title,
                artist = track.artist,
                packageName = track.sourcePackage,
                rule = rule
            )
        }.take(limit)
    }

    /**
     * Возвращает список ключей треков, попадающих под указанное правило.
     */
    suspend fun findMatchingTrackKeys(rule: BlacklistRuleEntity): List<String> = withContext(Dispatchers.IO) {
        val allTracks = db.musicDao().getAllTracks()
        allTracks.filter { track ->
            BlacklistMatcher.matches(
                title = track.title,
                artist = track.artist,
                packageName = track.sourcePackage,
                rule = rule
            )
        }.map { it.trackKey }
    }

    /**
     * Предварительный подсчет количества удаляемых треков (для превью в UI конструкторе).
     */
    suspend fun previewPurgeCount(rule: BlacklistRuleEntity): Int = withContext(Dispatchers.IO) {
        findMatchingTrackKeys(rule).size
    }

    /**
     * Атомарная каскадная очистка медиатеки от треков и сессий, подпадающих под правило.
     */
    suspend fun purgeMatchingTracks(rule: BlacklistRuleEntity): PurgeReport = withContext(Dispatchers.IO) {
        val matchingKeys = findMatchingTrackKeys(rule)
        if (matchingKeys.isEmpty()) {
            return@withContext PurgeReport(0, 0)
        }

        var deletedTracks = 0
        var deletedSessions = 0

        // Удаление порциями по 500 элементов во избежание переполнения аргументов SQLite
        matchingKeys.chunked(500).forEach { chunk ->
            deletedSessions += db.musicDao().deleteSessionsByTrackKeys(chunk)
            deletedTracks += db.musicDao().deleteTracksByKeys(chunk)
        }

        PurgeReport(
            deletedTracksCount = deletedTracks,
            deletedSessionsCount = deletedSessions
        )
    }
}
