package com.lirix.app.connect.session

import com.lirix.app.connect.proto.DeviceRole
import com.lirix.app.connect.proto.Envelope
import com.lirix.app.connect.proto.ErrorCode
import com.lirix.app.connect.proto.HandoffFailure
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.proto.SessionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Исключение при получении команды с устаревшим номером поколения (epoch).
 */
class StaleEpochException(
    val actionEpoch: Long,
    val currentEpoch: Long,
    message: String = "Stale epoch $actionEpoch is less than current epoch $currentEpoch"
) : IllegalArgumentException(message) {
    val errorCode: ErrorCode = ErrorCode.STALE_EPOCH
}

/**
 * Менеджер сессии Lirix Connect (SPEC-CONNECT-01 §4.3, §4.4, TASK-CON-PROTO-03).
 *
 * Управляет:
 * - ролью устройства (`PLAYER` — источник истины, `CONTROLLER` — пульт ДУ);
 * - арбитражем поколений `epoch` (отклонение устаревших команд с `ErrorCode.STALE_EPOCH`);
 * - монотонной генерацией конвертов `Envelope` (`msgId`, `sentAtMonoMs`).
 */
class ConnectSessionManager(
    initialRole: DeviceRole = DeviceRole.CONTROLLER,
    initialEpoch: Long = 0L,
    initialSessionId: String? = null
) {
    private val timeMark = TimeSource.Monotonic.markNow()
    private var msgIdCounter: Long = 1L

    private val _roleState = MutableStateFlow(initialRole)
    val roleState: StateFlow<DeviceRole> = _roleState.asStateFlow()

    val currentRole: DeviceRole get() = _roleState.value

    var sessionId: String? = initialSessionId
        private set

    var epoch: Long = initialEpoch
        private set

    var activeSessionSnapshot: SessionSnapshot? = null
        private set

    fun currentMonoMs(): Long = timeMark.elapsedNow().inWholeMilliseconds

    fun nextMsgId(): Long = msgIdCounter++

    /**
     * Создаёт конверт для отправки с монотонным msgId, текущим sessionId, epoch и временем.
     */
    fun wrapEnvelope(body: LirixConnectMsg, inReplyTo: Long? = null): Envelope {
        return Envelope(
            msgId = nextMsgId(),
            inReplyTo = inReplyTo,
            sessionId = sessionId,
            epoch = epoch,
            sentAtMonoMs = currentMonoMs(),
            body = body
        )
    }

    /**
     * Запуск новой сессии воспроизведения.
     */
    fun startSession(
        initialRole: DeviceRole = DeviceRole.PLAYER,
        id: String? = null
    ): String {
        val newSessionId = id ?: generateSessionUuid()
        sessionId = newSessionId
        epoch++
        _roleState.value = initialRole
        return newSessionId
    }

    fun setRole(role: DeviceRole) {
        _roleState.value = role
    }

    fun bumpEpoch(): Long {
        epoch++
        return epoch
    }

    /**
     * Валидация и применение входящей команды RemoteMediaAction.
     *
     * @return null если команда принята, либо LirixConnectMsg.Error(STALE_EPOCH) если epoch устарел.
     */
    fun validateRemoteAction(action: LirixConnectMsg.RemoteMediaAction): LirixConnectMsg.Error? {
        if (action.epoch < epoch) {
            return LirixConnectMsg.Error(
                code = ErrorCode.STALE_EPOCH,
                message = "Stale epoch ${action.epoch} < current $epoch"
            )
        }
        if (action.epoch > epoch) {
            epoch = action.epoch
        }
        return null
    }

    /**
     * Обработка RemoteMediaAction с возвратом Result.
     */
    fun handleRemoteAction(action: LirixConnectMsg.RemoteMediaAction): Result<Unit> {
        val error = validateRemoteAction(action)
        return if (error != null) {
            Result.failure(StaleEpochException(action.epoch, epoch, error.message ?: "Stale epoch"))
        } else {
            Result.success(Unit)
        }
    }

    /**
     * Валидация и применение входящего обновления состояния RemoteStateUpdate.
     */
    fun handleStateUpdate(update: LirixConnectMsg.RemoteStateUpdate): Result<Unit> {
        if (update.epoch < epoch) {
            return Result.failure(
                StaleEpochException(update.epoch, epoch, "Stale state update epoch ${update.epoch} < $epoch")
            )
        }
        if (update.epoch > epoch) {
            epoch = update.epoch
        }
        return Result.success(Unit)
    }

    /**
     * Обработка входящего запроса HandoffRequest.
     * При принятии устройство становится PLAYER с новым sessionId и newEpoch.
     */
    fun handleHandoffRequest(
        request: LirixConnectMsg.HandoffRequest,
        canAccept: Boolean = true
    ): LirixConnectMsg.HandoffAck {
        if (!canAccept) {
            return LirixConnectMsg.HandoffAck(
                accepted = false,
                reason = HandoffFailure.BUSY,
                suggestCast = true
            )
        }

        if (request.newEpoch < epoch) {
            return LirixConnectMsg.HandoffAck(
                accepted = false,
                reason = HandoffFailure.BUSY,
                suggestCast = false
            )
        }

        sessionId = request.sessionId
        epoch = request.newEpoch
        _roleState.value = DeviceRole.PLAYER

        activeSessionSnapshot = SessionSnapshot(
            sessionId = request.sessionId,
            epoch = request.newEpoch,
            track = request.track,
            isPlaying = request.isPlaying,
            positionMs = request.positionMs
        )

        return LirixConnectMsg.HandoffAck(
            accepted = true,
            startedAtMonoMs = currentMonoMs()
        )
    }

    /**
     * Обработка ответа на наш HandoffRequest.
     * Если цель приняла трек, мы переходим в режим CONTROLLER (пульт ДУ).
     */
    fun handleHandoffAck(ack: LirixConnectMsg.HandoffAck) {
        if (ack.accepted) {
            _roleState.value = DeviceRole.CONTROLLER
        }
    }

    /**
     * Возврат сессии обратно (HandoffReturn).
     */
    fun handleHandoffReturn(returnMsg: LirixConnectMsg.HandoffReturn) {
        sessionId = returnMsg.sessionId
        epoch = returnMsg.newEpoch
        _roleState.value = DeviceRole.PLAYER
    }

    /**
     * Сброс активной сессии.
     */
    fun resetSession() {
        sessionId = null
        epoch = 0L
        activeSessionSnapshot = null
        _roleState.value = DeviceRole.CONTROLLER
    }

    private fun generateSessionUuid(): String {
        val randomBytes = ByteArray(16)
        Random.nextBytes(randomBytes)
        // UUID v4 format
        randomBytes[6] = ((randomBytes[6].toInt() and 0x0f) or 0x40).toByte()
        randomBytes[8] = ((randomBytes[8].toInt() and 0x3f) or 0x80).toByte()

        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(36)
        for (i in 0..15) {
            val octet = randomBytes[i].toInt() and 0xFF
            sb.append(hexChars[octet ushr 4])
            sb.append(hexChars[octet and 0x0F])
            if (i == 3 || i == 5 || i == 7 || i == 9) sb.append('-')
        }
        return sb.toString()
    }
}
