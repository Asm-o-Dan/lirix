package com.example.npc.ingest.media.recovery

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.media.mapper.MediaPayloadMapper
import com.example.npc.ingest.media.model.ActiveMediaSession
import com.example.npc.ingest.media.model.MediaSessionEndReason
import com.example.npc.ingest.media.model.MediaSessionPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

private const val TAG = "MediaSessionRecoveryManager"
private const val ACTIVE_SESSION_KEY_PREFIX = "active_session_"

/**
 * Crash recovery manager for :ingest:media.
 *
 * Persists snapshots of [ActiveMediaSession] to [DataStore] on each significant state change,
 * so that if the process is killed unexpectedly (HyperOS LMK, power cycle), sessions are
 * recovered and properly closed on next app startup.
 *
 * @param dataStore Preferences DataStore for persisting session snapshots.
 * @param storageGateway Persistence gateway for writing recovered events.
 * @param ioDispatcher Dispatcher for DataStore + Room I/O (default: Dispatchers.IO).
 */
class MediaSessionRecoveryManager(
    private val dataStore: DataStore<Preferences>,
    private val storageGateway: StorageGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Saves a JSON snapshot of [session] to DataStore under key "active_session_${session.packageName}".
     *
     * Overwrites any previously saved snapshot for the same package.
     * IOException is caught and logged — recovery snapshot is best-effort.
     */
    suspend fun saveActiveSessionSnapshot(session: ActiveMediaSession) = withContext(ioDispatcher) {
        try {
            val key = stringPreferencesKey("$ACTIVE_SESSION_KEY_PREFIX${session.packageName}")
            val json = serializeSnapshot(session)
            dataStore.edit { prefs -> prefs[key] = json }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save session snapshot for ${session.packageName}", e)
        }
    }

    /**
     * Removes the DataStore snapshot for [packageName] after a session is cleanly closed.
     *
     * Safe to call even if no snapshot exists (no-op).
     */
    suspend fun clearActiveSessionSnapshot(packageName: String) = withContext(ioDispatcher) {
        try {
            val key = stringPreferencesKey("$ACTIVE_SESSION_KEY_PREFIX$packageName")
            dataStore.edit { prefs -> prefs.remove(key) }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to clear session snapshot for $packageName", e)
        }
    }

    /**
     * Recovers all dangling sessions (leftover from a previous process crash) on app startup.
     *
     * For each dangling session:
     *  - Constructs a [MediaSessionPayload] with [sessionEndedAt] = [appStartedAt] and [endReason] = APP_RESTART.
     *  - Inserts RawEvent and (if not micro-session) Event into storage.
     *  - Deletes the snapshot from DataStore.
     *
     * @param appStartedAt The instant at which the app was started (used as session close time).
     * @return Count of successfully recovered sessions.
     */
    suspend fun recoverDanglingSessions(appStartedAt: Instant): Int = withContext(ioDispatcher) {
        var recovered = 0
        try {
            val prefs = dataStore.data.first()
            val danglingKeys = prefs.asMap().keys
                .filter { it.name.startsWith(ACTIVE_SESSION_KEY_PREFIX) }

            for (prefKey in danglingKeys) {
                val json = prefs[prefKey as Preferences.Key<String>] ?: continue
                try {
                    val snapshot = deserializeSnapshot(json)
                    val payload = buildRecoveryPayload(snapshot, appStartedAt)

                    // Insert RawEvent
                    val payloadJson = MediaPayloadMapper.toPayloadJson(payload)
                    val dedupKey = EventNormalizer.computeDeduplicationKey(
                        SourceId.MEDIA,
                        snapshot.packageName,
                        payloadJson
                    )
                    val rawEvent = RawEvent(
                        id = 0L,
                        seq = 0L, // recovery events have seq = 0 (no seqGenerator available here)
                        source = SourceId.MEDIA,
                        packageName = snapshot.packageName,
                        receivedAt = appStartedAt,
                        payloadJson = payloadJson,
                        hash = dedupKey
                    )
                    val rawId = storageGateway.insertRawEvent(rawEvent)

                    // Insert domain Event if meaningful duration
                    if (rawId != -1L && !payload.isMicroSession) {
                        val title = payload.trackTitle ?: "Unknown Track"
                        val text = if (payload.artist != null) "${payload.artist} - $title" else title
                        val domainEvent = EventNormalizer.normalize(
                            rawEvent = rawEvent.copy(id = rawId),
                            title = title,
                            text = text,
                            threadKey = ThreadKey("media:${snapshot.packageName}"),
                            isUpdateOf = null
                        )
                        storageGateway.insertEvent(domainEvent)
                    }

                    // Remove dangling snapshot
                    dataStore.edit { p -> p.remove(prefKey as Preferences.Key<String>) }

                    recovered++
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to recover dangling session from key ${prefKey.name} — deleting corrupt snapshot", e)
                    try {
                        dataStore.edit { p -> p.remove(prefKey as Preferences.Key<String>) }
                    } catch (_: Throwable) {}
                }
            }

            // Update SourceHealth to indicate recovery was attempted
            storageGateway.upsertSourceHealth(
                SourceHealth(
                    source = SourceId.MEDIA,
                    lastEventAt = appStartedAt,
                    events24h = recovered,
                    lastError = if (recovered > 0) "recovered $recovered dangling session(s) after APP_RESTART" else null,
                    queueDepth = 0
                )
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Critical error during dangling session recovery", e)
        }
        recovered
    }

    // --- Serialization (minimal JSON — no external dependency) ---

    private fun serializeSnapshot(session: ActiveMediaSession): String = buildString {
        append("{")
        append("\"packageName\":\"${escapeJson(session.packageName)}\",")
        append("\"sessionId\":\"${escapeJson(session.sessionId)}\",")
        append("\"trackTitle\":${if (session.metadata.title != null) "\"${escapeJson(session.metadata.title)}\"" else "null"},")
        append("\"artist\":${if (session.metadata.artist != null) "\"${escapeJson(session.metadata.artist)}\"" else "null"},")
        append("\"album\":${if (session.metadata.album != null) "\"${escapeJson(session.metadata.album)}\"" else "null"},")
        append("\"trackDurationMs\":${session.metadata.durationMs},")
        append("\"sessionStartedAtEpochMs\":${session.sessionStartedAt.toEpochMilli()},")
        append("\"lastActivePlayStartedAtEpochMs\":${session.lastActivePlayStartedAt?.toEpochMilli()?.toString() ?: "null"},")
        append("\"accumulatedPlayTimeMs\":${session.accumulatedPlayTimeMs},")
        append("\"lastState\":${session.lastState},")
        append("\"lastEventAtEpochMs\":${session.lastEventAt.toEpochMilli()}")
        append("}")
    }

    private data class SessionSnapshot(
        val packageName: String,
        val sessionId: String,
        val trackTitle: String?,
        val artist: String?,
        val album: String?,
        val trackDurationMs: Long,
        val sessionStartedAtEpochMs: Long,
        val lastActivePlayStartedAtEpochMs: Long?,
        val accumulatedPlayTimeMs: Long,
        val lastState: Int,
        val lastEventAtEpochMs: Long
    )

    private fun deserializeSnapshot(json: String): SessionSnapshot {
        val map = parseSimpleJsonObject(json)
        return SessionSnapshot(
            packageName = map["packageName"] ?: error("Missing packageName"),
            sessionId = map["sessionId"] ?: error("Missing sessionId"),
            trackTitle = map["trackTitle"],
            artist = map["artist"],
            album = map["album"],
            trackDurationMs = map["trackDurationMs"]?.toLongOrNull() ?: -1L,
            sessionStartedAtEpochMs = map["sessionStartedAtEpochMs"]?.toLongOrNull()
                ?: error("Missing sessionStartedAtEpochMs"),
            lastActivePlayStartedAtEpochMs = map["lastActivePlayStartedAtEpochMs"]?.toLongOrNull(),
            accumulatedPlayTimeMs = map["accumulatedPlayTimeMs"]?.toLongOrNull() ?: 0L,
            lastState = map["lastState"]?.toIntOrNull() ?: 0,
            lastEventAtEpochMs = map["lastEventAtEpochMs"]?.toLongOrNull()
                ?: error("Missing lastEventAtEpochMs")
        )
    }

    private fun buildRecoveryPayload(snapshot: SessionSnapshot, appStartedAt: Instant): MediaSessionPayload {
        val lastKnownActiveMs = snapshot.lastActivePlayStartedAtEpochMs
        val activeSegmentMs = if (lastKnownActiveMs != null) {
            maxOf(0L, snapshot.lastEventAtEpochMs - lastKnownActiveMs)
        } else 0L
        val effectiveDurationMs = snapshot.accumulatedPlayTimeMs + activeSegmentMs

        return MediaSessionPayload(
            packageName = snapshot.packageName,
            trackTitle = snapshot.trackTitle,
            artist = snapshot.artist,
            album = snapshot.album,
            trackDurationMs = snapshot.trackDurationMs,
            sessionStartedAtEpochMs = snapshot.sessionStartedAtEpochMs,
            sessionEndedAtEpochMs = appStartedAt.toEpochMilli(),
            effectiveDurationMs = effectiveDurationMs,
            isMicroSession = effectiveDurationMs < MediaSessionPayload.MICRO_SESSION_THRESHOLD_MS,
            endReason = MediaSessionEndReason.APP_RESTART,
            lastError = "process_killed_unexpectedly"
        )
    }

    private fun escapeJson(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun parseSimpleJsonObject(json: String): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        val trimmed = json.trim().removePrefix("{").removeSuffix("}")
        var pos = 0
        while (pos < trimmed.length) {
            while (pos < trimmed.length && trimmed[pos] == ' ') pos++
            if (pos >= trimmed.length || trimmed[pos] != '"') break
            pos++
            val keyStart = pos
            while (pos < trimmed.length && trimmed[pos] != '"') {
                if (trimmed[pos] == '\\') pos++
                pos++
            }
            val key = trimmed.substring(keyStart, pos)
            pos++ // closing quote
            while (pos < trimmed.length && (trimmed[pos] == ' ' || trimmed[pos] == ':')) pos++
            if (pos >= trimmed.length) break
            val value: String?
            if (trimmed.startsWith("null", pos)) {
                value = null; pos += 4
            } else if (trimmed.startsWith("true", pos)) {
                value = "true"; pos += 4
            } else if (trimmed.startsWith("false", pos)) {
                value = "false"; pos += 5
            } else if (trimmed[pos] == '"') {
                pos++
                val sb = StringBuilder()
                while (pos < trimmed.length && trimmed[pos] != '"') {
                    if (trimmed[pos] == '\\' && pos + 1 < trimmed.length) {
                        when (trimmed[pos + 1]) {
                            'n' -> { sb.append('\n'); pos += 2 }
                            'r' -> { sb.append('\r'); pos += 2 }
                            't' -> { sb.append('\t'); pos += 2 }
                            'b' -> { sb.append('\b'); pos += 2 }
                            'f' -> { sb.append('\u000C'); pos += 2 }
                            '"' -> { sb.append('"'); pos += 2 }
                            '\\' -> { sb.append('\\'); pos += 2 }
                            'u' -> {
                                if (pos + 6 <= trimmed.length) {
                                    val hex = trimmed.substring(pos + 2, pos + 6)
                                    val code = hex.toIntOrNull(16)
                                    if (code != null) {
                                        sb.append(code.toChar())
                                        pos += 6
                                    } else {
                                        sb.append('u')
                                        pos += 2
                                    }
                                } else {
                                    sb.append('u')
                                    pos += 2
                                }
                            }
                            else -> { sb.append(trimmed[pos + 1]); pos += 2 }
                        }
                    } else { sb.append(trimmed[pos++]) }
                }
                value = sb.toString(); pos++
            } else {
                val numStart = pos
                while (pos < trimmed.length && trimmed[pos] != ',' && trimmed[pos] != '}') pos++
                value = trimmed.substring(numStart, pos).trim()
            }
            result[key] = value
            while (pos < trimmed.length && (trimmed[pos] == ',' || trimmed[pos] == ' ')) pos++
        }
        return result
    }
}
