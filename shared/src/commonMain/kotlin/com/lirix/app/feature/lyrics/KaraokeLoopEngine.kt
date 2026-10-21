package com.lirix.app.feature.lyrics

data class KaraokeLoop(val startMs: Long, val endMs: Long, val awaitingSinceMs: Long? = null)
data class KaraokeLoopTick(val loop: KaraokeLoop?, val seekToMs: Long? = null, val error: String? = null)

/** One outstanding seek at a time, rearmed only by actual source position inside A..B. */
object KaraokeLoopEngine {
    fun start(startMs: Long, endMs: Long, durationMs: Long, nowMs: Long): KaraokeLoop? {
        if (startMs < 0 || endMs < startMs || endMs - startMs < 500 || nowMs < 0 || (durationMs > 0 && endMs > durationMs)) return null
        return KaraokeLoop(startMs, endMs, nowMs)
    }

    fun tick(loop: KaraokeLoop, rawPositionMs: Long, playing: Boolean, canSeek: Boolean, matchesTrack: Boolean, nowMs: Long): KaraokeLoopTick {
        if (!matchesTrack || !canSeek) return KaraokeLoopTick(null, error = "Повтор выключен: источник песни изменился")
        if (!playing) return KaraokeLoopTick(loop)
        val waiting = loop.awaitingSinceMs
        if (waiting != null) {
            if (rawPositionMs in loop.startMs until loop.endMs) return KaraokeLoopTick(loop.copy(awaitingSinceMs = null))
            if (nowMs < waiting || nowMs - waiting > 2500) return KaraokeLoopTick(null, error = "Источник не подтвердил перемотку. Повтор выключен.")
            return KaraokeLoopTick(loop)
        }
        if (rawPositionMs >= loop.endMs) return KaraokeLoopTick(loop.copy(awaitingSinceMs = nowMs), seekToMs = loop.startMs)
        if (rawPositionMs < loop.startMs - 500) return KaraokeLoopTick(null, error = "Повтор выключен после перемотки вне фрагмента")
        return KaraokeLoopTick(loop)
    }
}
