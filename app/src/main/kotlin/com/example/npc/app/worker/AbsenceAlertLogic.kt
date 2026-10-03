package com.example.npc.app.worker

/**
 * Logic helper for AbsenceAlertWorker.
 *
 * Extracted as a plain class so it can be unit-tested without WorkManager or Android context.
 * The full @HiltWorker CoroutineWorker lives in the :app production module and delegates
 * threshold checks to this helper.
 */
class AbsenceAlertLogic {

    /**
     * Returns true if the absence delta exceeds the threshold, meaning a notification
     * should be posted.
     *
     * @param lastEventEpochMs  Epoch-millis of the most-recent recorded event, or null if no
     *                          event has ever been recorded.
     * @param currentEpochMs    Current wall-clock epoch-millis (System.currentTimeMillis()).
     * @param thresholdMs       Threshold in millis above which absence is considered critical.
     *                          Defaults to 6 hours.
     */
    fun checkAbsence(
        lastEventEpochMs: Long?,
        currentEpochMs: Long,
        thresholdMs: Long = AbsenceAlertConfig().thresholdHours * 3_600_000L
    ): Boolean {
        if (lastEventEpochMs == null) {
            // No events ever recorded – treat as critical absence
            return true
        }
        return (currentEpochMs - lastEventEpochMs) > thresholdMs
    }

    /**
     * Calculates the silence duration in full hours since the last event.
     */
    fun silenceDurationHours(lastEventEpochMs: Long, currentEpochMs: Long): Long {
        val deltaMs = currentEpochMs - lastEventEpochMs
        return deltaMs / 3_600_000L
    }
}
