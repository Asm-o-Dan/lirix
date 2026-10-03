package com.example.npc.ingest.media.mapper

import com.example.npc.core.model.Event

import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.ingest.media.model.ActiveMediaSession
import com.example.npc.ingest.media.model.MediaMetadataSnapshot
import com.example.npc.ingest.media.model.MediaPlaybackSnapshot
import com.example.npc.ingest.media.model.MediaSessionEndReason
import com.example.npc.ingest.media.model.MediaSessionPayload
import java.time.Instant

/**
 * Pure-function mapper object for :ingest:media.
 *
 * Responsibilities:
 *  1. Canonical deterministic serialization of [MediaSessionPayload] to JSON (toPayloadJson).
 *  2. Deserialization from JSON back to [MediaSessionPayload] (fromPayloadJson).
 *  3. Extraction of [MediaMetadataSnapshot] from MediaMetadataCompat (extractMetadata).
 *  4. Extraction of [MediaPlaybackSnapshot] from PlaybackStateCompat (extractPlaybackState).
 *
 * All functions are stateless and free of side effects.
 */
object MediaPayloadMapper {

    /**
     * Canonical deterministic serialization of a completed session payload to compact JSON
     * (RFC 8259 compliant, no whitespace between separators).
     *
     * Field order is fixed — this is important for deterministic DeduplicationKey generation.
     */
    fun toPayloadJson(payload: MediaSessionPayload): String = buildString {
        append("{")
        append("\"packageName\":\"").append(escapeJson(payload.packageName)).append("\",")
        if (payload.trackTitle != null) {
            append("\"trackTitle\":\"").append(escapeJson(payload.trackTitle)).append("\",")
        } else {
            append("\"trackTitle\":null,")
        }
        if (payload.artist != null) {
            append("\"artist\":\"").append(escapeJson(payload.artist)).append("\",")
        } else {
            append("\"artist\":null,")
        }
        if (payload.album != null) {
            append("\"album\":\"").append(escapeJson(payload.album)).append("\",")
        } else {
            append("\"album\":null,")
        }
        append("\"trackDurationMs\":").append(payload.trackDurationMs).append(",")
        append("\"sessionStartedAtEpochMs\":").append(payload.sessionStartedAtEpochMs).append(",")
        append("\"sessionEndedAtEpochMs\":").append(payload.sessionEndedAtEpochMs).append(",")
        append("\"effectiveDurationMs\":").append(payload.effectiveDurationMs).append(",")
        append("\"isMicroSession\":").append(payload.isMicroSession).append(",")
        append("\"endReason\":\"").append(payload.endReason.name).append("\",")
        if (payload.lastError != null) {
            append("\"lastError\":\"").append(escapeJson(payload.lastError)).append("\"")
        } else {
            append("\"lastError\":null")
        }
        append("}")
    }

    /**
     * Deserializes a JSON string produced by [toPayloadJson] back to [MediaSessionPayload].
     *
     * Uses a simple hand-rolled parser to avoid external JSON dependencies.
     * Throws [IllegalArgumentException] if required fields are missing or malformed.
     */
    fun fromPayloadJson(json: String): MediaSessionPayload {
        val map = parseSimpleJsonObject(json)

        val packageName = map["packageName"] ?: error("Missing packageName")
        val trackTitle = map["trackTitle"]
        val artist = map["artist"]
        val album = map["album"]
        val trackDurationMs = map["trackDurationMs"]?.toLongOrNull() ?: 0L
        val sessionStartedAtEpochMs = map["sessionStartedAtEpochMs"]?.toLongOrNull()
            ?: error("Missing sessionStartedAtEpochMs")
        val sessionEndedAtEpochMs = map["sessionEndedAtEpochMs"]?.toLongOrNull()
            ?: error("Missing sessionEndedAtEpochMs")
        val effectiveDurationMs = map["effectiveDurationMs"]?.toLongOrNull()
            ?: error("Missing effectiveDurationMs")
        val isMicroSession = map["isMicroSession"]?.toBooleanStrictOrNull()
            ?: error("Missing isMicroSession")
        val endReasonStr = map["endReason"] ?: error("Missing endReason")
        val endReason = MediaSessionEndReason.valueOf(endReasonStr)
        val lastError = map["lastError"]

        return MediaSessionPayload(
            packageName = packageName,
            trackTitle = trackTitle,
            artist = artist,
            album = album,
            trackDurationMs = trackDurationMs,
            sessionStartedAtEpochMs = sessionStartedAtEpochMs,
            sessionEndedAtEpochMs = sessionEndedAtEpochMs,
            effectiveDurationMs = effectiveDurationMs,
            isMicroSession = isMicroSession,
            endReason = endReason,
            lastError = lastError
        )
    }

    /**
     * Extracts [MediaMetadataSnapshot] from a raw [android.support.v4.media.MediaMetadataCompat]-like
     * long/string map (using reflection-safe constants).
     *
     * Accepts nullable MediaMetadataCompat (returns EMPTY snapshot when null).
     */
    fun extractMetadata(titleStr: String?, artistStr: String?, albumStr: String?, durationMs: Long): MediaMetadataSnapshot {
        val cleanTitle = titleStr?.takeIf { it.isNotBlank() }
        val cleanArtist = artistStr?.takeIf { it.isNotBlank() }
        val cleanAlbum = albumStr?.takeIf { it.isNotBlank() }
        val validDuration = if (durationMs >= -1L) durationMs else MediaMetadataSnapshot.DURATION_UNKNOWN
        return MediaMetadataSnapshot(
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanAlbum,
            durationMs = validDuration
        )
    }

