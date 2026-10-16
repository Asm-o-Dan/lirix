package com.lirix.app.connect.time

import com.lirix.app.connect.proto.LirixConnectMsg
import kotlin.math.max

/**
 * Опорная временная засечка воспроизведения трека (Anchor).
 */
data class PlaybackAnchor(
    val positionMs: Long,
    val positionAtMonoMs: Long,
    val durationMs: Long,
    val playbackRate: Float = 1.0f,
    val isPlaying: Boolean = true,
    val clockOffset: Long = 0L
)

/**
 * Интерполятор позиции воспроизведения трека (SPEC-CONNECT-01 §4.5, TASK-CON-TIME-04).
 *
 * Вычисляет точную локальную позицию караоке/суфлёра и целевого плеера в реальном времени:
 * targetPos = positionMs + playbackRate * (nowMonoMs - (positionAtMonoMs + offset))
 *
 * Инварианты:
 * - При паузе (isPlaying = false) позиция строго зафиксирована на positionMs.
 * - Ограничение диапазона: позиция зажата в пределах 0 .. durationMs.
 * - Устранение дрейфа при приходе периодических RemoteStateUpdate.
 */
class PlaybackPositionInterpolator(
    initialAnchor: PlaybackAnchor? = null
) {
    var anchor: PlaybackAnchor? = initialAnchor
        private set

    /**
     * Обновление опорной засечки напрямую.
     */
    fun setAnchor(
        positionMs: Long,
        positionAtMonoMs: Long,
        durationMs: Long,
        playbackRate: Float = 1.0f,
        isPlaying: Boolean = true,
        clockOffset: Long = 0L
    ) {
        anchor = PlaybackAnchor(
            positionMs = positionMs,
            positionAtMonoMs = positionAtMonoMs,
            durationMs = durationMs,
            playbackRate = playbackRate,
            isPlaying = isPlaying,
            clockOffset = clockOffset
        )
    }

    /**
     * Обновление засечки из входящего RemoteStateUpdate.
     */
    fun updateFromRemoteState(
        update: LirixConnectMsg.RemoteStateUpdate,
        clockOffset: Long = anchor?.clockOffset ?: 0L
    ) {
        setAnchor(
            positionMs = update.positionMs,
            positionAtMonoMs = update.positionAtMonoMs,
            durationMs = update.durationMs,
            playbackRate = update.playbackRate,
            isPlaying = update.isPlaying,
            clockOffset = clockOffset
        )
    }

    /**
     * Обновление засечки из входящего HandoffRequest.
     */
    fun updateFromHandoff(
        request: LirixConnectMsg.HandoffRequest,
        clockOffset: Long = anchor?.clockOffset ?: 0L
    ) {
        setAnchor(
            positionMs = request.positionMs,
            positionAtMonoMs = request.positionAtMonoMs,
            durationMs = request.durationMs,
            playbackRate = request.playbackRate,
            isPlaying = request.isPlaying,
            clockOffset = clockOffset
        )
    }

    /**
     * Вычисляет интерполированную позицию трека для заданного локального монотонного времени nowMonoMs.
     *
     * Если опорная точка не задана, возвращает 0L.
     */
    fun interpolate(nowMonoMs: Long): Long {
        val currentAnchor = anchor ?: return 0L
        return calculatePosition(
            positionMs = currentAnchor.positionMs,
            positionAtMonoMs = currentAnchor.positionAtMonoMs,
            durationMs = currentAnchor.durationMs,
            playbackRate = currentAnchor.playbackRate,
            isPlaying = currentAnchor.isPlaying,
            nowMonoMs = nowMonoMs,
            offset = currentAnchor.clockOffset
        )
    }

    companion object {
        /**
         * Чистая функция расчёта интерполированной позиции:
         * targetPos = positionMs + playbackRate * (nowMonoMs - (positionAtMonoMs + offset))
         */
        fun calculatePosition(
            positionMs: Long,
            positionAtMonoMs: Long,
            durationMs: Long,
            playbackRate: Float = 1.0f,
            isPlaying: Boolean = true,
            nowMonoMs: Long,
            offset: Long = 0L
        ): Long {
            val maxBound = if (durationMs > 0) durationMs else Long.MAX_VALUE
            val clampedBasePos = positionMs.coerceIn(0L, maxBound)

            if (!isPlaying) {
                return clampedBasePos
            }

            // Переводим метку времени отправителя в локальное монотонное время:
            // clockOffset = remoteTime - localTime  ==>  localTime = remoteTime - clockOffset
            val remoteTimestampInLocalTime = positionAtMonoMs - offset
            val elapsedLocalMs = nowMonoMs - remoteTimestampInLocalTime

            val deltaMs = (elapsedLocalMs * playbackRate).toLong()
            val rawCalculatedPos = clampedBasePos + deltaMs

            return rawCalculatedPos.coerceIn(0L, maxBound)
        }
    }
}
