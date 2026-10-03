package com.example.npc.feature.replay.engine

import com.example.npc.feature.replay.report.ReplayDiffReport
import java.time.Instant

sealed interface TimeRange {
    data object Last24Hours : TimeRange
    data object Last7Days : TimeRange
    data object AllTime : TimeRange
    data class Custom(val start: Instant, val end: Instant) : TimeRange {
        init {
            require(start <= end) { "start must be <= end" }
        }
    }
}

data class ReplayCriteria(
    val timeRange: TimeRange = TimeRange.Last7Days,
    val packageFilter: Set<String>? = null,
    val maxEventsLimit: Int = 1000,
    val chunkSize: Int = 150
) {
    init {
        require(maxEventsLimit > 0) { "maxEventsLimit must be > 0" }
        require(chunkSize > 0) { "chunkSize must be > 0" }
    }

    companion object {
        val Default = ReplayCriteria()
    }
}

sealed interface ReplayStatus {
    data object Idle : ReplayStatus

    data class Running(
        val processedCount: Int,
        val totalCount: Int,
        val percentProgress: Float,
        val currentEventId: Long
    ) : ReplayStatus

    data class Completed(
        val report: ReplayDiffReport
    ) : ReplayStatus

    data class Failed(
        val throwable: Throwable,
        val processedBeforeFailure: Int
    ) : ReplayStatus
}
