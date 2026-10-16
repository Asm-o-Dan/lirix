package com.lirix.app.connect.handoff

import com.lirix.app.connect.proto.HandoffFailure
import com.lirix.app.connect.proto.LirixConnectMsg

/**
 * Состояния FSM передачи воспроизведения (Handoff) (SPEC-CONNECT-01 §4.6, TASK-CON-HANDOFF-06).
 */
enum class HandoffState {
    /**
     * Исходное состояние: воспроизведение на текущем устройстве (или остановлено).
     */
    IDLE,

    /**
     * Отправлен HandoffRequest, ожидается HandoffAck от цели (таймаут 5 с).
     */
    REQUESTED,

    /**
     * Цель приняла запрос (HandoffAck accepted=true), запускает декодер/сеть.
     */
    TARGET_STARTING,

    /**
     * Цель начала воспроизведение (RemoteStateUpdate isPlaying=true), идёт кроссфейд 300 мс.
     */
    TARGET_PLAYING,

    /**
     * Кроссфейд завершён, локальный звук заглушен, устройство работает как пульт (CONTROLLER).
     */
    SOURCE_MUTED,

    /**
     * Цель отклонила прямой Handoff, но предложила Audio Cast (suggestCast=true).
     */
    SUGGEST_CAST,

    /**
     * Потеря связи во время управления удалённым хостом (автореконнект 10 с).
     */
    RECONNECTING,

    /**
     * Ошибка Handoff (отказ цели, фатальный сбой или истечение тайм-аута реконнекта).
     */
    FAILED
}

/**
 * Причина сбоя или отказа в процессе Handoff.
 */
sealed interface HandoffError {
    data class Rejected(val reason: HandoffFailure, val suggestCast: Boolean = false) : HandoffError
    data object Timeout : HandoffError
    data object ConnectionLost : HandoffError
    data class InvalidState(val message: String) : HandoffError
}

/**
 * Снимок контекста активного процесса Handoff.
 */
data class HandoffContext(
    val request: LirixConnectMsg.HandoffRequest? = null,
    val targetEndpointHex: String? = null,
    val requestedAtMs: Long = 0L,
    val error: HandoffError? = null,
    val isLocalPlayback: Boolean = true
)
