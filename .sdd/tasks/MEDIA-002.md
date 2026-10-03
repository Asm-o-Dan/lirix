## Задача MEDIA-002: Модели данных и DTO для :ingest:media

**Файлы:**
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/MediaSessionContract.kt`
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/model/MediaMetadataSnapshot.kt`
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/model/MediaPlaybackSnapshot.kt`
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/model/ActiveMediaSession.kt`
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/model/MediaSessionPayload.kt`
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/model/MediaSessionEndReason.kt`
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/model/SessionBoundaryDecision.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-media/overview.md#модели-данных-и-dto` (v1)

**Зависит от:** `MEDIA-001`

**Поведение:**
1. `MediaMetadataSnapshot`:
   - `val title: String?, val artist: String?, val album: String?, val durationMs: Long`
   - Инварианты: `durationMs >= -1L`, непустые строки или null.
2. `MediaPlaybackSnapshot`:
   - `val state: Int, val positionMs: Long, val speed: Float, val updateTimeMs: Long`
   - Поле `val isPlaying: Boolean get() = state == PlaybackStateCompat.STATE_PLAYING`
3. `ActiveMediaSession`:
   - `val sessionId: String, val packageName: String, val startedAt: Instant, val lastActiveAt: Instant, val metadata: MediaMetadataSnapshot, val playback: MediaPlaybackSnapshot, val accumulatedPlayDurationMs: Long`
4. `MediaSessionEndReason`:
   - `enum class MediaSessionEndReason { STOPPED, PAUSED_TIMEOUT, TRACK_CHANGED, SESSION_DESTROYED, APP_RESTART, HEARTBEAT_TIMEOUT }`
5. `MediaSessionPayload`:
   - Модель полезной нагрузки сессии для сериализации в `raw_event.payload_json`.
6. `SessionBoundaryDecision`:
   - `sealed interface SessionBoundaryDecision` (NoOp, StartSession, UpdateSession, CloseSession).
7. `MediaSessionContract`:
   - Межзонный контракт передачи данных завершенной/зафиксированной сессии.

**Критерий приёмки:**
- Все модели неизменяемы (`val`), содержат строгие инварианты (`require`), компилируются без ошибок.
