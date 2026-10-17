package com.lirix.app.storage

import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class LibrarySnapshot(
    val tracks: List<TrackEntity> = emptyList(), val sessions: List<ListeningSessionEntity> = emptyList(),
    val lyrics: List<LyricsCacheEntity> = emptyList(), val achievements: List<AchievementEntity> = emptyList(),
    val blacklist: List<BlacklistRuleEntity> = emptyList(), val sourceRules: List<CustomLyricsRuleEntity> = emptyList(),
    val rejections: List<LyricsRejectionEntity> = emptyList()
)
@Serializable
data class LibraryArchive(val version: Int = 1, val origin: String, val exportedAt: Long,
                          val data: LibrarySnapshot, val checksum: String)
data class LibraryMergePlan(val data: LibrarySnapshot, val newTracks: Int, val newSessions: Int,
                            val lyricConflicts: Int, val updatedTracks: Int)

object LibraryArchives {
    const val MAX_BYTES = 64 * 1024 * 1024
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private fun checksum(snapshot: LibrarySnapshot) = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun encode(snapshot: LibrarySnapshot, origin: String): String {
        validate(snapshot)
        return json.encodeToString(LibraryArchive(origin = origin, exportedAt = System.currentTimeMillis(), data = snapshot, checksum = checksum(snapshot)))
    }
    fun decode(text: String): LibraryArchive {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Архив больше 64 МБ" }
        val archive = json.decodeFromString<LibraryArchive>(text)
        require(archive.version == 1) { "Неподдерживаемая версия архива" }
        validate(archive.data)
        require(archive.checksum.equals(checksum(archive.data), true)) { "Контрольная сумма архива не совпадает" }
        return archive
    }
    private fun validate(data: LibrarySnapshot) {
        require(data.tracks.size <= 100_000 && data.sessions.size <= 500_000 && data.lyrics.size <= 100_000) { "Слишком много записей в архиве" }
        require(data.tracks.all { it.trackKey.isNotBlank() && it.trackKey.length <= 256 && it.title.isNotBlank() && it.playCount >= 0 && it.totalDurationMs >= 0 }) { "Некорректные треки в архиве" }
        require(data.sessions.all { it.trackKey.isNotBlank() && it.durationMs >= 0 && it.endTimeMs >= it.startTimeMs }) { "Некорректные сессии в архиве" }
        require(data.sourceRules.all { it.ruleJson.length <= 500_000 }) { "Слишком большое правило источника" }
    }
    private const val NOTES_SEPARATOR = "\n\n── С другого устройства ──\n"
    fun mergeNotes(local: String, incoming: String): String = (local.split(NOTES_SEPARATOR) + incoming.split(NOTES_SEPARATOR))
        .filter { it.isNotBlank() }.distinct().joinToString(NOTES_SEPARATOR)
    private fun identity(session: ListeningSessionEntity) = Triple(session.trackKey, session.sourcePackage, session.startTimeMs)

