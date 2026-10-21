package com.lirix.app.ui.components

/** Evidence for an editing bound; file evidence is valid only for its current decode generation. */
data class StudioDurationEvidence(
    val originalDurationMs: Long = 0,
    val sourceDurationMs: Long = 0,
    val fileGeneration: Long = 0,
    val decodedFileDurationMs: Long = 0
) {
    val effectiveMs: Long
        get() = sourceDurationMs.takeIf { it > 0 }
            ?: decodedFileDurationMs.takeIf { it > 0 }
            ?: originalDurationMs.coerceAtLeast(0)

    fun source(matchesTrack: Boolean, durationMs: Long) =
        copy(sourceDurationMs = if (matchesTrack) durationMs.coerceAtLeast(0) else 0)

    /** Replacement, removal and retry invalidate old evidence before any asynchronous decode. */
    fun invalidateFile() = copy(fileGeneration = fileGeneration + 1, decodedFileDurationMs = 0)

    fun decoded(generation: Long, durationMs: Long): StudioDurationEvidence =
        if (generation == fileGeneration) copy(decodedFileDurationMs = durationMs.coerceAtLeast(0)) else this
}
