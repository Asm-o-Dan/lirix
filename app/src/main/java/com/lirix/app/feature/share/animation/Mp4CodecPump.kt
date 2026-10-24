package com.lirix.app.feature.share.animation

/** The same bounded, cancellation-aware state machine is used by the device codec and fake tests. */
internal interface Mp4CodecIo {
    fun dequeueInput(timeoutUs: Long): Int
    fun queueInput(index: Int, presentationTimeUs: Long, eos: Boolean)
    /** null means no output; an event may contain a video sample, EOS, or only a format change. */
    fun drainOutput(timeoutUs: Long): Mp4Output?
    fun finishMuxer()
    fun release()
}

internal data class Mp4Output(val samples: Int = 0, val eos: Boolean = false)

internal class Mp4CodecPump(
    private val io: Mp4CodecIo,
    private val fps: Int,
    private val checkActive: () -> Unit = {},
    private val nanoTime: () -> Long = System::nanoTime,
    private val timeoutNs: Long = 10_000_000_000L
) {
    var submittedFrames = 0L
        private set
    var writtenFrames = 0L
        private set
    private var eosSeen = false
    private var completed = false
    private var failed = false
    private var released = false

    fun submit(writeInput: (Int) -> Unit) = guarded {
        check(!completed && !failed) { "Encoder is closed" }
        val index = awaitInput(nanoTime())
        writeInput(index)
        checkActive()
        io.queueInput(index, submittedFrames * 1_000_000L / fps, false)
        submittedFrames++
        drainAvailable(nanoTime())
    }

    fun finish() {
        check(!failed) { "Encoder failed; partial output is unusable" }
        if (completed) return
        guarded {
            check(submittedFrames > 0) { "No video frames were submitted" }
            val start = nanoTime()
            val index = awaitInput(start)
            io.queueInput(index, submittedFrames * 1_000_000L / fps, true)
            while (!eosSeen) {
                checkDeadline(start)
                io.drainOutput(10_000)?.let(::record)
            }
            check(writtenFrames == submittedFrames) {
                "Incomplete video: queued $submittedFrames frames, muxed $writtenFrames"
            }
            io.finishMuxer()
            release()
            completed = true
        }
    }

    fun abort() {
        failed = true
        release()
    }

    private fun awaitInput(start: Long): Int {
        while (true) {
            checkDeadline(start)
            val index = io.dequeueInput(10_000)
            if (index >= 0) return index
            drainAvailable(start)
        }
    }

    private fun drainAvailable(start: Long) {
        while (true) {
            checkDeadline(start)
            val event = io.drainOutput(0) ?: return
            record(event)
        }
    }

    private fun record(event: Mp4Output) {
        writtenFrames += event.samples
        eosSeen = eosSeen || event.eos
    }

    private fun checkDeadline(start: Long) {
        checkActive()
        check(nanoTime() - start < timeoutNs) { "Video codec timed out" }
    }

    private fun release() {
        if (!released) {
            released = true
            io.release()
        }
    }

    private inline fun guarded(block: () -> Unit) {
        try { block() } catch (failure: Throwable) {
            failed = true
            try { release() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
}
