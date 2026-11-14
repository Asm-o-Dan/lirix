package com.lirix.app.feature.blacklist

import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.libraryTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PurgeReport(
    val deletedTracksCount: Int,
    val deletedSessionsCount: Int
)

/** Всё, что удалила очистка: нужно для полной отмены. */
data class PurgeBackup(
    val tracks: List<TrackEntity>,
    val sessions: List<ListeningSessionEntity>
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
    suspend fun purgeMatchingTracks(rule: BlacklistRuleEntity): PurgeReport =
        purgeMatchingTracksWithBackup(rule).first

    /**
     * Каскадная очистка одной транзакцией с копией удалённых треков и их сессий для отмены (Undo).
     * Сбой посреди очистки откатывает её целиком: не остаётся треков без истории и истории без треков.
     */
    suspend fun purgeMatchingTracksWithBackup(rule: BlacklistRuleEntity): Pair<PurgeReport, PurgeBackup> = withContext(Dispatchers.IO) {
        db.libraryTransaction {
            val matching = db.musicDao().getAllTracks().filter { track ->
                BlacklistMatcher.matches(
                    title = track.title,
                    artist = track.artist,
                    packageName = track.sourcePackage,
                    rule = rule
                )
            }
            val matchingKeys = matching.map { it.trackKey }
            if (matchingKeys.isEmpty()) {
                return@libraryTransaction Pair(PurgeReport(0, 0), PurgeBackup(emptyList(), emptyList()))
            }

            val sessions = mutableListOf<ListeningSessionEntity>()
            var deletedTracks = 0
            var deletedSessions = 0

            // Порции по 500 элементов во избежание переполнения аргументов SQLite
            matchingKeys.chunked(500).forEach { chunk ->
                sessions += db.musicDao().getSessionsByTrackKeys(chunk)
                deletedSessions += db.musicDao().deleteSessionsByTrackKeys(chunk)
                deletedTracks += db.musicDao().deleteTracksByKeys(chunk)
            }

            Pair(PurgeReport(deletedTracks, deletedSessions), PurgeBackup(matching, sessions))
        }
    }

    /**
     * Отмена удаления: возвращает треки и их историю прослушиваний одной транзакцией.
     * Трек, который успел появиться заново после очистки, не перезаписывается (в нём уже новые данные).
     */
    suspend fun restore(backup: PurgeBackup) = withContext(Dispatchers.IO) {
        db.libraryTransaction {
            val existing = db.musicDao().getAllTracks().mapTo(HashSet()) { it.trackKey }
            backup.tracks.filter { it.trackKey !in existing }.forEach { db.musicDao().insertOrUpdateTrack(it) }
            backup.sessions.chunked(500).forEach { db.musicDao().restoreSessions(it) }
        }
    }
}
