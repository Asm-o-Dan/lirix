package com.lirix.app.connect.proto

import kotlinx.serialization.Serializable

/**
 * Роль устройства в активной сессии воспроизведения (SPEC-CONNECT-01 §4.3).
 */
enum class DeviceRole {
    /**
     * Источник истины воспроизведения: локально декодирует звук, исполняет команды,
     * рассылает RemoteStateUpdate.
     */
    PLAYER,

    /**
     * Пульт дистанционного управления: отправляет команды RemoteMediaAction,
     * интерполирует локальные строки караоке.
     */
    CONTROLLER
}

/**
 * Унифицированный конверт управляющих сообщений Lirix Connect (SPEC-CONNECT-01 §4.3).
 *
 * Передаётся внутри QUIC bi-stream "control".
 */
@Serializable
data class Envelope(
    /**
     * Монотонный строго возрастающий идентификатор сообщения отправителя.
     */
    val msgId: Long,

    /**
     * Идентификатор сообщения, на которое отправляется данный ответ (null для первичных запросов).
     */
    val inReplyTo: Long? = null,

    /**
     * Идентификатор активной сессии воспроизведения (UUID) или null вне активной сессии.
     */
    val sessionId: String? = null,

    /**
     * Поколение владения сессией. Инкрементируется при каждой смене PLAYER (Handoff).
     * Используется для защиты от устаревших команд (STALE_EPOCH).
     */
    val epoch: Long = 0,

    /**
     * Отметка монотонных часов отправителя в миллисекундах (для оценки RTT и синхронизации).
     */
    val sentAtMonoMs: Long,

    /**
     * Полезная нагрузка протокола.
     */
    val body: LirixConnectMsg
)