    fun plan(local: LibrarySnapshot, incoming: LibrarySnapshot, preferIncomingLyrics: Boolean = false): LibraryMergePlan {
        validate(local); validate(incoming)
        val sessions = local.sessions.associateBy(::identity).toMutableMap()
        var newSessions = 0
        for (session in incoming.sessions) {
            val id = identity(session)
            val old = sessions[id]
            if (old == null) { sessions[id] = session.copy(id = 0, isCompleted = true); newSessions++ }
            else sessions[id] = old.copy(endTimeMs = maxOf(old.endTimeMs, session.endTimeMs), durationMs = maxOf(old.durationMs, session.durationMs), isCompleted = old.isCompleted || session.isCompleted)
        }
        val sessionList = sessions.values.toList()
        val perTrack = sessionList.groupBy { it.trackKey }
        val tracks = local.tracks.associateBy { it.trackKey }.toMutableMap()
        var newTracks = 0; var updated = 0
        for (track in incoming.tracks) {
            val old = tracks[track.trackKey]
            val merged = if (old == null) { newTracks++; track } else old.copy(
                isFavorite = old.isFavorite || track.isFavorite, userNotes = mergeNotes(old.userNotes, track.userNotes),
                playCount = maxOf(old.playCount, track.playCount), totalDurationMs = maxOf(old.totalDurationMs, track.totalDurationMs),
                firstPlayedAt = minOf(old.firstPlayedAt, track.firstPlayedAt), lastPlayedAt = maxOf(old.lastPlayedAt, track.lastPlayedAt),
                plainLyrics = if (preferIncomingLyrics) track.plainLyrics ?: old.plainLyrics else old.plainLyrics ?: track.plainLyrics,
                syncedLyrics = if (preferIncomingLyrics) track.syncedLyrics ?: old.syncedLyrics else old.syncedLyrics ?: track.syncedLyrics,
                albumArtUri = old.albumArtUri ?: track.albumArtUri)
            tracks[track.trackKey] = merged.copy(
                playCount = maxOf(merged.playCount, perTrack[track.trackKey]?.size ?: 0),
                totalDurationMs = maxOf(merged.totalDurationMs, perTrack[track.trackKey]?.sumOf { it.durationMs } ?: 0))
            if (old != null && tracks[track.trackKey] != old) updated++
        }
        val lyrics = local.lyrics.associateBy { it.trackKey }.toMutableMap()
        var conflicts = 0
        for (incomingLyrics in incoming.lyrics) {
            val old = lyrics[incomingLyrics.trackKey]
            if (old == null) { lyrics[incomingLyrics.trackKey] = incomingLyrics; continue }
            val conflict = old.provider == "USER" && incomingLyrics.provider == "USER" &&
                (old.plainLyrics != incomingLyrics.plainLyrics || old.syncedLyricsLrc != incomingLyrics.syncedLyricsLrc || old.chordsAmDm != incomingLyrics.chordsAmDm)
            if (conflict) conflicts++
            val chooseIncoming = if (conflict) preferIncomingLyrics else when {
                old.provider == "USER" -> false
                incomingLyrics.provider == "USER" -> true
                else -> incomingLyrics.updatedAt > old.updatedAt
            }
            val preferred = if (chooseIncoming) incomingLyrics else old
            val secondary = if (chooseIncoming) old else incomingLyrics
            lyrics[incomingLyrics.trackKey] = preferred.copy(plainLyrics = preferred.plainLyrics ?: secondary.plainLyrics,
                syncedLyricsLrc = preferred.syncedLyricsLrc ?: secondary.syncedLyricsLrc,
                chordsAmDm = preferred.chordsAmDm ?: secondary.chordsAmDm, userNotes = mergeNotes(old.userNotes.orEmpty(), incomingLyrics.userNotes.orEmpty()))
        }
        // Cached USER edits and the library always expose the same selected version.
        for (cache in lyrics.values) tracks[cache.trackKey]?.let { track ->
            tracks[cache.trackKey] = track.copy(plainLyrics = cache.plainLyrics ?: track.plainLyrics, syncedLyrics = cache.syncedLyricsLrc ?: track.syncedLyrics)
        }
        val achievements = local.achievements.associateBy { it.id }.toMutableMap()
        incoming.achievements.forEach { a ->
            val old = achievements[a.id]
            achievements[a.id] = if (old == null) a else old.copy(isUnlocked = old.isUnlocked || a.isUnlocked,
                unlockedAt = listOfNotNull(old.unlockedAt, a.unlockedAt).minOrNull(), currentProgress = maxOf(old.currentProgress, a.currentProgress))
        }
        fun <T> keepLocal(a: List<T>, b: List<T>, key: (T) -> String) = (b + a).associateBy(key).values.toList()
        val rejections = (local.rejections + incoming.rejections).groupBy { it.trackKey to it.sourceId }.values.map { group -> group.maxBy { it.rejectedAt } }
        return LibraryMergePlan(LibrarySnapshot(tracks.values.toList(), sessionList, lyrics.values.toList(), achievements.values.toList(),
            keepLocal(local.blacklist, incoming.blacklist) { it.id }, keepLocal(local.sourceRules, incoming.sourceRules) { it.id }, rejections), newTracks, newSessions, conflicts, updated)
    }

    suspend fun snapshot(database: AppDatabase): LibrarySnapshot = withContext(Dispatchers.IO) {
        database.libraryTransaction { readSnapshot(database) }
    }
    private suspend fun readSnapshot(db: AppDatabase) = LibrarySnapshot(db.musicDao().getAllTracks(), db.musicDao().getAllSessions(),
        db.lyricsDao().getAllLyrics(), db.achievementDao().getAllAchievements(), db.blacklistDao().getAllRules(),
        db.lyricsDao().getAllRules(), db.lyricsDao().getAllRejections())

    suspend fun merge(database: AppDatabase, incoming: LibrarySnapshot, preferIncomingLyrics: Boolean = false): LibraryMergePlan = withContext(Dispatchers.IO) {
        database.libraryTransaction {
            val plan = plan(readSnapshot(database), incoming, preferIncomingLyrics)
            val music = database.musicDao(); val lyrics = database.lyricsDao()
            plan.data.tracks.forEach { music.insertOrUpdateTrack(it) }
            plan.data.sessions.forEach { if (it.id == 0L) music.insertSession(it) else music.updateSession(it) }
            plan.data.lyrics.forEach { lyrics.saveLyrics(it) }
            plan.data.achievements.forEach { database.achievementDao().insertOrUpdate(it) }
            plan.data.blacklist.forEach { database.blacklistDao().insertRule(it) }
            plan.data.sourceRules.forEach { lyrics.saveRule(it) }
            plan.data.rejections.forEach { lyrics.insertRejection(it) }
            plan
        }
    }
}
