package com.lirix.app.connect.proto

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import kotlinx.serialization.Serializable

@Serializable
enum class MediaAction {
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    SEEK_TO,
    SET_VOLUME,
    SET_MUTE,
    SET_QUEUE_INDEX
}

@Serializable
enum class RejectReason {
    UNTRUSTED_DEVICE,
    VERSION_MISMATCH,
    BUSY,
    USER_DECLINED,
    PAIRING_EXPIRED,
    TOO_MANY_ATTEMPTS
}

@Serializable
enum class ErrorCode {
    UNSUPPORTED,
    STALE_EPOCH,
    INVALID_STATE,
    INTERNAL
}

@Serializable
enum class ByeReason {
    NORMAL,
    UNPAIRED,
    SHUTDOWN
}

@Serializable
enum class HandoffFailure {
    NO_PROVIDER,
    NO_LOCAL_FILE,
    BUSY,
    USER_DECLINED
}

@Serializable
enum class CastCodec {
    OPUS,
    PCM_S16LE
}

@Serializable
enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

@Serializable
data class Capabilities(
    val canPlayLocal: Boolean,
    val canReceiveCast: Boolean,
    val canCaptureAudio: Boolean,
    val providers: List<String> = emptyList(),
    val maxQueueSize: Int = 100
)

@Serializable
data class TrackRef(
    val provider: String,
    val trackId: String? = null,
    val uri: String? = null,
    val contentHash: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null
)

@Serializable
data class LyricsRef(
    val queryKey: String,
    val lrc: String? = null,
    val syncOffsetMs: Long = 0L
)

@Serializable
data class QueueSnapshot(
    val items: List<TrackRef>,
    val index: Int = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF
)

@Serializable
data class SessionSnapshot(
    val sessionId: String,
    val epoch: Long,
    val track: TrackRef,
    val isPlaying: Boolean,
    val positionMs: Long
)

/**
 * Запечатанная иерархия сообщений протокола Lirix Connect (SPEC-CONNECT-01 §4.4).
 */
@Serializable
sealed interface LirixConnectMsg {

    // ── Рукопожатие ──

    @Serializable
    data class Hello(
        val supportedVersions: List<Int>,
        val deviceName: String,
        val deviceType: DeviceType,
        val capabilities: Capabilities
    ) : LirixConnectMsg

    @Serializable
    data class Welcome(
        val chosenVersion: Int,
        val deviceName: String,
        val capabilities: Capabilities,
        val activeSession: SessionSnapshot? = null
    ) : LirixConnectMsg

    @Serializable
    data class Reject(
        val reason: RejectReason,
        val message: String? = null
    ) : LirixConnectMsg

    @Serializable
    data class Error(
        val code: ErrorCode,
        val message: String? = null
    ) : LirixConnectMsg

    @Serializable
    data class Bye(
        val reason: ByeReason
    ) : LirixConnectMsg

    // ── Сопряжение (Pairing Phase) ──

    @Serializable
    data class PairRequest(
        val nonce: Bytes,
        val proof: Bytes,
        val deviceName: String,
        val deviceType: DeviceType = DeviceType.ANDROID_PHONE
    ) : LirixConnectMsg

    @Serializable
    data class PairResult(
        val ok: Boolean,
        val verifyCode: String? = null
    ) : LirixConnectMsg

    // ── Связность и время ──

    @Serializable
    data class Ping(
        val t0: Long
    ) : LirixConnectMsg

    @Serializable
    data class Pong(
        val t0: Long,
        val t1: Long,
        val t2: Long
    ) : LirixConnectMsg

    @Serializable
    data class DeviceHeartbeat(
        val isAvailable: Boolean,
        val batteryPct: Int? = null,
        val nowPlaying: Boolean
    ) : LirixConnectMsg

    // ── Handoff ──

    @Serializable
    data class HandoffRequest(
        val sessionId: String,
        val newEpoch: Long,
        val track: TrackRef,
        val queue: QueueSnapshot? = null,
        val positionMs: Long,
        val positionAtMonoMs: Long,
        val durationMs: Long,
        val playbackRate: Float = 1.0f,
        val isPlaying: Boolean = true,
        val volume: Float = 1.0f,
        val lyrics: LyricsRef? = null
    ) : LirixConnectMsg

    @Serializable
    data class HandoffAck(
        val accepted: Boolean,
        val reason: HandoffFailure? = null,
        val suggestCast: Boolean = false,
        val startedAtMonoMs: Long? = null
    ) : LirixConnectMsg

    @Serializable
    data class HandoffReturn(
        val sessionId: String,
        val newEpoch: Long
    ) : LirixConnectMsg

    // ── Пульт ДУ ──

    @Serializable
    data class RemoteMediaAction(
        val epoch: Long,
        val action: MediaAction,
        val seekToMs: Long? = null,
        val volume: Float? = null,
        val queueIndex: Int? = null
    ) : LirixConnectMsg

    @Serializable
    data class RemoteStateUpdate(
        val epoch: Long,
        val trackId: String,
        val trackTitle: String,
        val trackArtist: String,
        val durationMs: Long,
        val positionMs: Long,
        val positionAtMonoMs: Long,
        val playbackRate: Float = 1.0f,
        val isPlaying: Boolean,
        val volume: Float = 1.0f,
        val muted: Boolean = false,
        val queueIndex: Int = 0,
        val lyrics: LyricsRef? = null
    ) : LirixConnectMsg

    // ── Audio Cast ──

    @Serializable
    data class SetAudioCast(
        val enabled: Boolean,
        val codec: CastCodec = CastCodec.OPUS,
        val sampleRate: Int = 48000,
        val channels: Int = 2,
        val bitrateKbps: Int = 192
    ) : LirixConnectMsg

    @Serializable
    data class CastStatus(
        val receiverLatencyMs: Int,
        val bufferMs: Int,
        val lossPct: Float
    ) : LirixConnectMsg
}
