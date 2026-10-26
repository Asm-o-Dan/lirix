package com.lirix.app

import com.lirix.app.feature.lyrics.*
import com.lirix.app.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class AutomaticLookupCoordinatorTest {
    private class Store : AutomaticLookupStore {
        val tracks = mutableMapOf("a" to TrackEntity("a", "A", "Artist", sourcePackage = "test"), "b" to TrackEntity("b", "B", "Artist", sourcePackage = "test"))
        val caches = mutableMapOf<String, LyricsCacheEntity>()
        override suspend fun track(key: String) = tracks[key]
        override suspend fun cache(key: String) = caches[key]
        override suspend fun rejected(key: String) = emptySet<String>()
        override suspend fun merge(key: String, candidate: AutomaticLookupCandidate): LyricsCacheEntity? {
            if (candidate.plain == null && candidate.chords == null) return caches[key]
            return LyricsCacheEntity(key, candidate.plain?.text, candidate.synced?.text, candidate.chords?.text, "notes", "builtin:test").also { caches[key] = it }
        }
    }
    @Test fun threeWaitersAllMissConsumeOneParentAndOneSharedChordFollowup() = runTest {
        val store = Store(); var lyrics = 0; var chords = 0
        val blocked = CompletableDeferred<Unit>()
        val coordinator = AutomaticLookupCoordinator(store, { _, target, _ ->
            if (target == LookupTarget.LYRICS) { lyrics++; blocked.await() } else { chords++; delay(10) }
            AutomaticLookupCandidate()
        }, backgroundScope, 1000)
        val parent = coordinator.acquireOrJoin("a", emptySet(), LookupOperation.LYRICS_WITH_CHORD_ENRICHMENT)
        val waiters = List(3) { async { coordinator.awaitTargetAttempt(parent, "a", emptySet(), LookupTarget.CHORDS) } }
        runCurrent(); assertEquals(1, lyrics); blocked.complete(Unit)
        waiters.awaitAll(); assertEquals(1, lyrics); assertEquals(1, chords)
        // Awaiting the same finished parent again cannot reacquire after a miss.
        coordinator.awaitTargetAttempt(parent, "a", emptySet(), LookupTarget.CHORDS)
        assertEquals(1, chords)
        assertEquals(LookupPhase.Incomplete, coordinator.lookup("a", LookupTarget.CHORDS, true))
        assertEquals(2, chords) // A new explicit attempt is permitted, no persistent negative cache.
    }
    @Test fun reverseCoverageAndTimeoutAlsoTerminateAllWaiters() = runTest {
        val store = Store(); var lyrics = 0; var chords = 0
        val coordinator = AutomaticLookupCoordinator(store, { _, target, _ ->
            if (target == LookupTarget.LYRICS) lyrics++ else chords++
            delay(500); AutomaticLookupCandidate()
        }, backgroundScope, 100)
        val parent = coordinator.acquireOrJoin("a", emptySet(), LookupOperation.MISSING_CHORDS)
        List(3) { async { coordinator.awaitTargetAttempt(parent, "a", emptySet(), LookupTarget.LYRICS) } }.awaitAll()
        assertEquals(1, lyrics); assertEquals(1, chords)
    }
    @Test fun offlineRecoveryRepeatedCallbacksAndPendingRecoveryWhileLoading() = runTest {
        val store = Store(); var calls = 0
        val first = CompletableDeferred<Unit>()
        val coordinator = AutomaticLookupCoordinator(store, { _, _, _ ->
            calls++
            if (calls == 1) { first.await(); AutomaticLookupCandidate() }
            else AutomaticLookupCandidate(plain = AutomaticLookupField("real words", "builtin:test"))
        }, backgroundScope, 5000)
        val session = ForegroundLookupRecovery("a", LookupTarget.LYRICS, coordinator, backgroundScope)
        session.onNetwork(LookupNetworkSnapshot(false, 0)); runCurrent()
        assertEquals(0, calls); assertEquals(LookupPhase.WaitingForNetwork, session.phase.value)
        session.onNetwork(LookupNetworkSnapshot(true, 1)); runCurrent(); assertEquals(1, calls)
        session.onNetwork(LookupNetworkSnapshot(false, 1)); session.onNetwork(LookupNetworkSnapshot(true, 2))
        repeat(100) { session.onNetwork(LookupNetworkSnapshot(true, 2)); session.retry() }
        runCurrent(); assertEquals(1, calls); first.complete(Unit); runCurrent()
        assertEquals(2, calls); assertEquals(LookupPhase.Available, session.phase.value)
        repeat(100) { session.onNetwork(LookupNetworkSnapshot(true, 2)) }; runCurrent(); assertEquals(2, calls)
        session.dispose()
    }
    @Test fun steadyUnknownDoesNotRetryAndDisposedTrackCannotPublishLateContent() = runTest {
        val store = Store(); var calls = 0; val block = CompletableDeferred<Unit>()
        val coordinator = AutomaticLookupCoordinator(store, { _, _, _ -> calls++; block.await(); AutomaticLookupCandidate() }, backgroundScope)
        val a = ForegroundLookupRecovery("a", LookupTarget.LYRICS, coordinator, backgroundScope)
        a.onNetwork(LookupNetworkSnapshot(true, 1)); runCurrent(); a.dispose()
        val b = ForegroundLookupRecovery("b", LookupTarget.LYRICS, coordinator, backgroundScope)
        b.onNetwork(LookupNetworkSnapshot(true, 1)); runCurrent(); block.complete(Unit); runCurrent()
        assertEquals(LookupPhase.Incomplete, b.phase.value)
        repeat(100) { b.onNetwork(LookupNetworkSnapshot(true, 1)) }; runCurrent(); assertEquals(2, calls)
        b.retry(); runCurrent(); assertEquals(3, calls); b.dispose()
    }
    @Test fun cancellingOneWaiterLeavesSharedAutomaticCallerWorking() = runTest {
        val store = Store(); var calls = 0; val block = CompletableDeferred<Unit>()
        val coordinator = AutomaticLookupCoordinator(store, { _, _, _ -> calls++; block.await(); AutomaticLookupCandidate(plain = AutomaticLookupField("persisted", "builtin:test")) }, backgroundScope)
        val foreground = async { coordinator.lookup("a", LookupTarget.LYRICS, true) }
        val overlay = async { coordinator.lookup("a", LookupTarget.LYRICS, true) }
        val prefetch = async { coordinator.lookup("a", LookupTarget.LYRICS, true) }
        runCurrent(); foreground.cancel(); block.complete(Unit)
        assertEquals(LookupPhase.Available, overlay.await()); assertEquals(LookupPhase.Available, prefetch.await()); assertEquals(1, calls)
    }

    @Test fun providerExceptionsConsumeCoverageAndExplicitRetryCanStartAgain() = runTest {
        val store = Store(); var lyrics = 0; var chords = 0
        val blocked = CompletableDeferred<Unit>()
        val coordinator = AutomaticLookupCoordinator(store, { _, target, _ ->
            if (target == LookupTarget.LYRICS) { lyrics++; blocked.await() } else chords++
            throw java.io.IOException("unclassified source error")
        }, backgroundScope, 1000)
        val parent = coordinator.acquireOrJoin("a", emptySet(), LookupOperation.LYRICS_WITH_CHORD_ENRICHMENT)
        val waiters = List(3) { async { coordinator.awaitTargetAttempt(parent, "a", emptySet(), LookupTarget.CHORDS) } }
        runCurrent(); blocked.complete(Unit); waiters.awaitAll()
        assertEquals(1, lyrics); assertEquals(1, chords)
        assertEquals(LookupPhase.Incomplete, coordinator.lookup("a", LookupTarget.CHORDS, true))
        assertEquals(2, chords)
    }

    @Test fun lateNonCancellableAcquisitionAfterDeadlineCannotCommitAndGateIsReusable() = runTest {
        val store = Store(); var calls = 0
        val coordinator = AutomaticLookupCoordinator(store, { _, _, _ ->
            calls++
            if (calls == 1) withContext(NonCancellable) { delay(200) }
            AutomaticLookupCandidate(plain = AutomaticLookupField("late response", "builtin:test"))
        }, backgroundScope, 100)
        assertEquals(LookupPhase.Incomplete, coordinator.lookup("a", LookupTarget.LYRICS, true))
        assertNull(store.caches["a"])
        // Subscriber returns at100ms; the application job still owns its entry through200ms unwind.
        assertEquals(100L, testScheduler.currentTime)
        advanceTimeBy(101); runCurrent()
        assertEquals(LookupPhase.Available, coordinator.lookup("a", LookupTarget.LYRICS, true))
        assertEquals("late response", store.caches["a"]!!.plainLyrics); assertEquals(2, calls)
    }

    @Test fun inlineChordWordsSatisfyTargetButPrivateNotesNeverDo() = runTest {
        val store = Store(); var requests = 0
        val coordinator = AutomaticLookupCoordinator(store, { _, _, _ -> requests++; AutomaticLookupCandidate() }, backgroundScope)
        store.tracks["a"] = store.tracks.getValue("a").copy(userNotes = "Am G\nprivate notes")
        assertEquals(LookupPhase.WaitingForNetwork, coordinator.lookup("a", LookupTarget.CHORDS, false))
        store.tracks["a"] = store.tracks.getValue("a").copy(plainLyrics = "Am G\nActual words for this song")
        assertEquals(LookupPhase.Available, coordinator.lookup("a", LookupTarget.CHORDS, true))
        assertEquals(0, requests)
    }

    @Test fun outageDuringLoadingPublishesWaitingWhenMissCompletesWithoutReconnect() = runTest {
        val store = Store(); val finish = CompletableDeferred<Unit>(); var calls = 0
        val coordinator = AutomaticLookupCoordinator(store, { _, _, _ -> calls++; finish.await(); AutomaticLookupCandidate() }, backgroundScope)
        val session = ForegroundLookupRecovery("a", LookupTarget.LYRICS, coordinator, backgroundScope)
        session.onNetwork(LookupNetworkSnapshot(true, 1)); runCurrent()
        session.onNetwork(LookupNetworkSnapshot(false, 1)); finish.complete(Unit); runCurrent()
        assertEquals(LookupPhase.WaitingForNetwork, session.phase.value); assertEquals(1, calls)
        session.onNetwork(LookupNetworkSnapshot(true, 2)); runCurrent()
        assertEquals(LookupPhase.Incomplete, session.phase.value); assertEquals(2, calls)
        session.dispose()
    }

    @Test fun subscriberDeadlineEndsLoadingWhileSharedBlockingUnwindRetainsOwnership() = runTest {
        for (offlineAtDeadline in listOf(false, true)) {
            val store = Store(); var calls = 0
            val coordinator = AutomaticLookupCoordinator(store, { _, _, _ ->
                calls++; withContext(NonCancellable) { delay(500) }
                AutomaticLookupCandidate(plain = AutomaticLookupField("late words", "builtin:test"))
            }, backgroundScope, 100)
            val session = ForegroundLookupRecovery("a", LookupTarget.LYRICS, coordinator, backgroundScope)
            val started = testScheduler.currentTime
            session.onNetwork(LookupNetworkSnapshot(true, 1)); runCurrent()
            advanceTimeBy(99); runCurrent(); assertEquals(LookupPhase.Loading, session.phase.value)
            if (offlineAtDeadline) session.onNetwork(LookupNetworkSnapshot(false, 1))
            advanceTimeBy(1); runCurrent()
            assertEquals(100L, testScheduler.currentTime - started)
            assertEquals(if (offlineAtDeadline) LookupPhase.WaitingForNetwork else LookupPhase.Incomplete, session.phase.value)
            val joined = coordinator.acquireOrJoin("a", emptySet(), LookupOperation.LYRICS_WITH_CHORD_ENRICHMENT)
            assertFalse(joined.completion.isCompleted); assertFalse(joined.completion.isCancelled)
            assertEquals(1, calls); assertNull(store.caches["a"])
            advanceTimeBy(401); runCurrent()
            assertTrue(joined.completion.await().timedOut); assertNull(store.caches["a"])
            assertEquals(if (offlineAtDeadline) LookupPhase.WaitingForNetwork else LookupPhase.Incomplete, session.phase.value)
            session.dispose()
        }
    }

    @Test fun provenLegacyNoteDuplicateIsExcludedButIndependentTrackWordsRemainAccepted() {
        val notes = "Private notes\nAm G"
        val cache = LyricsCacheEntity("a", notes, null, null, notes, "note:user")
        val polluted = TrackEntity("a", "Song", "Artist", sourcePackage = "test", userNotes = notes, plainLyrics = notes)
        assertFalse(automaticLookupSatisfied(LookupTarget.LYRICS, polluted, cache))
        assertFalse(automaticLookupSatisfied(LookupTarget.CHORDS, polluted, cache))
        val independent = polluted.copy(plainLyrics = "Actual independent song words")
        assertTrue(automaticLookupSatisfied(LookupTarget.LYRICS, independent, cache))
        assertEquals(independent.plainLyrics, automaticLookupAcceptedTrackField(independent.plainLyrics, independent, cache))
    }
}