    /**
     * Extracts [MediaPlaybackSnapshot] from raw PlaybackStateCompat values.
     *
     * Returns EMPTY snapshot when state data is unavailable.
     */
    fun extractPlaybackState(state: Int, positionMs: Long, playbackSpeed: Float, updateTimeEpochMs: Long): MediaPlaybackSnapshot {
        val validPosition = maxOf(0L, positionMs)
        val validSpeed = maxOf(0.0f, playbackSpeed)
        val validUpdateTime = maxOf(0L, updateTimeEpochMs)
        return MediaPlaybackSnapshot(
            state = state,
            positionMs = validPosition,
            playbackSpeed = validSpeed,
            updateTimeEpochMs = validUpdateTime
        )
    }

    /**
     * Converts an [ActiveMediaSession] + close metadata to a [RawEvent] for storage.
     *
     * Computes [effectiveDurationMs] based on [endedAt] and whether the player was
     * actively playing at close time.
     */
    fun toRawEvent(
        session: ActiveMediaSession,
        seq: Long,
        endReason: MediaSessionEndReason,
        endedAt: Instant
    ): RawEvent {
        val activeSegmentMs = if (session.lastActivePlayStartedAt != null) {
            maxOf(0L, endedAt.toEpochMilli() - session.lastActivePlayStartedAt.toEpochMilli())
        } else 0L
        val effectiveDurationMs = session.accumulatedPlayTimeMs + activeSegmentMs

        val payload = MediaSessionPayload(
            packageName = session.packageName,
            trackTitle = session.metadata.title,
            artist = session.metadata.artist,
            album = session.metadata.album,
            trackDurationMs = session.metadata.durationMs,
            sessionStartedAtEpochMs = session.sessionStartedAt.toEpochMilli(),
            sessionEndedAtEpochMs = endedAt.toEpochMilli(),
            effectiveDurationMs = effectiveDurationMs,
            isMicroSession = effectiveDurationMs < MediaSessionPayload.MICRO_SESSION_THRESHOLD_MS,
            endReason = endReason,
            lastError = null
        )

        val payloadJson = toPayloadJson(payload)
        val dedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.MEDIA,
            session.packageName,
            payloadJson
        )

        return RawEvent(
            id = 0L,
            seq = seq,
            source = SourceId.MEDIA,
            packageName = session.packageName,
            receivedAt = endedAt,
            payloadJson = payloadJson,
            hash = dedupKey
        )
    }

    /**
     * Converts a [RawEvent] + [ActiveMediaSession] to a domain [Event].
     *
     * Returns null if [effectiveDurationMs] < 5000ms (micro-session / skip — filtered from Timeline).
     */
    fun toDomainEvent(
        rawEvent: RawEvent,
        session: ActiveMediaSession,
        endedAt: Instant
    ): Event? {
        val activeSegmentMs = if (session.lastActivePlayStartedAt != null) {
            maxOf(0L, endedAt.toEpochMilli() - session.lastActivePlayStartedAt.toEpochMilli())
        } else 0L
        val effectiveDurationMs = session.accumulatedPlayTimeMs + activeSegmentMs

        if (effectiveDurationMs < MediaSessionPayload.MICRO_SESSION_THRESHOLD_MS) {
            return null
        }

        val title = session.metadata.title ?: "Unknown Track"
        val text = if (session.metadata.artist != null) "${session.metadata.artist} - $title" else title

        return EventNormalizer.normalize(
            rawEvent = rawEvent,
            title = title,
            text = text,
            threadKey = ThreadKey("${session.packageName}:media:${session.sessionId}"),
            isUpdateOf = null
        )
    }

    // --- Private helpers ---

    private fun escapeJson(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code in 0..0x1F) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * Minimal JSON object parser for flat key-value structure (no nesting).
     * Returns map of String -> String? (null for JSON null values).
     */
    private fun parseSimpleJsonObject(json: String): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        val trimmed = json.trim().removePrefix("{").removeSuffix("}")

        // Simple tokenizer: split by "," then parse "key":value pairs
        var pos = 0
        while (pos < trimmed.length) {
            // Skip whitespace
            while (pos < trimmed.length && trimmed[pos] == ' ') pos++
            if (pos >= trimmed.length) break

            // Parse key
            if (trimmed[pos] != '"') break
            pos++ // skip opening quote
            val keyStart = pos
            while (pos < trimmed.length && trimmed[pos] != '"') {
                if (trimmed[pos] == '\\') pos++ // skip escaped char
                pos++
            }
            val key = trimmed.substring(keyStart, pos)
            pos++ // skip closing quote

            // Skip ":"
            while (pos < trimmed.length && (trimmed[pos] == ' ' || trimmed[pos] == ':')) pos++

            // Parse value
            if (pos >= trimmed.length) break
            val value: String?
            if (trimmed.startsWith("null", pos)) {
                value = null
                pos += 4
            } else if (trimmed.startsWith("true", pos)) {
                value = "true"
                pos += 4
            } else if (trimmed.startsWith("false", pos)) {
                value = "false"
                pos += 5
            } else if (trimmed[pos] == '"') {
                pos++ // skip opening quote
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
                    } else {
                        sb.append(trimmed[pos++])
                    }
                }
                value = sb.toString()
                pos++ // skip closing quote
            } else {
                // Number
                val numStart = pos
                while (pos < trimmed.length && trimmed[pos] != ',' && trimmed[pos] != '}') pos++
                value = trimmed.substring(numStart, pos).trim()
            }
            result[key] = value

            // Skip comma
            while (pos < trimmed.length && (trimmed[pos] == ',' || trimmed[pos] == ' ')) pos++
        }

        return result
    }
}
