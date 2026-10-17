package com.lirix.app.desktop.library

/** Pure playback accounting. Wall-clock jumps and seeks never become listening time. */
data class ListeningSignal(val key: String, val playing: Boolean, val positionMs: Long, val durationMs: Long)
data class ListeningTick(
    val elapsedKey: String? = null, val elapsedMs: Long = 0,
    val completedKey: String? = null, val startedKey: String? = null
)

class ListeningLedger {
    private var previous: ListeningSignal? = null
    private var previousAt: Long? = null
    private var sessionKey: String? = null
    private var lastPlayingAt: Long? = null

    fun sample(signal: ListeningSignal?, monotonicMs: Long): ListeningTick {
        val old = previous
        // Sampling is normally once per second. Never count process suspension/sleep.
        val interval = previousAt?.let { (monotonicMs - it).takeIf { delta -> delta in 0..5_000 } } ?: 0
        val elapsed = if (old?.playing == true) interval else 0
        val replay = old != null && signal != null && old.key == signal.key &&
            old.durationMs > 0 && old.positionMs >= old.durationMs - 5_000 &&
            signal.positionMs < 5_000 && old.positionMs - signal.positionMs > 10_000
        val expired = lastPlayingAt?.let { monotonicMs - it > 30_000 } ?: true
        val starts = signal?.playing == true && (sessionKey != signal.key || replay || expired)
        val completed = if (starts) sessionKey else null
        if (starts) sessionKey = signal!!.key
        if (signal?.playing == true) lastPlayingAt = monotonicMs
        previous = signal
        previousAt = monotonicMs
        return ListeningTick(old?.key, elapsed, completed, signal?.key?.takeIf { starts })
    }
}
