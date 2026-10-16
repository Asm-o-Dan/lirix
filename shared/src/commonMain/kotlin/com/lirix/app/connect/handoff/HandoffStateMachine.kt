package com.lirix.app.connect.handoff

import com.lirix.app.connect.proto.DeviceRole
import com.lirix.app.connect.proto.HandoffFailure
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.session.ConnectSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Машина состояний процесса бесшовной передачи воспроизведения Handoff (SPEC-CONNECT-01 §4.6, TASK-CON-HANDOFF-06).
 *
 * Жизненный цикл FSM:
 * ```
 * IDLE ──► REQUESTED ──► (HandoffAck accepted) ──► TARGET_STARTING ──► TARGET_PLAYING ──► SOURCE_MUTED (CONTROLLER)
 *                      ├──► (accepted=false, suggestCast) ──► SUGGEST_CAST
 *                      ├──► (accepted=false, suggestCast=false) ──► FAILED
 *                      └──► Таймаут 5 с ──► IDLE (Звук остаётся на источнике, Timeout error)
 * ```
 *
 * Инварианты:
 * - Плавный переход: локальный звук приглушается (кроссфейд 300 мс) только ПОСЛЕ
 *   получения RemoteStateUpdate(isPlaying=true) от цели.
 * - При таймауте 5 с или отказе локальное воспроизведение не прерывается.
 * - При потере связи в режиме контроллера (SOURCE_MUTED) включается режим RECONNECTING (10 с).
 *   При неудаче переход в FAILED с возможностью [resumeLocally].
 */
