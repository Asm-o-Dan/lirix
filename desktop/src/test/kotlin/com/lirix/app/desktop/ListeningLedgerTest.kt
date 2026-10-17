package com.lirix.app.desktop

import com.lirix.app.desktop.library.*
import org.junit.Assert.*
import org.junit.Test

class ListeningLedgerTest {
    private fun song(playing: Boolean = true, position: Long = 0, key: String = "a") =
        ListeningSignal(key, playing, position, 180_000)

    @Test fun pauseDoesNotAccrueTimeAndShortResumeKeepsOnePlay() {
        val ledger = ListeningLedger()
        assertEquals("a", ledger.sample(song(), 0).startedKey)
        assertEquals(1_000, ledger.sample(song(false, 1_000), 1_000).elapsedMs)
        assertEquals(0, ledger.sample(song(false, 1_000), 20_000).elapsedMs)
        assertNull(ledger.sample(song(position = 1_000), 21_000).startedKey)
        assertEquals(1_000, ledger.sample(song(position = 2_000), 22_000).elapsedMs)
    }

    @Test fun seekAndRepeatedMetadataDoNotCreateTimeOrPlays() {
        val ledger = ListeningLedger()
        ledger.sample(song(), 10)
        val seek = ledger.sample(song(position = 100_000), 1_010)
        assertEquals(1_000, seek.elapsedMs)
        assertNull(seek.startedKey)
        assertNull(ledger.sample(song(position = 100_000), 1_010).startedKey)
        assertEquals(0, ledger.sample(song(position = 100_000), 1_010).elapsedMs)
    }

    @Test fun sleepIsNotListeningAndStartsANewSession() {
        val ledger = ListeningLedger()
        ledger.sample(song(), 0)
        val resumed = ledger.sample(song(position = 2_000), 600_000)
        assertEquals(0, resumed.elapsedMs)
        assertEquals("a", resumed.completedKey)
        assertEquals("a", resumed.startedKey)
    }

    @Test fun trackChangeAndReplayCreateNewPlays() {
        val ledger = ListeningLedger()
        ledger.sample(song(position = 179_000), 0)
        assertEquals("a", ledger.sample(song(position = 0), 1_000).startedKey)
        val change = ledger.sample(song(key = "b"), 2_000)
        assertEquals("a", change.completedKey)
        assertEquals("b", change.startedKey)
    }
}
