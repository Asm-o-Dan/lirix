package com.lirix.app

import com.lirix.app.analytics.WrappedStatsEngine
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.TrackEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistedListeningStatsTest {
    @Test fun persistedIntervalsAreNotExpandedAcrossAPause() {
        val track = TrackEntity("key", "Song", "Artist", sourcePackage = "player", totalDurationMs = 100_000)
        val session = ListeningSessionEntity(id = 1, trackKey = "key", sourcePackage = "player",
            startTimeMs = 1_000, endTimeMs = 3_000, durationMs = 2_000, isCompleted = false)
        val stats = WrappedStatsEngine.calculateStats(listOf(track), listOf(session), referenceTimestampMs = 20_000,
            includeLiveSessionEstimate = false)
        assertEquals(2_000L, stats.totalListeningTimeMs)
        assertEquals(2_000L, stats.topTracks.single().durationMs)
    }
}