class HandoffStateMachine(
    private val scope: CoroutineScope? = null,
    val handoffTimeoutMs: Long = DEFAULT_HANDOFF_TIMEOUT_MS,
    val crossfadeDurationMs: Long = DEFAULT_CROSSFADE_DURATION_MS,
    val reconnectTimeoutMs: Long = DEFAULT_RECONNECT_TIMEOUT_MS,
    private val sessionManager: ConnectSessionManager? = null
) {
    companion object {
        const val DEFAULT_HANDOFF_TIMEOUT_MS = 5000L
        const val DEFAULT_CROSSFADE_DURATION_MS = 300L
        const val DEFAULT_RECONNECT_TIMEOUT_MS = 10_000L
    }

    private val _state = MutableStateFlow(HandoffState.IDLE)
    val state: StateFlow<HandoffState> = _state.asStateFlow()

    private val _isLocalPlayback = MutableStateFlow(true)
    val isLocalPlayback: StateFlow<Boolean> = _isLocalPlayback.asStateFlow()

    private val _lastError = MutableStateFlow<HandoffError?>(null)
    val lastError: StateFlow<HandoffError?> = _lastError.asStateFlow()

    private val _activeRequest = MutableStateFlow<LirixConnectMsg.HandoffRequest?>(null)
    val activeRequest: StateFlow<LirixConnectMsg.HandoffRequest?> = _activeRequest.asStateFlow()

    private var ackTimeoutJob: Job? = null
    private var crossfadeJob: Job? = null
    private var reconnectTimeoutJob: Job? = null

    /**
     * Инициирование передачи трека на целевое устройство.
     */
    fun initiateHandoff(
        request: LirixConnectMsg.HandoffRequest,
        targetEndpointHex: String? = null
    ) {
        cancelAllJobs()
        _lastError.value = null
        _activeRequest.value = request
        _isLocalPlayback.value = true
        _state.value = HandoffState.REQUESTED

        ackTimeoutJob = scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(handoffTimeoutMs)
            onHandoffTimeout()
        }
    }

    /**
     * Срабатывание тайм-аута ожидания ответа цели (5 с).
     * Звук остаётся на источнике, FSM возвращается в IDLE.
     */
    fun onHandoffTimeout() {
        if (_state.value == HandoffState.REQUESTED) {
            ackTimeoutJob?.cancel()
            ackTimeoutJob = null
            _lastError.value = HandoffError.Timeout
            _state.value = HandoffState.IDLE
            _isLocalPlayback.value = true
        }
    }

    /**
     * Обработка ответа на запрос HandoffAck.
     */
    fun onHandoffAck(ack: LirixConnectMsg.HandoffAck): Boolean {
        if (_state.value != HandoffState.REQUESTED) return false

        ackTimeoutJob?.cancel()
        ackTimeoutJob = null

        if (ack.accepted) {
            _state.value = HandoffState.TARGET_STARTING
        } else {
            val failureReason = ack.reason ?: HandoffFailure.BUSY
            _lastError.value = HandoffError.Rejected(failureReason, ack.suggestCast)
            if (ack.suggestCast) {
                _state.value = HandoffState.SUGGEST_CAST
            } else {
                _state.value = HandoffState.FAILED
            }
        }
        return true
    }

    /**
     * Обработка обновления состояния удаленного плеера.
     * При переходе цели в статус isPlaying=true активируется кроссфейд 300 мс.
     */
    fun onTargetStateUpdate(update: LirixConnectMsg.RemoteStateUpdate) {
        if (_state.value == HandoffState.TARGET_STARTING && update.isPlaying) {
            _state.value = HandoffState.TARGET_PLAYING

            crossfadeJob?.cancel()
            crossfadeJob = scope?.launch(start = CoroutineStart.UNDISPATCHED) {
                delay(crossfadeDurationMs)
                onCrossfadeComplete()
            }
        }
    }

    /**
     * Завершение плавного 300 мс кроссфейда.
     * Локальный звук глушится, устройство переходит в режим пульта (SOURCE_MUTED).
     */
    fun onCrossfadeComplete() {
        if (_state.value == HandoffState.TARGET_PLAYING) {
            crossfadeJob?.cancel()
            crossfadeJob = null
            _isLocalPlayback.value = false
            _state.value = HandoffState.SOURCE_MUTED
            sessionManager?.setRole(DeviceRole.CONTROLLER)
        }
    }

    /**
     * Обработка возврата воспроизведения на текущее устройство (HandoffReturn).
     */
    fun onHandoffReturn(returnMsg: LirixConnectMsg.HandoffReturn) {
        if (_state.value in listOf(HandoffState.SOURCE_MUTED, HandoffState.TARGET_PLAYING, HandoffState.TARGET_STARTING)) {
            cancelAllJobs()
            _isLocalPlayback.value = true
            _state.value = HandoffState.IDLE
            sessionManager?.handleHandoffReturn(returnMsg)
        }
    }

    /**
     * Обработка потери связи при активном управлении хостом.
     */
    fun onConnectionLost() {
        if (_state.value == HandoffState.SOURCE_MUTED) {
            _state.value = HandoffState.RECONNECTING

            reconnectTimeoutJob?.cancel()
            reconnectTimeoutJob = scope?.launch(start = CoroutineStart.UNDISPATCHED) {
                delay(reconnectTimeoutMs)
                onReconnectTimeout()
            }
        }
    }

    /**
     * Срабатывание тайм-аута реконнекта (10 с).
     */
    fun onReconnectTimeout() {
        if (_state.value == HandoffState.RECONNECTING) {
            reconnectTimeoutJob?.cancel()
            reconnectTimeoutJob = null
            _lastError.value = HandoffError.ConnectionLost
            _state.value = HandoffState.FAILED
        }
    }

    /**
     * Восстановление связи во время RECONNECTING.
     */
    fun onConnectionRestored() {
        if (_state.value == HandoffState.RECONNECTING) {
            reconnectTimeoutJob?.cancel()
            reconnectTimeoutJob = null
            _state.value = HandoffState.SOURCE_MUTED
        }
    }

    /**
     * Ручное или аварийное возобновление локального звука на текущем устройстве.
     */
    fun resumeLocally() {
        cancelAllJobs()
        _isLocalPlayback.value = true
        _state.value = HandoffState.IDLE
        sessionManager?.setRole(DeviceRole.PLAYER)
    }

    /**
     * Полный сброс машины состояний в исходное положение.
     */
    fun reset() {
        cancelAllJobs()
        _state.value = HandoffState.IDLE
        _isLocalPlayback.value = true
        _lastError.value = null
        _activeRequest.value = null
    }

    private fun cancelAllJobs() {
        ackTimeoutJob?.cancel()
        ackTimeoutJob = null
        crossfadeJob?.cancel()
        crossfadeJob = null
        reconnectTimeoutJob?.cancel()
        reconnectTimeoutJob = null
    }
}
